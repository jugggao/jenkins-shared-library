package jugggao.com.tools.mysql

import jugggao.com.utils.logging.Logger
import org.jenkinsci.plugins.workflow.cps.CpsScript

import static jugggao.com.utils.CheckUtils.emptyVarPanic

class Mysql implements Serializable {

    protected static Script script

    protected Logger log = new Logger(this)

    protected Map dbConnectParams

    protected String id

    Mysql(Script script, String id, Map dbConnectParams) {
        if (!(script instanceof CpsScript)) {
            log.fatal "Script $script is not CpsScript Object."
        }

        this.script = script
        this.id = id
        this.dbConnectParams = dbConnectParams
    }

    String execSql(sql = 'select 1', String database = '', Boolean stripFirstLine = false) {

        String credential = dbConnectParams.credential
        String host = dbConnectParams.host
        String port = dbConnectParams.port
        String usernameId = 'username-' + id
        String passwordId = 'password-' + id

        log.info "Query by '$sql' from $host."

        emptyVarPanic(script, 'credential', credential)

        String command = 'mysql --defaults-file=/dev/stdin -N ' + database + " -e '" + sql + "'"

        if (stripFirstLine) {
            command += ' | tail -n +2'
        }

        try {
            script.withCredentials([
                script.usernamePassword(
                    credentialsId: 'mysql-' + id,
                    usernameVariable: usernameId,
                    passwordVariable: passwordId,
                )
            ]) {
                script.sh(
                    script: "echo -e '" + mysqlClientSettings(
                        host: host, port: port,
                        user: script.env[usernameId],
                        password: script.env[passwordId]
                    ) + '\'| ' + command,
                    returnStdout: true
                )
            }
        } catch (e) {
            log.error "Falied to query of $host."
            throw e
        }
    }

    String execDump(String options, String target) {
        String credential = dbConnectParams.credential
        String host = dbConnectParams.host
        String port = dbConnectParams.port
        String usernameId = 'username-' + id
        String passwordId = 'password-' + id

        log.info "Dump by '$options' from $host."

        emptyVarPanic(script, 'credential', credential)

        try {
            script.withCredentials([
                script.usernamePassword(
                    credentialsId: 'mysql-' + id,
                    usernameVariable: usernameId,
                    passwordVariable: passwordId,
                )
            ]) {
                script.sh(
                    script: "echo -e '" + mysqlClientSettings(
                        host: host, port: port,
                        user: script.env[usernameId],
                        password: script.env[passwordId]
                    ) + '\'| mysqldump --defaults-file=/dev/stdin -R -E --single-transaction --set-gtid-purged=OFF '
                        + options + ' > ' + target,
                )
            }
        } catch (e) {
            log.error "Falied to dump of $host."
            throw e
        }
    }

    void execImport(String database, String target) {
        String credential = dbConnectParams.credential
        String host = dbConnectParams.host
        String port = dbConnectParams.port
        String usernameId = 'username-' + id
        String passwordId = 'password-' + id

        log.info "Import to $database by $target file from $host."

        emptyVarPanic(script, 'credential', credential)

        try {
            script.withCredentials([
                script.usernamePassword(
                    credentialsId: 'mysql-' + id,
                    usernameVariable: usernameId,
                    passwordVariable: passwordId,
                )
            ]) {
                script.sh(
                    script: "echo -e '" + mysqlClientSettings(
                        host: host, port: port,
                        user: script.env[usernameId],
                        password: script.env[passwordId]
                    ) + '\'| mysql --defaults-file=/dev/stdin ' +
                        database + ' -e "source ' + target + ';"',
                )
            }
        } catch (e) {
            log.error "Falied to import of $host."
            throw e
        }
    }

    static String mysqlClientSettings(Map args) {

        ['host', 'port', 'user', 'password'].each { args.get(it, '') }
        String host = args.host
        String port = args.port
        String user = args.user
        String password = args.password

        String settings = """[client]
            |host = $host
            |port = $port
            |user = $user
            |password = $password
            """.stripMargin()
        return settings
    }
}
