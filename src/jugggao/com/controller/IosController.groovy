/**
 * Create by Peng.Gao at 2021/3/16 9:48
 *
 * A Part of the Project jenkins-shared-library
 */

package jugggao.com.controller

import jugggao.com.app.Archive
import jugggao.com.app.Publish
import jugggao.com.ci.Build
import jugggao.com.app.IosBuild
import jugggao.com.utils.CheckUtils
import jugggao.com.utils.config.ConfigApp
import jugggao.com.utils.git.Git
import jugggao.com.utils.git.Gitlab
import jugggao.com.utils.logging.Logger
import jugggao.com.utils.notification.Notification
import org.jenkinsci.plugins.workflow.cps.CpsScript

import static jugggao.com.utils.CommonUtils.*
import static jugggao.com.utils.InfoUtils.getFirstDirectory
import static jugggao.com.utils.config.ConfigConstants.*


class IosController implements Serializable {

    Logger log = new Logger(this)

    private static Script script

    private Map config

    private Map appInfo

    IosController(Script script) {
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

                    codeBuild()

//                    script.parallel(
//                        'Publish': { doPublish() },
//                        'Archive': { doArchive() }
//                    )
                    doPublish()

                    doArchive()


                } catch (e) {
                    script.currentBuild['result'] = script.currentBuild['result'] ?: 'FAILURE'
                    throw e
                } finally {
                    script.currentBuild['result'] = script.currentBuild['result'] ?: 'SUCCESS'
                    def currentResult = script.currentBuild['result'] ?: 'SUCCESS'

                    if (currentResult == 'UNSTABLE') {
                        // Pipeline post unstable stage
                        script.echo 'This will run only if the run was marked as unstable'
                    }

                    doAlwaysPost()
                }
            }

            body()
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

                ConfigApp config = new ConfigApp(script)
                config.initMerge(args.settings as String)

                String repo = config.data.git['repo']
                String branch = config.data.git['branch']
                Integer projectId = -1

                if (config.data.git['webhook']) {
                    projectId = config.data.git['projectId'] as Integer
                }

                this.config = config.data

                script.properties([
                    script.parameters([
                        script.listGitBranches(
                            remoteURL: repo,
                            credentialsId: GIT_CREDENTIALS,
                            branchFilter: 'refs/heads/(.*)',
                            defaultValue: 'dev',
                            name: 'GIT_BRANCH',
                            quickFilterEnabled: true,
                            selectedValue: 'NONE',
                            sortMode: 'DESCENDING_SMART',
                            tagFilter: '*',
                            type: 'PT_BRANCH',
                            description: 'Please select a branch to build'
                        ),
                        script.booleanParam(
                            defaultValue: false,
                            name: 'CLEAN',
                            description: 'Clear build directory'
                        ),
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
                            regexpFilterExpression: "^($projectId)_(refs/heads/${branch})\$",
                            regexpFilterText: '${project_id}_${ref}',
                            token: JENKINS_GENERIC_WEBHOOK_TRIGGER_TOKEN,
                            //tokenCredentialId: JENKINS_GENERIC_WEBHOOK_TRIGGER_TOKEN_CREDENTIALS
                        )
                    ]),
                ])
            }
        }
    }

    private void codeClone() {
        script.node(IOS_BUILD_NODE) {
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

                String branch = config.git['branch']
                String repo = config.git['repo'] ?: git.getUrl()

                git.clone(repo, branch)
                script.parallel(
                    'Update CommitID': { config['git']['commitID'] = git.getCommitID(40) },
                    'Update Author': { config['git']['author'] = git.getAuthor() },
                    'Update Message': { config['git']['message'] = git.getCommitMessage() },
                )

                Integer projectId = config.git['projectId'] as Integer
                Gitlab gitlab = new Gitlab(script, projectId)

                ArrayList projectHooks = gitlab.getProjectHook()
                if (!(GITLAB_WEBHOOK_URL in projectHooks)) {
                    gitlab.addProjectHook()
                }

                currentBuildInfo()
            }
        }
    }

    private void currentBuildInfo() {

        String user = config.base['user']
        if (!user) user = config.git['author']

        String name = script.env['BUILD_NUMBER'] + ' ' + config['git']['branch']
        String desc = 'Build ios by ' + user

        script.currentBuild['displayName'] = name
        script.currentBuild['description'] = desc
    }

    private void codeBuild() {
        script.node(IOS_BUILD_NODE) {
            script.stage('Build Code') {
                script.dir(config.build['buildDir'] as String) {

                    log.info "Build Code Stage."

                    String buildCommand = config.build['command']

                    Build build = new Build(script)
                    build.init(config['build'] as Map)
                    build.build(buildCommand)

                    String profileId = config.archive['developmentProfileId']
                    Map archiveOptions = config.archive as Map

                    IosBuild iosBuild = new IosBuild(script)
                    // iosBuild.importDeveloperProfile(profileId)
                    iosBuild.unlockKeychain()
                    iosBuild.xcodeBuild(archiveOptions)

                }
            }
        }
    }

    void doPublish() {
        script.node(IOS_BUILD_NODE) {
            script.stage('Publish') {
                script.dir(config.build['buildDir'] as String) {

                    log.info "Publish Stage."

                    Map publishOptions = config.publish as Map
                    String target = publishOptions.target
                    Publish publish = new Publish(script)

                    appInfo = publish.upload(publishOptions)
                }
            }
        }
    }

    void doArchive() {
        script.node(IOS_BUILD_NODE) {
            script.stage('Archive') {
                script.dir(config.build['buildDir'] as String) {

                    log.info "Archive Stage."

                    String packageTarget =  config.publish['target']
                    String packageName = appInfo.buildVersion + '-' + appInfo.buildVersionNo + '-' + appInfo.buildFileName
                    String packageDir = (packageTarget as String).split('/')[0..-2].join('/')
                    String archiveTarget = packageDir + '/' + packageName

                    Archive archive = new Archive(script)
                    archive.renameBuildFile(packageTarget, archiveTarget)
                    String archiveDownloadUrl = archive.archiveFile(archiveTarget)
                    appInfo << ['archiveDownloadUrl': archiveDownloadUrl]

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

        log.info "Notify."

        Map info = [
            'Job'   : (config.base['job'] as String),
            'Result': script.currentBuild['result'],
            'Date'  : new Date().format("yyyy-MM-dd HH:mm:ss")
        ]

        if (script.currentBuild['result'] == 'SUCCESS') {
            info << ['build Name': appInfo.buildName]
            info << ['Publish Type': config.publish['type']]
            info << ['build Version': appInfo.buildVersion]
            info << ['build Version No': appInfo.buildVersionNo]
            info << ['Install Url': 'https://www.pgyer.com/' + appInfo.buildShortcutUrl]
            info << ['Download Url': appInfo.archiveDownloadUrl]
        }

        String notifyType = config.notification[jobType]['type'] as String

        Map config = config.notification[jobType][notifyType.toLowerCase()] as Map

        Notification notification = new Notification(script)
        notification.selector(notifyType, config)
        notification.notify(info)
    }
}
