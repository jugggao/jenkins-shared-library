/**
 * Created by Peng.Gao at 2021/3/21 16:14
 * A Part of the Project jenkins-shared-library
 */

package jugggao.com.utils.notification

import jugggao.com.utils.logging.Logger
import org.jenkinsci.plugins.workflow.cps.CpsScript

class WebHook implements Serializable, Notify {

    private static Script script

    private Logger log = new Logger(this)

    private Map config

    WebHook(Script script, Map config) {
        if (!(script instanceof CpsScript)) {
            log.fatal "The script $script is not CpsScript"
        }

        this.script = script
        this.config = config
    }

    @Override
    def genMessage(Map info) {
        return null
    }

    @Override
    void sendMessage(String message) {}
}
