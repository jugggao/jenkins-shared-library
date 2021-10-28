/**
 * Created by Peng.Gao at 2021/2/28 21:52
 * A Part of the Project jenkins-shared-library
 */

package jugggao.com.utils.logging


enum LogLevel implements Serializable {

    ALL(0, 0),
    TRACE(2, 8),
    DEBUG(3, 12),
    INFO(4, 0),
    DEPRECATED(5, 93),
    WARN(6, 202),
    ERROR(7, 9),
    FATAL(8, 5),
    NONE(Integer.MAX_VALUE, 0)

    Integer level

    static COLOR_CODE_PREFIX = "1;38;5;"
    Integer color

    LogLevel(Integer level, Integer color) {
        this.level = level
        this.color = color
    }

    static LogLevel fromInteger(Integer value) {
        for (lvl in values()) {
            if (lvl.getLevel() == value) return lvl
        }
        return INFO
    }

    static LogLevel fromString(String value) {
        for (lvl in values()) {
            if (lvl.toString().equalsIgnoreCase(value)) return lvl
        }
        return INFO
    }

    String getColorCode() {
        return COLOR_CODE_PREFIX + color.toString()
    }
}
