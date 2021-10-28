/**
 * Create by Peng.Gao at 2021/4/7 17:04
 *
 * A Part of the Project jenkins-shared-library
 */

package jugggao.com.resolution

import jugggao.com.utils.logging.Logger
import org.jenkinsci.plugins.workflow.cps.CpsScript


class AliCloudResolve implements Serializable, Resolve {

    private static Script script

    private Logger log = new Logger(this)

    private String subDomain

    private Domain domain

    AliCloudResolve(Script script, Domain domain, String subDomain, String env) {
        if (!(script instanceof CpsScript)) {
            log.fatal "The script $script is not CpsScript"
        }

        this.script = script
        this.subDomain = env + '-' + subDomain
        this.domain = domain
    }

    @Override
    ArrayList<String> getDomainResolveRecord() {

        log.info "Get $subDomain resolution list."

        ArrayList<String> domainDnsRecord = []

        log.debug "credentialsId: ${domain.credentials}"

        try {

            script.withCredentials([
                script.usernamePassword(
                    credentialsId: domain.credentials,
                    passwordVariable: 'aliyunAccessKeySecret',
                    usernameVariable: 'aliyunAccessKeyId'
                )
            ]) {

                String describeSubDomainRecordsCmd = 'aliyun --access-key-id ' + script.env['aliyunAccessKeyId'] +
                    ' --access-key-secret ' + script.env['aliyunAccessKeySecret'] +
                    ' --region cn-beijing alidns DescribeSubDomainRecords --SubDomain ' + subDomain +
                    ' --DomainName ' + domain.toString() + ' | jq ".DomainRecords.Record[].Value"'

                String sDomainDnsRecord = (script.sh(
                    script: describeSubDomainRecordsCmd,
                    returnStdout: true
                ) as String).replaceAll('"', '')

                log.debug "sDomainDnsRecord: $sDomainDnsRecord"

                if (sDomainDnsRecord) {
                    domainDnsRecord = sDomainDnsRecord.split('\n') as ArrayList<String>
                }

                log.debug "domainDnsRecord: $domainDnsRecord"
            }

        } catch (e) {
            log.error "Failed to describe domain name resolution."
            throw e
        }

        return domainDnsRecord
    }

    @Override
    void addDomainResolveRecord(String address) {

        log.info "Add $subDomain resolution to $address."

        try {

            String resolveRecord = Domain.getResolveRecord(subDomain)

            log.debug "credentialsId: ${domain.credentials}"

            script.withCredentials([
                script.usernamePassword(
                    credentialsId: domain.credentials,
                    passwordVariable: 'aliyunAccessKeySecret',
                    usernameVariable: 'aliyunAccessKeyId'
                )
            ]) {

                String addDomainResolveRecordCmd = 'aliyun --access-key-id ' + script.env['aliyunAccessKeyId'] +
                    ' --access-key-secret ' + script.env['aliyunAccessKeySecret'] +
                    ' --region cn-beijing alidns AddDomainRecord --RR ' + resolveRecord +
                    ' --DomainName ' + domain.toString() +
                    ' --Type A --Value ' + address

                script.sh(
                    script: addDomainResolveRecordCmd,
                    returnStdout: true
                )
            }

        } catch (e) {
            log.error "Failed to add $subDomain resolution to $address."
            throw e
        }
    }

    @Override
    void updateDomainRecord(String address) {
        log.info "Update $subDomain resolution to $address."

        try {
            String resolveRecord = Domain.getResolveRecord(subDomain)
            String domainResolveRecordId = getDomainResolveRecordId()[0]

            aliyunCliAuth(domain.credentials) {
                String updateDomainRecordCmd = 'aliyun --access-key-id ' + script.env['aliyunAccessKeyId'] +
                    ' --access-key-secret ' + script.env['aliyunAccessKeySecret'] +
                    ' --region cn-beijing alidns UpdateDomainRecord --RR ' + resolveRecord +
                    ' --RecordId ' + domainResolveRecordId +
                    ' --Type A --Value ' + address

                script.sh(
                    script: updateDomainRecordCmd,
                    returnStdout: true
                )
            }
        } catch (e) {
            log.error "Failed to update $subDomain resolution to $address."
            throw e
        }
    }

    @Override
    void deleteDomainRecord() {
        log.info "Clean up $subDomain resolution."

        try {
            ArrayList<String> domainResolveRecordId = getDomainResolveRecordId()

            aliyunCliAuth(domain.credentials) {

                domainResolveRecordId.each {
                    String deleteDomainRecordCmd = 'aliyun --access-key-id ' + script.env['aliyunAccessKeyId'] +
                        ' --access-key-secret ' + script.env['aliyunAccessKeySecret'] +
                        ' --region cn-beijing alidns DeleteDomainRecord --RecordId ' + it

                    script.sh(
                        script: deleteDomainRecordCmd,
                        returnStdout: true
                    )
                }
            }
        } catch (e) {
            log.error "Failed to clean $subDomain resolution."
            throw e
        }
    }

    private static void aliyunCliAuth(String credentials, Closure body = {}) {
        script.withCredentials([
            script.usernamePassword(
                credentialsId: credentials,
                passwordVariable: 'aliyunAccessKeySecret',
                usernameVariable: 'aliyunAccessKeyId'
            )
        ]) {
            body()
        }
    }

    private ArrayList<String> getDomainResolveRecordId() {
        log.info "Get domain resolution record id."

        ArrayList<String> domainResolveRecordId = []

        try {
            aliyunCliAuth(domain.credentials) {
                String describeSubDomainRecordsCmd = 'aliyun --access-key-id ' + script.env['aliyunAccessKeyId'] +
                    ' --access-key-secret ' + script.env['aliyunAccessKeySecret'] +
                    ' --region cn-beijing alidns DescribeSubDomainRecords --SubDomain ' + subDomain +
                    ' --DomainName ' + domain.toString() + ' | jq ".DomainRecords.Record[].RecordId"'

                domainResolveRecordId = (script.sh(
                    script: describeSubDomainRecordsCmd,
                    returnStdout: true
                ) as String).replaceAll('"', '').split('\n') as ArrayList<String>
            }
        } catch (e) {
            log.error "Failed to get domain resolution record id."
            throw e
        }

        return domainResolveRecordId
    }
}
