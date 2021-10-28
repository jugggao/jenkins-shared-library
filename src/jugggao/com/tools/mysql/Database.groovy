package jugggao.com.tools.mysql

import com.cloudbees.groovy.cps.NonCPS

import static jugggao.com.utils.config.ConfigConstants.*
import static jugggao.com.utils.CommonUtils.*
import static jugggao.com.utils.CheckUtils.*

class Database extends Mysql implements Serializable {

    Database(Script script, String id, Map dbConnectParams) {
        super(script, id, dbConnectParams)
    }

    ArrayList getDatabases() {

        String sql = 'show databases'

        return execSql(sql, '', false).split("\n")
    }

    Map dumpDatabase(String database, String directory = '') {

        String options = '--databases ' + database
        directory = directory ?: TOOL_MYSQL_DUMP_DIR + 'db/' + id
        makeDir(script, directory)

        String target = directory + '/' +
                database + '-' + new Date().format("yyyyMMddHHmm") +
                '.sql'

        execDump(options, target)
        return [(database): target]
    }

    void importDatabase(String database, String target) {
        fileNotExistPanic(script, target) {
            execImport(database, target)
        }
    }

    void createDatabase(String database) {

        String sql = 'CREATE DATABASE ' + database + ' CHARACTER SET utf8 COLLATE utf8_general_ci;'

        execSql(sql, '', false)
    }

    void createOrReplaceView(String project, ArrayList databases) {

        log.info "Create or replace view from $project."

        String sql = 'CREATE OR REPLACE VIEW ' + project + '_database_tables' +
                ' AS SELECT TABLE_SCHEMA, TABLE_NAME FROM information_schema.`TABLES`' +
                ' WHERE TABLE_SCHEMA IN(' + listToQuoteStr(databases) + ')'

        log.debug "View Sql: $sql"
        execSql(sql, TOOL_MYSQL_PIPELINE_DATABASE, false)
    }

    @NonCPS
    private static listToQuoteStr(ArrayList ary) {
        return String.format("\"%s\"", ary.join('","'))
    }

    Map getViewData(String project) {

        Map dtInfo = [:]

        String sql = 'select * from ' + project + '_database_tables;'
        String sDtInfo = execSql(sql, TOOL_MYSQL_PIPELINE_DATABASE, false)

        ArrayList<String> dt = sDtInfo.trim().split("\n")

        emptyVarPanic(script, 'dt', dt)

        dt.each {

            String database = it.split(/\s+/)[0] ?: ''
            String table = it.split(/\s+/)[1] ?: ''

            if (database && table) {
                if (!dtInfo[(database)]) {
                    dtInfo << [(database): [table]]
                } else {
                    dtInfo[(database)] << table
                }
            }
        }

        return dtInfo
    }
}
