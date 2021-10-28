/**
 * Create by Peng.Gao at 2021/2/24
 *
 * A Part of the Project jenkins-shared-library
 */

package jugggao.com.utils.config

import com.cloudbees.groovy.cps.NonCPS
import jugggao.com.utils.logging.Logger
import org.jenkinsci.plugins.workflow.cps.CpsScript
import org.yaml.snakeyaml.Yaml

import static jugggao.com.utils.CheckUtils.fileNotExistPanic
import static jugggao.com.utils.CommonUtils.mergeNested
import static jugggao.com.utils.InfoUtils.*

class ConfigMultiService implements Serializable {

    private static Script script

    Logger log = new Logger(this)

    public static Map data = [:]

    ConfigMultiService(Script script) {
        if (!(script instanceof CpsScript)) {
            log.fatal "The script is not CpsScript"
        }
        this.script = script
    }

    void merge(Map settings) {
        log.info "Loading and merge configuration with Map type."

        initMerge(settings)
        paramsSettings()
        environmentSettings()

        debug()
    }

    void merge(String settings) {
        log.info "Loading and merge configuration with String type."

        initMerge(settings)

        paramsSettings()
        environmentSettings()

        debug()
    }

    void initMerge(Map settings) {
        log.info "Init configuration with String type."
        data = mergeNested(init(), settings)
    }

    void initMerge(String settings) {
        log.info "Init configuration with String type."

        data = init()
        settingsFile(settings)
    }

    void debug() {
        String configYaml = configToYaml(data)
        log.debug "Config.data:\n$configYaml"
    }

    private Map init() {

        log.info 'Init default config.'

        Map conf = [
                base        : [
                        job      : getJobName(script).toLowerCase(),
                        type     : getJobType(script).toLowerCase(),
                        action   : 'deploy',
                        dir      : script.env['workspace'] ?: getFirstDirectory(script),
                        env      : getEnvironment(script),
                        name     : getApplicationName(script),
                        port     : 80,
                        project  : getProjectName(script).toLowerCase(),
                        workspace: script.env['JENKINS_HOME'] + '/workspace/' + script.env['JOB_NAME'],
                        user     : getBuildUserName(script) ?: 'webhook',
                        userid   : getBuildUserNameID(script) ?: 'webhook',
                        logLevel : '',
                ],
                git         : [],
                build       : [],
                scanner     : [
                        enabled   : false,
                        sources   : '',
                        exclusions: '',
                ],
                docker      : [
                        imgName        : '',
                        imgTag         : '',
                        imgBuildOptions: '',
                        dockerfile     : '',
                        ignorefile     : '',
                        appRoot        : 'data/app',
                        content        : '',
                        chown          : '',
                ],
                kubernetes  : [
                        enabled                 : true,
                        cloud                   : ['alicloud-beijing'],
                        imgPullPolicy           : 'Always',
                        namespace               : getProjectName(script),
                        limitsCpu               : '1000m',
                        limitsMemory            : '1024Mi',
                        requestsCpu             : '1m',
                        requestsMemory          : '10Mi',
                        probePath               : '/actuator/health',
                        probeInitialDelaySeconds: 60,
                        probePeriodSeconds      : 30,
                        probeSuccessThreshold   : 1,
                        probeFailureThreshold   : 5,
                        probeTimeoutSeconds     : 5,
                        deploymentFile          : '',
                        serviceFile             : '',
                        configMapFile           : '',
                        ingressFile             : '',
                ],
                domain      : [
                        enabled    : false,
                        dnsServer  : ['aliCloud'],
                        annotations: [:],
                        host       : [],
                        tls        : [],
                ],
                nacos       : [
                        enabled    : false,
                        application: getApplicationName(script),
                        namespace  : getEnvironment(script),
                        address    : 'nacos.ambow.com',
                ],
                notification: [
                        build  : [
                                enabled: false,
                                type   : 'wechat',
                                wechat : [
                                        mobile: [],
                                        url   : '',
                                ],
                                email  : [:],
                                webhook: [:],
                        ],
                        sync   : [
                                enabled: false,
                                type   : 'wechat',
                                wechat : [
                                        mobile: [],
                                        url   : '',
                                ],
                                email  : [:],
                                webhook: [:],
                        ],
                        release: [
                                enabled: false,
                                type   : 'wechat',
                                wechat : [
                                        mobile: [],
                                        url   : '',
                                ],
                                email  : [:],
                                webhook: [:],
                        ],
                        update : [
                                enabled: false,
                                type   : 'wechat',
                                wechat : [
                                        mobile: [],
                                        url   : '',
                                ],
                                email  : [:],
                                webhook: [:],
                        ]
                ]
        ]

        return conf
    }

    private void settingsFile(String file) {
        log.info 'Load config of local file.'

        fileNotExistPanic(script, file) {
            try {
                Map yaml = script.readYaml(file: file) as Map
                data = mergeNested(data, yaml)

                String dataYaml = configToYaml(data)

            } catch (e) {
                log.error 'An error occurred while try to load config of local file.'
                throw e
            }
        }
    }

    private void paramsSettings() {

        log.info 'Load config of build params.'

        try {
            if (script.params['ACTION']) {
                data['base']['action'] = script.params['ACTION']
            }
//            if (script.params['ENVIRONMENT']) {
//                data['base']['env'] = script.params['ENVIRONMENT']
//            }

            ArrayList<Map> gitSettings = data.git as ArrayList<Map>

            for (gitSetting in gitSettings) {
                String param = (gitSetting.name as String).toUpperCase() + '_GIT_BRANCH'
                if (script.params[param]) {
                    gitSetting.branch = script.params[param]
                }
            }
        }
        catch (e) {
            log.error 'Load params settings failed.'
            throw e
        }
    }

    static private void environmentSettings() {

        if (data.base['env'] == 'pre') {

            ArrayList<Map> gitSettings = data.git as ArrayList<Map>

            for (gitSetting in gitSettings) {
                gitSetting.branch = 'master'
            }
            data.scanner['enabled'] = false
        }

    }

    private String configYaml() {
        try {
            script.writeYaml(
                    file: 'config.yaml',
                    data: data,
                    charset: 'UTF-8',
                    overwrite: true
            )
            String config = script.readFile('config.yaml')
            return config

        } catch (e) {
            log.error "Convert Map to Yaml failed."
            throw e
        }
    }

    @NonCPS
    static String configToYaml(Map config) {
        Yaml yaml = new Yaml()
        return yaml.dump(config)
    }

}