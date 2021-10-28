/**
 * Create by Peng.Gao at 2021/3/16 9:45
 *
 * A Part of the Project jenkins-shared-library
 */

import jugggao.com.controller.MultiUpdateController
import jugggao.com.utils.logging.Logger


def call(Map config = [:], Closure body = {}) {

    Logger.init(this, 'trace')

    MultiUpdateController controller = new MultiUpdateController(this)

    controller(config)

    body()

}

