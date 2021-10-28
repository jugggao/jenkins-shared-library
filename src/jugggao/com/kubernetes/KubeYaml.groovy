/**
 * Create by Peng.Gao at 2021/2/26 17:30
 *
 * A Part of the Project jenkins-shared-library
 */

package jugggao.com.kubernetes

import jugggao.com.ci.Language
import jugggao.com.utils.logging.Logger
import org.apache.maven.model.Model
import org.jenkinsci.plugins.workflow.cps.CpsScript

import static jugggao.com.utils.CheckUtils.emptyVarPanic
import static jugggao.com.utils.InfoUtils.getReplicasNumber
import static jugggao.com.utils.config.ConfigConstants.*

class KubeYaml implements Serializable {

    private static Script script

    private Logger log = new Logger(this)

    private Map data

    private Map deployment

    private Map service

    private Map configmap

    private Map ingress

    private Map pvc

    KubeYaml(Script script, Map config) {
        if (!(script instanceof CpsScript)) {
            log.fatal "Script $script is not CpsScript Object."
        }

        this.script = script
        this.data = config
    }

    private Map yamlReaderFromFile(String file) {
        Map yaml

        try {
            yaml = script.readYaml(
                file: file
            ) as Map

        } catch (e) {
            log.error "Read template $file file failed."
            throw e
        }
        return yaml
    }

    private Map yamlReaderFromContent(String text) {

        Map yaml

        try {
            yaml = script.readYaml(
                text: text
            ) as Map
        } catch (e) {
            log.error "Failed to read yaml from text."
            throw e
        }
        return yaml
    }

    void genDeploymentFile(Language language) {
        log.info "Generate kubernetes deployment file."


        if (data.kubernetes['deploymentFile']) {
            String deploymentFile = script.env['workspace'] + '/' + data.kubernetes['deploymentFile']
            deployment = yamlReaderFromFile(deploymentFile)
        } else {
            String template = KUBERNETES_TEMPLATE_DIR + '/yaml/language/' +
                language.toString().toLowerCase() + '/' + KUBERNETES_DEPLOYMENT_FILE
            String content = script.libraryResource(template) as String
            deployment = yamlReaderFromContent(content)
        }

        try {
            Map metadata = deployment['metadata'] as Map
            Map spec = deployment['spec'] as Map
            Map strategy = spec['strategy']['rollingUpdate'] as Map
            Map templateMetadata = spec['template']['metadata'] as Map
            Map templateSpec = spec['template']['spec'] as Map
            Map container = (templateSpec['containers'] as ArrayList)[0] as Map

            Map port = (container['ports'] as ArrayList)[0] as Map
            Map resources = container['resources'] as Map
            ArrayList env = container['env'] as ArrayList ?: []
            Map livenessProbe = container['livenessProbe'] as Map
            Map readinessProbe = container['readinessProbe'] as Map

            metadata['name'] = data['base']['name']
            metadata['namespace'] = data['kubernetes']['namespace']
            metadata['labels']['app'] = data['base']['name']

            spec['replicas'] = getReplicasNumber(data['base']['env'] as String)
            spec['selector']['matchLabels']['app'] = data['base']['name']
            spec['progressDeadlineSeconds'] = KUBERNETES_PROGRESS_DEADLINE_SECONDS
            spec['revisionHistoryLimit'] = KUBERNETES_REVISION_HISTORY_LIMIT

            strategy['maxSurge'] = KUBERNETES_STRATEGY_MAX_SURGE
            strategy['maxUnavailable'] = KUBERNETES_STRATEGY_MAX_UNAVAILABLE

            templateMetadata['labels']['app'] = data['base']['name']
            templateSpec['terminationGracePeriodSeconds'] = KUBERNETES_TERMINATION_GPS

            container['name'] = data['base']['name']
            container['image'] = data['docker']['image']

            port['containerPort'] = data['base']['port']

            if (env.size() > 0) {
                env[0]['name'] = 'ENVIRONMENT'
                env[0]['value'] = data['base']['env']
            }

            resources['requests']['cpu'] = data['kubernetes']['requestsCpu']
            resources['requests']['memory'] = data['kubernetes']['requestsMemory']
            resources['limits']['cpu'] = data['kubernetes']['limitsCpu']
            resources['limits']['memory'] = data['kubernetes']['limitsMemory']

            livenessProbe['httpGet']['path'] = data['kubernetes']['probePath']
            livenessProbe['httpGet']['port'] = data['base']['port']
            livenessProbe['initialDelaySeconds'] = data['kubernetes']['probeInitialDelaySeconds']
            livenessProbe['periodSeconds'] = data['kubernetes']['probePeriodSeconds']
            livenessProbe['successThreshold'] = data['kubernetes']['probeSuccessThreshold']
            livenessProbe['failureThreshold'] = data['kubernetes']['probeFailureThreshold']
            livenessProbe['timeoutSeconds'] = data['kubernetes']['probeTimeoutSeconds']

            readinessProbe['httpGet']['path'] = data['kubernetes']['probePath']
            readinessProbe['httpGet']['port'] = data['base']['port']
            readinessProbe['initialDelaySeconds'] = data['kubernetes']['probeInitialDelaySeconds']
            readinessProbe['periodSeconds'] = data['kubernetes']['probePeriodSeconds']
            readinessProbe['successThreshold'] = data['kubernetes']['probeSuccessThreshold']
            readinessProbe['failureThreshold'] = data['kubernetes']['probeFailureThreshold']
            readinessProbe['timeoutSeconds'] = data['kubernetes']['probeTimeoutSeconds']

            // if (data['kubernetes']['persistence']['enabled']) extraVolumes()

            extraLanguageProperties(language)

            script.writeYaml(
                file: KUBERNETES_DEPLOYMENT_FILE,
                data: deployment,
                overwrite: true
            )

            String sDeployment = script.readFile(KUBERNETES_DEPLOYMENT_FILE as String)

            log.debug "Deployment: \n$sDeployment"
        } catch (e) {
            log.error "Failed to generate deployment file."
            throw (e)
        }
    }

