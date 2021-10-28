/**
 * jenkins-shared-library 
 *
 * @author Peng.Gao 　
 * @date 2021/3/18
 */

package jugggao.com.kubernetes

import org.jenkinsci.plugins.workflow.cps.CpsScript
import jugggao.com.utils.logging.Logger

import static jugggao.com.utils.CheckUtils.*
import static jugggao.com.utils.config.ConfigConstants.*
import static jugggao.com.utils.CommonUtils.disableOutputSh
import static jugggao.com.utils.CheckUtils.emptyVarPanic

class KubeCommand implements Serializable {

    private static Script script

    private Logger log = new Logger(this)

    private String environment

    KubeCommand(Script script, String environment) {
        if (!(script instanceof CpsScript)) {
            log.fatal "Script $script is not CpsScript Object."
        }

        this.script = script
        this.environment = environment
    }

    private void applyCommand(String cmd, String target) {

        emptyVarPanic(script, 'cmd', cmd)
        fileNotExistPanic(script, target)

        if (!(cmd in KUBERNETES_ALLOWED_COMMANDS)) {
            log.fatal "kubernetes $cmd not allowed."
        }

        try {
            script.withCredentials([
                script.file(
                    credentialsId: KUBERNETES_KUBECONFIG_CREDENTIALS_PREFIX + '-' + environment,
                    variable: 'kubeconfig'
                )
            ]) {
                String command = "kubectl --kubeconfig ${script.env['kubeconfig']} $cmd -f $target"
                script.sh(
                    script: command
                )
            }
        } catch (e) {
            log.error "Command 'kubectl $cmd -f $target' execute failed."
            throw e
        }
    }

    void applyDeployment(String deploymentFile = KUBERNETES_DEPLOYMENT_FILE) {
        log.info "Apply deployment on $environment environment."

        applyCommand('apply', deploymentFile)
    }


    void applyService(String serviceFile = KUBERNETES_SERVICE_FILE) {
        log.info "Apply Service on $environment environment."

        applyCommand('apply', serviceFile)
    }

    void applyConfigMap(String configMapFile = KUBERNETES_CONFIGMAP_FILE) {
        log.info "Apply ConfigMap on $environment environment."

        applyCommand('apply', configMapFile)
    }

    void applyIngress(String ingressFile = KUBERNETES_INGRESS_FILE) {
        log.info "Apply Ingress on $environment environment."

        applyCommand('apply', ingressFile)
    }

    void applySecret(String secretFile) {
        log.info "Apply Secret on $environment environment."

        applyCommand('apply', secretFile)
    }

    void applyPvc(String pvcFile = KUBERNETES_PVC_FILE) {
        log.info "Apply Pvc on $environment environment."

        applyCommand('apply', pvcFile)
    }

    void scaleDeploymentReplicas(Map args) {

        ['namespace', 'deployment'].each { args.get(it, '') }

        args.get('replicas', 1)

        String namespace = args.namespace
        String deployment = args.deployment
        String replicas = args.replicas

        log.info "Scale replicas to $replicas on $environment environment."

        try {
            script.withCredentials([
                    script.file(
                            credentialsId: KUBERNETES_KUBECONFIG_CREDENTIALS_PREFIX + '-' + environment,
                            variable: 'kubeconfig'
                    )
            ]) {

                String ScaleDeploymentReplicasCmd = 'kubectl --kubeconfig ' + script.env['kubeconfig'] + ' -n ' +
                        namespace + ' scale deployment ' + deployment + ' --replicas=' + replicas

                script.sh(
                        script: ScaleDeploymentReplicasCmd
                )
            }
        } catch (e) {
            log.error "Failed to scale replicas on $environment environment."
            throw e
        }
    }

    void updateDeploymentImage(Map args) {

        ['namespace', 'image', 'deployment'].each { args.get(it, '') }

        String namespace = args.namespace
        String image = args.image
        String deployment = args.deployment

        log.info "Update image on $environment environment."

        try {
            script.withCredentials([
                script.file(
                    credentialsId: KUBERNETES_KUBECONFIG_CREDENTIALS_PREFIX + '-' + environment,
                    variable: 'kubeconfig'
                )
            ]) {

                String updateDeploymentImage = 'kubectl --kubeconfig ' + script.env['kubeconfig'] + ' -n ' +
                    namespace + ' set image deploy ' + deployment + ' ' + deployment + '=' + image

                script.sh(
                    script: updateDeploymentImage
                )
            }
        } catch (e) {
            log.error "Failed to update image on $environment environment."
            throw e
        }

    }

