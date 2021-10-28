package jugggao.com.ci

import org.jenkinsci.plugins.workflow.cps.CpsScript
import jugggao.com.utils.logging.Logger

import static jugggao.com.utils.CommonUtils.getFileMd5
import static jugggao.com.utils.CheckUtils.fileNotExistPanic
import static jugggao.com.utils.config.ConfigConstants.*


class Build implements Serializable {

    static Script script

    private Logger log = new Logger(this)

    public static Language language

    public static Tool tool

    Build(Script script) {
        if (!(script instanceof CpsScript)) {
            log.fatal "This script is not instance of CpsScript, type: ${script.getClass().getName()}"
        }
        this.script = script
    }

    void init(Map args) {
        ['language', 'tool', 'options', 'target'].each { args.get(it, '') }

        log.info "Build initializes."
        initLanguage(args)
        initTool(args)
    }

    private void initLanguage(Map args) {
        String lang = args.language

        if (!Language.contains(lang)) {
            log.warn "$lang not support."
            script.error "Initialize language failed."
        }
        language = Language[lang.toUpperCase()] as Language
    }

    private void initTool(Map args) {

        String t = (args['tool'] as String).toUpperCase()
        String options = args['options']
        String content = args['content']

        tool = language.tool

        if (t) {
            if (!Tool.contains(t)) {
                log.fatal "$t not support."
            }
            tool = Tool[t] as Tool

        }

        if (options) {
            tool.options = options
        }

        if (content) {
            tool.content = content
        }
    }

    void build(String command = '') {

        log.info "Complete build initialized. " + "Language is ${language}, " +
            "use ${tool} to building, " + "extended options is '${tool.options}'"

        fileNotExistPanic(script, tool.buildFile) {
            try {
                switch (language) {
                    case Language.NODEJS:
                        String buildCommand = command ?: (tool.command + ' ' + NODE_BUILD_COMMAND) as String
                        nodejsBuild(buildCommand)
                        break
                    case Language.SWIFT:
                        script.sh("$tool.command $tool.options")
                        break
                    case Language.HTML:
                        break
                    case Language.PHP:
                        break
                    default:
                        script.sh("$tool.command $tool.options")
                }

                log.info "Build stage completed."
            } catch (e) {
                log.error "Build command execute failed."
                throw e
            }
        }
    }

    private void nodejsBuild(String command) {

        String md5File = tool.buildFile + '.md5'
        String nodeTool = tool.toString().toLowerCase()

        try {
            if (script.fileExists(md5File)) {
                String oldMd5 = script.readFile(md5File) as String
                String newMd5 = getFileMd5(script, tool.buildFile)

                if (oldMd5 != newMd5) {

                    log.debug "Node Tool: $nodeTool"

                    script.nodejs(nodeTool) {
                        String nodeVersion = script.sh(
                                script: 'node --version',
                                returnStdout: true
                        )
                        log.debug "Node Version: $nodeVersion"

                        String npmConfig = script.sh(
                                script: 'npm config ls',
                                returnStdout: true
                        )
                        log.debug "NPM Config: $npmConfig"

                        script.sh(tool.command + ' ' + tool.options)
                    }

                    script.writeFile(
                        file: md5File,
                        text: newMd5
                    )
                } else {
                    log.info "$md5File md5 check ok, pass the stage."
                }
            } else {
                String md5 = getFileMd5(script, tool.buildFile)

                log.debug "Node Tool: $nodeTool"

                script.nodejs(nodeTool) {
                    String nodeVersion = script.sh(
                            script: 'node --version',
                            returnStdout: true
                    )
                    log.debug "Node Version: $nodeVersion"

                    String npmConfig = script.sh(
                            script: 'npm config ls',
                            returnStdout: true
                    )
                    log.debug "NPM Config: $npmConfig"

                    script.sh(tool.command + ' ' + tool.options)
                }

                script.writeFile(
                    file: md5File,
                    text: md5
                )
            }
        } catch (e) {
            log.error "Nodejs install stage failed."
            throw e
        }

        script.sh(command)
    }
}