    void extraVolumes() {

        Map spec = deployment['spec'] as Map
        Map templateSpec = spec['template']['spec'] as Map
        Map container = (templateSpec['containers'] as ArrayList)[0] as Map

        templateSpec['volumes'] = []
        ArrayList volumes = templateSpec['volumes']
        volumes.add(
            name: 'data',
            persistentVolumeClaim: [
                claimName: data['base']['name']
            ]
        )

        container['volumeMounts'] = []
        container['volumeMounts'].add(
            mountPath: data['kubernetes']['persistence']['mountPath'],
            name: 'data'
        )
    }

    void genServiceFile(Language language) {
        log.info "Generate kubernetes service file."

        if (data.kubernetes['serviceFile']) {
            String serviceFile = script.env['workspace'] + '/' + data.kubernetes['serviceFile']
            service = yamlReaderFromFile(serviceFile)
        } else {
            String template = KUBERNETES_TEMPLATE_DIR + '/yaml/language/' +
                language.toString().toLowerCase() + '/' + KUBERNETES_SERVICE_FILE
            String content = script.libraryResource(template) as String
            service = yamlReaderFromContent(content)
        }

        Map metadata = service['metadata'] as Map
        Map spec = service['spec'] as Map
        Map port = (service['spec']['ports'] as ArrayList)[0] as Map

        metadata['name'] = data['base']['name']
        metadata['namespace'] = data['kubernetes']['namespace']
        metadata['labels']['app'] = data['base']['name']

        spec['selector']['app'] = data['base']['name']

        port['port'] = data['base']['port']
        port['targetPort'] = data['base']['port']

        script.writeYaml(
            file: KUBERNETES_SERVICE_FILE,
            data: service,
            overwrite: true
        )

        String sService = script.readFile(KUBERNETES_SERVICE_FILE as String)

        log.debug "Service: \n$sService"

    }

    void genIngressFile(Language language, Closure body = {}) {

        log.info "Generate Kubernetes ingress file."

        if (data.kubernetes['ingressFile']) {
            String ingressFile = script.env['workspace'] + '/' + data.kubernetes['ingressFile']
            ingress = yamlReaderFromFile(ingressFile)
        } else {
            String template = KUBERNETES_TEMPLATE_DIR + '/yaml/language/' +
                language.toString().toLowerCase() + '/' + KUBERNETES_INGRESS_FILE
            String content = script.libraryResource(template) as String
            ingress = yamlReaderFromContent(content)
        }

        ArrayList hosts = data['domain']['hosts']
        emptyVarPanic(script, 'host', hosts) {

            try {
                String template = KUBERNETES_TEMPLATE_DIR + '/yaml/language/' +
                    language.toString().toLowerCase() + '/' + KUBERNETES_INGRESS_FILE
                String content = script.libraryResource(template) as String
                ingress = yamlReaderFromContent(content)
                Map metadata = ingress['metadata'] as Map

                metadata['name'] = data['base']['name']
                metadata['namespace'] = data['kubernetes']['namespace']
                metadata['labels']['app'] = data['base']['name']

                if (data['domain']['annotations']) {
                    ingress['metadata']['annotations'] = data['domain']['annotations']
                }

                ingress['spec']['rules'] = [] as ArrayList
                data['domain']['hosts'].each { Map host ->
                    ingress['spec']['rules'].add(
                        host: data['base']['env'] + '-' + host['host'],
                        http: [
                            paths: [
                                [
                                    backend : [
                                        service: [
                                            name: data['base']['name'],
                                            port: [
                                                number: data['base']['port'],
                                            ]
                                        ]
                                    ],
                                    path    : host['path'],
                                    pathType: host['pathType'],
                                ]
                            ]
                        ]
                    )
                }

                if (data['domain']['tls']) {
                    ingress['spec']['tls'] = [] as ArrayList
                    data['domain']['tls'].each { Map cert ->
                        ingress['spec']['tls'].add(
                            [
                                hosts     : [data['base']['env'] + '-' + cert['host']],
                                secretName: cert['cert'],
                            ]
                        )
                    }
                }
            } catch (e) {
                log.error "Failed to generate ingress data."
                throw e
            }

            try {
                script.writeYaml(
                    file: KUBERNETES_INGRESS_FILE,
                    data: ingress,
                    overwrite: true
                )
            } catch (e) {
                log.error "Failed to write ingress file."
                throw e
            }

            String sIngress = script.readFile(KUBERNETES_INGRESS_FILE as String)

            log.debug "Ingress: \n$sIngress"

            body()
        }
    }

