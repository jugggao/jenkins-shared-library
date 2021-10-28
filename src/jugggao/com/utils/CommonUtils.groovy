/**
 * Created by Peng.Gao at 2021/2/28 21:03
 * A Part of the Project jenkins-shared-library
 */

package jugggao.com.utils

import jugggao.com.utils.logging.Logger
import org.apache.commons.codec.digest.DigestUtils
//import org.zeroturnaround.zip.ZipUtil

class CommonUtils implements Serializable {

    static Map mergeNested(Map[] sources) {
        if (sources.length == 0) return [:]
        if (sources.length == 1) return sources[0]

        sources.inject([:]) { result, source ->
            source.each { k, v ->
                result[k] = result[k] instanceof Map ? mergeNested(result[k] as Map, v as Map) : v
            }
            return result
        }
    }

    /*
    sh 插件即使返回值为数值，但类型依然为 String
     */

    static String disableOutputSh(Map args, Script script) {
        ['returnStdout', 'returnStatus'].each { args.get(it, false) }
        args.get('script', '')

        if (args['returnStdout'] && args['returnStatus']) {
            script.error "You may not select both returnStdout and returnStatus."
        }

        script.sh(
            script: '#!/bin/sh -e\n' + args['script'],
            returnStdout: args['returnStdout'],
            returnStatus: args['returnStatus']
        )
    }

    static String getFileMd5(Script script, String file) {

        String content = script.readFile(file)

        return DigestUtils.md5Hex(content)
    }

    static void cleanDir(Script script, String dir) {

        script.sh(
            script: 'rm -rf ' + dir + '/*'
        )

    }

    static String archiveFile(Script script, String target) {
        script.archiveArtifacts(
            artifacts: target,
            onlyIfSuccessful: true,
        )

        String fileName = target.split('/')[-1].join('')
        String url = script.env['BUILD_URL'] + 'artifact/archive/' + fileName

        return url
    }

    static String renameFile(Script script, String source, String target) {
        script.sh 'mv -f ' + source + ' ' + target
        return target
    }

    static void makeDir(Script script, String directory) {

        Logger log = new Logger('makeDir')

        if (CheckUtils.hasDirectory(script, directory)) {
            script.sh 'mkdir -p ' + directory
        }
    }

    static String archiveDir(Script script, String directory, String fileName) {
        Logger log = new Logger('archiveDir')

        String zipTarget = fileName + '.zip'

        script.zip(
                archive: false,
                overwrite: true,
                dir: directory,
                zipFile: zipTarget
        )

        try {
            script.archiveArtifacts(
                    artifacts: zipTarget,
                    onlyIfSuccessful: true,
            )

            String url = script.env['BUILD_URL'] + 'artifact/' + zipTarget
            return url
        } catch(e) {
            log.error "Failed to Archive ${zipTarget}."
            throw e
        }
    }
}