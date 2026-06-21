package uk.co.nichuk.hubitat.openhasp

import spock.lang.Specification
import spock.lang.Unroll

class OpenHaspSupportSpec extends Specification {
    def 'parses OpenHASP JSON events and recognises actionable event types'() {
        expect:
        OpenHaspSupport.parseEvent('{"event":"changed","val":37}') == [event: 'changed', val: 37]
        OpenHaspSupport.isActionEvent([event: 'changed'])
        OpenHaspSupport.isActionEvent([event: 'up'])
        !OpenHaspSupport.isActionEvent([event: 'down'])
        !OpenHaspSupport.isActionEvent([:])
    }

    @Unroll
    def 'normalizes MQTT Import switch payload #payload to #switchValue'() {
        expect:
        OpenHaspSupport.normalizeSwitchValue(payload) == switchValue

        where:
        payload                         || switchValue
        'on'                            || 'on'
        'off'                           || 'off'
        1                               || 'on'
        0                               || 'off'
        '{"event":"up","val":1}'        || 'on'
        '{"event": "up", "val": 0}'     || 'off'
        '{"event":"changed","val":true}' || 'on'
    }

    @Unroll
    def 'normalizes MQTT Import level payload #payload to #level'() {
        expect:
        OpenHaspSupport.normalizeLevelValue(payload, 100) == level

        where:
        payload                        || level
        37                             || 37
        '38'                           || 38
        '{"event":"changed","val":42}' || 42
        '{"event": "up", "val": 7}'    || 7
    }

    @Unroll
    def 'converts Hubitat level #level to Zigbee2MQTT brightness #brightness'() {
        expect:
        OpenHaspSupport.levelToBrightness(level) == brightness

        where:
        level || brightness
        1     || 3
        50    || 127
        100   || 254
        0     || 3
        150   || 254
    }

    @Unroll
    def 'converts Zigbee2MQTT brightness #brightness to Hubitat level #level'() {
        expect:
        OpenHaspSupport.brightnessToLevel(brightness) == level

        where:
        brightness || level
        1          || 1
        127        || 50
        254        || 100
        0          || 1
        999        || 100
    }

    def 'adds timer increments up to the configured maximum'() {
        expect:
        OpenHaspSupport.addTimerSeconds(1_000L, null, 60, 180) == 1_060L
        OpenHaspSupport.addTimerSeconds(1_000L, 1_060L, 60, 180) == 1_120L
        OpenHaspSupport.addTimerSeconds(1_000L, 1_170L, 60, 180) == 1_180L
    }

    @Unroll
    def 'formats timer button labels'() {
        expect:
        OpenHaspSupport.timerButtonText(remaining, 60) == text

        where:
        remaining || text
        0         || 'Start 1m'
        1         || '0:01'
        60        || '1:00'
        179       || '2:59'
    }

    def 'includes bathroom defaults used by the migration from the bridge'() {
        when:
        def controls = OpenHaspSupport.defaultBathroomControls()

        then:
        controls.p1b21.role == 'timerButton'
        controls.p1b42.kind == 'switch'
        controls.p1b43.kind == 'dimmer'
        controls.p1b52.kind == 'switch'
        controls.p1b53.kind == 'dimmer'
    }
}
