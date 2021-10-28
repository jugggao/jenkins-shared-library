package jugggao.com.controller

import jugggao.com.tools.mysql.Database
import jugggao.com.utils.config.ConfigTools
import jugggao.com.utils.git.Git
import jugggao.com.utils.logging.Logger
import org.apache.commons.lang.StringUtils
import org.jenkinsci.plugins.workflow.cps.CpsScript
import jugggao.com.utils.git.Gitlab

import static jugggao.com.utils.CommonUtils.*
import static jugggao.com.utils.config.ConfigConstants.ANSI_COLOR_XTERM
import static jugggao.com.utils.InfoUtils.*
import static jugggao.com.utils.config.ConfigConstants.TOOL_EXPORT_PROJECT_DATA_DIR
import static jugggao.com.utils.config.ConfigConstants.TOOL_EXPORT_PROJECT_DATA_NODE

class ExportProjectDataController {

    private static Script script

    private Logger log = new Logger(this)

    private String project

    private Map config

    private ArrayList codeInfo

    private Map databaseInfo

    private String date = new Date().format("yyyyMMdd")

    private ArrayList<Map> dumpInfo = []

    ExportProjectDataController(Script script) {
        if (script instanceof CpsScript) {
            this.script = script
        }
    }

    void call(Map args, Closure body = {}) {
        script.ansiColor(ANSI_COLOR_XTERM) {
            script.timestamps {
                try {

                    initialStage(args)

                    switch (args.toolType) {
                        case 'code':
                            codeInfo()
                            codeClone()
                            codeArchive()
                            break
                        case 'database':
                            databaseInfo()
                            databaseDump()
                            break
                        default:
                            log.fatal "The ${args.toolType} does not support."
                    }
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

                    // doAlwaysPost()
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

                ConfigTools config = new ConfigTools(script)
                config.merge(args.settings as String)

                this.config = config.data
                this.project = projectName(script)

                log.debug "Project: $project"
            }
        }
    }

    private void codeInfo() {
        script.node(TOOL_EXPORT_PROJECT_DATA_NODE) {
            script.stage('Code Info') {

                log.info "Code Info Stage."

                ArrayList codeInfo = (ArrayList) config[project]['codes']

                codeInfo.eachWithIndex { info, index ->

                    Integer projectId = (Integer) info['id']
                    Gitlab gitlab = new Gitlab(script, projectId)
                    (codeInfo[index] as Map).put('release', gitlab.getTags()[0])
                    (codeInfo[index] as Map).remove('id')
                }

                this.codeInfo = codeInfo

                log.info "Code Info:\n${codeFormatInfo(codeInfo)}"

            }
        }
    }

    private void databaseInfo() {
        script.node(TOOL_EXPORT_PROJECT_DATA_NODE) {
            script.stage('Database Info Stage') {
                log.info "Database Info Stage."
                databaseInfo = (Map) config[project]['databases']

                log.info "Database Info:\n${databaseFormatInfo(databaseInfo)}"
            }
        }
    }


    private void codeClone() {
        script.node(TOOL_EXPORT_PROJECT_DATA_NODE) {
            script.stage('Clone Code') {
                log.info "Clone Code Stage."

                String exportCodeDir = TOOL_EXPORT_PROJECT_DATA_DIR + '/' + project + '/code/' + date

                cleanDir(script, exportCodeDir)
                codeInfo.each {
                    Git git = new Git(script)
                    String repo = it['repository']
                    String tag = it['release']
                    String dir = exportCodeDir + '/' + it['service'] + '-' + tag
                    git.cloneWithTag(repo, tag, dir)
                }
            }
        }
    }

    private void databaseDump() {
        script.node(TOOL_EXPORT_PROJECT_DATA_NODE) {
            script.stage('Database Dump') {

                String exportDatabaseDir = TOOL_EXPORT_PROJECT_DATA_DIR + '/' + project + '/database/' + date
                cleanDir(script, exportDatabaseDir)

                databaseInfo.each { sourceName, sourceInfo ->

                    Map sourceConnectParams = [
                            host: sourceInfo['host'],
                            port: sourceInfo['port'],
                            credential: 'mysql-' + sourceName,
                    ]

                    Database sourceDb = new Database(script, sourceName as String, sourceConnectParams)
                    ArrayList databases = (ArrayList) sourceInfo['databases']
                    databases.each { database ->
                        dumpInfo << sourceDb.dumpDatabase(database as String, exportDatabaseDir)
                    }

                    log.debug "Dump Info: $dumpInfo."
                }
            }
        }
    }

    private void codeArchive() {
        script.node(TOOL_EXPORT_PROJECT_DATA_NODE) {
            script.stage('Archive Code') {
                log.info "Archive Code Stage."

                String exportCodeDir = TOOL_EXPORT_PROJECT_DATA_DIR + '/' + project + '/code/' + date
                archiveDir(script, exportCodeDir, project + '-code-' + date)
            }
        }
    }


    private static String codeFormatInfo(ArrayList codeInfo) {

        String fillStr = '-'
        String fillLine = String.format("|%20s|%100s|%20s|\n", fillStr * 20, fillStr * 100, fillStr * 20)
        String title = fillLine + String.format("|%20s|%100s|%20s|\n",
                StringUtils.center('Service', 20),
                StringUtils.center('Repository', 100),
                StringUtils.center('Release', 20)) + fillLine
        String info = ''

        codeInfo.each {
            info += String.format("|%20s|%93s|%20s|\n",
                    StringUtils.center((String) it['service'], 20),
                    StringUtils.center((String) it['repository'], 93),
                    StringUtils.center((String) it['release'], 20)) + fillLine
        }

        return title + info

    }

    private static String databaseFormatInfo(Map databaseInfo) {
        String fillStr = '-'
        String fillLine = String.format("|%50s|%50s|\n", fillStr * 50, fillStr * 50)
        String title = fillLine + String.format("|%50s|%50s|\n",
                StringUtils.center('Source', 50),
                StringUtils.center('Database', 50)) + fillLine
        String info = ''

        databaseInfo.each { sourceName, sourceInfo ->
            sourceInfo['databases'].each {
                info += String.format("|%20s|%30s|\n",
                        StringUtils.center((String) sourceName, 50),
                        StringUtils.center((String) it, 50)
                ) + fillLine
            }
        }

        return title + info
    }

}
