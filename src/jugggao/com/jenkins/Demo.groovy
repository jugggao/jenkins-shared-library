package jugggao.com.jenkins

class Demo implements Serializable {

    private static Script script

    private String test = 'test'

    Demo(Script script) {
        this.script = script
    }

    def call() {
        script.sh('echo ' + this.test)
    }
}
