/**
 * Create by Peng.Gao at 2021/2/25
 *
 * A Part of the Project jenkins-shared-library
 */

import jugggao.com.controller.ServiceController
import jugggao.com.utils.logging.Logger

import static jugggao.com.utils.config.ConfigConstants.*

def call(Map config = [:], Closure body = {}) {

    Logger.init(this, 'trace')

    ServiceController controller = new ServiceController(this)

    controller(config)

    body()

}