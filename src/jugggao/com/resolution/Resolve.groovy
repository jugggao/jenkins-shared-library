/**
 * Create by Peng.Gao at 2021/4/7 17:05
 *
 * A Part of the Project jenkins-shared-library
 *
 */

package jugggao.com.resolution

import org.jenkinsci.plugins.workflow.cps.CpsScript

interface Resolve {

    ArrayList<String> getDomainResolveRecord()

    void addDomainResolveRecord(String address)

    void updateDomainRecord(String address)

    void deleteDomainRecord( )

}