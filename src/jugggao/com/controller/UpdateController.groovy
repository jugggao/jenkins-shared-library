/**
 * Create by Peng.Gao at 2021/3/16 9:47
 *
 * A Part of the Project jenkins-shared-library
 */

package jugggao.com.controller

import jugggao.com.ci.Build
import jugggao.com.kubernetes.KubeCommand
import jugggao.com.utils.config.ConfigService
import jugggao.com.utils.git.Gitlab
import jugggao.com.utils.logging.Logger
import jugggao.com.utils.notification.Notification
import org.jenkinsci.plugins.workflow.cps.CpsScript

import static jugggao.com.utils.InfoUtils.getReplicasNumber
import static jugggao.com.utils.config.ConfigConstants.*
import static jugggao.com.utils.CheckUtils.emptyVarPanic


class UpdateController implements Serializable {

    private static Script script

    private Logger log = new Logger(this)

    private String currentDeploymentImage

    private String newDeploymentImage

    UpdateController(Script script) {
        if (script instanceof CpsScript) {
            this.script = script
        }
    }

    void call(Map args, Closure body = {}) {
        script.ansiColor(ANSI_COLOR_XTERM) {

            script.timestamps {

                try {

                    initialStage(args)
                    doImage()
                    doKubernetes()

                    body()
                } catch (e) {
                    script.currentBuild['result'] = script.currentBuild['result'] ?: 'FAILURE'

                    doRollback()

                    throw e
                } finally {
                    script.currentBuild['result'] = script.currentBuild['result'] ?: 'SUCCESS'
                    def currentResult = script.currentBuild['result'] ?: 'SUCCESS'
                    if (currentResult == 'UNSTABLE') {
                        script.echo 'This will run only if the run was marked as unstable'
                    }

                    doAlwaysPost()
                }
            }
        }
    }

    private void initialStage(Map args) {
        script.node('master') {
            script.stage('Initial Stage') {

                log.info "Initial Stage."

                script.checkout script.scm
                configMerge(args)

                script.properties([
                    script.parameters([
                        script.validatingString(
                            defaultValue: '',
                            description: 'A integer of length 12 or empty',
                            failedValidationMessage: 'A integer of length 12 or empty',
                            name: 'RELEASE',
                            regex: '([0-9]{12})|(^$)'
                        )
                    ]),
                    script.disableConcurrentBuilds(),
                ])

                Build build = new Build(script)
                build.init(ConfigService.data['build'] as Map)

                modifyCurrentBuildInfo()

            }
        }
    }


    private static void configMerge(Map args) {
        ConfigService config = new ConfigService(script)
        config.merge(args.settings as String)
    }

    private static void modifyCurrentBuildInfo() {

        String name = "${script.env['BUILD_NUMBER']} master"
        String desc = (ConfigService.data.base['type'] as String).capitalize() + ' to ' +
            (ConfigService.data.base['env'] as String).toUpperCase() + ' by ' + ConfigService.data.base['user']

        script.currentBuild['displayName'] = name
        script.currentBuild['description'] = desc
    }

    private void doImage() {
        script.node(DOCKER_STAGE_NODE) {
            script.stage('Image') {

                log.info "Confirm to release image."

                String namespace = ConfigService.data.kubernetes['namespace'] as String
                String deployment = ConfigService.data.base['name'] as String
                String project = ConfigService.data['base']['project'] as String
                Integer projectId = ConfigService.data.git['projectId'] as Integer
                String appName = ConfigService.data['base']['name'] as String
                String environment = 'prd-' + (ConfigService.data['kubernetes']['cloud'] as ArrayList)[0]

                log.debug "Cloud: ${ConfigService.data.kubernetes['cloud']}"

                if (environment == 'prd-google-cloud') {
                    script.node('google-cloud-build') {
                        KubeCommand prdKubeCommand = new KubeCommand(script, environment)
                        currentDeploymentImage = prdKubeCommand.getDeploymentImage(namespace, deployment)
                    }
                } else {
                    KubeCommand prdKubeCommand = new KubeCommand(script, environment)
                    currentDeploymentImage = prdKubeCommand.getDeploymentImage(namespace, deployment)
                }

                if (script.env['RELEASE']) {

                    String release = script.env['RELEASE']

                    Gitlab gitlab = new Gitlab(script, projectId)
                    gitlab.tagNotTagsPanic(release) {
                        newDeploymentImage = DOCKER_REGISTER + '/' + project.toLowerCase() + '-release/' +
                            appName.toLowerCase() + ':master-' + release
                    }

                } else {
                    String env = 'pre'
                    if (ConfigService.data.base['env'] == 'test') env = 'test'
                    KubeCommand kubeCommand = new KubeCommand(script, env)
                    newDeploymentImage = kubeCommand.getDeploymentImage(namespace, deployment)
                    String nodes = kubeCommand.getNodes()
                    log.debug "Pre Nodes: $nodes"
                }

                ConfigService.data.docker['image'] = newDeploymentImage

                log.debug "Old Image: $currentDeploymentImage, New Image: $newDeploymentImage"

                if (currentDeploymentImage == newDeploymentImage) {
                    log.warn "New deployment image same as current deployment image, no need to update."
                    script.currentBuild['result'] = 'UNSTABLE'
                }
            }
        }
    }

