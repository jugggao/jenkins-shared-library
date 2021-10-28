import jugggao.com.controller.MultiSyncController
import jugggao.com.utils.logging.Logger

def call(Map config = [:], Closure body = {}) {

    Logger.init(this, 'trace')

    MultiSyncController controller = new MultiSyncController(this)

    controller(config)

    body()

}