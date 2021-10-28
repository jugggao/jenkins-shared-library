package jugggao.com.app

import jugggao.com.utils.logging.Logger
import org.jenkinsci.plugins.workflow.cps.CpsScript


import static jugggao.com.utils.config.ConfigConstants.*
import static jugggao.com.utils.CheckUtils.emptyVarPanic

class IosBuild implements Serializable {

    static Script script

    private Logger log = new Logger(this)

    IosBuild(Script script) {
        if (!(script instanceof CpsScript)) {
            log.fatal "This script is not instance of CpsScript, type: ${script.getClass().getName()}"
        }
        this.script = script
    }

    void importDeveloperProfile(String profileId) {

        try {
            script.importDeveloperProfile(
                importIntoExistingKeychain: false,
                profileId: profileId
            )
        } catch (e) {
            log.error "Failed to import developer profile."
            throw e
        }
    }

    void unlockKeychain(String credentials = IOS_BUILD_AMBOW_USER_CREDENTIALS) {

        log.info "Unlock keychain."

        try {
            script.unlockMacOSKeychain(
                keychainId: 'ios-build-keychain-ambow'
            )
//            script.withCredentials([
//                script.usernamePassword(
//                    credentialsId: credentials,
//                    passwordVariable: 'iosBuildSshPassword',
//                    usernameVariable: 'iosBuildSshUsername'
//                )
//            ]) {
//                script.sh(
//                    script: 'security unlock-keychain -p ' + script.env['iosBuildSshPassword'] + ' login.keychain'
//                )
//            }
        } catch (e) {
            log.error "Failed to unlock keychain."
            throw e
        }
    }


    void xcodeBuild(Map options) {

        log.info "Xcode build."

        String sOptions = ''

        options.each {k, v ->
            sOptions += k + ': ' + v + "\n"
        }

        log.debug "Options: $sOptions"

        [
            'xcodeSchema',
            'xcodeWorkspaceFile',
            'ipaOutputDirectory',
            'ipaExportMethod',
            'generateArchive',
            'buildIpa',
            'bundleID',
            'configuration',
            'developmentTeamName',
        ].each {
            emptyVarPanic(script, it, options[it])
        }

        try {

            script.xcodeBuild(
                target: options.target as String,
                xcodeSchema: options.xcodeSchema as String,
                xcodeWorkspaceFile: options.xcodeWorkspaceFile as String,
                ipaOutputDirectory: options.ipaOutputDirectory as String,
                ipaExportMethod: options.ipaExportMethod as String,
                generateArchive: options.generateArchive as Boolean,
                buildIpa: options.buildIpa as Boolean,
                // ipaName: 'test-${VERSION}-${BUILD_DATE}',
                bundleID: options.bundleID as String,
                cleanBeforeBuild: options.cleanBeforeBuild as Boolean,
                configuration: options.configuration as String,
                xcodebuildArguments: options.xcodebuildArguments as String,
                developmentTeamName: options.developmentTeamName as String,
                // cfBundleShortVersionStringValue: '1.0.0',
                // cfBundleVersionValue: '1',
                keychainId: IOS_BUILD_AMBOW_USER_KEYCHAIN_CREDENTIALS,
                signingMethod: options.signingMethod as String,
                //provisioningProfiles: options.provisioningProfiles,
                noConsoleLog: options.noConsoleLog as Boolean,
            )

        } catch (e) {
            log.error "Failed to xcode build."
            throw e
        }
    }
}
