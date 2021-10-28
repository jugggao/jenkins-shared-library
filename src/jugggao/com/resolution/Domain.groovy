/**
 * Create by Peng.Gao at 2021/4/8 9:40
 *
 * A Part of the Project jenkins-shared-library
 *
 */

package jugggao.com.resolution

import com.cloudbees.groovy.cps.NonCPS

import static jugggao.com.utils.config.ConfigConstants.*
import static jugggao.com.utils.config.ConfigConstants.ALIYUN_EBOPO_CREDENTIALS
import static jugggao.com.utils.config.ConfigConstants.ALIYUN_HUANYULIREN_CREDENTIALS

enum Domain {

    EXAMPLE('example.com', ALIYUN_HUANYULIREN_CREDENTIALS),
    public String domain
    public String credentials

    Domain(String domainName, String credentials) {
        this.domain = domainName
        this.credentials = credentials
    }

    @Override
    @NonCPS
    public String toString() {
        return domain.toString()
    }

    static String getResolveRecord(String subDomain) {
        String ResolveRecord = subDomain.tokenize('.')[0]

        return ResolveRecord
    }

    @NonCPS
    static Domain getDomainBySubDomain(String subDomain) {

        String domainName = subDomain.tokenize('.')[1..-1].join('.')

        Domain domain = null

        values().each {
            if (domainName == it.toString()) {
                domain = it
            }
        }

        // for (i in values()) {
        //    if (domain == i.toString()) {
        //        return i
        //    }
        // }

        return domain
    }

    static boolean contains(String subDomain) {
        String[] domains = values()

        String domain = subDomain.tokenize('.')[1, -1].join('.')

        if (domain in domains) {
            return true
        }
        return false
    }
}