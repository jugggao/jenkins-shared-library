/**
 * Create by Peng.Gao at 2021/3/9 15:42
 *
 * A Part of the Project jenkins-shared-library
 */

package jugggao.com.ci

import jugggao.com.utils.logging.Logger
import org.jenkinsci.plugins.workflow.cps.CpsScript

import static jugggao.com.utils.config.ConfigConstants.*
import static jugggao.com.utils.CheckUtils.emptyVarPanic

class SonarQube implements Serializable {

    private static Script script

    private Logger log = new Logger(this)

    private static Language language

    private Map options

    SonarQube(Script script, Language language) {

        if (!(script instanceof CpsScript)) {
            log.fatal("The $script is not CpsScript")
        }

        this.script = script
        this.language = language
    }

    void withOptions(Map options, Closure body = {}) {

        ['projectKey', 'projectName', 'projectVersion', 'sources', 'exclusions', 'javaBinaries'].each {
            options.get(it, '')
        }
        ['projectKey', 'projectName', 'projectVersion', 'sources'].each {
            emptyVarPanic(script, it, options[it])
        }

        this.options = [
                'sonar.projectKey'    : options['projectKey'],
                'sonar.projectName'   : options['projectName'],
                'sonar.projectVersion': options['projectVersion'],
                'sonar.sources'       : options['sources']
        ]

        if (language.toString() == 'JAVA') {
            String contentDir = options['sources'] + '/' + language.tool.content
            this.options.put('sonar.java.binaries', contentDir)
            this.options.put('sonar.java.source', JAVA_VERSION)
        }

        if (options['exclusions']) {
            this.options.put('sonar.exclusions', options['exclusions'])
        }

        body()
    }

    void genPropertiesFile() {
        log.deprecated("genPropertiesFile", "genPropertiesOptions")
        log.info "Generate Sonar Scanner properties file."

        String content = ''
        options.each { key, value ->
            content += (key + ' = ' + value + '\n') as String
        }

        log.debug "Properties: $content"

        try {
            script.sh("rm -f $SONARQUBE_PROPERTIES_FILE")
            script.writeFile(
                    file: SONARQUBE_PROPERTIES_FILE,
                    text: content
            )
        } catch (e) {
            log.error "Generate sonar scanner properties failed."
            throw e
        }
    }

    private String genPropertiesOptions() {
        log.info "Generate Sonar Scanner properties options."

        String content = ''
        options.each { key, value ->
            content += ('-D' + key + '=' + value + ' ') as String
        }

        return content
    }

    void scanner() {

        log.info "Analyze project with Sonar Scanner."
//        String dir = (script.sh(
//                script: "pwd && ls",
//                returnStdout: true
//        ) as String).trim()
//
//        log.debug "Dir: $dir"

        try {
            script.withSonarQubeEnv(credentialsId: SONARQUBE_TOKEN) {
                script.sh "$SONARQUBE_CMD ${genPropertiesOptions()}"
            }
        } catch (e) {
            log.error "Sonar scanner command execute failed."
            throw e
        }
    }

    // SonarQube Web API: https://sq.ambow.com/web_api/api/project_tags
    void setTag(String tag) {

        log.info "Set $tag tag for ${options['sonar.projectKey']}."

        String url = SONARQUBE_URL + '/api/project_tags/set?project=' +
                options['sonar.projectKey'] + '&tags=' + tag

        try {
            script.httpRequest(
                    url: url,
                    httpMode: 'POST',
                    authentication: SONARQUBE_CREDENTIALS
            )
        } catch (e) {
            log.error "Set sonar project tags failed."
            throw e
        }
    }

    void qualityGateStatus() {

        log.info "Wait for SonarQube analysis to be completed and return quality gate status."

        try {
            script.timeout(time: QUALITY_GATE_STATUS_TIMEOUT, unit: 'SECONDS') {
                def qg = script.waitForQualityGate(
                        credentialsId: SONARQUBE_CREDENTIALS
                )

                log.debug "${qg['status']}"

//                if (qg['status'] != 'OK') {
//                    log.fatal "Pipeline aborted due to quality gate failure: ${qg['status']}"
//                }
            }
        }
        catch (e) {
            log.error "Loading quality gate stats failed."
            throw e
        }
    }
}
