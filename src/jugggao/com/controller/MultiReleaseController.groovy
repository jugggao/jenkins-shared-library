package jugggao.com.controller

import jugggao.com.ci.Build
import jugggao.com.container.Docker
import jugggao.com.kubernetes.KubeCommand
import jugggao.com.kubernetes.KubeYaml
import jugggao.com.resolution.Resolution
import jugggao.com.utils.CheckUtils
import jugggao.com.utils.config.ConfigMultiService
import jugggao.com.utils.git.Git
import jugggao.com.utils.git.Gitlab
import jugggao.com.utils.logging.Logger
import jugggao.com.utils.notification.Notification
import org.jenkinsci.plugins.workflow.cps.CpsScript

import static jugggao.com.ci.Build.language
import static jugggao.com.utils.CommonUtils.cleanDir
import static jugggao.com.utils.CommonUtils.makeDir
import static jugggao.com.utils.InfoUtils.getFirstDirectory
import static jugggao.com.utils.InfoUtils.getReplicasNumber
import static jugggao.com.utils.config.ConfigConstants.*

class MultiReleaseController {

    Logger log = new Logger(this)

    private static Script script

    private Map config

    private String release

    MultiReleaseController(Script script) {
        if (script instanceof CpsScript) {
            this.script = script
        }
    }

