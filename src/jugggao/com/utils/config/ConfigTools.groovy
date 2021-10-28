package jugggao.com.utils.config

import com.cloudbees.groovy.cps.NonCPS
import jugggao.com.utils.logging.Logger
import org.jenkinsci.plugins.workflow.cps.CpsScript
import org.yaml.snakeyaml.Yaml

import static jugggao.com.utils.CheckUtils.fileNotExistPanic
import static jugggao.com.utils.CommonUtils.mergeNested
import static jugggao.com.utils.InfoUtils.*

class ConfigTools implements Serializable {
    private static Script script

    Logger log = new Logger(this)

    public Map data = [:]

    ConfigTools(Script script) {
        if (!(script instanceof CpsScript)) {
            log.fatal "The script is not CpsScript"
        }
        this.script = script
    }

    void merge(String settings) {

        data = init()
        settingsFile(settings)

        debug()
    }

    private Map init() {
        log.info 'Init default config.'
        Map conf = [
            base: [
                job      : getJobName(script),
                type     : getJobType(script),
                toolType : getToolType(script),
                dir      : script.env['workspace'] ?: getFirstDirectory(script),
                name     : getApplicationName(script),
                project  : getProjectName(script),
                workspace: script.env['JENKINS_HOME'] + '/workspace/' + script.env['JOB_NAME'],
                user     : getBuildUserName(script) ?: 'webhook',
                userid   : getBuildUserNameID(script) ?: 'webhook',
                logLevel : '',
            ],
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
