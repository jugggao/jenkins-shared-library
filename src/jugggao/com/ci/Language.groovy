package jugggao.com.ci

enum Language {

    JAVA(Tool.MAVEN),
    NODEJS(Tool.NODE12),
    GOLANG(Tool.GOLANG),
    SWIFT(Tool.COCOAPODS),
    HTML(Tool.HTML),
    PHP(Tool.PHP)

    public Tool tool

    Language(Tool tool) {
        this.tool = tool
    }

    static boolean contains(String language) {
        String[] languages = values().collect { it.toString() }
        if (language.toUpperCase() in languages) {
            return true
        }
        return false
    }
}