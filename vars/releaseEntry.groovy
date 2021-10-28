/**
 * Create by Peng.Gao at 2021/3/16 9:43
 *
 * A Part of the Project jenkins-shared-library
 */

import jugggao.com.controller.ReleaseController
import jugggao.com.utils.logging.Logger

import static jugggao.com.utils.config.ConfigConstants.*

def call(Map config = [:], Closure body = {}) {

    Logger.init(this, 'trace')

    ReleaseController controller = new ReleaseController(this)

    controller(config)

    body()

}