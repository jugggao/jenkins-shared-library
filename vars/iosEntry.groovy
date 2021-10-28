/**
 * Create by Peng.Gao at 2021/3/16 9:46
 *
 * A Part of the Project jenkins-shared-library
 */


import jugggao.com.controller.IosController
import jugggao.com.utils.logging.Logger

def call(Map config = [:], Closure body = {}) {

    Logger.init(this, 'trace')

    IosController controller = new IosController(this)

    controller(config)

    body()

}
