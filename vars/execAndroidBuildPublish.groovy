import jugggao.com.utils.config.ConfigService
import jugggao.com.utils.logging.*
import jugggao.com.utils.notify.Notify
import jugggao.com.utils.Publish
import jugggao.com.app.Sign

import static jugggao.com.utils.config.ConfigConstants.JENKINS_GENERIC_WEBHOOK_TRIGGER_TOKEN

def call(Map config) {

    Logger.init(this, 'trace')
    Logger log = new Logger(this)


    branch = config?.branch ?: 'null'
    projectId = config?.projectId ?: 244
    is360Jiagu = config?.is360Jiagu ?: false
    isNotify = config?.isNotify ?: false
    branch = (env['JOB_BASE_NAME'] as String).split('_')[-1].toLowerCase()
    if(isNotify) notify = new Notify()
    isPublish = config?.isPublish ?: false
    if(isPublish) publish = new Publish()
    apkDir = config?.apkDir ?: 'app/build/outputs/apk/release/'
    apkName = config?.apkName ?: 'release'
    certsPath = config?.certsPath ?: ''

    sign = new Sign()

    pipeline {
        agent {
            label 'build'
        }
        options {
            disableConcurrentBuilds()
            ansiColor('xterm')
            timeout(time: 60, unit: 'MINUTES')
            timestamps()
            skipDefaultCheckout()
        }

        environment {
            ANDROID_SDK_ROOT = "/data/jenkins/android-sdk"
            ANDROID_HOME = "/data/jenkins/android-sdk"
            JIAGU_HOME = "/data/jenkins/tools/jiagu/"
            WECHAT_URL = 'https://qyapi.weixin.qq.com/cgi-bin/webhook/send?key=cdfacbd2-0185-4846-9357-e7361bf73721'
            OUTPUT_PATH = "archive"
            JIAGU_PATH = '/data/jenkins/tools/jiagu/jiagu.jar'
            OSSUTIL_PATH = '/data/jenkins/tools/aliyun-oss/ossutil'
            API_KEY = '965196bc64b6004ed43090ec1cdeed32'
        }

        triggers {
            GenericTrigger(
                    causeString: 'Generic Cause',
                    genericVariables: [
                            [
                                    defaultValue: '',
                                    key         : 'ref',
                                    regexpFilter: '',
                                    value       : '$.ref'
                            ],
                            [
                                    defaultValue: '',
                                    key         : 'project_id',
                                    regexpFilter: '',
                                    value       : '$.project_id'
                            ],
                    ],
                    regexpFilterExpression: "^($projectId)_(refs/heads/$branch)\$",
                    regexpFilterText: '${project_id}_${ref}',
                    token: JENKINS_GENERIC_WEBHOOK_TRIGGER_TOKEN,
            )
        }

        stages {
            stage('?????????') {
                steps {
                    checkout scm
                    script {
                        log.debug("Config: ${config}")
                        log.debug("ANDROID_SDK_ROOT: ${ANDROID_SDK_ROOT}")
                        log.debug("is360Jiagu: ${is360Jiagu}")
                        log.debug("isNotify: ${isNotify}")
                        ossDir = config?.ossDir ?: "app/android_test/"
                        if (!config.cert.path || !config.cert.alias || !config.cert.password) {
                            log.error('certsPath ????????????')
                            error()
                        }
                        log.debug("apkName: ${apkName}")
                        log.debug("apkDir: ${apkDir}")
                        log.debug("ossDir: ${ossDir}")
                        log.debug("certInfo: ${config.cert}")
                        sh "[[ -d $OUTPUT_PATH ]] || mkdir -p $OUTPUT_PATH"

                    }
                }
            }


            stage('????????????') {
                steps {
                    script {
                        try {
                            sh (script: "chmod +x ./gradlew && ./gradlew clean && rm -rf ${apkDir.split('/')[0]}/build/", returnStatus: true)
                            log.info("??????????????????")

                            sh (script: './gradlew assembleRelease')
                            log.info("????????????")

                            apkPath = findApkPath(apkDir)
                            sh "mv ${apkPath} ${apkDir}/release-unsigned.apk"
                        } catch(e) {
                            log.error("????????????")
                            error(e)
                        }
                    }
                }
            }

            stage('??????') {
                when {
                    expression {
                        return !is360Jiagu
                    }
                }
                steps {
                    script {
                        try {
                            sign.signApk("${apkDir}/release-unsigned.apk", "${apkDir}/release.apk", config.cert)
                            log.info("????????????")
                            signApkPath = findSignApkPath(apkDir)
                            version = sh(
                                script: "cat ${apkDir.split('/')[0]}/build.gradle | grep versionName | awk '/^\\s.+versionName/{print \$2}'",
                                returnStdout: true
                            ).trim()
                            archiveFile = "${OUTPUT_PATH}/${version.replace('"', '')}-${apkName}.apk"
                            log.debug("mv: ${signApkPath} -> ${archiveFile}")
                            sh "mv ${signApkPath} ${OUTPUT_PATH}/${version}-${apkName}.apk"
                            if (!sign.signCheck(archiveFile)) error("??????????????????")
                        } catch(e) {
                            log.error("????????????")
                            error(e)
                        }
                    }
                }
            }

            stage('??????&??????') {
                when {
                    expression {
                        return is360Jiagu
                    }
                }
                environment {
                    JIAGU_CREDS = credentials('360-jiagu-creds')
                }
                steps {
                    script {
                        try {
                            sh "java -jar ${JIAGU_PATH} -login $JIAGU_CREDS_USR $JIAGU_CREDS_PSW"
                            sh """
                                 java -jar ${JIAGU_PATH} \
                                 -importsign ${config.cert.path} ${config.cert.password} \
                                 ${config.cert.alias} ${config.cert.password}
                               """
                            sh "java -jar ${JIAGU_PATH} -jiagu ${apkDir}/release-unsigned.apk ${apkDir} -autosign"
                            log.info("????????????")

                            signApkPath = findSignApkPath(apkDir)
                            version = sh(
                                script: "cat ${apkDir.split('/')[0]}/build.gradle | grep versionName | awk '/^\\s.+versionName/{print \$2}'",
                                returnStdout: true
                            ).trim()
                            archiveFile = "${OUTPUT_PATH}/${version.replace('"', '')}-${apkName}.apk"
                            log.debug("mv: ${signApkPath} -> ${archiveFile}")
                            sh "mv ${signApkPath} ${OUTPUT_PATH}/${version}-${apkName}.apk"

                            if (!sign.signCheck(archiveFile)) error("??????????????????")

                        } catch(e) {
                            log.error('????????????')
                            error(e)
                        }
                    }
                }
            }

            stage('??????') {
                steps {
                    script {
                        try {
                            log.debug("push: $OUTPUT_PATH/${version}-${apkName}.apk -> oss://ambow-globlate/$ossDir/${version.replace('"', '')}-${apkName}.apk")
                            sh "${OSSUTIL_PATH} --config-file /data/jenkins/aliyun-tools/.ossutilconfig cp -r -u ${archiveFile} oss://ambow-globlate/$ossDir/${env.BRANCH_NAME}/${version.replace('\"', '')}-${apkName}.apk"
                            sh "${OSSUTIL_PATH} --config-file /data/jenkins/aliyun-tools/.ossutilconfig cp -r -u ${archiveFile} oss://ambow-globlate/$ossDir/${env.BRANCH_NAME}/latest-${apkName}.apk"
                            log.info("????????????")
                        } catch(e) {
                            log.error("????????????")
                            error(e)
                        }
                    }
                }
            }

            stage('??????') {
                when {
                    expression {
                        return isPublish
                    }
                }
                steps {
                    script {
                        try {
                            def publishJson = publish.publishPgyer(archiveFile, API_KEY)
                            log.debug("publishJson: ${publishJson}")
                            publishInfo = publish.getPublishInfo(publishJson)
                            log.debug("publishInfo: ${publishInfo}")
                            log.info("????????????")
                        } catch(e) {
                            log.error("????????????")
                            error(e)
                        }
                    }
                }
            }

            stage('??????') {
                steps {
                    script {
                        try {
                            archiveArtifacts artifacts: archiveFile, fingerprint: true
                            log.info("????????????")
                        } catch(e) {
                            log.error("????????????")
                        }
                    }
                }
            }
        }

        post {
            always {
                script {
                    log.info("????????????")
                    sh "rm -rf ./archive"
                }
            }
            success {
                script {
                    def message = []
                    message << "### O?????????????????????"
                    message << "> ?????????${version.replace('"', '')}-${apkName}.apk"
                    message << "> OSS ???????????????oss://ambow-globlate/$ossDir"
                    if (isPublish) {
                        message << "> ????????????[????????????](${publishInfo.shortcutUrl})"
                        message << "> ????????????[????????????](${publishInfo.qrCode})"
                        message << "> ????????????[????????????](${env.JOB_URL}/lastSuccessfulBuild/artifact/archive/${publishInfo.packageName})"
                    }
                    if (isNotify) notify.wechatNotify(message.join('\n\n'), WECHAT_URL)
                }
            }
            failure {
                script {
                    def message = []
                    message << "### O?????????????????????"
                    message << "???????????????[????????????](${env.BUILD_URL}/console)"
                    if (isNotify) notify.wechatNotify(message.join('\n\n'), WECHAT_URL)
                }
            }
        }
    }
}

def findApkPath(String apkDir) {
    def apkPath = sh (returnStdout: true, script: "find ${apkDir} -type f -name '*.apk'").trim()
    return apkPath
}

def findSignApkPath(String apkDir) {
    def apkPath = sh (returnStdout: true, script: "find ${apkDir} -type f -name '*.apk' | grep -v 'release-unsigned.apk'").trim()
    return apkPath
}