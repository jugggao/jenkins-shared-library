/**
 * Create by Peng.Gao at 2021/2/26 13:15
 *
 * A Part of the Project jenkins-shared-library
 */

package jugggao.com.container

import jugggao.com.ci.Language
import jugggao.com.utils.logging.Logger
import org.jenkinsci.plugins.workflow.cps.CpsScript

import static jugggao.com.utils.CheckUtils.*
import static jugggao.com.utils.config.ConfigConstants.*
import static jugggao.com.utils.config.ConfigService.data
import static jugggao.com.utils.config.ConfigService.data

class Docker implements Serializable {

    private static Script script

    Logger log = new Logger(this)

    private Map info

    Docker(Script script) {
        if (!(script instanceof CpsScript)) {
            log.fatal "The script $script is not CpsScript"
        }

        this.script = script
    }

    void withInfo(Map info, Closure body = {}) {

        log.debug "Info: $info"

        ['image', 'commitID', 'options', 'dockerfile', 'ignorefile', 'repository', 'tag', 'chown'].each {
            info.get(it, '')
        }
        info.get('userID', 'webhook')
        info.get('content', '.')

        ['image'].each {
            emptyVarPanic(script, it, info[it])
        }

        this.info = info

        body()
    }

    private String cmd(String command) {
        try {
            script.sh("sudo docker $command")
        } catch (e) {
            log.error "Docker $command command execute failed."
            throw e
        }
    }

    void genDockerfile(Language language) {

        String dockerfileContent = ''

        try {
            if (info.dockerfile) {

                String dockerfile = script.env['workspace'] + '/' + info.dockerfile
                dockerfileContent = script.readFile(dockerfile)

            } else {

                script.sh("rm -f $DOCKER_BUILD_FILE")
                String template = DOCKER_TEMPLATE_DIR + '/language/' +
                        language.toString().toLowerCase() + '/' + DOCKER_BUILD_FILE
                dockerfileContent = script.libraryResource(template) as String
            }
        } catch(e) {
            log.error "Failed to read file."
            throw e
        }

        emptyVarPanic(script, 'dockerfileContent', dockerfileContent) {

            log.info "Generate template."

            try {

                script.writeFile(
                    file: DOCKER_BUILD_FILE,
                    text: dockerfileContent
                )
            } catch (e) {
                log.error 'Generate Dockerfile for template failed.'
                throw e
            }
        }
    }

    void genDockerfile() {

        ArrayList<String> dockerfileCommand = []

        dockerfileCommand.add(
            'LABEL made.by=Jenkins' +
                ' build.user=' + info['userID'] +
                ' commit.id=' + info['commitID']
        )
        dockerfileCommand.add('RUN mkdir -p ' + DOCKER_APP_ROOT)

        if (info.chown) {
            dockerfileCommand.add('COPY --chown=' + info.chown + ' . ' + DOCKER_APP_ROOT)
        } else {
            dockerfileCommand.add('COPY . ' + DOCKER_APP_ROOT)
        }

        try {
            fileNotExistPanic(script, DOCKER_BUILD_FILE) {
                script.sh("echo >> $DOCKER_BUILD_FILE")

                dockerfileCommand.each {
                    script.sh("echo $it >> $DOCKER_BUILD_FILE")
                }

                String sDockerfile = script.readFile(DOCKER_BUILD_FILE)
                log.debug "Dockerfile: \n$sDockerfile"
            }
        } catch (e) {
            log.error "Generate Dockerfile for settings failed."
            throw e
        }
    }

    void genMultiServiceDockerfile(ArrayList<Map> contents) {

        ArrayList<String> dockerfileCommand = []

        dockerfileCommand.add(
                'LABEL made.by=Jenkins' +
                        ' build.user=' + info['userID']
        )
        dockerfileCommand.add('RUN mkdir -p ' + DOCKER_APP_ROOT)

        String chownOption = ''

        if (info.chown) {
            chownOption = '--chown=' + info.chown
        }

        for (content in contents) {
            dockerfileCommand.add('COPY ' + chownOption + ' ' + content.path + ' ' + DOCKER_APP_ROOT + '/' + content.name)
        }

        try {
            fileNotExistPanic(script, DOCKER_BUILD_FILE) {
                script.sh("echo >> $DOCKER_BUILD_FILE")

                dockerfileCommand.each {
                    script.sh("echo $it >> $DOCKER_BUILD_FILE")
                }

                String sDockerfile = script.readFile(DOCKER_BUILD_FILE)
                log.debug "Dockerfile: \n$sDockerfile"
            }
        } catch (e) {
            log.error "Generate Dockerfile for settings failed."
            throw e
        }
    }

    void genDockerIgnoreFile(Language language) {

        if (!info['ignorefile']) {

            log.info "Generate $DOCKER_IGNORE_FILE template file."

            String template = "$DOCKER_TEMPLATE_DIR/language/${language.toString().toLowerCase()}/$DOCKER_IGNORE_FILE"

            try {
                script.sh("rm -f $DOCKER_IGNORE_FILE")

                String ignore = script.libraryResource(template)
                script.writeFile(
                    file: info['content'] + '/' + DOCKER_IGNORE_FILE,
                    text: ignore
                )
                log.debug ".dockerignore:\n$ignore"

            } catch (e) {
                log.error 'Generate .dockerignore for template failed.'
                throw e
            }
        }
    }

    String build() {

        String image = info.image
        String content = info.content
        String options = info.options

        log.info "Build image: $image Build content: $content."

        log.debug "Check empty directory: $content."

        emptyDirectoryPanic(script, content)

        String command = 'build ' + options + ' -t ' + image +
            ' -f ' + DOCKER_BUILD_FILE + ' ' + content

        fileNotExistPanic(script, DOCKER_BUILD_FILE) {
            script.timeout(time: DOCKER_IMAGE_BUILD_TIMEOUT, unit: 'SECONDS') {
                cmd(command)
            }
        }

        return image
    }

    void push() {

        log.info "Push image ${info['image']}"

        script.timeout(time: DOCKER_IMAGE_PUSH_TIMEOUT, unit: 'SECONDS') {
            cmd("push ${info['image']}")
        }
    }

    void push(String image) {
        log.info "Push image $image"

        script.timeout(time: DOCKER_IMAGE_PUSH_TIMEOUT, unit: 'SECONDS') {
            cmd("push $image")
        }
    }

    void reTag(String image) {
        log.info "Retag ${info.image} to $image."

        script.timeout(time: DOCKER_IMAGE_PUSH_TIMEOUT, unit: 'SECONDS') {
            cmd('tag' + ' ' + info.image + ' ' + image)
        }
    }

    void login() {
        log.info "Login to Docker Registry "

        try {
            script.timeout(time: DOCKER_LOGIN_TIMEOUT, unit: 'SECONDS') {
                script.withCredentials([
                    script.usernamePassword(
                        credentialsId: DOCKER_REGISTER_CREDENTIALS,
                        passwordVariable: 'registryPassword',
                        usernameVariable: 'registryUsername'
                    )
                ]) {
                    String loginArgs = 'login -u' + script.env['registryUsername'] +
                        ' -p ' + script.env['registryPassword'] + ' ' + DOCKER_REGISTER
                    cmd(loginArgs)
                }
            }
        }
        catch (e) {
            log.error 'Error occurred during login to registry'
            throw e
        }
    }

    String logout() {
        cmd('logout')
    }
}