package jugggao.com.utils.notify

import groovy.json.JsonOutput

def wechatNotify(String message, String hookurl) {
    def wechatURL = hookurl
    def payloadJson = [
        "msgtype": "markdown",
        "markdown" : [
            "content": message
        ]
    ]
//    def encodedReq = URLEncoder.encode(JsonOutput.toJson(payloadJson), "UTF-8")
    def MsgJson = JsonOutput.toJson(payloadJson)
    sh (returnStdout: false,
        script: "curl -s -S -X POST  -H 'Content-Type: application/json' -d \'${MsgJson}\' \'${wechatURL}\'"
    )
}
