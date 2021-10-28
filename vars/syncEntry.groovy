/**
 * Create by Peng.Gao at 2021/3/16 9:42
 *
 * A Part of the Project jenkins-shared-library
 */

import jugggao.com.controller.SyncController
import jugggao.com.utils.logging.Logger


def call(Map config = [:], Closure body = {}) {

    Logger.init(this, 'trace')

    SyncController controller = new SyncController(this)

    controller(config)

    body()

}

