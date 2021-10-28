/**
 * Create by Peng.Gao at 2021/5/20
 *
 * A Part of the Project jenkins-shared-library
 */

package jugggao.com.utils.config

import com.cloudbees.groovy.cps.NonCPS
import jugggao.com.utils.logging.Logger
import org.jenkinsci.plugins.workflow.cps.CpsScript
import org.yaml.snakeyaml.Yaml

import static jugggao.com.utils.CheckUtils.fileNotExistPanic
import static jugggao.com.utils.CommonUtils.mergeNested
import static jugggao.com.utils.InfoUtils.getApplicationName
import static jugggao.com.utils.InfoUtils.getBuildUserName
import static jugggao.com.utils.InfoUtils.getBuildUserNameID
import static jugggao.com.utils.InfoUtils.getEnvironment
import static jugggao.com.utils.InfoUtils.getFirstDirectory
import static jugggao.com.utils.InfoUtils.getJobName
import static jugggao.com.utils.InfoUtils.getJobType
import static jugggao.com.utils.InfoUtils.getProjectName

class ConfigApp {

    private static Script script

    Logger log = new Logger(this)

    public Map data = [:]

    ConfigApp(Script script) {
        if (!(script instanceof CpsScript)) {
            log.fatal "The script is not CpsScript"
        }
        this.script = script
    }

    void initMerge(Map settings) {

        data = mergeNested(init(), settings)
    }

    void initMerge(String settings) {

        data = init()
        settingsFile(settings)

        debug()
    }

    private Map init() {
        log.info 'Init default config.'

        Map conf = [
            base   : [
                job      : getJobName(script).toLowerCase(),
                type     : getJobType(script).toLowerCase(),
                action   : 'deploy',
                dir      : script.env['workspace'] ?: getFirstDirectory(script),
                name     : getApplicationName(script),
                project  : getProjectName(script).toLowerCase(),
                workspace: script.env['JENKINS_HOME'] + '/workspace/' + script.env['JOB_NAME'],
                user     : getBuildUserName(script) ?: 'webhook',
                userid   : getBuildUserNameID(script) ?: 'webhook',
                logLevel : '',
            ],
            git    : [
                commitLength: 8,
                commitId    : '',
                repo        : '',
                branch      : 'dev',
                projectId   : 0,
                webhook     : true
            ],
            build  : [
                tool    : '',
                buildDir: '.',
                appDir  : '.',
                language: 'swift',
                options : '',
                command : '',
            ],
            archive: [
                target: '',
                developmentProfileId: '',
                developmentTeamName: '',
                xcodeSchema: '',
                xcodeWorkspaceFile: '',
                ipaOutputDirectory: 'archive',
                ipaExportMethod: 'app-store',
                generateArchive: true,
                buildIpa: true,
                ipaName: '',
                bundleID: '',
                cleanBeforeBuild: true,
                configuration: 'Release',
                signingMethod: 'Automatic',
                noConsoleLog: true,
                xcodebuildArguments: '',
            ],
            publush: [
                type: 'pgyer'
            ]
        ]

        return conf
    }

    private void settingsFile(String file) {
        log.info 'Load config of local file.'

        log.debug "User File: $file."

        fileNotExistPanic(script, file) {

            try {
                Map yaml = script.readYaml(file: file) as Map

                data = mergeNested(data, yaml)

                String sYaml = configToYaml(yaml)

                log.debug "User Config: \n $sYaml"

            } catch (e) {
                log.error 'An error occurred while try to load config of local file.'
                throw e
            }
        }
    }

    void debug() {
        String configYaml = configToYaml(data)
        log.debug "Config.data:\n$configYaml"
    }

    @NonCPS
    static String configToYaml(Map config) {
        Yaml yaml = new Yaml()
        return yaml.dump(config)
    }

}
