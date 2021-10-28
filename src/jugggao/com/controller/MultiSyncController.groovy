package jugggao.com.controller

import jugggao.com.ci.Build
import jugggao.com.ci.Language
import jugggao.com.container.Harbor
import jugggao.com.kubernetes.KubeCommand
import jugggao.com.kubernetes.KubeYaml
import jugggao.com.resolution.Resolution
import jugggao.com.utils.config.ConfigMultiService
import jugggao.com.utils.git.Git
import jugggao.com.utils.logging.Logger
import jugggao.com.utils.notification.Notification
import org.jenkinsci.plugins.workflow.cps.CpsScript

import static jugggao.com.utils.CommonUtils.makeDir
import static jugggao.com.utils.InfoUtils.getReplicasNumber
import static jugggao.com.utils.config.ConfigConstants.*

class MultiSyncController {

    Logger log = new Logger(this)

    private static Script script

    private Map config

    private Language language

    private String tag

    private String image

    MultiSyncController(Script script) {
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

                log.info "Initial Stage."

                try {
                    script.checkout(script.scm)
                } catch (e) {
                    log.error "Failed to checkout scm."
                    throw e
                }

                ConfigMultiService config = new ConfigMultiService(script)
                config.merge(args.settings as String)

                ArrayList<Map> gitConfig = config.data.git as ArrayList<Map>
                ArrayList parameters = []

                for (i in gitConfig) {
                    parameters.add(
                            script.listGitBranches(
                                    remoteURL: i.repo as String,
                                    credentialsId: GIT_CREDENTIALS,
                                    branchFilter: 'refs/heads/(.*)',
                                    defaultValue: i.branch as String,
                                    name: (i.name as String).toUpperCase() + '_GIT_BRANCH',
                                    quickFilterEnabled: true,
                                    selectedValue: 'NONE',
                                    sortMode: 'DESCENDING_SMART',
                                    tagFilter: '*',
                                    type: 'PT_BRANCH',
                                    description: 'Please select a branch to build'
                            )
                    )
                }

                script.properties([
                        script.parameters(parameters),
                        script.disableConcurrentBuilds(),
                ])



                this.config = config.data

                script.stash(
                        allowEmpty: true,
                        includes: 'template/*',
                        name: 'template'
                )
            }
        }
    }

    private void codeClone() {
        script.node(GIT_STAGE_NODE) {
            script.stage('Git Clone') {

                log.info "Git Clone Stage."

                Git git = new Git(script)
                ArrayList<Map> gitSettings = config.git as ArrayList<Map>

                for (gitSetting in gitSettings) {

                    makeDir(script, gitSetting.name as String)
                    script.dir(gitSetting.name as String) {

                        String branch = gitSetting.branch
                        String repo = gitSetting.repo ?: git.getUrl()

                        git.clone(repo, branch)

                        script.parallel(
                                'Update CommitID': { gitSetting.commitID = git.getCommitID(40) },
                                'Update Author': { gitSetting['author'] = git.getAuthor() },
                                'Update Message': { gitSetting['message'] = git.getCommitMessage() },
                        )
                    }
                }

                log.debug "Config: $config"

                currentBuildInfo()

                script.unstash 'template'
            }
        }
    }

    private void currentBuildInfo() {

        String user = config.base['user'] ?: 'webhook'

        ArrayList branchInfo = []
        ArrayList<Map> gitSettings = config.git as ArrayList<Map>

        gitSettings.each {
            branchInfo.add(it.name + ': ' + it.branch)
        }

        String name = script.env['BUILD_NUMBER'] + ' ' + branchInfo.join(' ')
        String desc = (config.base['action'] as String).capitalize() + ' to ' +
                (config.base['env'] as String).toUpperCase() + ' by ' + user

        script.currentBuild['displayName'] = name
        script.currentBuild['description'] = desc
    }

    private void doImage() {
        script.node(BUILD_STAGE_NODE) {
            script.stage('Image') {

                log.info "Image Stage."

                ArrayList branchInfo = []
                ArrayList<Map> gitSettings = config.git as ArrayList<Map>
                gitSettings.each {
                    branchInfo.add(it.name + '-' + it.branch + '-' + (it.commitID as String)[0..7])
                }
                tag = branchInfo.join('-')

                String project = config.base['project'] as String
                String appName = config.base['name'] as String
                Harbor harbor = new Harbor(script, project, appName)
                harbor.tagNotInBranchTagsPanic(gitSettings, tag)

                Build build = new Build(script)
                ArrayList<Map> buildSettings = config.build as ArrayList<Map>
                build.init(buildSettings[0] as Map)
                language = Build.language

                log.debug "Tag: $tag."

                String image = DOCKER_REGISTER + '/' + project.toLowerCase() + '/' + appName.toLowerCase() + ':' + tag
                config.docker['image'] = image


            }
        }
    }

    private void doKubernetes() {
        script.node(KUBERNETES_STAGE_NODE) {
            script.stage('Kubernetes') {

                log.info "Kubernetes Stage."

                String environment = config.base['env']
                String namespace = config.kubernetes['namespace']
                String deployment = config.base['name']
                Integer replicas = getReplicasNumber(environment)

                log.debug "Language: $language"

                KubeYaml kubeYaml = new KubeYaml(script, config)
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

                if (config.domain['enabled']) {

                    if (config.domain['tls']) {
                        config.domain['tls'].each {
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

                    if (config.domain['resolved']) {

                        Resolution resolution = new Resolution()

                        config.domain['hosts'].each { host ->
                            String env = config.base['env']
                            String subDomain = host['host']
                            ArrayList dnsServer = config.domain['dnsServer'] as ArrayList
                            resolution(script, dnsServer, subDomain, env)
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
                'Date'  : new Date().format("yyyy-MM-dd HH:mm:ss")
        ]

        if (script.currentBuild['result'] == 'SUCCESS') {

            ArrayList branchInfo = []
            ArrayList<Map> gitSettings = config.git as ArrayList<Map>

            gitSettings.each {
                branchInfo.add(it.name + ': ' + it.branch + '-' + (it.commitID as String)[0..7])
            }

            info.put('BranchInfo', branchInfo.join(' '))
            info.put('User', config.base['user'])

            if (config.domain['enabled']) {
                ArrayList domains = []
                config.domain['hosts'].each {
                    String httpFlag = 'http://'
                    if (config.domain['tls']) httpFlag = 'https://'
                    domains.add(httpFlag + config.base['env'] + '-' + it['host'])
                }
                script.sh "echo domains: $domains"
                info.put('Domain', domains.join(', '))
            }
        }

        String notifyType = config.notification[jobType]['type'] as String

        Map config = config.notification[jobType][notifyType.toLowerCase()] as Map

        Notification notification = new Notification(script)
        notification.selector(notifyType, config)
        notification.notify(info)
    }
}