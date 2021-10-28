/**
 * Created by Peng.Gao at 2021/3/6 23:57
 * A Part of the Project jenkins-shared-library
 */

package jugggao.com.utils.git

import com.cloudbees.groovy.cps.NonCPS
import groovy.json.JsonSlurper
import jugggao.com.utils.logging.Logger
import org.jenkinsci.plugins.workflow.cps.CpsScript


import static jugggao.com.utils.CheckUtils.emptyVarPanic
import static jugggao.com.utils.config.ConfigConstants.*

class Gitlab implements Serializable {

    private static Script script

    private Logger log = new Logger(this)

    private Integer projectId

    private String apiUrl = GITLAB_API_URL

    private String apiToken = GITLAB_API_TOKEN_CREDENTIALS

    Gitlab(Script script, Integer projectId) {
        if (!(script instanceof CpsScript)) {
            log.fatal "The script is not CpsScript"
        }
        this.script = script
        this.projectId = projectId
    }

    /*
     * https://docs.gitlab.com/ee/api/branches.html#get-single-repository-branch
     */

    String getBranchLastCommitId(String branch) {
        log.info "Get last commit id for $branch branch."

        String commitId = ''

        try {

            String url = apiUrl + 'projects/' + projectId + '/repository/branches/' + branch

            emptyVarPanic(script, 'branch', branch) {
                withGitlabApiTokenCredentials {
                    def responseBody = script.httpRequest(
                        url: url,
                        httpMode: 'GET',
                        contentType: 'APPLICATION_JSON',
                        customHeaders: [
                            [
                                name : 'PRIVATE-TOKEN',
                                value: script.env['gitlabApiToken']
                            ]
                        ],
                    )

                    commitId = (jsonParse(responseBody['content'] as String) as Map).commit['id']

                    log.debug "CommitId: ${commitId}"

                }
            }

        } catch (e) {
            log.error "Failed to get last commit id for $branch branch."
            throw e
        }

        return commitId
    }

    void createNewTag(String tag, String commitId) {
        log.info "Create $tag tag with $commitId."

        try {

            String url = apiUrl + 'projects/' + projectId + '/repository/tags' +
                '?tag_name=' + tag + '&ref=' + commitId

            emptyVarPanic(script, 'tag', tag) {
                withGitlabApiTokenCredentials {
                    def responseBody = script.httpRequest(
                        url: url,
                        httpMode: 'POST',
                        contentType: 'APPLICATION_JSON',
                        customHeaders: [
                            [
                                name : 'PRIVATE-TOKEN',
                                value: script.env['gitlabApiToken']
                            ]
                        ],
                    )
                    log.debug "ResponseBody: ${responseBody['content']}"

                }
            }

        } catch (e) {
            log.error "Failed to create new tag."
            throw e
        }

    }

    /*
     * https://docs.gitlab.com/ee/api/tags.html#list-project-repository-tags
     */

    ArrayList getTags() {
        log.info "Get tags list."

        ArrayList tags = []

        try {

            String url = apiUrl + 'projects/' + projectId + '/repository/tags'

            withGitlabApiTokenCredentials {
                String responseBody = script.httpRequest(
                    url: url,
                    httpMode: 'GET',
                    contentType: 'APPLICATION_JSON',
                    customHeaders: [
                        [
                            name : 'PRIVATE-TOKEN',
                            value: script.env['gitlabApiToken']
                        ]
                    ],
                )['content'] as String

                ArrayList tagsInfo = jsonParse(responseBody) as ArrayList
                log.trace "tagsInfo Class: ${tagsInfo.getClass().name}"

                tagsInfo.each { info ->
                    tags.add(info['name'])
                }

                log.debug "Tags: $tags"

            }

        } catch (e) {
            log.error "Failed to get tags list."
            throw e
        }

        return tags
    }

    void deleteTag(String tag) {

        log.info "Delete $tag tag."

        try {
            String url = apiUrl + 'projects/' + projectId + '/repository/tags/' + tag

            withGitlabApiTokenCredentials {
                String responseBody = script.httpRequest(
                    url: url,
                    httpMode: 'DELETE',
                    contentType: 'APPLICATION_JSON',
                    customHeaders: [
                        [
                            name : 'PRIVATE-TOKEN',
                            value: script.env['gitlabApiToken']
                        ]
                    ],
                )
            }
        } catch (e) {
            log.error "Falied to delete $tag tag."
            throw e
        }
    }