    void genConfigmapFile(Language language) {

        log.info "Generate kubernetes config map file."

        if (data.kubernetes['configMapFile']) {
            String configmapFile = script.env['workspace'] + '/' + data.kubernetes['configMapFile']
            configmap = yamlReaderFromFile(configmapFile)
        } else {
            String template = KUBERNETES_TEMPLATE_DIR + '/yaml/language/' +
                language.toString().toLowerCase() + '/' + KUBERNETES_CONFIGMAP_FILE
            String content = script.libraryResource(template) as String
            configmap = yamlReaderFromContent(content)
        }

        Map metadata = configmap['metadata'] as Map

        metadata['name'] = data['base']['name']
        metadata['namespace'] = data['kubernetes']['namespace']
        metadata['labels']['app'] = data['base']['name']

        script.writeYaml(
            file: KUBERNETES_CONFIGMAP_FILE,
            data: configmap,
            overwrite: true
        )

        String sConfigmap = script.readFile(KUBERNETES_CONFIGMAP_FILE as String)

        log.debug "Service: \n$sConfigmap"
    }


    void genSecretFile(String secretName, Closure body = {}) {

        log.info "Generate Kubernetes secret file."

        String secretFile = secretName + '.yaml'
        String template = KUBERNETES_TEMPLATE_DIR + '/yaml/secret/' + secretName + '.yaml'
        String content = script.libraryResource(template) as String
        Map secret = yamlReaderFromContent(content)

        secret['metadata']['namespace'] = data['kubernetes']['namespace']
        script.writeYaml(
            file: secretFile,
            data: secret,
            overwrite: true
        )

        body()
    }

    void genPvcFile(Language language) {

        log.info "Generate kubernetes pvc file."

        if (data.kubernetes['persistence']['pvcFile']) {
            String pvcFile = script.env['workspace'] + '/' + data.kubernetes['persistence']['pvcFile']
            pvc = yamlReaderFromFile(pvcFile)
        } else {
            String template = KUBERNETES_TEMPLATE_DIR + '/yaml/language/' +
                language.toString().toLowerCase() + '/' + KUBERNETES_PVC_FILE
            String content = script.libraryResource(template) as String
            pvc = yamlReaderFromContent(content)
        }

        Map metadata = pvc['metadata'] as Map

        metadata['name'] = data['base']['name']
        metadata['namespace'] = data['kubernetes']['namespace']
        metadata['labels']['app'] = data['base']['name']

        Map spec = pvc['spec'] as Map

        spec['resources']['requests']['storage'] = data['kubernetes']['persistence']['requestStorage']
        spec['storageClassName'] = data['kubernetes']['persistence']['storageClass']

        script.writeYaml(
            file: KUBERNETES_PVC_FILE,
            data: pvc,
            overwrite: true
        )

        String sPvc = script.readFile(KUBERNETES_PVC_FILE as String)

        log.debug "PVC: \n$sPvc"
    }


    private void extraLanguageProperties(Language language) {
        log.debug "Language: ${language.toString()}"

        switch (language) {
            case Language.JAVA:
                extraJavaProperties()
                if (data['nacos']['enabled']) {
                    extraNacosProperties()
                }
                if (data.skywalking['enabled']) {
                    extraSkywalkingProperties()
                }
                break
            case Language.NODEJS:
                extraWebProperties()
                break
            case Language.HTML:
                extraWebProperties()
                break
            case Language.PHP:
                extraWebProperties()
                break
            default:
                log.warn "$language not support."
                script.error "Language error."
        }
    }

