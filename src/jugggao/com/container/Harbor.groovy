/**
 * Create by Peng.Gao at 2021/4/16 17:14
 *
 * A Part of the Project jenkins-shared-library
 */

package jugggao.com.container

import com.cloudbees.groovy.cps.NonCPS
import groovy.json.JsonSlurper
import jugggao.com.utils.logging.Logger
import org.jenkinsci.plugins.workflow.cps.CpsScript

import java.util.regex.Pattern

import static jugggao.com.utils.config.ConfigConstants.HARBOR_API_URL


class Harbor implements Serializable {
    private static Script script

    Logger log = new Logger(this)

    private Map info

    private String project

    private String repository

    Harbor(Script script, String project, String repository) {
        if (!(script instanceof CpsScript)) {
            log.fatal "The script $script is not CpsScript"
        }

        this.script = script
        this.project = project
        this.repository = repository
    }

    ArrayList getTagsOfBranch(String branch) {
        log.info "Get tags of branch."

        ArrayList tags = []

        try {

            String url = HARBOR_API_URL + '/projects/' + project + '/repositories/' + repository + '/artifacts?page_size=100'
            Pattern regTags = ~"${branch.toLowerCase()}-\\w{8}"

            String responseBody = script.httpRequest(
                url: url,
                httpMode: 'GET',
                contentType: 'APPLICATION_JSON'
            )['content'] as String

            ArrayList artifacts = jsonParse(responseBody) as ArrayList

            artifacts.each { artifact ->
                artifact['tags'].each { tag ->
                    if ((tag['name'] as String).matches(regTags)) {
                        tags.add(tag['name'])
                    }
                }
            }
        } catch(e) {
            log.error('Failed to get tags of branch.')
            throw e
        }

        log.debug "Tags: $tags"

        return tags
    }

    ArrayList getTagsOfBranch(ArrayList<Map> branch) {
        log.info "Get tags of branch."

        ArrayList tags = []

        try {

            String url = HARBOR_API_URL + '/projects/' + project + '/repositories/' + repository + '/artifacts?page_size=100'

            ArrayList tagsInfo = []
            branch.each {
                tagsInfo.add(it.name + '-' + it.branch + '-\\w{8}')
            }

            Pattern regTags = ~tagsInfo.join('-')

            log.debug "Tags of regex: $regTags."

            String responseBody = script.httpRequest(
                    url: url,
                    httpMode: 'GET',
                    contentType: 'APPLICATION_JSON'
            )['content'] as String

            ArrayList artifacts = jsonParse(responseBody) as ArrayList

            artifacts.each { artifact ->
                artifact['tags'].each { tag ->
                    if ((tag['name'] as String).matches(regTags)) {
                        tags.add(tag['name'])
                    }
                }
            }
        } catch(e) {
            log.error('Failed to get tags of branch.')
            throw e
        }

        log.debug "Tags: $tags"

        return tags
    }

    void tagNotInBranchTagsPanic(String branch, String tag) {
        log.info "Confirm the tag in branch tags exists."

        ArrayList tags = getTagsOfBranch(branch)
        if (!(tag in tags)) {
            log.fatal "The $tag tag not in harbor $repository repository, Confirm to build on DEV environment or release on PRE evironment."
        }
    }

    void tagNotInBranchTagsPanic(ArrayList<Map> branch, String tag) {
        log.info "Confirm the tag in branch tags exists."

        ArrayList tags = getTagsOfBranch(branch)
        if (!(tag in tags)) {
            log.fatal "The $tag tag not in harbor $repository repository, Confirm to build on DEV environment or release on PRE evironment."
        }
    }

    @NonCPS
    private static ArrayList jsonParse(String json) {
        return new JsonSlurper().parseText(json) as ArrayList
    }
}
