/**
 * Create by Peng.Gao at 2021/2/25
 *
 * A Part of the Project jenkins-shared-library*/

package jugggao.com.controller

import jugggao.com.ci.Language
import jugggao.com.ci.SonarQube
import jugggao.com.container.Docker
import jugggao.com.kubernetes.KubeCommand
import jugggao.com.kubernetes.KubeYaml
import jugggao.com.resolution.Resolution
import jugggao.com.utils.CheckUtils
import jugggao.com.utils.config.ConfigService
import jugggao.com.utils.git.Git
import jugggao.com.ci.Build
import jugggao.com.utils.git.Gitlab
import jugggao.com.utils.logging.Logger
import jugggao.com.utils.notification.Notification
import org.jenkinsci.plugins.workflow.cps.CpsScript

import static jugggao.com.ci.Build.language
import static jugggao.com.ci.Build.tool
import static jugggao.com.utils.config.ConfigConstants.*
import static jugggao.com.utils.InfoUtils.*
import static jugggao.com.utils.CommonUtils.cleanDir

class ServiceController implements Serializable {

    Logger log = new Logger(this)

    private static Script script

    ServiceController(Script script) {
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

                    if (((ConfigService.data['build']['language'] as String).toLowerCase()) in SCAN_AFTER_BUILD) {
                        codeBuild()
                        if (ConfigService.data['scanner']['enabled']) sonarScan()
                        script.parallel(
                                // 'Test': { codeTest() },
                                //'Sonar Scan': { sonarScan() },
                                'Docker': { doDocker() }
                        )

                    } else {
                        if (ConfigService.data['scanner']['enabled']) sonarScan()
                        codeBuild()
                        script.parallel(
                                // 'Test': { codeTest() },
                                'Docker': { doDocker() }
                        )
                    }

                    if (ConfigService.data['kubernetes']['enabled']) doKubernetes()

                    body()

                    // Pipeline post success stage
                    // script.currentBuild['result'] = script.currentBuild['result'] ?: 'SUCCESS'
                } catch (e) {
                    // Pipeline post failure stage
                    script.currentBuild['result'] = script.currentBuild['result'] ?: 'FAILURE'
                    // script.currentBuild['result'] = 'FAILURE'
                    // script.echo "${script.currentBuild['result']}"
                    throw e
                } finally {
                    script.currentBuild['result'] = script.currentBuild['result'] ?: 'SUCCESS'
                    def currentResult = script.currentBuild['result'] ?: 'SUCCESS'

                    if (currentResult == 'UNSTABLE') {
                        // Pipeline post unstable stage
                        script.echo 'This will run only if the run was marked as unstable'
                    }

                    // def previousResult = script.currentBuild?.previousBuild?.result ?: null
                    // if (previousResult != null && previousResult != currentResult) {
                    // Pipeline post changed stage
                    //     script.echo 'This will run only if the state of the Pipeline has changed'
                    //     script.echo 'For example, if the Pipeline was previously failing but is now successful'
                    // }
                    // Pipeline post always stage
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

                ConfigService config = new ConfigService(script)
                config.initMerge(args.settings as String)

                String repo = ConfigService.data.git['repo']
                String branch = ConfigService.data.git['branch']

                Integer projectId = -1

                if (ConfigService.data.git['webhook']) {
                    projectId = ConfigService.data.git['projectId'] as Integer
                }

                script.properties([
                        script.parameters([
                                script.listGitBranches(
                                        remoteURL: repo,
                                        credentialsId: GIT_CREDENTIALS,
                                        branchFilter: 'refs/heads/(.*)',
                                        defaultValue: branch,
                                        name: 'GIT_BRANCH',
                                        quickFilterEnabled: true,
                                        selectedValue: 'NONE',
                                        sortMode: 'DESCENDING_SMART',
                                        tagFilter: '*',
                                        type: 'PT_BRANCH',
                                        description: 'Please select a branch to build'
                                ),
                                script.choice(
                                        choices: 'deploy\nrollback',
                                        description: 'Please select action',
                                        name: 'ACTION'
                                ),
                                script.booleanParam(
                                        defaultValue: false,
                                        name: 'CLEAN',
                                        description: 'Clear build directory'
                                )
                        ]),
                        script.disableConcurrentBuilds(),
                        script.pipelineTriggers([
                                script.GenericTrigger(
                                        causeString: 'Generic Cause',
                                        genericVariables: [
                                                [
                                                        defaultValue: '',
                                                        key         : 'ref',
                                                        regexpFilter: '',
                                                        value       : '$.ref'
                                                ],
                                                [
                                                        defaultValue: '',
                                                        key         : 'project_id',
                                                        regexpFilter: '',
                                                        value       : '$.project_id'
                                                ],
                                        ],
                                        regexpFilterExpression: "^($projectId)_(refs/heads/${ConfigService.data.git['branch']})\$",
                                        regexpFilterText: '${project_id}_${ref}',
                                        token: JENKINS_GENERIC_WEBHOOK_TRIGGER_TOKEN,
                                        //tokenCredentialId: JENKINS_GENERIC_WEBHOOK_TRIGGER_TOKEN_CREDENTIALS
                                )
                        ]),
                ])

                config.merge(args.settings as String)

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

                if (script.params['CLEAN']) {

                    String dir = getFirstDirectory(script)

                    log.info "Clean dir: $dir."
                    CheckUtils checkUtils = new CheckUtils()

                    checkUtils.notDirectoryPanic(script, dir) {
                        cleanDir(script, dir)
                    }
                }

                Git git = new Git(script)

                String branch = ConfigService.data.git['branch']
                String repo = ConfigService.data.git['repo'] ?: git.getUrl()

                git.clone(repo, branch)
                script.parallel(
                        'Update CommitID': { ConfigService.data['git']['commitID'] = git.getCommitID(40) },
                        'Update Author': { ConfigService.data['git']['author'] = git.getAuthor() },
                        'Update Message': { ConfigService.data['git']['message'] = git.getCommitMessage() },
                )
                currentBuildInfo()

                Integer projectId = ConfigService.data.git['projectId'] as Integer
                Gitlab gitlab = new Gitlab(script, projectId)

                ArrayList projectHooks = gitlab.getProjectHook()
                if (!(GITLAB_WEBHOOK_URL in projectHooks)) {
                    gitlab.addProjectHook()
                }

                script.unstash 'template'
            }
        }
    }

    private static void currentBuildInfo() {

        String user = ConfigService.data.base['user']
        if (!user) user = ConfigService.data.git['author']

        String name = script.env['BUILD_NUMBER'] + ' ' + ConfigService.data['git']['branch']
        String desc = (ConfigService.data.base['action'] as String).capitalize() + ' to ' +
                (ConfigService.data.base['env'] as String).toUpperCase() + ' by ' + user

        script.currentBuild['displayName'] = name
        script.currentBuild['description'] = desc
    }

    private void codeBuild() {
        script.node(BUILD_STAGE_NODE) {
            script.stage('Build Code') {
                script.dir(ConfigService.data.build['buildDir'] as String) {

                    log.info "Build Code Stage."

                    Build build = new Build(script)

                    String buildCommand = ConfigService.data.build['command']

                    build.init(ConfigService.data['build'] as Map)
                    build.build(buildCommand)

                }
            }
        }
    }

    private void sonarScan() {
        script.node(SONARQUBE_STAGE_NODE) {
            script.stage('SonarQube Scanner') {
                script.dir(ConfigService.data.build['buildDir'] as String) {

                    log.info "SonarQube Scanner Stage."

                    SonarQube sonar = new SonarQube(script, language)

                    String projectKey = ConfigService.data['base']['name'] as String
                    String projectName = ConfigService.data['base']['name'] as String
                    String projectVersion = ConfigService.data['git']['commitID'] as String
                    String sources = ConfigService.data['scanner']['sources'] ?:
                            (ConfigService.data['build']['buildDir'] + '/' + ConfigService.data['build']['appDir']) as String
                    String exclusions = ConfigService.data['scanner']['exclusions'] as String
                    String tag = ConfigService.data['base']['project'] as String

                    sonar.withOptions(
                            projectKey: projectKey,
                            projectName: projectName,
                            projectVersion: projectVersion,
                            sources: sources,
                            exclusions: exclusions
                    ) {
                        sonar.scanner()
                        sonar.setTag(tag)
                        sonar.qualityGateStatus()

                    }
                }
            }
        }
    }

    private void doDocker() {
        script.node(DOCKER_STAGE_NODE) {
            script.stage('Docker') {
                script.dir((ConfigService.data.build['buildDir'] + '/' + ConfigService.data.build['appDir']) as String) {

                    log.info "Docker Stage."

                    Docker docker = new Docker(script)

                    String image = DOCKER_REGISTER + '/' +
                            (ConfigService.data['base']['project'] as String).toLowerCase() + '/' +
                            (ConfigService.data['base']['name'] as String).toLowerCase() + ':' +
                            (ConfigService.data['git']['branch'] as String).toLowerCase() + '-' +
                            (ConfigService.data['git']['commitID'] as String)[0..7].toLowerCase()
                    String content = ConfigService.data['docker']['content'] ?: tool.content
                    String buildUser = ConfigService.data['base']['userid'] as String
                    String shortCommitID = (ConfigService.data['git']['commitID'] as String)[0..7]
                    String dockerfile = ConfigService.data['docker']['dockerfile'] as String
                    String ignorefile = ConfigService.data['docker']['ignorefile'] as String
                    String chown = ConfigService.data['docker']['chown'] as String


                    docker.withInfo(
                            image: image, userID: buildUser, options: '',
                            commitID: shortCommitID, content: content,
                            dockerfile: dockerfile, ignorefile: ignorefile,
                            chown: chown
                    ) {
                        script.parallel(
                                'Generate Dockerfile': {
                                    docker.genDockerfile(language)
                                    docker.genDockerfile()
                                },
                                'Generate .dockerignore': {
                                    docker.genDockerIgnoreFile(language)
                                },
                                'Login to Registry': { docker.login() }
                        )

                        ConfigService.data['docker']['image'] = docker.build()
                        docker.push()
                    }
                }
            }
        }
    }

    private void doKubernetes() {
        script.node(KUBERNETES_STAGE_NODE) {
            script.stage('Kubernetes') {
                script.dir((ConfigService.data.build['buildDir'] + '/' + ConfigService.data['build']['appDir']) as String) {

                    log.info "Kubernetes Stage."

                    String environment = ConfigService.data['base']['env']
                    String namespace = ConfigService.data['kubernetes']['namespace']
                    String deployment = ConfigService.data['base']['name']
                    Integer replicas = getReplicasNumber(environment)

                    String deploymentFile = KUBERNETES_DEPLOYMENT_FILE
                    String serviceFile = KUBERNETES_SERVICE_FILE
                    String configMapFile = KUBERNETES_CONFIGMAP_FILE
                    String ingressFile = KUBERNETES_INGRESS_FILE

                    KubeYaml kubeYaml = new KubeYaml(script, ConfigService.data as Map)
                    script.parallel(
                            'Generate Deployment': { kubeYaml.genDeploymentFile(language) },
                            'Generate Service': { kubeYaml.genServiceFile(language) },
                            'Generate ConfigMap': { if (language in WEB_LANGUAGES) kubeYaml.genConfigmapFile(language) },
                            'Generate Pvc': { if (ConfigService.data['kubernetes']['persistence']['enabled']) kubeYaml.genPvcFile(language) }
                    )

                    KubeCommand kubeCommand = new KubeCommand(script, environment)
                    script.parallel(
                            'Apply Deployment': { kubeCommand.applyDeployment() },
                            'Apply Service': { kubeCommand.applyService() },
                            'Apply ConfigMap': { if (language in WEB_LANGUAGES) kubeCommand.applyConfigMap() },
                            'Apply Pvc': { if (ConfigService.data['kubernetes']['persistence']['enabled']) kubeCommand.applyPvc() }
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

                log.info "Post Stage."

                String jobType = ConfigService.data.base['type'] as String

                if (ConfigService.data.notification[jobType]['enabled']) {
                    notification(jobType)
                }
            }
        }
    }

    private static void notification(String jobType) {

        Map info = [
                'Job'   : (ConfigService.data.base['job'] as String),
                'Result': script.currentBuild['result'],
                'Date'  : new Date().format("yyyy-MM-dd HH:mm:ss")
        ]

        if (script.currentBuild['result'] == 'SUCCESS') {

            info.put('Branch', ConfigService.data.git['branch'])
            info.put('CommitID', ConfigService.data.git['commitID'])
            info.put('Author', ConfigService.data.git['author'] as String)
            info.put('Message', ConfigService.data.git['message'] as String)

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
        }

        String notifyType = ConfigService.data.notification[jobType]['type'] as String

        Map config = ConfigService.data.notification[jobType][notifyType.toLowerCase()] as Map

        Notification notification = new Notification(script)
        notification.selector(notifyType, config)
        notification.notify(info)
    }
}