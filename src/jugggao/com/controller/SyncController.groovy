/**
 * Create by Peng.Gao at 2021/3/16 9:47
 *
 * A Part of the Project jenkins-shared-library
 */

package jugggao.com.controller

import jugggao.com.ci.Build
import jugggao.com.ci.Language
import jugggao.com.container.Harbor
import jugggao.com.kubernetes.KubeCommand
import jugggao.com.kubernetes.KubeYaml
import jugggao.com.resolution.Resolution
import jugggao.com.utils.config.ConfigService
import jugggao.com.utils.git.Git
import jugggao.com.utils.git.Gitlab
import jugggao.com.utils.logging.Logger
import jugggao.com.utils.notification.Notification
import org.jenkinsci.plugins.workflow.cps.CpsScript


import static jugggao.com.utils.config.ConfigConstants.*
import static jugggao.com.utils.InfoUtils.getReplicasNumber

class SyncController implements Serializable {

    private static Script script

    private Logger log = new Logger(this)

    private Language language

    private String commitId

    SyncController(Script script) {
        if (script instanceof CpsScript) {
            this.script = script
        }
    }

    void call(Map args, Closure body = {}) {
        script.ansiColor(ANSI_COLOR_XTERM) {

            script.timestamps {

                try {

                    initialStage(args)
                    codeClone()
                    doImage()
                    doKubernetes()

                    body()
                } catch (e) {
                    script.currentBuild['result'] = script.currentBuild['result'] ?: 'FAILURE'
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

                log.info "Initial Stage"

                script.checkout script.scm
                configMerge(args)

                String repo = ConfigService.data.git['repo']

                script.properties([
                    script.parameters([
                        script.listGitBranches(
                            remoteURL: repo,
                            credentialsId: GIT_CREDENTIALS,
                            branchFilter: 'refs/heads/(.*)',
                            defaultValue: 'dev',
                            listSize: '10',
                            name: 'GIT_BRANCH',
                            quickFilterEnabled: true,
                            selectedValue: 'NONE',
                            sortMode: 'DESCENDING_SMART',
                            tagFilter: '*',
                            type: 'PT_BRANCH',
                            description: 'Please select a branch to build'
                        ),
                        script.validatingString(
                            defaultValue: '',
                            description: 'A string of length 40 or empty',
                            failedValidationMessage: 'A string of length 40 or empty',
                            name: 'COMMIT_ID',
                            regex: '(\\w{40})|(^$)'
                        )
                    ]),
                    script.disableConcurrentBuilds(),
                ])

                Build build = new Build(script)
                build.init(ConfigService.data.build as Map)
                language = Build.language
                log.trace "Build Class Language: $language."

                script.stash(
                    allowEmpty: true,
                    includes: 'template/*',
                    name: 'template'
                )
            }
        }
    }

    private static void configMerge(Map args) {
        ConfigService config = new ConfigService(script)
        config.merge(args.settings as String)
    }

    private void codeClone() {
        script.node(GIT_STAGE_NODE) {
            script.stage('Git Clone') {

                log.info "Git Clone."

                Git git = new Git(script)

                String branch = ConfigService.data.git['branch']
                String repo = ConfigService.data.git['repo'] ?: git.getUrl()

                git.clone(repo, branch)

                if (script.params['COMMIT_ID']) {
                    git.checkoutCommitId(script.params['COMMIT_ID'] as String)
                }

                script.parallel(
                    'Update CommitID': { ConfigService.data['git']['commitID'] = git.getCommitID(40) },
                    'Update Author': { ConfigService.data['git']['author'] = git.getAuthor() },
                    'Update Message': { ConfigService.data['git']['message'] = git.getCommitMessage() },
                )
                currentBuildInfo()

                script.unstash 'template'
            }
        }
    }

    private static void currentBuildInfo() {
        String name = script.env['BUILD_NUMBER'] + ' ' + ConfigService.data.git['branch']
        String desc = ' Sync to ' + (ConfigService.data.base['env'] as String).toUpperCase() +
            ' by ' + ConfigService.data.base['user']

        script.currentBuild['displayName'] = name
        script.currentBuild['description'] = desc
    }

    private void doImage() {
        script.node(DOCKER_STAGE_NODE) {
            script.stage('Image') {

                log.info "Image Stage."

                String project = ConfigService.data['base']['project'] as String
                Integer projectId = ConfigService.data.git['projectId'] as Integer
                String branch = ConfigService.data.git['branch'] as String
                String appName = ConfigService.data['base']['name'] as String
                commitId = ConfigService.data.git['commitId'] as String

                Gitlab gitlab = new Gitlab(script, projectId)

                if (!commitId) {
                    commitId = gitlab.getBranchLastCommitId(branch)
                }

                String tag = branch.toLowerCase() + '-' + commitId[0..7].toLowerCase()

                Harbor harbor = new Harbor(script, project, appName)
                harbor.tagNotInBranchTagsPanic(branch, tag)

                String image = DOCKER_REGISTER + '/' + project.toLowerCase() + '/' + appName.toLowerCase() + ':' + tag
                ConfigService.data.docker['image'] = image

            }
        }
    }

    private void doKubernetes() {
        script.node(KUBERNETES_STAGE_NODE) {
            script.stage('Kubernetes') {
                script.dir((ConfigService.data['build']['buildDir'] + '/' + ConfigService.data['build']['appDir']) as String) {

                    log.info "Kubernetes Stage."

                    String environment = ConfigService.data['base']['env']
                    String namespace = ConfigService.data['kubernetes']['namespace']
                    String deployment = ConfigService.data['base']['name']
                    Integer replicas = getReplicasNumber(environment)
                    ArrayList webLanguages = [Language.NODEJS, Language.HTML, Language.PHP]

                    String deploymentFile = KUBERNETES_DEPLOYMENT_FILE
                    String serviceFile = KUBERNETES_SERVICE_FILE
                    String configMapFile = KUBERNETES_CONFIGMAP_FILE
                    String ingressFile = KUBERNETES_INGRESS_FILE

                    KubeYaml kubeYaml = new KubeYaml(script, ConfigService.data as Map)
                    script.parallel(
                        'Generate Deployment': { kubeYaml.genDeploymentFile(language) },
                        'Generate Service': { kubeYaml.genServiceFile(language) },
                        'Generate ConfigMap': { if (language in WEB_LANGUAGES) kubeYaml.genConfigmapFile(language) }
                    )

                    KubeCommand kubeCommand = new KubeCommand(script, environment)
                    script.parallel(
                        'Apply Deployment': { kubeCommand.applyDeployment() },
                        'Apply Service': { kubeCommand.applyService() },
                        'Apply ConfigMap': { if (language in WEB_LANGUAGES) kubeCommand.applyConfigMap() }
                    )
                    kubeCommand.waitForUpdateReady(namespace, deployment, replicas)

                    if (ConfigService.data['domain']['enabled']) {

                        if (ConfigService.data['domain']['tls']) {
                            ConfigService.data['domain']['tls'].each {
                                String secretName = it['cert'] as String
                                if (!kubeCommand.hasSecret(namespace, secretName)) {
                                    kubeYaml.genSecretFile(secretName) {
                                        kubeCommand.applySecret((secretName + '.yaml'))
                                    }
                                }
                            }
                        }

                        kubeYaml.genIngressFile(language) {
                            kubeCommand.applyIngress()
                        }

                        if (ConfigService.data['domain']['resolved']) {

                            Resolution resolution = new Resolution()

                            ConfigService.data['domain']['hosts'].each { host ->
                                String env = ConfigService.data['base']['env']
                                String subDomain = host['host']
                                ArrayList dnsServer = ConfigService.data.domain['dnsServer'] as ArrayList
                                resolution(script, dnsServer, subDomain, env)
                            }
                        }
                    }
                }
            }
        }
    }

    private void doAlwaysPost() {
        script.node(KUBERNETES_STAGE_NODE) {
            script.stage('Post Actions') {
                script.dir(ConfigService.data['base']['dir'] as String) {

                    log.info "Post Stage."

                    String jobType = ConfigService.data.base['type'] as String

                    if (ConfigService.data.notification[jobType]['enabled']) {
                        notification(jobType)
                    }
                }
            }
        }
    }

    private void notification(String jobType) {

        log.info "Notify."

        Map info = [
            'Job'     : (ConfigService.data.base['job'] as String),
            'Result'  : script.currentBuild['result'],
            'Date'    : new Date().format("yyyy-MM-dd HH:mm:ss")
        ]

        if (script.currentBuild['result'] == 'SUCCESS') {

            info.put('Branch', ConfigService.data.git['branch'])
            info.put('CommitID', ConfigService.data.git['commitID'])
            info.put('User', ConfigService.data.base['user'] as String)

            if (ConfigService.data.domain['enabled']) {
                ArrayList domains = []
                ConfigService.data.domain['hosts'].each {
                    String httpFlag = 'http://'
                    if (ConfigService.data.domain['tls']) httpFlag = 'https://'
                    domains.add(httpFlag + ConfigService.data.base['env'] + '-' + it['host'])
                }

                script.sh "echo domains: $domains"
                info.put('Domain', domains.join(', '))
            }

            if (ConfigService.data.domain['resolved']) {
                ArrayList dnsServer = []
                ConfigService.data.domain['dnsServer'].each {
                    dnsServer.add(it)
                }
                info.put('Resolution Server', dnsServer.join(', '))
            }

        }

        String notifyType = ConfigService.data.notification[jobType]['type'] as String

        Map config = ConfigService.data.notification[jobType][notifyType.toLowerCase()] as Map

        Notification notification = new Notification(script)
        notification.selector(notifyType, config)
        notification.notify(info)
    }
}