/*
 * Copyright 2026 NichUK
 *
 * Licensed under the Apache License, Version 2.0.
 */

metadata {
    definition(
        name: 'OpenHASP Timer Button',
        namespace: 'nichuk',
        author: 'NichUK',
        importUrl: 'https://raw.githubusercontent.com/NichUK/Hubitat-OpenHASP/main/drivers/openhasp-timer-button.groovy'
    ) {
        capability 'PushableButton'
        capability 'Switch'
        capability 'Actuator'
        capability 'Refresh'

        command 'push'
    }
}

void installed() {
    sendEvent(name: 'numberOfButtons', value: 1)
    sendEvent(name: 'switch', value: 'off')
}

void push() {
    sendEvent(name: 'pushed', value: 1, isStateChange: true)
    parent?.childButtonPushed(device.deviceNetworkId)
}

void on() {
    sendEvent(name: 'switch', value: 'on', isStateChange: true)
}

void off() {
    sendEvent(name: 'switch', value: 'off', isStateChange: true)
}

void refresh() {
}