    void call(Map args, Closure body = {}) {

        script.ansiColor(ANSI_COLOR_XTERM) {

            script.timestamps {

                try {

                    initialStage(args)

                    version()

                    codeClone()

                    codeBuild()

                    doDocker()

                    if (config.kubernetes['enabled']) doKubernetes()

                    doGitlabTag()

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

                script.properties([
                        script.parameters([
                                script.validatingString(
                                        defaultValue: '',
                                        description: 'A string or empty',
                                        failedValidationMessage: 'A string or empty. Supported characters: [a-zA-Z0-9._-]',
                                        name: 'RELEASE',
                                        regex: '([a-zA-Z0-9._-]+)|(^$)'
                                ),
                                script.booleanParam(
                                        defaultValue: false,
                                        name: 'CLEAN',
                                        description: 'Clear build directory'
                                ),
                        ]),
                        script.disableConcurrentBuilds(),
                ])

                ConfigMultiService config = new ConfigMultiService(script)
                config.merge(args.settings as String)

                this.config = config.data
                release = (script.params['RELEASE']) ?: new Date().format("yyyyMMddHHmm") as String

                script.stash(
                        allowEmpty: true,
                        includes: 'template/*',
                        name: 'template'
                )
            }
        }
    }

    private void version() {
        script.node(GIT_STAGE_NODE) {
            script.stage('Version') {

                ArrayList<Map> gitSettings = config.git as ArrayList<Map>

                for (gitSetting in gitSettings) {
                    Integer projectId = gitSetting.projectId as Integer
                    Gitlab gitlab = new Gitlab(script, projectId)
                    ArrayList gitlabTags = gitlab.getTags()

                    if (release in gitlabTags) {
                        script.input(
                                id: 'isOverwriteGitlabTag',
                                message: 'The ' + release + ' tag already exists in gitlab, Overwrite?',
                                ok: 'Overwrite'
                        )

                        gitlab.deleteTag(release)

                        String environment = config.base['env']
                        String namespace = config.kubernetes['namespace']
                        String deployment = config.base['name']

                        KubeCommand kubeCommand = new KubeCommand(script, environment)
                        kubeCommand.deleteDeployment(namespace, deployment)
                    }
                }
            }
        }
    }

    private void codeClone() {
        script.node(GIT_STAGE_NODE) {
            script.stage('Git Clone') {

                log.info "Git Clone Stage."

                if (script.params['CLEAN']) {

                    String dir = getFirstDirectory(script)

                    log.info "Clean dir: $dir."
                    CheckUtils checkUtils = new CheckUtils()

                    checkUtils.notDirectoryPanic(script, dir) {
                        cleanDir(script, dir)
                    }
                }

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

                    Integer projectId = gitSetting.projectId as Integer
                    Gitlab gitlab = new Gitlab(script, projectId)

                    ArrayList projectHooks = gitlab.getProjectHook()
                    if (!(GITLAB_WEBHOOK_URL in projectHooks)) {
                        gitlab.addProjectHook()
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

    private void codeBuild() {
        script.node(BUILD_STAGE_NODE) {
            script.stage('Build Code') {

                log.info "Build Code Stage."

                Build build = new Build(script)
                ArrayList<Map> buildSettings = config.build as ArrayList<Map>

                for (buildSetting in buildSettings) {

                    log.info "Build ${buildSetting.name}."

                    script.dir(buildSetting.buildDir as String) {
                        String buildCommand = buildSetting.command
                        build.init(buildSetting)
                        build.build(buildCommand)
                    }
                }
            }
        }
    }

    private void doDocker() {
        script.node(DOCKER_STAGE_NODE) {
            script.stage('Docker') {

                log.info "Docker Stage."

                Docker docker = new Docker(script)

                ArrayList branchInfo = []
                ArrayList releaseInfo = []
                ArrayList<Map> gitSettings = config.git as ArrayList<Map>

                log.debug "Git Settings: $gitSettings"

                gitSettings.each {
                    branchInfo.add(it.name + '-' + it.branch + '-' + (it.commitID as String)[0..7])
                }

                gitSettings.each {
                    releaseInfo.add(it.name + '-' + it.branch)
                }

                String image = DOCKER_REGISTER + '/' +
                        (config.base['project'] as String).toLowerCase() + '/' +
                        (config.base['name'] as String).toLowerCase() + ':' +
                        branchInfo.join('-')
                String content = config.docker['content'] ?: '.'
                String buildUser = config.base['userid'] as String
                String dockerfile = config.docker['dockerfile'] as String
                String ignorefile = config.docker['ignorefile'] as String
                String chown = config.docker['chown'] as String

                String releaseRepository = DOCKER_REGISTER + '/' +
                        (config.base['project'] as String).toLowerCase() + '-release/' +
                        (config.base['name'] as String).toLowerCase()
                String releaseTag = releaseInfo.join('-') + '-' + release
                String releaseImage = releaseRepository + ':' + releaseTag

                ArrayList<Map> contents = []
                ArrayList<Map> buildSettings = config.build as ArrayList<Map>

                log.debug "Build Settings: $buildSettings"

                buildSettings.each {
                    contents.add([name: it.buildDir as String, path: it.buildDir + '/' + it.appDir])
                }

                log.debug "Contents: $contents"

                docker.withInfo(
                        image: image, userID: buildUser, options: '',
                        content: content, dockerfile: dockerfile,
                        ignorefile: ignorefile, chown: chown
                ) {
                    script.parallel(
                            'Generate Dockerfile': {
                                docker.genDockerfile(language)
                                docker.genMultiServiceDockerfile(contents)
                            },
                            'Generate .dockerignore': {
                                docker.genDockerIgnoreFile(language)
                            },
                            'Login to Registry': { docker.login() }
                    )

                    config.docker['image'] = docker.build()
                    docker.push()
                    docker.reTag(releaseImage)
                    docker.push(releaseImage)
                }

                config.docker['image'] = releaseImage
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

                String deploymentFile = KUBERNETES_DEPLOYMENT_FILE
                String serviceFile = KUBERNETES_SERVICE_FILE
                String configMapFile = KUBERNETES_CONFIGMAP_FILE
                String ingressFile = KUBERNETES_INGRESS_FILE

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

    private void doGitlabTag() {
        script.node(GIT_STAGE_NODE) {
            script.stage('Gitlab Tag') {

                log.info "Gitlab Tag Stage."

                ArrayList<Map> gitSettings = config.git as ArrayList<Map>

                for (gitSetting in gitSettings) {
                    String commitId = gitSetting.commitID as String
                    Integer projectId = gitSetting.projectId as Integer

                    Gitlab gitlab = new Gitlab(script, projectId)
                    gitlab.createNewTag(release, commitId)
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
            info.put('User', config.base['user'] as String)
            info.put('Release', release)

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