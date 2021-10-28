package jugggao.com.tools.mysql

import static jugggao.com.utils.CheckUtils.fileNotExistPanic
import static jugggao.com.utils.CommonUtils.makeDir
import static jugggao.com.utils.config.ConfigConstants.TOOL_MYSQL_DUMP_DIR

class Table extends Mysql implements Serializable{

    private String database

    Table(Script script, String id, String database, Map dbConnectParams) {
        super(script, id, dbConnectParams)
        this.database = database
    }

    ArrayList getTables() {

        String sql = 'show tables;'

        return execSql(sql, database, false).split("\n")
    }

    Map dumpTable(String table) {

        makeDir(script, TOOL_MYSQL_DUMP_DIR + 'table/' + id)

        String options = database + ' ' + table
        String target = TOOL_MYSQL_DUMP_DIR + 'db/' + id + '/' +
            database + '-' + table + '-'+ new Date().format("yyyyMMddHHmm") +
            '.sql'

        execDump(options, target)
        return [(table): target]
    }

    void importTable(String target) {
        fileNotExistPanic(script, target) {
            execImport(database, target)
        }
    }
}
