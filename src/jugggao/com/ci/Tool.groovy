/**
 * Create by Peng.Gao at 2021/3/4 17:45
 *
 * A Part of the Project jenkins-shared-library
 */

package jugggao.com.ci

import static jugggao.com.utils.config.ConfigConstants.*


enum Tool {

    MAVEN(MAVEN_CMD, MAVEN_OPTIONS, MAVEN_CONTENT, MAVEN_BUILD_FILE),
    GRADLE(GRADLE_CMD, GRADLE_OPTIONS, GRADLE_CONTENT, GRADLE_BUILD_FILE),
    NODE12(NODE12_CMD, NODE12_OPTIONS, NODE12_CONTENT, NODE12_BUILD_FILE),
    NODE10(NODE10_CMD, NODE10_OPTIONS, NODE10_CONTENT, NODE10_BUILD_FILE),
    NODE14(NODE14_CMD, NODE14_OPTIONS, NODE14_CONTENT, NODE14_BUILD_FILE),
    CNPM(CNPM_CMD, CNPM_OPTIONS, CNPM_CONTENT, CNPM_BUILD_FILE),
    YARN(YARN_CMD, YARN_OPTIONS, YARN_CONTENT, YARN_BUILD_FILE),
    GOLANG(GOLANG_CMD, GOLANG_OPTIONS, GOLANG_CONTENT, GOLANG_BUILD_FILE),
    COCOAPODS(COCOAPODS_CMD, COCOAPODS_OPTIONS, COCOAPODS_CONTENT, COCOAPODS_BUILD_FILE),
    HTML(HTML_CMD, HTML_OPTIONS, HTML_CONTENT, HTML_BUILD_FILE),
    PHP(PHP_CMD, PHP_OPTIONS, PHP_CONTENT, PHP_BUILD_FILE),

    public String command
    public String options
    public String content
    public String buildFile

    Tool(String command, String options, String content, String buildFile) {
        this.command = command
        this.options = options
        this.content = content
        this.buildFile = buildFile
    }

//    @Override
//    public String toString() {
//        return this.toLowerCase()
//    }

    static boolean contains(String tool) {
        ArrayList tools = values().collect { it.toString() }

        if (tool.toUpperCase() in tools) {
            return true
        }
        return false
    }
}