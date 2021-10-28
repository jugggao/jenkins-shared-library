/**
 * Created by Peng.Gao at 2021/2/28 22:49
 * A Part of the Project app-build-publish
 */

package jugggao.com.utils.logging

import jugggao.com.utils.config.ConfigConstants

class Logger implements Serializable {

    private static Script script

    public String name

    public static LogLevel level

    Logger(String name = '') {
        this.name = name
    }

    Logger(Object object) {
        if (object instanceof Object) {
            this.name = object.getClass().getCanonicalName().toString()
            if (this.name == null) {
                this.name = "$object"
            }
        }
    }

    static void init(Script script, LogLevel logLvl) {
        level = logLvl
        this.script = script
    }

    static void init(Script script, String sLevel = ConfigConstants.LOGLEVEL) {
        init(script, LogLevel.fromString(sLevel))
    }

    static void init(Script script, Integer iLevel) {
        init(script, LogLevel.fromInteger(iLevel))
    }

    void trace(String message) {
        log(LogLevel.TRACE, message)
    }

    void info(String message) {
        log(LogLevel.INFO, message)
    }

    void debug(String message) {
        log(LogLevel.DEBUG, message)
    }

    void warn(String message) {
        try {
            script.addWarningBadge(message)
        } catch (Throwable e) {
            throw e
        }
        log(LogLevel.WARN, message)
    }

    void error(String message) {
        try {
            script.addErrorBadge(message)
        } catch (Throwable e) {
            throw e
        }
        log(LogLevel.ERROR, message)
    }

    void fatal(String message) {
        try {
            script.addErrorBadge(message)
        } catch (Throwable e) {
            throw e
        }
        log(LogLevel.FATAL, message)
        script.error(message)
    }

    void deprecated(String message) {
        try {
            script.addWarningBadge(message)
        } catch (Throwable e) {
            throw e
        }
        log(LogLevel.DEPRECATED, message)
    }

    void deprecated(String deprecatedItem, String newItem) {
        String message = "The step/function/class '$deprecatedItem' is marked as depecreated and will be removed in future releases. " +
                "Please use '$newItem' instead."
        deprecated(message)
    }

    void log(LogLevel logLevel, String message) {
        if (doLog(logLevel)) {
            String msg = "$name : $message"
            writeLogMsg(logLevel, msg)
        }
    }

    private static void writeLogMsg(LogLevel logLevel, String msg) {
        String lvlString = wrapColor(
                logLevel.getColorCode(),
                "[${logLevel.toString()}]"
        )

        if (script != null) {
            script.echo("$lvlString $msg")
        }
    }

    private static String wrapColor(String colorCode, String str) {
        String flag = str
        if (hasTermEnv()) {
            flag = "\u001B[${colorCode}m${str}\u001B[0m"
        }

        return flag
    }

    private static Boolean hasTermEnv() {
        String termEnv = null
        if (script != null) {
            try {
                termEnv = script.env['TERM']
            } catch (e) {
                throw e
            }
        }

        return termEnv != null
    }

    private static boolean doLog(LogLevel logLevel) {
        if (logLevel.getLevel() >= level.getLevel()) {
            return true
        }

        return false
    }
}
