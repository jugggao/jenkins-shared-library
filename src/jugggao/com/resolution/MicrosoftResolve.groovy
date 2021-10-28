package jugggao.com.resolution

import com.cloudbees.groovy.cps.NonCPS
import jugggao.com.utils.logging.Logger
import org.jenkinsci.plugins.workflow.cps.CpsScript

import static jugggao.com.utils.config.ConfigConstants.*
import static jugggao.com.utils.CheckUtils.*

class MicrosoftResolve implements Serializable, Resolve {

    private static Script script

    private Logger log = new Logger(this)

    private String subDomain

    private Domain domain

    MicrosoftResolve(Script script, Domain domain, String subDomain, String env) {
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

        try {
            withWindowsServerAuth {
                String describeSubDomainRecordsCmd = 'sshpass -p ' + script.env['windowsServerPassword'] +
                    ' ssh ' + script.env['windowsServerUser'] + '@' + MICROSOFT_WINDOWS_SERVER_ADDRESS +
                    " 'dnscmd /enumrecords " + domain + ' ' + subDomain +
                    '. /type A | findstr "[0-9][0-9]*\\.[0-9][0-9]*\\.[0-9][0-9]*\\.[0-9][0-9]*"\' | awk \'{print $NF}\''
                String sDomainDnsRecord = (script.sh(
                    script: describeSubDomainRecordsCmd,
                    returnStdout: true
                ) as String).trim()

                if (sDomainDnsRecord) {
                    domainDnsRecord = sDomainDnsRecord.split('\r\n') as ArrayList<String>
                }
            }

        } catch (e) {
            log.error "Failed to get domain resolve record."
            throw e
        }

        return domainDnsRecord
    }

    @Override
    void addDomainResolveRecord(String address) {

        log.info "Add $subDomain resolution to ${address}."

        try {

            String resolveRecord = Domain.getResolveRecord(subDomain)

            withWindowsServerAuth {
                String addDomainResolveRecordCmd = 'sshpass -p ' + script.env['windowsServerPassword'] +
                    ' ssh ' + script.env['windowsServerUser'] + '@' + MICROSOFT_WINDOWS_SERVER_ADDRESS +
                    ' dnscmd /recordadd ' + domain + ' ' + resolveRecord + ' A ' + address

                script.sh(
                    script: addDomainResolveRecordCmd,
                    returnStdout: true
                )
            }

        } catch (e) {
            log.error "Failed to add domain name resolution."
            throw e
        }

    }

    @Override
    void updateDomainRecord(String address) {
        deleteDomainRecord()
        addDomainResolveRecord(address)
    }

    @Override
    void deleteDomainRecord() {
        log.info "Clean up $subDomain resolution."

        try {

            String resolveRecord = Domain.getResolveRecord(subDomain)
            ArrayList<String> domainResolveRecord = getDomainResolveRecord()

            withWindowsServerAuth {

                domainResolveRecord.each {
                    String deleteDomainRecordCmd = 'sshpass -p ' + script.env['windowsServerPassword'] +
                        ' ssh ' + script.env['windowsServerUser'] + '@' + MICROSOFT_WINDOWS_SERVER_ADDRESS +
                        ' dnscmd /recorddelete ' + domain + ' ' + resolveRecord + ' A ' + it + ' /f'

                    script.sh(
                        script: deleteDomainRecordCmd,
                        returnStdout: true
                    )
                }
            }
        } catch (e) {
            log.error "Failed to clean domain name resolution."
            throw e
        }
    }

    private static void withWindowsServerAuth(Closure body = {}) {
        script.withCredentials([
            script.usernamePassword(
                credentialsId: MICROSOFT_WINDOWS_SERVER_CREDENTIALS,
                usernameVariable: 'windowsServerUser',
                passwordVariable: 'windowsServerPassword'
            )
        ]) {
            body()
        }
    }
}
