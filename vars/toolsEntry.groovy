/**
 * Create by Peng.Gao at 2021-07-05
 *
 * A Part of the Project jenkins-shared-library
 */


import jugggao.com.controller.ExportProjectDataController
import jugggao.com.controller.SyncMysqlController
import jugggao.com.controller.BackupMysqlController
import jugggao.com.utils.logging.Logger


def call(Map config = [:]) {

    Logger.init(this, 'trace')

    this.echo "$config"

    String configFile = config.settings
    String jobName = config.name
    String toolType = config.toolType

    switch (jobName) {
        case 'sync-mysql':
            SyncMysqlController controller = new SyncMysqlController(this)
            controller(settings: configFile, toolType: toolType)
            break
        case 'export-project-data':
            ExportProjectDataController controller = new ExportProjectDataController(this)
            controller(settings: configFile, toolType: toolType)
            break
        case 'backup-mysql':
            BackupMysqlController controller = new BackupMysqlController(this)
            controller(settings: configFile, toolType: toolType)
            break
        default:
            this.error "The ${jobName} pipeline is not support."
    }
}

