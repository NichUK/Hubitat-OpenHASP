package uk.co.nichuk.hubitat.openhasp

import spock.lang.Specification

class HubitatAppSyntaxSpec extends Specification {
    def 'Hubitat app files parse as Groovy scripts'() {
        given:
        def shell = new GroovyShell()

        expect:
        ['openhasp-manager.groovy', 'openhasp-panel.groovy'].each { String fileName ->
            shell.parse(new File("apps/${fileName}"))
        }
    }
}
