/*
 * Copyright 2026 NichUK
 *
 * Licensed under the Apache License, Version 2.0.
 */

metadata {
    definition(
        name: 'Boost Timer Device',
        namespace: 'nichuk',
        author: 'NichUK',
        importUrl: 'https://raw.githubusercontent.com/NichUK/Hubitat-OpenHASP/main/drivers/boost-timer-device.groovy'
    ) {
        capability 'Actuator'
        capability 'Switch'
        capability 'PushableButton'

        attribute 'remainingSeconds', 'number'
        attribute 'displayText', 'string'
        attribute 'integrationType', 'string'
        attribute 'openHaspRowType', 'string'

        command 'boost'
        command 'cancel'
    }
}

void installed() {
    initialize()
}

void updated() {
    initialize()
}

void initialize() {
    sendEvent(name: 'numberOfButtons', value: 1)
    if (device.currentValue('switch') == null) sendEvent(name: 'switch', value: 'off')
    if (device.currentValue('remainingSeconds') == null) sendEvent(name: 'remainingSeconds', value: 0)
    if (device.currentValue('displayText') == null) sendEvent(name: 'displayText', value: 'Start')
    sendEvent(name: 'integrationType', value: 'boostTimer')
    sendEvent(name: 'openHaspRowType', value: 'timerButton')
}

void push(Object buttonNumber = 1) {
    sendEvent(name: 'pushed', value: safeButton(buttonNumber), isStateChange: true)
    parent?.componentBoost(device)
}

void boost() {
    parent?.componentBoost(device)
}

void on() {
    parent?.componentBoost(device)
}

void off() {
    parent?.componentCancel(device)
}

void cancel() {
    parent?.componentCancel(device)
}

void setTimerState(String switchValue, long remainingSeconds, String displayText) {
    sendEvent(name: 'switch', value: switchValue)
    sendEvent(name: 'remainingSeconds', value: remainingSeconds)
    sendEvent(name: 'displayText', value: displayText)
}

Integer safeButton(Object value) {
    try {
        return Math.max(1, "${value ?: 1}".toInteger())
    } catch (ignored) {
        return 1
    }
}
