/**
 * Created by Peng.Gao at 2021/2/28 15:34
 * A Part of the Project jenkins-shared-library
 */

package jugggao.com.controller

import jugggao.com.utils.logging.Logger
import org.jenkinsci.plugins.workflow.cps.CpsScript

class PostController {

    Script script

    Logger log = new Logger(this)

    PostController(script) {
        if(!(script instanceof CpsScript)) {
            log.fatal "The script is not CpsScript"
        }
        this.script = script
    }

    void clean() {
        ArrayList<String> cleanFile = ['Dockerfile', '.dockerignore']
        for (i in cleanFile) {
            if (fileExists(i)) {
                log.info "Clean up file $i."
                script.sh("rm -f $i")
            }
        }
    }


}