    private void extraJavaProperties() {

        String packageName = getMavenProperties('name') ?: getMavenProperties('artifactId')
        emptyVarPanic(script, 'packageName', packageName) {
            Map container = (deployment['spec']['template']['spec']['containers'] as ArrayList)[0] as Map
            container['args'] =
                [
                    '-XX:+UseContainerSupport',
                    '-XX:MaxRAMPercentage=75.0',
                    '-jar',
                    data['docker']['appRoot'] + '/' + packageName + '.jar',
                    '--spring.profiles.active=' + data['base']['env']
                ]
            container['command'] =
                [
                    'java'
                ]
        }

        log.debug "packageName: $packageName"
    }

    private void extraNacosProperties() {
        Map container = (deployment['spec']['template']['spec']['containers'] as ArrayList)[0] as Map

        String nacosAddress = data['nacos']['address']
        String nacosApplication = data['nacos']['application']
        String[] dq = ['dev', 'uat', 'pre', 'test']

        emptyVarPanic(script, 'nacosAddress', nacosAddress)
        emptyVarPanic(script, 'nacosApplication', nacosApplication)

        emptyVarPanic(script, 'dq', dq) {
            if ((data['base']['env'] as String) in dq) {
                container['args'] += [
                    '--spring.cloud.nacos.config.server-addr=' +
                        data['nacos']['address'] + ':80'
                ]
            }
        }

        container['env'] += [
            [
                name     : 'POD_IP',
                valueFrom: [
                    fieldRef: [
                        apiVersion: 'v1',
                        fieldPath : 'status.podIP'
                    ]
                ]
            ]
        ]

        container['lifecycle'] = [
            preStop: [
                exec: [
                    command: [
                        '/bin/sh',
                        '-c',
                        'curl --request PUT "http://' +
                            data['nacos']['address'] +
                            '/nacos/v1/ns/instance?serviceName=' +
                            data['nacos']['application'] +
                            '&namespaceId=' +
                            data['base']['env'] +
                            '&ip=${POD_IP}&port=' +
                            data['base']['port'] +
                            '&enabled=false";sleep 30'
                    ]
                ]
            ]
        ]
    }

    private void extraWebProperties() {

        Map templateSpec = deployment['spec']['template']['spec'] as Map
        Map container = (deployment['spec']['template']['spec']['containers'] as ArrayList)[0] as Map

        templateSpec['volumes'] = templateSpec['volumes'] ?: []
        container['volumeMounts'] = container['volumeMounts'] ?: []

        templateSpec['volumes'] += ([
            configMap: [
                defaultMode: 420,
                name       : data['base']['name']
            ],
            name     : 'config'
        ])

        container['volumeMounts'] += ([
            mountPath: '/etc/nginx/conf.d/default.conf',
            name     : 'config',
            subPath  : 'default.conf'
        ])
    }

    private String getMavenProperties(String item) {

        Model pom = script.readMavenPom(
            file: 'pom.xml'
        ) as Model

        return pom[item]
    }

    private String extraSkywalkingProperties() {
        Map templateSpec = deployment['spec']['template']['spec'] as Map
        Map container = (deployment['spec']['template']['spec']['containers'] as ArrayList)[0] as Map

        templateSpec['initContainers'] = ([
            [
                args                    : [
                    '-c',
                    'cp -r /usr/skywalking/* /skywalking'
                ],
                command                 : [
                    'sh'
                ],
                image                   : 'cr.ambow.com/tools/skywalking-java-agent:v8.5',
                imagePullPolicy         : 'IfNotPresent',
                name                    : 'skywalking-agent',
                resources               : [:],
                terminationMessagePath  : '/dev/termination-log',
                terminationMessagePolicy: 'File',
                volumeMounts            : [[
                                               mountPath: '/skywalking',
                                               name     : 'sw-agent'
                                           ]]

            ]
        ])


        ArrayList<String> extraArgs = [
            '-Dskywalking.agent.instance_name=' + data.base['env'],
            '-Dskywalking.agent.service_name=' + data['kubernetes']['namespace'] + ':' + ':' + data.base['name'],
            '-Dskywalking.collector.backend_service=10.10.115.11:11800',
            '-javaagent:/skywalking/skywalking-agent.jar'
        ]

        for (argument in extraArgs) {
            (container['args'] as ArrayList).add(0, argument)
        }

        container['volumeMounts'] = ([
            [
                mountPath: '/skywalking',
                name     : 'sw-agent'
            ]
        ])

        if (templateSpec.volumes) {
            (templateSpec.volumes as ArrayList).add([
                [
                    emptyDir: [:],
                    name    : 'sw-agent'
                ]
            ])
        } else {
            templateSpec.volumes = [
                [
                    emptyDir: [:],
                    name    : 'sw-agent'
                ]
            ]
        }

    }

}