    Integer getDeploymentHistory(String namespace, String deployment) {

        Integer deploymentHistory = 0

        try {
            script.withCredentials([
                script.file(
                    credentialsId: KUBERNETES_KUBECONFIG_CREDENTIALS_PREFIX + '-' + environment,
                    variable: 'kubeconfig'
                )
            ]) {

                String getDeploymentHistoryCmd = 'kubectl --kubeconfig ' + script.env['kubeconfig'] + ' -n ' +
                    namespace + ' rollout history deployment ' + deployment + ' | wc -l'

                deploymentHistory = (script.sh(
                    script: getDeploymentHistoryCmd,
                    returnStdout: true
                ) as Integer) - 3
            }
        } catch (e) {
            log.error "Failed to get deployment history on $environment environment."
            throw e
        }

        log.debug "Deployment History Number: $deploymentHistory."

        return deploymentHistory
    }

    void rollbackDeployment(String namespace, String deployment) {

        log.info "Roll back image to previous version on $environment."

        try {
            script.withCredentials([
                script.file(
                    credentialsId: KUBERNETES_KUBECONFIG_CREDENTIALS_PREFIX + '-' + environment,
                    variable: 'kubeconfig'
                )
            ]) {

                String rollbackDeploymentCmd = 'kubectl --kubeconfig ' + script.env['kubeconfig'] + ' -n ' +
                    namespace + ' rollout undo deployment ' + deployment

                script.sh(
                    script: rollbackDeploymentCmd
                )
            }
        } catch (e) {
            log.error "Failed to roll back image on $environment environment."
            throw e
        }

    }

    void deleteDeployment(String namespace, String deployment) {

        log.info "Delete deployment $deployment on $environment."

        try {
            script.withCredentials([
                    script.file(
                            credentialsId: KUBERNETES_KUBECONFIG_CREDENTIALS_PREFIX + '-' + environment,
                            variable: 'kubeconfig'
                    )
            ]) {

                String deleteDeploymentCmd = 'kubectl --kubeconfig ' + script.env['kubeconfig'] + ' -n ' +
                        namespace + ' delete deployment ' + deployment

                script.sh(
                        script: deleteDeploymentCmd
                )
            }
        } catch (e) {
            log.error "Failed to delete deployment $deployment on $environment environment."
            throw e
        }
    }

    String getDeploymentImage(String namespace, String deployment) {

        String deploymentImage = ''

        log.debug "getDeploymentImage environment: $environment."

        try {
            script.withCredentials([
                script.file(
                    credentialsId: KUBERNETES_KUBECONFIG_CREDENTIALS_PREFIX + '-' + environment,
                    variable: 'kubeconfig'
                )
            ]) {

                String getDeploymentImageCommand = 'kubectl --kubeconfig ' + script.env['kubeconfig'] + ' -n ' +
                    namespace + ' get deployments.apps ' + deployment +
                    ' -o go-template="{{range .spec.template.spec.containers}}{{.image}}{{end}}"'
                deploymentImage = (script.sh(
                    script: getDeploymentImageCommand,
                    returnStdout: true
                ) as String).trim()
            }

        } catch (e) {
            log.error "Failed to get kubernetes deployment image."
            throw e
        }

        return deploymentImage
    }

    String getDeploymentImage(String environment, String namespace, String deployment) {

        String deploymentImage = ''

        log.debug "getDeploymentImage environment: $environment."

        try {
            script.withCredentials([
                script.file(
                    credentialsId: KUBERNETES_KUBECONFIG_CREDENTIALS_PREFIX + '-' + environment,
                    variable: 'kubeconfig'
                )
            ]) {

//                String getDeploymentImageCommand = 'kubectl --kubeconfig ' + script.env['kubeconfig'] + ' -n ' +
//                    namespace + ' get ns'
                String getDeploymentImageCommand = 'kubectl --kubeconfig ' + script.env['kubeconfig'] + ' -n ' +
                    namespace + ' get deployments.apps ' + deployment +
                    ' -o go-template="{{range .spec.template.spec.containers}}{{.image}}{{end}}"'
                deploymentImage = (script.sh(
                    script: getDeploymentImageCommand,
                    returnStdout: true
                ) as String).trim()

                return deploymentImage
            }

        } catch (e) {
            log.error "Failed to get kubernetes deployment image."
            throw e
        }

        return deploymentImage
    }

