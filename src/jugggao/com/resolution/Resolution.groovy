/**
 * Create by Peng.Gao at 2021/4/12 15:42
 *
 * A Part of the Project jenkins-shared-library
 */

package jugggao.com.resolution

import jugggao.com.utils.logging.Logger

import static jugggao.com.utils.config.ConfigConstants.RESOLUTION_SERVERS
import static jugggao.com.utils.CheckUtils.emptyVarPanic

class Resolution implements Serializable {

    private Logger log = new Logger(this)

    void call(Script script, ArrayList dnsServer, String subDomain, String env) {

        Domain domain = Domain.getDomainBySubDomain(subDomain)

        log.debug "subDomain: $subDomain, Domain $domain."

        emptyVarPanic(script, 'domain', domain)

        Address address = Address[env.toUpperCase()] as Address

        dnsServer.each {
            if (!(it in RESOLUTION_SERVERS)) {
                log.fatal "${(it as String).capitalize()}  dns server not found."
            }
        }

        if ('aliCloud' in dnsServer) {

            AliCloudResolve aliyunResolution = new AliCloudResolve(script, domain, subDomain, env)

            ArrayList<String> aliyunResolutions = aliyunResolution.getDomainResolveRecord() as ArrayList<String>

            log.debug "Resolutions: ${aliyunResolutions}, Resolutions Size: ${aliyunResolutions.size()}"

            switch (aliyunResolutions.size()) {
                case 0:
                    aliyunResolution.addDomainResolveRecord(address.publicAddress as String)
                    break
                case 1:
                    if (aliyunResolutions.join('') != address.publicAddress) {
                        aliyunResolution.updateDomainRecord(address.publicAddress as String)
                    }
                    break
                default:
                    aliyunResolution.deleteDomainRecord()
                    aliyunResolution.addDomainResolveRecord(address.publicAddress as String)
            }
        }

        if ('winServer' in dnsServer) {

            MicrosoftResolve microsoftResolution = new MicrosoftResolve(script, domain, subDomain, env)

            ArrayList<String> microsoftResolutions = microsoftResolution.getDomainResolveRecord() as ArrayList<String>

            log.debug "Resolutions: ${microsoftResolutions}, Resolutions Size: ${microsoftResolutions.size()}"

            switch (microsoftResolutions.size()) {
                case 0:
                    microsoftResolution.addDomainResolveRecord(address.innerAddress as String)
                    break
                case 1:
                    if (microsoftResolutions.join('') != address.innerAddress) {
                        microsoftResolution.updateDomainRecord(address.innerAddress as String)
                    }
                    break
                default:
                    microsoftResolution.deleteDomainRecord()
                    microsoftResolution.addDomainResolveRecord(address.innerAddress as String)
            }
        }

    }

}
