/**
 * Created by Peng.Gao at 2021/2/28 21:25
 * A Part of the Project jenkins-shared-library
 */

package jugggao.com.utils

import com.cloudbees.groovy.cps.NonCPS
import jugggao.com.utils.logging.Logger
import org.jenkinsci.plugins.workflow.cps.CpsScript

class CheckUtils implements Serializable {

    static void notDirectoryPanic(Script script, String dir, Closure body = {}) {

        Logger log = new Logger('notDirectoryPanic')

        log.debug "Clean Dir $dir"

        notCpsScriptPanic(script) {

            Integer returnCode = script.sh(
                script: "[ -d $dir ]",
                returnStatus: true
            ) as Integer

            if (returnCode != 0) {
                log.fatal "$dir does not exist or is not a directory"
            }

            body()
        }
    }

    static Boolean hasDirectory(Script script, String dir) {

        notCpsScriptPanic(script) {
            Integer returnCode = script.sh(
                    script: "[ -d $dir ]",
                    returnStatus: true
            ) as Integer

            if (returnCode != 0) {
                return false
            }
        }

        return true
    }

    static void emptyDirectoryPanic(Script script, String dir, Closure body = {}) {
        Logger log = new Logger('emptyDirectoryPanic')

        String countDirectoryCmd = 'ls ' + dir + ' | wc -w'

        Integer returnStdout = (script.sh(
                script: countDirectoryCmd,
                returnStdout: true
        ) as String).trim() as Integer

        if (returnStdout == 0) {
            log.fatal "Directory $dir is empty."
        }

        body()
    }

    static void fileNotExistPanic(Script script, String file, Closure body = {}) {
        Logger log = new Logger('fileNotExistPanic')
        notCpsScriptPanic(script) {

            if (!script.fileExists(file)) {
                log.fatal "No such file or directory of $file"
            }

            body()
        }
    }

    static void emptyVarPanic(Script script, String varName, def var, Closure body = {}) {
        Logger log = new Logger(script)
        notCpsScriptPanic(script) {

            if (!var) {
                log.fatal "Non-empty $varName value is empty."
            }

            body()
        }
    }

    static void notCpsScriptPanic(Script script, Closure body = {}) {
        if (!(script instanceof CpsScript)) {
            script.error "Script $script is not a CpsScript object."
        }

        body()
    }
}