    private Integer getUpdateStatus(String namespace, String deployment) {

        Integer readyPodNumber = 0

        try {
            script.withCredentials([
                script.file(
                    credentialsId: KUBERNETES_KUBECONFIG_CREDENTIALS_PREFIX + '-' + environment,
                    variable: 'kubeconfig'
                )
            ]) {

                String getNewReplicaSetCommand = 'kubectl --kubeconfig ' + script.env['kubeconfig'] + ' -n ' +
                    namespace + ' describe deployments.apps ' + deployment + ' | awk \'/NewReplicaSet:/{ print $2}\''

                String getOldReplicaSetCommand = 'kubectl --kubeconfig ' + script.env['kubeconfig'] + ' -n ' +
                        namespace + ' describe deployments.apps ' + deployment + ' | awk \'/OldReplicaSets:/{ print $2}\''

                String newReplicaSet = (disableOutputSh(
                    script,
                    script: getNewReplicaSetCommand,
                    returnStdout: true
                ) as String).trim()

                if (newReplicaSet == '<none>') {
                    newReplicaSet = (disableOutputSh(
                            script,
                            script: getOldReplicaSetCommand,
                            returnStdout: true
                    ) as String).trim()
                }

                String getReadyPodNumberCommand = 'kubectl --kubeconfig ' + script.env['kubeconfig'] + ' -n ' +
                    namespace + ' get replicasets.apps ' + newReplicaSet + ' | awk \'/' +
                    newReplicaSet + '/{ print $4}\''
                readyPodNumber = (disableOutputSh(
                    script,
                    script: getReadyPodNumberCommand,
                    returnStdout: true
                ) as String).trim().toInteger()

//                log.debug "getNewReplicaSetCommand: $getNewReplicaSetCommand\nnewReplicaSet: $newReplicaSet\n"
//                log.debug "getReadyPodNumberCommand: $getReadyPodNumberCommand\nreadyPodNumber: $readyPodNumber"

                return readyPodNumber
            }

        } catch (e) {
            log.error "Failed to get kubernetes update status on $environment."
            throw e
        }

        return readyPodNumber
    }

    String getNodes() {

        String nodes = ''

        try {
            script.withCredentials([
                script.file(
                    credentialsId: KUBERNETES_KUBECONFIG_CREDENTIALS_PREFIX + '-' + environment,
                    variable: 'kubeconfig'
                )
            ]) {
                String getNodesCmd = 'kubectl --kubeconfig ' + script.env['kubeconfig'] + ' get nodes'
                nodes = (disableOutputSh(
                    script,
                    script: getNodesCmd,
                    returnStdout: true
                ) as String).trim()
            }
        } catch (e) {
            log.error "Failed to get nodes."
            throw e
        }

        return nodes
    }

    void waitForUpdateReady(String namespace, String deployment, Integer desiredReplicas) {

        log.info "Wait for service update to be ready."

        try {
            script.timeout(
                    time: KUBERNETES_WAIT_UPDATE_READY_SECONDS,
                    unit: 'SECONDS'
            ) {
                script.waitUntil(
                        initialRecurrencePeriod: 6000,
                        quiet: true
                ) {
                    Integer currentReplicas = getUpdateStatus(namespace, deployment)
                    currentReplicas == desiredReplicas
                }
            }
        } catch (e) {
            log.error "Health check failed."
            throw e
        }
    }

    boolean hasSecret(String namespace, String secretName) {

        log.info "Verify the $secretName exists."

        script.withCredentials([
            script.file(
                credentialsId: KUBERNETES_KUBECONFIG_CREDENTIALS_PREFIX + '-' + environment,
                variable: 'kubeconfig'
            )
        ]) {
            Integer returnCode = script.sh(
                script: 'kubectl --kubeconfig ' + script.env['kubeconfig'] +
                    ' -n ' + namespace + ' get secrets  | awk \'NR!=1{print $1}\' | grep ' + secretName,
                returnStatus: true
            ) as Integer

            if (returnCode == 0) return true
        }

        return false
    }
}
