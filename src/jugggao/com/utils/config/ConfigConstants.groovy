/**
 * Created by Peng.Gao at 2021/3/3 22:53
 * A Part of the Project jenkins-shared-library
 */

package jugggao.com.utils.config

import jugggao.com.ci.Language


class ConfigConstants implements Serializable {

    public static final String JENKINS_GENERIC_WEBHOOK_TRIGGER_TOKEN_CREDENTIALS = 'jenkins-generic-webhook-trigger-token'
    public static final String JENKINS_GENERIC_WEBHOOK_TRIGGER_TOKEN = '0eec4e6078b8aa9dca6c916f9d90423f'
    public static final String ANSI_COLOR_XTERM = 'xterm'
    public static final String LOGLEVEL = 'trace'

    public static final String GIT_CREDENTIALS = 'gitlab-jenkins'
    public static final String GIT_STAGE_NODE = 'build'
    public static final String GITLAB_API_URL = 'http://git.example.com/api/v4/'
    public static final String GITLAB_API_TOKEN_CREDENTIALS = 'gitlab-api-token-root'
    public static final String GITLAB_WEBHOOK_URL = 'https://ci.example.com/generic-webhook-trigger/invoke'
    public static final String GITLAB_WEBHOOK_TOKEN = '0eec4e6078b8aa9dca6c916f9d90423f'

    public static final String BUILD_STAGE_NODE = 'build'
    public static final String JAVA_CMD = '/usr/bin/java'
    public static final String JAVA_OPTIONS = ''
    public static final String JAVA_VERSION = '8'
    public static final String MAVEN_CMD = '/usr/local/maven3/bin/mvn'
    public static final String MAVEN_OPTIONS = '-ff -ntp -U clean -Dmaven.test.skip=true package dependency:tree'
    public static final String MAVEN_CONTENT = 'target'
    public static final String MAVEN_BUILD_FILE = 'pom.xml'
    public static final String GRADLE_CMD = ''
    public static final String GRADLE_OPTIONS = ''
    public static final String GRADLE_CONTENT = ''
    public static final String GRADLE_BUILD_FILE = ''
    public static final String NODE12_CMD = '/usr/local/node/bin/npm'
    public static final String NODE12_OPTIONS = 'install --registry http://repo.example.com/repository/npm-group/ --unsafe-perm=true --allow-root'
    public static final String NODE12_CONTENT = 'dist'
    public static final String NODE12_BUILD_FILE = 'package.json'
    public static final String NODE_BUILD_COMMAND = 'run build'
    public static final String NODE10_CMD = '/usr/local/node10/bin/npm'
    public static final String NODE10_OPTIONS = 'install --registry http://repo.example.com/repository/npm-group/ --unsafe-perm=true --allow-root'
    public static final String NODE10_CONTENT = 'dist'
    public static final String NODE10_BUILD_FILE = 'package.json'
    public static final String NODE14_CMD = '/usr/local/node14/bin/npm'
    public static final String NODE14_OPTIONS = 'install --registry http://repo.example.com/repository/npm-group/ --unsafe-perm=true --allow-root'
    public static final String NODE14_CONTENT = 'dist'
    public static final String NODE14_BUILD_FILE = 'package.json'
    public static final String YARN_CMD = ''
    public static final String YARN_OPTIONS = ''
    public static final String YARN_CONTENT = ''
    public static final String YARN_BUILD_FILE = 'package.json'
    public static final String CNPM_CMD = ''
    public static final String CNPM_OPTIONS = ''
    public static final String CNPM_CONTENT = ''
    public static final String CNPM_BUILD_FILE = 'package.json'
    public static final String GOLANG_CMD = ''
    public static final String GOLANG_OPTIONS = ''
    public static final String GOLANG_CONTENT = ''
    public static final String GOLANG_BUILD_FILE = ''
    public static final String[] SCAN_AFTER_BUILD = ['java']
    public static final String COCOAPODS_CMD = 'arch -arch x86_64 /usr/local/bin/pod'
    public static final String COCOAPODS_OPTIONS = 'install'
    public static final String COCOAPODS_CONTENT = ''
    public static final String COCOAPODS_BUILD_FILE = 'Podfile'
    public static final String HTML_CMD = ''
    public static final String HTML_OPTIONS = ''
    public static final String HTML_CONTENT = './'
    public static final String HTML_BUILD_FILE = ''
    public static final String PHP_CMD = ''
    public static final String PHP_OPTIONS = ''
    public static final String PHP_CONTENT = './'
    public static final String PHP_BUILD_FILE = ''

    public static final String SONARQUBE_STAGE_NODE = 'build'
    public static final String SONARQUBE_URL = 'https://sq.example.com'
    public static final String SONARQUBE_CREDENTIALS = 'sonarqube-admin-user'
    public static final String SONARQUBE_TOKEN = 'sonarqube-admin-token'
    public static final String SONARQUBE_PROPERTIES_FILE = 'sonar.properties'
    public static final String SONARQUBE_CMD = '/usr/local/bin/sonar-scanner'
    public static final Integer QUALITY_GATE_STATUS_TIMEOUT = 600

