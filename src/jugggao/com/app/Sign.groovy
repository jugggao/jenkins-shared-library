package jugggao.com.app

boolean signCheck(file) {
    def signInfo = sh (
        returnStdout: true,
        script: "jarsigner -verify ${file}"
    )
    return signInfo.contains("jar verified.")
}

void signApk(unalignedApk, alignApk, Map cert) {
    sh "/data/jenkins/android-sdk/build-tools/30.0.2/zipalign -pfv 4 $unalignedApk $alignApk"
    sh "/data/jenkins/android-sdk/build-tools/30.0.2/apksigner sign -v --ks ${cert.path} --ks-key-alias ${cert.alias} --ks-pass pass ${cert.password} ${alignApk}"
}
