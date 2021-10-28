/**
 * Created by Peng.Gao at 2021/3/21 16:14
 * A Part of the Project jenkins-shared-library
 */

package jugggao.com.utils.notification

import com.cloudbees.groovy.cps.NonCPS
import groovy.json.JsonSlurper
import jugggao.com.utils.logging.Logger
import org.jenkinsci.plugins.workflow.cps.CpsScript
import groovy.json.JsonOutput

import static jugggao.com.utils.CheckUtils.emptyVarPanic

class Wechat implements Serializable, Notify {

    private static Script script

    private Logger log = new Logger(this)

    private Map config

    Wechat(Script script, Map config) {
        if (!(script instanceof CpsScript)) {
            log.fatal "The script $script is not CpsScript"
        }

        this.script = script
        this.config = config
    }

    @Override
    def genMessage(Map info) {

        log.info "Generate wechat message template."

        String message = ''
        info.each { key, value ->
            message += key + ' : ' + value + '\n'
        }

        Map messageBody = [
            msgtype: 'text',
            text   : [
                "content"            : message,
                //mentioned_mobile_list: config['mobile']
            ],
        ]

        String sMessageBody = JsonOutput.toJson(messageBody)

        return sMessageBody
    }

    @Override
    void sendMessage(String message) {
        log.info "Send wechat message."

        String url = config['url']

        try {
            emptyVarPanic(script, 'url', url) {
                def response = script.httpRequest(
                    url: url,
                    httpMode: 'POST',
                    //contentType: 'APPLICATION_JSON',
                    customHeaders: [
                        [
                            name: 'Content-Type', value: 'application/json; charset=UTF-8'
                        ]
                    ],
                    requestBody: message
                )
                log.debug "Send Message Status: ${response['status']}"
                log.debug "Send Message Response Content: ${response['content']}"

                log.debug "Send Message Map: ${jsonToMap(message)}"

                Map wechatResponseContent =  jsonToMap(response['content'] as String)
                log.debug "Wechat Response Content Error Code: ${wechatResponseContent?.errcode}"
                if (wechatResponseContent?.errcode != 0 ) {
                    log.fatal("Send wechat Message failed.")
                }
            }
        } catch (e) {
            log.error "Send wechat Message failed."
            throw e
        }
    }

    @NonCPS
    static Map jsonToMap(String str) {
        def jsonSlurper = new JsonSlurper()
        return jsonSlurper.parseText(str) as Map
    }
}