    private void doKubernetes() {

        String kubernetesStageNode
        ArrayList clouds = ConfigService.data.kubernetes['cloud'] as ArrayList

        if (clouds.contains('google-cloud')) {
            kubernetesStageNode = 'google-cloud-build'
        } else {
            kubernetesStageNode = KUBERNETES_STAGE_NODE
        }

        script.node(kubernetesStageNode) {
            script.stage('Kubernetes') {

                // ArrayList clouds = ConfigService.data.kubernetes['cloud'] as ArrayList

                String namespace = ConfigService.data.kubernetes['namespace']
                String deployment = ConfigService.data.base['name']

                emptyVarPanic(script, 'newDeploymentImage', newDeploymentImage) {

                    clouds.each { cloud ->

                        String environment = 'prd-' + cloud
                        Integer replicas = ConfigService.data.kubernetes['replicas'] ?: getReplicasNumber(environment)

                        log.debug "clouds: $clouds\nenvironment: $environment\nimage:$newDeploymentImage\nReplicas: $replicas"

                        KubeCommand kubeCommand = new KubeCommand(script, environment)

                        try {
                            kubeCommand.updateDeploymentImage(
                                    namespace: namespace,
                                    deployment: deployment,
                                    image: newDeploymentImage
                            )
                            kubeCommand.scaleDeploymentReplicas(
                                    namespace: namespace,
                                    deployment: deployment,
                                    replicas: replicas
                            )
                            kubeCommand.waitForUpdateReady(namespace, deployment, replicas)
                        } catch (e) {
                            log.error "Failed to update on $environment."
                            throw e
                        }
                    }
                }
            }
        }
    }

    private void doRollback() {
        script.node(KUBERNETES_STAGE_NODE) {
            script.stage('Rollback Stage') {
                log.info "Rollback Stage."

                ArrayList clouds = ConfigService.data.kubernetes['cloud'] as ArrayList

                String namespace = ConfigService.data.kubernetes['namespace']
                String deployment = ConfigService.data.base['name']

                emptyVarPanic(script, 'currentDeploymentImage', currentDeploymentImage) {
                    clouds.each { cloud ->

                        String environment = 'prd-' + cloud
                        Integer replicas = getReplicasNumber(environment)

                        KubeCommand kubeCommand = new KubeCommand(script, environment)

                        try {
                            kubeCommand.updateDeploymentImage(
                                    namespace: namespace,
                                    deployment: deployment,
                                    image: currentDeploymentImage
                            )
                            kubeCommand.waitForUpdateReady(namespace, deployment, replicas)
                        } catch (e) {
                            log.error "Rollback failed on $environment."
                            throw e
                        }
                    }
                }
            }
        }
    }

    private void doAlwaysPost() {
        script.node(KUBERNETES_STAGE_NODE) {
            script.stage('Post Actions') {

                log.info "Post Stage."

                String jobType = ConfigService.data.base['type'] as String

                if (ConfigService.data.notification[jobType]['enabled']) {
                    notification(jobType)
                }

            }
        }
    }

    private void notification(String jobType) {

        Map info = [
            'Job'   : (ConfigService.data.base['job'] as String),
            'Result': script.currentBuild['result'],
            'User'  : ConfigService.data.base['user'],
            'Date'  : new Date().format("yyyy-MM-dd HH:mm:ss")
        ]

        if (script.currentBuild['result'] == 'SUCCESS') {
            info.put('Change', currentDeploymentImage + ' --> ' + newDeploymentImage)
        }

        String notifyType = ConfigService.data.notification[jobType]['type'] as String

        Map config = ConfigService.data.notification[jobType][notifyType.toLowerCase()] as Map

        Notification notification = new Notification(script)
        notification.selector(notifyType, config)
        notification.notify(info)
    }
}
