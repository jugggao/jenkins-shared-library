package jugggao.com.app

import jugggao.com.utils.logging.Logger
import org.jenkinsci.plugins.workflow.cps.CpsScript

class Archive implements Serializable {

    static Script script

    private Logger log = new Logger(this)

    Archive(Script script) {
        if (!(script instanceof CpsScript)) {
            log.fatal "This script is not instance of CpsScript, type: ${script.getClass().getName()}"
        }
        this.script = script
    }

    String archiveFile(String target) {

        log.info "Archive package."

        try {
            script.archiveArtifacts(
                artifacts: target,
                onlyIfSuccessful: true,
            )

            String url = script.env['BUILD_URL'] + 'artifact/' + target

            return url
        } catch(e) {
            log.error "Failed to Archive package."
            throw e
        }
    }

    void renameBuildFile(String source, String target) {
        log.info "Rename $source to $target."
        script.sh 'cp -f ' + source + ' ' + target
    }
}
