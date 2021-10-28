import jugggao.com.controller.MultiReleaseController
import jugggao.com.utils.logging.Logger

def call(Map config = [:], Closure body = {}) {

    Logger.init(this, 'trace')

    MultiReleaseController controller = new MultiReleaseController(this)

    controller(config)

    body()

}