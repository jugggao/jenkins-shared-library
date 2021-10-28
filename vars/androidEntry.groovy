/**
 * Create by Peng.Gao at 2021/3/16 9:46
 *
 * A Part of the Project jenkins-shared-library
 */


import jugggao.com.controller.AndroidController
import jugggao.com.utils.logging.Logger

def call(Map config = [:], Closure body = {}) {

    Logger.init(this, 'trace')

    AndroidController controller = new AndroidController(this)

    controller(config)

    body()

}
