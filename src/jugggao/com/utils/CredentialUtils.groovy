package jugggao.com.utils

class CredentialUtils implements Serializable {

    static void withUsernamePasswordCredentials(
        Script script, String credentials, String id, Closure body = {}
    ) {

        String username = id + '-username'
        String password = id + '-password'

        script.withCredentials([
            script.usernamePassword(
                credentialsId: credentials,
                usernameVariable: username.inspect(),
                passwordVariable: password.inspect(),
            )
        ]) {
            body()
        }
    }

}
