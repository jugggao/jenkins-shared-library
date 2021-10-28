/**
 * Create by Peng.Gao at 2021/3/22 10:25
 *
 * A Part of the Project jenkins-shared-library
 */

package jugggao.com.utils.notification

import jugggao.com.utils.logging.Logger
import org.jenkinsci.plugins.workflow.cps.CpsScript

enum BuildResult {
    SUCCESS, FAILURE, UNSTABLE
}

enum NotificationTool {
    WECHAT, EMAIL, WEBHOOK,

    @Override
    public String toString() {
        return this.toString().toLowerCase()
    }
}

class Notification implements Serializable {

    private static Script script

    private Logger log = new Logger(this)

    private def notification

    Notification(Script script) {
        if (!(script instanceof CpsScript)) {
            log.fatal "The script $script is not CpsScript"
        }

        this.script = script
    }

    void selector(String tool, Map config) {

        NotificationTool notificationTool = NotificationTool[tool.toUpperCase()] as NotificationTool

        switch (notificationTool) {

            case NotificationTool.WECHAT:
                notification = new Wechat(script, config)
                break
            case NotificationTool.EMAIL:
                notification = new Email(script, config)
                break
            case NotificationTool.WEBHOOK:
                notification = new WebHook(script, config)
                break
            default:
                log.fatal "$notificationTool is not support."
        }
    }

    void notify(Map info) {

        String buildResult = script.currentBuild['result'] ?: 'SUCCESS'

        Map failed = info + [
                'Pipeline log': script.env['BUILD_URL'] + 'console'
        ]
        Map unstable = info + [
                'Pipeline log': script.env['BUILD_URL'] + 'console'
        ]

        String message = ''

        switch (BuildResult[buildResult.toUpperCase()]) {

            case BuildResult.SUCCESS:
                message = notification.genMessage(info)
                break
            case BuildResult.FAILURE:
                message = notification.genMessage(failed)
                break
            case BuildResult.UNSTABLE:
                message = notification.genMessage(unstable)
                break
            default:
                log.fatal "Pipeline $buildResult result out of range."
        }
        notification.sendMessage(message)
    }
}
