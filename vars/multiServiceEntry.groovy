import jugggao.com.controller.MultiServiceController
import jugggao.com.utils.logging.Logger

def call(Map config = [:], Closure body = {}) {

    Logger.init(this, 'trace')

    MultiServiceController controller = new MultiServiceController(this)

    controller(config)

    body()

}