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
            stage('初始化') {
                steps {
                    checkout scm
                    script {
                        log.debug("Config: ${config}")
                        log.debug("ANDROID_SDK_ROOT: ${ANDROID_SDK_ROOT}")
                        log.debug("is360Jiagu: ${is360Jiagu}")
                        log.debug("isNotify: ${isNotify}")
                        ossDir = config?.ossDir ?: "app/android_test/"
                        if (!config.cert.path || !config.cert.alias || !config.cert.password) {
                            log.error('certsPath 配置错误')
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


            stage('编译构建') {
                steps {
                    script {
                        try {
                            sh (script: "chmod +x ./gradlew && ./gradlew clean && rm -rf ${apkDir.split('/')[0]}/build/", returnStatus: true)
                            log.info("环境清理完成")

                            sh (script: './gradlew assembleRelease')
                            log.info("编译完成")

                            apkPath = findApkPath(apkDir)
                            sh "mv ${apkPath} ${apkDir}/release-unsigned.apk"
                        } catch(e) {
                            log.error("构建失败")
                            error(e)
                        }
                    }
                }
            }

            stage('签名') {
                when {
                    expression {
                        return !is360Jiagu
                    }
                }
                steps {
                    script {
                        try {
                            sign.signApk("${apkDir}/release-unsigned.apk", "${apkDir}/release.apk", config.cert)
                            log.info("签名完成")
                            signApkPath = findSignApkPath(apkDir)
                            version = sh(
                                script: "cat ${apkDir.split('/')[0]}/build.gradle | grep versionName | awk '/^\\s.+versionName/{print \$2}'",
                                returnStdout: true
                            ).trim()
                            archiveFile = "${OUTPUT_PATH}/${version.replace('"', '')}-${apkName}.apk"
                            log.debug("mv: ${signApkPath} -> ${archiveFile}")
                            sh "mv ${signApkPath} ${OUTPUT_PATH}/${version}-${apkName}.apk"
                            if (!sign.signCheck(archiveFile)) error("签名校验失败")
                        } catch(e) {
                            log.error("签名失败")
                            error(e)
                        }
                    }
                }
            }

            stage('加固&签名') {
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
                            log.info("加固完成")

                            signApkPath = findSignApkPath(apkDir)
                            version = sh(
                                script: "cat ${apkDir.split('/')[0]}/build.gradle | grep versionName | awk '/^\\s.+versionName/{print \$2}'",
                                returnStdout: true
                            ).trim()
                            archiveFile = "${OUTPUT_PATH}/${version.replace('"', '')}-${apkName}.apk"
                            log.debug("mv: ${signApkPath} -> ${archiveFile}")
                            sh "mv ${signApkPath} ${OUTPUT_PATH}/${version}-${apkName}.apk"

                            if (!sign.signCheck(archiveFile)) error("签名校验失败")

                        } catch(e) {
                            log.error('加固失败')
                            error(e)
                        }
                    }
                }
            }

            stage('上传') {
                steps {
                    script {
                        try {
                            log.debug("push: $OUTPUT_PATH/${version}-${apkName}.apk -> oss://ambow-globlate/$ossDir/${version.replace('"', '')}-${apkName}.apk")
                            sh "${OSSUTIL_PATH} --config-file /data/jenkins/aliyun-tools/.ossutilconfig cp -r -u ${archiveFile} oss://ambow-globlate/$ossDir/${env.BRANCH_NAME}/${version.replace('\"', '')}-${apkName}.apk"
                            sh "${OSSUTIL_PATH} --config-file /data/jenkins/aliyun-tools/.ossutilconfig cp -r -u ${archiveFile} oss://ambow-globlate/$ossDir/${env.BRANCH_NAME}/latest-${apkName}.apk"
                            log.info("上传成功")
                        } catch(e) {
                            log.error("上传失败")
                            error(e)
                        }
                    }
                }
            }

            stage('发布') {
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
                            log.info("发布成功")
                        } catch(e) {
                            log.error("发布失败")
                            error(e)
                        }
                    }
                }
            }

            stage('归档') {
                steps {
                    script {
                        try {
                            archiveArtifacts artifacts: archiveFile, fingerprint: true
                            log.info("归档成功")
                        } catch(e) {
                            log.error("归档失败")
                        }
                    }
                }
            }
        }

        post {
            always {
                script {
                    log.info("清理资源")
                    sh "rm -rf ./archive"
                }
            }
            success {
                script {
                    def message = []
                    message << "### O课安卓构建完成"
                    message << "> 包名：${version.replace('"', '')}-${apkName}.apk"
                    message << "> OSS 存储路径：oss://ambow-globlate/$ossDir"
                    if (isPublish) {
                        message << "> 蒲公英：[点击查看](${publishInfo.shortcutUrl})"
                        message << "> 二维码：[点击查看](${publishInfo.qrCode})"
                        message << "> 归档包：[点击下载](${env.JOB_URL}/lastSuccessfulBuild/artifact/archive/${publishInfo.packageName})"
                    }
                    if (isNotify) notify.wechatNotify(message.join('\n\n'), WECHAT_URL)
                }
            }
            failure {
                script {
                    def message = []
                    message << "### O课安卓构建失败"
                    message << "构建日志：[点击查看](${env.BUILD_URL}/console)"
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