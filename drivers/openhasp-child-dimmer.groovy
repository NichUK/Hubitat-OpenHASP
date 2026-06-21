/*
 * Copyright 2026 NichUK
 *
 * Licensed under the Apache License, Version 2.0.
 */

metadata {
    definition(
        name: 'OpenHASP Child Dimmer',
        namespace: 'nichuk',
        author: 'NichUK',
        importUrl: 'https://raw.githubusercontent.com/NichUK/Hubitat-OpenHASP/main/drivers/openhasp-child-dimmer.groovy'
    ) {
        capability 'Switch'
        capability 'SwitchLevel'
        capability 'Actuator'
        capability 'Refresh'
    }
}

void installed() {
    sendEvent(name: 'switch', value: 'off')
    sendEvent(name: 'level', value: 100, unit: '%')
}

void on() {
    sendEvent(name: 'switch', value: 'on', isStateChange: true)
    parent?.childSwitchCommand(device.deviceNetworkId, 'on')
}

void off() {
    sendEvent(name: 'switch', value: 'off', isStateChange: true)
    parent?.childSwitchCommand(device.deviceNetworkId, 'off')
}

void setLevel(level) {
    int bounded = Math.max(1, Math.min(100, "${level}".toBigDecimal().intValue()))
    sendEvent(name: 'level', value: bounded, unit: '%', isStateChange: true)
    sendEvent(name: 'switch', value: 'on', isStateChange: true)
    parent?.childLevelCommand(device.deviceNetworkId, bounded)
}

void refresh() {
}
