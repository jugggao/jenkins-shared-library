/**
 * Create by Peng.Gao at 2021/3/16 9:47
 *
 * A Part of the Project jenkins-shared-library
 */

package jugggao.com.controller

import jugggao.com.ci.Build
import jugggao.com.kubernetes.KubeCommand
import jugggao.com.utils.config.ConfigMultiService
import jugggao.com.utils.git.Gitlab
import jugggao.com.utils.logging.Logger
import jugggao.com.utils.notification.Notification
import org.jenkinsci.plugins.workflow.cps.CpsScript

import static jugggao.com.utils.CheckUtils.emptyVarPanic
import static jugggao.com.utils.InfoUtils.getReplicasNumber
import static jugggao.com.utils.config.ConfigConstants.*

class MultiUpdateController implements Serializable {

    private static Script script

    private Map config

    private Logger log = new Logger(this)

    private String currentDeploymentImage

    private String newDeploymentImage

    MultiUpdateController(Script script) {
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
                ConfigMultiService config = new ConfigMultiService(script)
                config.merge(args.settings as String)

                this.config = config.data

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
            }
        }
    }

    private void doImage() {
        script.node(DOCKER_STAGE_NODE) {
            script.stage('Image') {


                log.info "Confirm to release image."

                Build build = new Build(script)
                ArrayList<Map> buildSettings = config.build as ArrayList<Map>

                for (buildSetting in buildSettings) {

                    log.info "Build ${buildSetting.name}."

                    script.dir(buildSetting.buildDir as String) {
                        String buildCommand = buildSetting.command
                        build.init(buildSetting)
                    }
                }

                String namespace = config.kubernetes['namespace'] as String
                String deployment = config.base['name'] as String
                String project = config['base']['project'] as String
                String appName = config['base']['name'] as String
                String environment = 'prd-' + (config.kubernetes['cloud'] as ArrayList)[0]

                log.debug "Cloud: ${config.kubernetes['cloud']}"

                KubeCommand prdKubeCommand = new KubeCommand(script, environment)
                currentDeploymentImage = prdKubeCommand.getDeploymentImage(namespace, deployment)

                if (script.env['RELEASE']) {

                    String release = script.env['RELEASE']

                    ArrayList<Map> gitSettings = config.git as ArrayList<Map>

                    Integer projectId = gitSettings[0].projectId as Integer
                    Gitlab gitlab = new Gitlab(script, projectId)
                    gitlab.tagNotTagsPanic(release) {
                        newDeploymentImage = DOCKER_REGISTER + '/' + project.toLowerCase() + '-release/' +
                                appName.toLowerCase() + ':master-' + release
                    }

                } else {
                    KubeCommand kubeCommand = new KubeCommand(script, 'pre')
                    newDeploymentImage = kubeCommand.getDeploymentImage(namespace, deployment)
                    String nodes = kubeCommand.getNodes()
                    log.debug "Pre Nodes: $nodes"
                }

                config.docker['image'] = newDeploymentImage

                log.debug "Old Image: $currentDeploymentImage, New Image: $newDeploymentImage"

                if (currentDeploymentImage == newDeploymentImage) {
                    log.warn "New deployment image same as current deployment image, no need to update."
                    script.currentBuild['result'] = 'UNSTABLE'
                }

                modifyCurrentBuildInfo()
            }
        }
    }

    private void modifyCurrentBuildInfo() {

        String name = "${script.env['BUILD_NUMBER']} master"
        String desc = (config.base['type'] as String).capitalize() + ' to ' +
                (config.base['env'] as String).toUpperCase() + ' by ' + config.base['user']

        script.currentBuild['displayName'] = name
        script.currentBuild['description'] = desc
    }

    private void doKubernetes() {
        script.node(KUBERNETES_STAGE_NODE) {
            script.stage('Kubernetes') {

                ArrayList clouds = config.kubernetes['cloud'] as ArrayList

                String namespace = config.kubernetes['namespace']
                String deployment = config.base['name']

                emptyVarPanic(script, 'newDeploymentImage', newDeploymentImage) {

                    clouds.each { cloud ->

                        String environment = config.base['env'] + '-' + cloud
                        Integer replicas = getReplicasNumber(environment)

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

                ArrayList clouds = config.kubernetes['cloud'] as ArrayList

                String namespace = config.kubernetes['namespace']
                String deployment = config.base['name']

                emptyVarPanic(script, 'currentDeploymentImage', currentDeploymentImage) {
                    clouds.each { cloud ->

                        String environment = config.base['env'] + '-' + cloud
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

                String jobType = config.base['type'] as String

                if (config.notification[jobType]['enabled']) {
                    notification(jobType)
                }

            }
        }
    }

    private void notification(String jobType) {

        Map info = [
                'Job'   : (config.base['job'] as String),
                'Result': script.currentBuild['result'],
                'User'  : config.base['user'],
                'Date'  : new Date().format("yyyy-MM-dd HH:mm:ss")
        ]

        if (script.currentBuild['result'] == 'SUCCESS') {
            info.put('Change', currentDeploymentImage + ' --> ' + newDeploymentImage)
        }

        String notifyType = config.notification[jobType]['type'] as String

        Map config = config.notification[jobType][notifyType.toLowerCase()] as Map

        Notification notification = new Notification(script)
        notification.selector(notifyType, config)
        notification.notify(info)
    }
}
