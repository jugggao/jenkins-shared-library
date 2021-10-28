/**
 * Create by Peng.Gao at 2021/3/16 9:48
 *
 * A Part of the Project jenkins-shared-library
 */

package jugggao.com.controller

import jugggao.com.utils.logging.Logger
import org.jenkinsci.plugins.workflow.cps.CpsScript


class AndroidController {

    Logger log = new Logger(this)

    private static Script script

    AndroidController(Script script) {
        if (script instanceof CpsScript) {
            this.script = script
        }
    }

    void call(Map args, Closure body = {}) {

        

        body()
    }

}
