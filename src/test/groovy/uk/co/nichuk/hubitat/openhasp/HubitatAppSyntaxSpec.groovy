package uk.co.nichuk.hubitat.openhasp

import spock.lang.Specification

class HubitatAppSyntaxSpec extends Specification {
    def 'Hubitat app and driver files parse as Groovy scripts'() {
        given:
        def shell = new GroovyShell()

        expect:
        [
            'apps/boost-timer.groovy',
            'apps/openhasp-manager.groovy',
            'apps/openhasp-panel.groovy',
            'drivers/boost-timer-device.groovy',
            'drivers/openhasp-connector.groovy',
            'drivers/openhasp-text-label.groovy'
        ].each { String fileName ->
            shell.parse(new File(fileName))
        }
    }
}
