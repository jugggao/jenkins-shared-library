package jugggao.com.app

import com.cloudbees.groovy.cps.NonCPS
import groovy.json.JsonSlurper
import jugggao.com.utils.logging.Logger
import org.jenkinsci.plugins.workflow.cps.CpsScript

import static jugggao.com.utils.CheckUtils.emptyVarPanic
import static jugggao.com.utils.config.ConfigConstants.*

class Publish implements Serializable {

    static Script script

    private Logger log = new Logger(this)

    Publish(Script script) {
        if (!(script instanceof CpsScript)) {
            log.fatal "This script is not instance of CpsScript, type: ${script.getClass().getName()}"
        }
        this.script = script
    }

    Map upload(Map options) {

        log.debug "Upload Options: $options"

        String type = options.type
        Map info = [:]

        emptyVarPanic(script, 'publishType', type) {
            switch (type) {
                case 'pgyer':
                    info = uploadPgyer(options.target as String)
            }
        }

        return info
    }

    Map uploadPgyer(String target) {

        log.info "Upload package to pgyer."

        emptyVarPanic(script, 'file', target)

        String file = target
        Map info = [:]

        try {
            script.withCredentials([
                script.string(
                    credentialsId: PGYER_API_KEY_CREDENTIALS,
                    variable: 'pgyerApiKey'
                )
            ]) {
                String responseContent = script.httpRequest(
                    url: PGYER_API_UPLOAD_URL + '?_api_key=' + script.env['pgyerApiKey'],
                    httpMode: 'POST',
                    multipartName: 'file',
                    uploadFile: file,
                    customHeaders: [
                        [
                            name: 'Content-Type', value: 'multipart/form-data; charset=UTF-8'
                        ]
                    ],
                )['content'] as String

                Map content = script.readJSON(
                    text:  responseContent
                ) as Map
                Integer code = content.code as Integer

                if (code != 0) {
                    log.fatal "${content.message}"
                }

                info = content.data as Map

                log.debug "Pgyer upload info: \n $info"

            }
        } catch (e) {
            log.error "Failed to upload package to pgyer."
            throw e
        }
        return info
    }

    @NonCPS
    private static Map jsonParse(String json) {
        return new JsonSlurper().parseText(json) as Map
    }

}
