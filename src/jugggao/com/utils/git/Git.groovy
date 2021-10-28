package jugggao.com.utils.git

import jugggao.com.utils.logging.Logger
import org.jenkinsci.plugins.workflow.cps.CpsScript

import static jugggao.com.utils.config.ConfigConstants.DOCKER_REGISTER_CREDENTIALS
import static jugggao.com.utils.config.ConfigConstants.GIT_CREDENTIALS

class Git implements Serializable {

    private static Script script

    Logger log = new Logger(this)

    Git(Script script) {
        if (!(script instanceof CpsScript)) {
            log.fatal "The script is not CpsScript"
        }
        this.script = script
    }

    void clone(String repo, String branch) {
        log.info "Git clone $branch branch from $repo."
        try {
            /*
            script.checkout([
                $class: 'GitSCM',
                branches: [[name: branch]],
                doGenerateSubmoduleConfigurations: false,
                extensions: [[$class: 'CleanCheckout']],
                submoduleCfg: [],
                userRemoteConfigs: [[credentialsId: GIT_CREDENTIALS, url: repo]]
            ])
             */
            script.git(
                credentialsId: GIT_CREDENTIALS,
                branch: branch,
                url: repo
            )

        } catch (e) {
            log.error "Git clone $branch branch from $repo failed."
            throw e
        }
    }

    void cloneWithTag(String repo, String tag, String dir = '') {

        try {
            script.withCredentials([
                    script.usernamePassword(
                            credentialsId: GIT_CREDENTIALS,
                            usernameVariable: 'gitUsername',
                            passwordVariable: 'gitPassword',
                    )
            ]) {


                def (_, head, tail) = (repo =~ /(https?:\/\/)(.*)/)[0]

                script.sh 'git clone ' + (String) head  + script.env['gitUsername'] + ':' +
                        script.env['gitPassword'] + '@' + (String) tail + ' ' + dir
            }
        } catch(e) {
            log.error "Failed to git clone $tag from $repo."
            throw e
        }
    }

    void checkoutCommitId(String commitId) {
        try {
            script.sh(
                script: 'git reset --hard ' + commitId
            )
        } catch(e) {
            log.error "Failed to checkout commit id."
            throw e
        }
    }

    String getUrl() {
        try {
            (script.sh(
                    script: "git config --get remote.origin.url",
                    returnStdout: true
            ) as String).trim()
        } catch (e) {
            log.error 'Git get-url command execute failed.'
            throw e
        }
    }

    String getCommitID(int len = 40) {
        try {
            String commitID = (script.sh(
                    script: 'git rev-parse HEAD',
                    returnStdout: true
            ) as String).trim()[0..(len - 1)]

            return commitID
        } catch (e) {
            log.error 'Git rev-parse command execute failed.'
            throw e
        }
    }

    String getCommitMessage() {
        String commitID = getCommitID()

        try {
            script.sh(
                    script: "git log --oneline --pretty='%H ## %s' | \
                                     grep $commitID              | \
                                     awk -F ' ## ' '{print \$2}'",
                    returnStdout: true).toString().trim()
        } catch (e) {
            log.error 'Git log command execute failed.'
            throw e
        }
    }

    String getAuthor() {
        String commitID = getCommitID()
        try {
            script.sh(
                    script: "git show $commitID | awk  '/^Author:/{print \$2}'",
                    returnStdout: true).toString().trim()
        } catch (e) {
            log.error 'Git log command execute failed.'
            throw e
        }
    }
}