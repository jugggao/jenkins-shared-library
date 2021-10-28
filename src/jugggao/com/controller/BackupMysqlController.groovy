/**
 * Create by Peng.Gao at 2021/07/05 9:47
 *
 * A Part of the Project jenkins-shared-library
 */

package jugggao.com.controller

import com.cloudbees.groovy.cps.NonCPS
import jugggao.com.tools.mysql.Database
import jugggao.com.tools.mysql.Table
import jugggao.com.utils.config.ConfigTools
import jugggao.com.utils.logging.Logger
import jugggao.com.utils.notification.Notification
import org.jenkinsci.plugins.workflow.cps.CpsScript

import static jugggao.com.utils.CheckUtils.emptyVarPanic
import static jugggao.com.utils.CommonUtils.mergeNested
import static jugggao.com.utils.config.ConfigConstants.*

class BackupMysqlController implements Serializable {

    private static Script script

    private Logger log = new Logger(this)

    private Map config

    private Map sourceInfo

    private ArrayList databases = []

    private ArrayList tables = []

    private ArrayList options

    private ArrayList<Map> dumpInfo = []

    BackupMysqlController(Script script) {
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
                        case 'db':
                            dbPropertiesSet()
                            dbCheck()
                            dbDump()
                            break
                        case 'table':
                            tablePropertiesSet()
                            tableCheck()
                            tableDump()
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

                ConfigTools config = new ConfigTools(script)
                config.merge(args.settings as String)

                this.config = config.data
            }
        }
    }

    private void dbCheck() {
        script.node(TOOL_MYSQL_BUILD_NODE) {
            script.stage('Check Stage') {

                log.info "Verify these databases exists in source server and target server"

                log.debug "sourceInfo: $sourceInfo"

                emptyVarPanic(script, 'databases', databases) {
                    Database sourceDb = new Database(script, sourceInfo.name as String, sourceInfo.connectParams as Map)
                    ArrayList sourceDatabases = sourceDb.getDatabases()

                    log.debug "Source Databases: $sourceDatabases."
                    log.debug "Choice Databases: $databases."

                    if (!(sourceDatabases.containsAll(databases))) {
                        log.fatal "Some databases do not exist in source server."
                    }
                }
            }
        }
    }

    private void tableCheck() {
        script.node(TOOL_MYSQL_BUILD_NODE) {
            script.stage('Check Stage') {

                log.info "Verify these tables exists in source Database and target Database"
                log.debug "sourceInfo: $sourceInfo"

                String database = databases.join('')
            }
        }
    }

    private void tableDump() {
        script.node(TOOL_MYSQL_BUILD_NODE) {
            script.stage('Dump Stage') {

                log.info "Dump tables of ${tables.join(',')}."

                String database = databases.join('')

                Table sourceTable = new Table(script, sourceInfo.name as String, database, sourceInfo.connectParams as Map)
                tables.each { table ->
                    dumpInfo << sourceTable.dumpTable(table as String)
                }

                log.debug "Dump Info: $dumpInfo."

            }
        }
    }

    private void dbDump() {
        script.node(TOOL_MYSQL_BUILD_NODE) {
            script.stage('Dump Stage') {

                log.info "Dump databases of ${databases.join(',')}."

                Database sourceDb = new Database(script, sourceInfo.name as String, sourceInfo.connectParams as Map)
                databases.each { database ->
                    dumpInfo << sourceDb.dumpDatabase(database as String)
                }

                log.debug "Dump Info: $dumpInfo."

            }
        }
    }

    private void doAlwaysPost() {
        script.node(KUBERNETES_STAGE_NODE) {
            script.stage('Post Actions') {

                log.info "Post Stage."

                WriteLog()

                String jobType = config.base['type'] as String

                if (config.notification[jobType]['enabled']) {
                    notification(jobType)
                }

            }
        }
    }

    private void WriteLog() {
        script.node(TOOL_MYSQL_BUILD_NODE) {
            script.stage('Log Stage') {

                String log = log()

                script.sh "echo $log >> sync.log"
            }
        }
    }

    @NonCPS
    private String log() {

        String date = new Date().format("yyyy-MM-dd HH:mm:ss")
        String sDatabases = databases.join(',')
        String sTables = tables.join(',')
        String result = script.currentBuild['result']
        String user = config.base['userid']

        String log = sprintf('%20s [%s] [%s %s] [%s] - %s %s', date, user,
                sourceInfo.host, sourceInfo.port,
            result, sDatabases, sTables
        )

        return log
    }

    private void notification(String jobType) {

        log.info "Notify."

        Map info = [
            'Job'   : (config.base['job'] as String),
            'Result': script.currentBuild['result'],
            'Date'  : new Date().format("yyyy-MM-dd HH:mm:ss")
        ]

        if (script.currentBuild['result'] == 'SUCCESS') {
            String source = sourceInfo.name
            info.put('Source', source)

            String sDatabases = databases.join(',')
            info.put('Databases', sDatabases)

            if (config.base['toolType'] == 'table') {
                String sTables = tables.join(',')
                info.put('Tables', sTables)
            }
        }

        String notifyType = config.notification[jobType]['type'] as String

        Map config = config.notification[jobType][notifyType.toLowerCase()] as Map

        Notification notification = new Notification(script)
        notification.selector(notifyType, config)
        notification.notify(info)
    }

    @NonCPS
    private String scriptOfSource() {
         return 'return ' + (config.sources as Map).keySet().inspect()
    }

    @NonCPS
    private String scriptOfDatabases() {

        Map sources = config.sources as Map

        String head = "switch (Source) {\n"

        StringBuffer middle = new StringBuffer()
        sources.keySet().each {
            middle <<
                """    case ${it.inspect()}:
        |        return ${sources[it]['databases'].inspect()}
        |""".stripMargin()
        }

        String tail =
            """    default:
            |        return ['Unknown Server']
            |}
            """.stripMargin()

        return (head + middle + tail) as String
    }

    @NonCPS
    private static String scriptOfTables(Map dtInfo) {
        String head = "switch (Databases) {\n"

        StringBuffer middle = new StringBuffer()

        dtInfo.keySet().each {
            middle <<
                """    case ${it.inspect()}:
        |        return ${dtInfo[it].inspect()}
        |""".stripMargin()
        }

        String tail =
            """    default:
            |        return ['Unknown Database']
            |}
            """.stripMargin()

        return (head + middle + tail) as String
    }

    private dbPropertiesSet(){
        script.node(TOOL_MYSQL_BUILD_NODE) {
            script.stage('Properties Stage') {

                script.properties([
                    script.disableConcurrentBuilds()
                ])

                script.properties([
                    script.parameters([
                        [
                            $class: 'ChoiceParameter',
                            choiceType: 'PT_SINGLE_SELECT',
                            description: '',
                            filterLength: 1,
                            filterable: false,
                            name: 'Source',
                            script: [
                                $class: 'GroovyScript',
                                fallbackScript: [
                                    classpath: [],
                                    sandbox: false,
                                    script: "return ['Groovy script error']"
                                ],
                                script: [
                                    classpath: [],
                                    sandbox: false,
                                    script: scriptOfSource(),
                                ]
                            ]
                        ],
                        [
                            $class: 'CascadeChoiceParameter',
                            choiceType: 'PT_CHECKBOX',
                            description: '',
                            filterLength: 1,
                            filterable: true,
                            name: 'Databases',
                            referencedParameters: 'Source',
                            script: [
                                $class: 'GroovyScript',
                                fallbackScript: [
                                    classpath: [],
                                    sandbox: false,
                                    script: "return ['Groovy script error']"
                                ],
                                script: [
                                    classpath: [],
                                    sandbox: false,
                                    script: scriptOfDatabases(),
                                ]
                            ]
                        ]
                    ])
                ])

                log.debug "Source: ${script.params['Source']}"
                log.debug "Databases: ${script.params['Databases']}"

                String source = script.params['Source']
                databases = (script.params['Databases'] as String).split(',')

                String sourceHost = config.sources[source]['host']
                String sourcePort = config.sources[source]['port']

                Map sourceConnectParams = [
                    host: sourceHost,
                    port: sourcePort,
                    credential: 'mysql-' + source,
                ]

                sourceInfo = [
                    name: source,
                    host: sourceHost,
                    port: sourcePort,
                    connectParams: sourceConnectParams
                ]

                log.debug "Source Info: $sourceInfo"
            }
        }
    }

    private viewSet() {

        Map sources = (Map) config.sources
        String project = config.base['project']

        log.debug "Sources: $sources"

        ArrayList dtInfos = []

        sources.keySet().each { source ->

            String sourceHost = config.sources[(String) source]['host']
            String sourcePort = config.sources[(String) source]['port']
            Map sourceConnectParams = [
                host: sourceHost,
                port: sourcePort,
                credential: 'mysql-' + source,
            ]
            ArrayList dbs = (ArrayList) config.sources[(String) source]['databases']
            Database sourceDb = new Database(script, (String) source, sourceConnectParams)
            ArrayList sourceDatabases = sourceDb.getDatabases()

            if (!sourceDatabases.contains(TOOL_MYSQL_PIPELINE_DATABASE)) {
                sourceDb.createDatabase(TOOL_MYSQL_PIPELINE_DATABASE)
            }

            sourceDb.createOrReplaceView(project, dbs)
            dtInfos << sourceDb.getViewData(project)
        }

        return mergeNested((Map[]) dtInfos)
    }

    private tablePropertiesSet() {
        script.node(TOOL_MYSQL_BUILD_NODE) {
            script.stage('Properties Stage') {

                Map dtInfo = viewSet()

                script.properties([
                    script.disableConcurrentBuilds()
                ])

                script.properties([
                    script.parameters([
                        [
                            $class: 'ChoiceParameter',
                            choiceType: 'PT_SINGLE_SELECT',
                            description: '',
                            filterLength: 1,
                            filterable: false,
                            name: 'Source',
                            script: [
                                $class: 'GroovyScript',
                                fallbackScript: [
                                    classpath: [],
                                    sandbox: false,
                                    script: "return ['Groovy script error']"
                                ],
                                script: [
                                    classpath: [],
                                    sandbox: false,
                                    script: scriptOfSource(),
                                ]
                            ]
                        ],
                        [
                            $class: 'CascadeChoiceParameter',
                            choiceType: 'PT_SINGLE_SELECT',
                            description: '',
                            filterLength: 1,
                            filterable: true,
                            name: 'Databases',
                            referencedParameters: 'Source',
                            script: [
                                $class: 'GroovyScript',
                                fallbackScript: [
                                    classpath: [],
                                    sandbox: false,
                                    script: "return ['Groovy script error']"
                                ],
                                script: [
                                    classpath: [],
                                    sandbox: false,
                                    script: scriptOfDatabases(),
                                ]
                            ]
                        ],
                        [
                            $class: 'CascadeChoiceParameter',
                            choiceType: 'PT_CHECKBOX',
                            description: '',
                            filterLength: 1,
                            filterable: true,
                            name: 'Tables',
                            referencedParameters: 'Databases',
                            script: [
                                $class: 'GroovyScript',
                                fallbackScript: [
                                    classpath: [],
                                    sandbox: false,
                                    script: "return ['Groovy script error']"
                                ],
                                script: [
                                    classpath: [],
                                    sandbox: false,
                                    script: scriptOfTables(dtInfo),
                                ]
                            ]
                        ],
                    ])
                ])
            }
        }

        log.debug "Source: ${script.params['Source']}"
        log.debug "Databases: ${script.params['Databases']}"
        log.debug "Tables: ${script.params['Tables']}"

        String source = script.params['Source']
        databases = (script.params['Databases'] as String).split(',')
        tables = (script.params['Tables'] as String).split(',')
        options = (script.params['Options'] as String).split(',')

        String sourceHost = config.sources[source]['host']
        String sourcePort = config.sources[source]['port']

        Map sourceConnectParams = [
            host: sourceHost,
            port: sourcePort,
            credential: 'mysql-' + source,
        ]

        sourceInfo = [
            name: source,
            host: sourceHost,
            port: sourcePort,
            connectParams: sourceConnectParams
        ]
    }
}