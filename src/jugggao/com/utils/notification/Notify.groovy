/**
 * Created by Peng.Gao at 2021/3/21 16:24
 * A Part of the Project jenkins-shared-library
 */

package jugggao.com.utils.notification

interface Notify {

    def genMessage(Map info)

    void sendMessage(String message)

}