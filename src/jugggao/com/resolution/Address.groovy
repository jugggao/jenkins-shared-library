/**
 * Create by Peng.Gao at 2021/4/8 16:23
 *
 * A Part of the Project jenkins-shared-library
 *
 */

package jugggao.com.resolution

import static jugggao.com.utils.config.ConfigConstants.*

enum Address {

    DEV(DEV_INNER_ENDPOINT_ADDRESS, DEV_PUBLIC_ENDPOINT_ADDRESS),
    UAT(UAT_INNER_ENDPOINT_ADDRESS, UAT_PUBLIC_ENDPOINT_ADDRESS),
    PRE(PRE_INNER_ENDPOINT_ADDRESS, PRE_PUBLIC_ENDPOINT_ADDRESS),
    PRD(PRD_INNER_ENDPOINT_ADDRESS, PRD_PUBLIC_ENDPOINT_ADDRESS),
    TEST(TEST_INNER_ENDPOINT_ADDRESS, TEST_PUBLIC_ENDPOINT_ADDRESS)

    public innerAddress
    public publicAddress

    Address(String innerAddress, String publicAddress) {
        this.innerAddress = innerAddress
        this.publicAddress = publicAddress
    }
}