    ArrayList getBranches() {

        ArrayList branches = []

        try {

            String url = apiUrl + 'projects/' + projectId + '/repository/branches'

            withGitlabApiTokenCredentials {
                String responseBody = script.httpRequest(
                    url: url,
                    httpMode: 'GET',
                    contentType: 'APPLICATION_JSON',
                    customHeaders: [
                        [
                            name : 'PRIVATE-TOKEN',
                            value: script.env['gitlabApiToken']
                        ]
                    ],
                )['content'] as String

                ArrayList branchesInfo = jsonParse(responseBody) as ArrayList

                branchesInfo.each { info ->
                    branches.add(info['name'])
                }

                log.debug "Branches: $branches"

            }
        } catch (e) {
            log.error "Failed to get branches."
            throw e
        }

        return branches
    }

    /*
     * ref: https://docs.gitlab.com/ee/api/projects.html#hooks
     */

    ArrayList getProjectHook() {

        ArrayList projectHooks = []

        try {
            String url = apiUrl + 'projects/' + projectId + '/hooks'

            withGitlabApiTokenCredentials {
                String responseBody = script.httpRequest(
                    url: url,
                    httpMode: 'GET',
                    contentType: 'APPLICATION_JSON',
                    customHeaders: [
                        [
                            name : 'PRIVATE-TOKEN',
                            value: script.env['gitlabApiToken']
                        ]
                    ],
                )['content'] as String

                ArrayList projectHooksInfo = jsonParse(responseBody) as ArrayList
                projectHooksInfo.each {
                    projectHooks.add(it['url'])
                }

            }
        } catch (e) {
            log.error "Failed to get project hooks."
            throw e
        }

        log.debug "ProjectHooks: $projectHooks"

        return projectHooks
    }

    void updateProjectHook(Integer hookId) {

        try {
            String url = apiUrl + 'projects/' + projectId + '/hooks/' + hookId +
                '?url=' + GITLAB_WEBHOOK_URL + '&token=' + GITLAB_WEBHOOK_TOKEN +
                '&enable_ssl_verification=false&push_events=true'

            withGitlabApiTokenCredentials {
                script.httpRequest(
                    url: url,
                    httpMode: 'PUT',
                    contentType: 'APPLICATION_JSON',
                    customHeaders: [
                        [
                            name : 'PRIVATE-TOKEN',
                            value: script.env['gitlabApiToken']
                        ]
                    ],
                )
            }
        } catch (e) {
            log.error "Failed to update project hooks."
            throw e
        }
    }

    void addProjectHook() {

        try {
            String url = apiUrl + 'projects/' + projectId + '/hooks' +
                '?url=' + GITLAB_WEBHOOK_URL + '&token=' + GITLAB_WEBHOOK_TOKEN +
                '&enable_ssl_verification=false&push_events=true'

            withGitlabApiTokenCredentials {
                script.httpRequest(
                    url: url,
                    httpMode: 'POST',
                    contentType: 'APPLICATION_JSON',
                    customHeaders: [
                        [
                            name : 'PRIVATE-TOKEN',
                            value: script.env['gitlabApiToken']
                        ]
                    ],
                )
            }
        } catch (e) {
            log.error "Failed to add project hooks."
            throw e
        }
    }

    void tagNotTagsPanic(String tag, Closure body = {}) {
        log.info "Confirm the tag in branch tags exists."

        ArrayList tags = getTags()
        if (!(tag in tags)) {
            log.fatal "The $tag tag not in tags list, Please confirm to release on PRE environment."
        }

        body()
    }

    private void withGitlabApiTokenCredentials(body = {}) {
        script.withCredentials([
            script.string(
                credentialsId: apiToken,
                variable: 'gitlabApiToken'
            )
        ]) {
            body()
        }
    }

    /*
     * 类型不固定，有可能是 ArrayList、Map，为了方便使用动态类型
     */

    @NonCPS
    private static def jsonParse(String json) {
        return new JsonSlurper().parseText(json)
    }

}
