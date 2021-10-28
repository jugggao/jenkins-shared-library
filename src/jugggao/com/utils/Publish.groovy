package jugggao.com.utils

def publishPgyer(file, apiKey) {
    def publishJson = sh(
            returnStdout: true,
            script: "curl -4 -F 'file=@${file}' -F '_api_key=${apiKey}' 'https://www.pgyer.com/apiv2/app/upload'"
    )
    return publishJson
}

def getPublishInfo(publishJson) {
    def publishInfo = [:]
    def publishMap = readJSON( text: publishJson)
    publishInfo << ["packageName": publishMap.data.buildFileName]
    publishInfo << ["shortcutUrl": "https://www.pgyer.com/${publishMap.data.buildShortcutUrl}"]
    publishInfo << ["qrCode": publishMap.data.buildQRCodeURL]
    return publishInfo
}
