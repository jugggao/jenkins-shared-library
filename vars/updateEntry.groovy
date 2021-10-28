/**
 * Create by Peng.Gao at 2021/3/16 9:45
 *
 * A Part of the Project jenkins-shared-library
 */

import jugggao.com.controller.UpdateController
import jugggao.com.utils.logging.Logger


def call(Map config = [:], Closure body = {}) {

    Logger.init(this, 'trace')

    UpdateController controller = new UpdateController(this)

    controller(config)

    body()

}

