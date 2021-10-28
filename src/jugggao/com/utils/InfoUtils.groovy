/**
 * Created by Peng.Gao at 2021/2/28 23:00
 * A Part of the Project app-build-publish
 */

package jugggao.com.utils

import jugggao.com.utils.logging.Logger
import org.apache.maven.model.Model

class InfoUtils implements Serializable {

    static String getJobName(Script script) {
        return (script.env['JOB_BASE_NAME'] as String).toLowerCase()
    }

    static String getApplicationName(Script script) {
        return (script.env['JOB_BASE_NAME'] as String).split('_')[1].toLowerCase()
    }

    static String getJobType(Script script) {
        return (script.env['JOB_BASE_NAME'] as String).split('_')[0].toLowerCase()
    }

    static String getToolType(Script script) {
        return (script.env['JOB_BASE_NAME'] as String).split('_')[-1].toLowerCase()
    }

    static String projectName(Script script) {
        return (script.env['JOB_BASE_NAME'] as String).split('_')[-2].toLowerCase()
    }

    static String getEnvironment(Script script) {

        Logger log = new Logger('getEnvironment')

        String environment = ''
        String jobType = getJobType(script)

        switch (jobType) {
            case ~/.*build.*/:
                environment = 'dev'
                break
            case ~/.*sync.*/:
                environment = 'uat'
                break
            case ~/.*release.*/:
                environment = 'pre'
                break
            case ~/.*update.*/:
                environment = 'prd'
                break
            default:
                log.fatal "Failed to get environment."
        }

        return environment
    }

    static String getBuildUserName(Script script) {
        script.wrap([$class: 'BuildUser']) {
            String buildUser = script.env['BUILD_USER']
            return buildUser
        }
    }

    static String getBuildUserNameID(Script script) {
        script.wrap([$class: 'BuildUser']) {
            String buildUserId = script.env['BUILD_USER_ID']
            return buildUserId
        }
    }

    static String getFirstDirectory(Script script) {
        String firstDir = script.pwd()
        return firstDir
    }

    static String getProjectName(Script script) {
        String projectName = ((script.env['JOB_NAME'] as String).split('/')[0])
        projectName = projectName.toLowerCase()
        return projectName
    }

    static Integer getReplicasNumber(String environment) {
        Integer replicas = 2

        String[] dq = ['dev', 'uat', 'pre', 'test', 'prd-vsphere-cloud', 'vsphere-cloud']
        if (environment in dq) {
            replicas = 1
        }

        return replicas
    }

    /*
    官方不推荐使用此方法。
    Avoid using this step and writeMavenPom. It is better to use the sh step to run mvn goals.
    详见：https://www.jenkins.io/doc/pipeline/steps/pipeline-utility-steps/#readmavenpom-read-a-maven-project-file
    我还是继续用了，因为速度快
     */

    static String getMavenInfo(Script script, String item) {

        Model pom = script.readMavenPom(
                file: 'pom.xml'
        ) as Model

        return pom[item]
    }

    /*
    官方推荐的方法。
    速度上不及上面的方法。
     */
    static String _getMavenInfo(Script script, String item) {

        String info = (script.sh(
                script: "mvn help:evaluate -Dexpression=project[$item] -q -DforceStdout",
                returnStdout: true
        ) as String).trim()

        return info
    }
}


