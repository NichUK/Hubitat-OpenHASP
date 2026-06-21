/*
 * Copyright 2026 NichUK
 *
 * Licensed under the Apache License, Version 2.0.
 */

metadata {
    definition(
        name: 'OpenHASP Child Switch',
        namespace: 'nichuk',
        author: 'NichUK',
        importUrl: 'https://raw.githubusercontent.com/NichUK/Hubitat-OpenHASP/main/drivers/openhasp-child-switch.groovy'
    ) {
        capability 'Switch'
        capability 'Actuator'
        capability 'Refresh'
    }
}

void installed() {
    sendEvent(name: 'switch', value: 'off')
}

void on() {
    sendEvent(name: 'switch', value: 'on', isStateChange: true)
    parent?.childSwitchCommand(device.deviceNetworkId, 'on')
}

void off() {
    sendEvent(name: 'switch', value: 'off', isStateChange: true)
    parent?.childSwitchCommand(device.deviceNetworkId, 'off')
}

void refresh() {
}