    public static final String DOCKER_STAGE_NODE = 'build'
    public static final String DOCKER_REGISTER = 'cr.example.com'
    public static final String DOCKER_REGISTER_CREDENTIALS = 'container-registry-admin'
    public static final String DOCKER_TEMPLATE_DIR = 'jugggao/com/templates/container/docker'
    public static final String DOCKER_BUILD_FILE = 'Dockerfile'
    public static final String DOCKER_IGNORE_FILE = '.dockerignore'
    public static final String DOCKER_APP_ROOT = '/data/app'
    public static final Integer DOCKER_IMAGE_BUILD_TIMEOUT = 1800
    public static final Integer DOCKER_IMAGE_PUSH_TIMEOUT = 600
    public static final Integer DOCKER_LOGIN_TIMEOUT = 15
    public static final String HARBOR_API_URL = 'https://cr.example.com/api/v2.0'

    public static final ArrayList WEB_LANGUAGES = [Language.NODEJS, Language.HTML, Language.PHP]
    public static final String KUBERNETES_STAGE_NODE = 'build'
    public static final String[] KUBERNETES_ALLOWED_COMMANDS = ['apply', 'create', 'delete', 'get', 'autoscale']
    public static final String KUBERNETES_KUBECONFIG_CREDENTIALS_PREFIX = 'kubeconfig'
    public static final String KUBERNETES_IMAGES_PULL_SECRET = 'container-images-pull-secret'
    public static final String KUBERNETES_TEMPLATE_DIR = 'jugggao/com/templates/kubernetes'
    public static final String KUBERNETES_DEPLOYMENT_FILE = 'deployment.yaml'
    public static final String KUBERNETES_SERVICE_FILE = 'service.yaml'
    public static final String KUBERNETES_CONFIGMAP_FILE = 'configmap.yaml'
    public static final String KUBERNETES_INGRESS_FILE = 'ingress.yaml'
    public static final String KUBERNETES_PVC_FILE = 'pvc.yaml'
    public static final Integer KUBERNETES_REVISION_HISTORY_LIMIT = 3
    public static final Integer KUBERNETES_PROGRESS_DEADLINE_SECONDS = 600
    public static final String KUBERNETES_STRATEGY_MAX_SURGE = '25%'
    public static final String KUBERNETES_STRATEGY_MAX_UNAVAILABLE = '25%'
    public static final Integer KUBERNETES_TERMINATION_GPS = 60
    public static final Integer KUBERNETES_WAIT_UPDATE_READY_SECONDS = 600
    public static final Integer KUBERNETES_WAIT_INITRECURRENCE_PERIOD = 6000

    public static final ArrayList RESOLUTION_SERVERS = ['aliCloud', 'winServer']
    public static final String ALIYUN_HUANYULIREN_CREDENTIALS = ''
    public static final String ALIYUN_AMBOW_CREDENTIALS = ''
    public static final String ALIYUN_IVALLEYTECH_CREDENTIALS = ''
    public static final String ALIYUN_OOOK_TW_CREDENTIALS = ''
    public static final String ALIYUN_OOOK_CN_CREDENTIALS = ''
    public static final String ALIYUN_EBOPO_CREDENTIALS = ''
    public static final String ALIYUN_GTEDU_CREDENTIALS = ''
    public static final String ALIYUN_OOOK_CREDENTIALS = ''
    public static final String ALIYUN_HYCAREER_CREDENTIALS = ''
    public static final String MICROSOFT_WINDOWS_SERVER_CREDENTIALS = ''
    public static final String MICROSOFT_WINDOWS_SERVER_ADDRESS = '10.10.113.100'
    public static final String DEV_INNER_ENDPOINT_ADDRESS = '10.10.115.11'
    public static final String DEV_PUBLIC_ENDPOINT_ADDRESS = ''
    public static final String UAT_INNER_ENDPOINT_ADDRESS = '10.10.115.12'
    public static final String UAT_PUBLIC_ENDPOINT_ADDRESS = ''
    public static final String PRE_INNER_ENDPOINT_ADDRESS = '10.10.115.13'
    public static final String PRE_PUBLIC_ENDPOINT_ADDRESS = ''
    public static final String TEST_INNER_ENDPOINT_ADDRESS = '10.10.115.11'
    public static final String TEST_PUBLIC_ENDPOINT_ADDRESS = ''
    public static final String PRD_INNER_ENDPOINT_ADDRESS = ''
    public static final String PRD_PUBLIC_ENDPOINT_ADDRESS = ''

    public static final Integer NOTIFICATION_TIMEOUT = 15

    public static final String IOS_BUILD_NODE = 'ios-build'
    public static final String IOS_BUILD_AMBOW_USER_CREDENTIALS = 'ssh-ios-build-ambow'
    public static final String IOS_BUILD_AMBOW_USER_KEYCHAIN_CREDENTIALS = 'ios-build-keychain-ambow'
    public static final String APPLE_DEVELOPER_PROFILE_GLOBALTALENT_TW_CREDENTIALS = 'apple-developer-profile-globaltalent-tw'
    public static final String APPLE_DEVELOPER_PROFILE_GLOBALTALENT_ZH_CREDENTIALS = 'apple-developer-profile-globaltalent-zh'
    public static final String PGYER_API_UPLOAD_URL = 'https://www.pgyer.com/apiv2/app/upload'
    public static final String PGYER_API_KEY_CREDENTIALS = 'pgyer-api-key'

    public static final String TOOL_MYSQL_BUILD_NODE = 'build'
    public static final String TOOL_MYSQL_DUMP_DIR = '/var/backup/mysql/'
    public static final String TOOL_MYSQL_PIPELINE_DATABASE = 'pipeline_jobs'

    public static final String TOOL_EXPORT_PROJECT_DATA_NODE = 'build'
    public static final String TOOL_EXPORT_PROJECT_DATA_DIR = '/data-backup'
}