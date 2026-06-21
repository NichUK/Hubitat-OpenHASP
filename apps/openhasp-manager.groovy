/*
 * Copyright 2026 NichUK
 *
 * Licensed under the Apache License, Version 2.0.
 */

import groovy.json.JsonSlurper

definition(
    name: 'OpenHASP Manager',
    namespace: 'nichuk',
    author: 'NichUK',
    description: 'Binds Hubitat MQTT Import OpenHASP devices to ordinary Hubitat devices.',
    category: 'Convenience',
    importUrl: 'https://raw.githubusercontent.com/NichUK/Hubitat-OpenHASP/main/apps/openhasp-manager.groovy',
    iconUrl: '',
    iconX2Url: '',
    singleInstance: false
)

preferences {
    page(name: 'mainPage', title: 'OpenHASP Manager', install: true, uninstall: true) {
        section('Panel') {
            paragraph 'Use Hubitat MQTT Import Integration to map OpenHASP MQTT topics as Hubitat devices, then select those devices here.'
            input 'plateName', 'text', title: 'OpenHASP plate name', defaultValue: 'bathroom_panel', required: true
            input 'timerIncrementMinutes', 'number', title: 'UFH timer increment minutes', defaultValue: 1, required: true
            input 'timerMaxMinutes', 'number', title: 'UFH timer maximum minutes', defaultValue: 3, required: true
        }
        section('Office lighting circuit') {
            input 'officePanelSwitch', 'capability.switch', title: 'MQTT Import panel switch', multiple: false, required: false
            input 'officePanelDimmerEvent', 'capability.switch', title: 'MQTT Import panel dimmer event device (raw JSON, optional)', multiple: false, required: false
            input 'officePanelDimmer', 'capability.switchLevel', title: 'MQTT Import panel dimmer command device', multiple: false, required: false
            input 'officeLevelTextDevice', 'capability.notification', title: 'MQTT Import panel level text device (optional)', multiple: false, required: false
            input 'officeTarget', 'capability.switchLevel', title: 'Hubitat target dimmer', multiple: false, required: false
        }
        section('Bedroom lighting circuit') {
            input 'bedroomPanelSwitch', 'capability.switch', title: 'MQTT Import panel switch', multiple: false, required: false
            input 'bedroomPanelDimmerEvent', 'capability.switch', title: 'MQTT Import panel dimmer event device (raw JSON, optional)', multiple: false, required: false
            input 'bedroomPanelDimmer', 'capability.switchLevel', title: 'MQTT Import panel dimmer command device', multiple: false, required: false
            input 'bedroomLevelTextDevice', 'capability.notification', title: 'MQTT Import panel level text device (optional)', multiple: false, required: false
            input 'bedroomTarget', 'capability.switchLevel', title: 'Hubitat target dimmer', multiple: false, required: false
        }
        section('Underfloor heating timer') {
            input 'ufhPanelButton', 'capability.pushableButton', title: 'MQTT Import panel timer button (preferred)', multiple: false, required: false
            input 'ufhPanelSwitch', 'capability.switch', title: 'MQTT Import panel timer switch fallback', multiple: false, required: false
            input 'ufhTimerTextDevice', 'capability.notification', title: 'MQTT Import timer button text device (optional)', multiple: false, required: false
            input 'ufhStateTextDevice', 'capability.notification', title: 'MQTT Import UFH state text device (optional)', multiple: false, required: false
            input 'manageUfhVirtualSwitch', 'bool', title: 'Create and use a safe virtual UFH switch', defaultValue: true, required: true
            input 'ufhTargetSwitch', 'capability.switch', title: 'Existing Hubitat UFH target switch (optional)', multiple: false, required: false
        }
        section('Options') {
            input 'debugLogging', 'bool', title: 'Enable debug logging', defaultValue: false
        }
    }
}

void installed() {
    initialize()
}

void updated() {
    unsubscribe()
    unschedule()
    initialize()
}

void initialize() {
    if (settingEnabled(settings.manageUfhVirtualSwitch, true)) {
        managedUfhSwitch()
    }
    subscribePanelControls()
    subscribeTargets()
    syncAllTargetsToPanel()
    updateTimerOutputs()
}

void subscribePanelControls() {
    if (officePanelSwitch) subscribe(officePanelSwitch, 'switch', officePanelSwitchHandler)
    if (officePanelDimmerEvent) subscribe(officePanelDimmerEvent, 'switch', officePanelDimmerEventHandler)
    if (officePanelDimmer) subscribe(officePanelDimmer, 'level', officePanelLevelHandler)
    if (bedroomPanelSwitch) subscribe(bedroomPanelSwitch, 'switch', bedroomPanelSwitchHandler)
    if (bedroomPanelDimmerEvent) subscribe(bedroomPanelDimmerEvent, 'switch', bedroomPanelDimmerEventHandler)
    if (bedroomPanelDimmer) subscribe(bedroomPanelDimmer, 'level', bedroomPanelLevelHandler)
    if (ufhPanelButton) subscribe(ufhPanelButton, 'pushed', ufhPanelButtonHandler)
    if (ufhPanelSwitch) subscribe(ufhPanelSwitch, 'switch', ufhPanelSwitchHandler)
}

void subscribeTargets() {
    if (officeTarget) {
        subscribe(officeTarget, 'switch', officeTargetSwitchHandler)
        subscribe(officeTarget, 'level', officeTargetLevelHandler)
    }
    if (bedroomTarget) {
        subscribe(bedroomTarget, 'switch', bedroomTargetSwitchHandler)
        subscribe(bedroomTarget, 'level', bedroomTargetLevelHandler)
    }
    def ufh = activeUfhTarget()
    if (ufh) {
        subscribe(ufh, 'switch', ufhTargetSwitchHandler)
    }
}

void officePanelSwitchHandler(evt) {
    commandSwitch(officeTarget, normalizeSwitchValue(evt.value))
}

void officePanelLevelHandler(evt) {
    commandLevel(officeTarget, normalizeLevelValue(evt.value, 100))
}

void officePanelDimmerEventHandler(evt) {
    commandLevel(officeTarget, normalizeLevelValue(evt.value, 100))
}

void bedroomPanelSwitchHandler(evt) {
    commandSwitch(bedroomTarget, normalizeSwitchValue(evt.value))
}

void bedroomPanelLevelHandler(evt) {
    commandLevel(bedroomTarget, normalizeLevelValue(evt.value, 100))
}

void bedroomPanelDimmerEventHandler(evt) {
    commandLevel(bedroomTarget, normalizeLevelValue(evt.value, 100))
}

void ufhPanelButtonHandler(evt) {
    addUfhTimerIncrement()
}

void ufhPanelSwitchHandler(evt) {
    addUfhTimerIncrement()
}

void officeTargetSwitchHandler(evt) {
    commandSwitch(officePanelSwitch, evt.value)
}

void officeTargetLevelHandler(evt) {
    commandLevelOnly(officePanelDimmer, evt.value)
    sendTextCommand(officeLevelTextDevice, "${safeInt(evt.value, 0)}")
}

void bedroomTargetSwitchHandler(evt) {
    commandSwitch(bedroomPanelSwitch, evt.value)
}

void bedroomTargetLevelHandler(evt) {
    commandLevelOnly(bedroomPanelDimmer, evt.value)
    sendTextCommand(bedroomLevelTextDevice, "${safeInt(evt.value, 0)}")
}

void ufhTargetSwitchHandler(evt) {
    sendTextCommand(ufhStateTextDevice, evt.value == 'on' ? 'ON' : 'OFF')
}

void syncAllTargetsToPanel() {
    if (officeTarget) {
        officeTargetSwitchHandler([value: officeTarget.currentSwitch ?: 'off'])
        officeTargetLevelHandler([value: officeTarget.currentLevel ?: 100])
    }
    if (bedroomTarget) {
        bedroomTargetSwitchHandler([value: bedroomTarget.currentSwitch ?: 'off'])
        bedroomTargetLevelHandler([value: bedroomTarget.currentLevel ?: 100])
    }
    def ufh = activeUfhTarget()
    if (ufh) {
        ufhTargetSwitchHandler([value: ufh.currentSwitch ?: 'off'])
    }
}

void addUfhTimerIncrement() {
    long nowSeconds = epochSeconds()
    int incrementSeconds = Math.max(1, safeInt(timerIncrementMinutes, 1)) * 60
    int maxSeconds = Math.max(incrementSeconds, safeInt(timerMaxMinutes, 3) * 60)
    state.ufhTimerDeadlineEpochSeconds = addTimerSeconds(nowSeconds, state.ufhTimerDeadlineEpochSeconds as Long, incrementSeconds, maxSeconds)
    commandSwitch(activeUfhTarget(), 'on')
    updateTimerOutputs()
    runIn(1, 'timerTick')
}

void timerTick() {
    long remaining = remainingTimerSeconds()
    if (remaining <= 0) {
        state.remove('ufhTimerDeadlineEpochSeconds')
        commandSwitch(activeUfhTarget(), 'off')
        updateTimerOutputs()
        return
    }
    updateTimerOutputs()
    runIn(1, 'timerTick')
}

void updateTimerOutputs() {
    long remaining = remainingTimerSeconds()
    int incrementSeconds = Math.max(1, safeInt(timerIncrementMinutes, 1)) * 60
    sendTextCommand(ufhTimerTextDevice, timerButtonText(remaining, incrementSeconds))
    def ufh = activeUfhTarget()
    String stateText = remaining > 0 || ufh?.currentSwitch == 'on' ? 'ON' : 'OFF'
    sendTextCommand(ufhStateTextDevice, stateText)
}

long remainingTimerSeconds() {
    remainingSeconds(epochSeconds(), state.ufhTimerDeadlineEpochSeconds as Long)
}

void commandSwitch(device, Object value) {
    if (!device) return
    String normalized = normalizeSwitchValue(value)
    if (debugLogging) log.debug "Commanding ${device.displayName} switch ${normalized}"
    normalized == 'on' ? device.on() : device.off()
}

void commandLevel(device, Object level) {
    if (!device) return
    int bounded = Math.max(1, Math.min(100, safeInt(level, 100)))
    if (debugLogging) log.debug "Commanding ${device.displayName} level ${bounded}"
    device.setLevel(bounded)
    device.on()
}

void commandLevelOnly(device, Object level) {
    if (!device) return
    int bounded = Math.max(1, Math.min(100, safeInt(level, 100)))
    if (debugLogging) log.debug "Commanding ${device.displayName} level ${bounded}"
    device.setLevel(bounded)
}

void sendTextCommand(device, String text) {
    if (!device) return
    if (debugLogging) log.debug "Commanding ${device.displayName} text ${text}"
    try {
        device.deviceNotification(text)
        return
    } catch (MissingMethodException ignored) {
    } catch (Exception e) {
        if (debugLogging) log.debug "deviceNotification failed for ${device.displayName}: ${e.message}"
    }
    try {
        device.speak(text)
        return
    } catch (MissingMethodException ignored) {
    } catch (Exception e) {
        if (debugLogging) log.debug "speak failed for ${device.displayName}: ${e.message}"
    }
    if (text == 'ON' || text == 'OFF') {
        commandSwitch(device, text == 'ON' ? 'on' : 'off')
    }
}

def activeUfhTarget() {
    ufhTargetSwitch ?: (settingEnabled(settings.manageUfhVirtualSwitch, true) ? managedUfhSwitch() : null)
}

def managedUfhSwitch() {
    String dni = "openhasp-${plateName ?: 'bathroom_panel'}-ufh"
    def child = getChildDevice(dni)
    if (!child) {
        child = addChildDevice('hubitat', 'Virtual Switch', dni, [
            name: 'Bathroom UFH Virtual Switch',
            label: 'Bathroom UFH Virtual Switch',
            isComponent: false
        ])
    }
    return child
}

long epochSeconds() {
    Math.floor(now() / 1000D) as long
}

int safeInt(Object value, int fallback = 0) {
    try {
        return "${value}".toBigDecimal().intValue()
    } catch (ignored) {
        return fallback
    }
}

String normalizeSwitchValue(Object value) {
    Object normalized = openHaspPayloadValue(value)
    if (normalized instanceof Boolean) {
        return normalized ? 'on' : 'off'
    }
    if (normalized instanceof Number) {
        return normalized != 0 ? 'on' : 'off'
    }
    String text = "${normalized}".trim().toLowerCase()
    text in ['1', 'true', 'on', 'yes'] ? 'on' : 'off'
}

int normalizeLevelValue(Object value, int fallback = 100) {
    safeInt(openHaspPayloadValue(value), fallback)
}

Object openHaspPayloadValue(Object value) {
    if (value == null) {
        return null
    }
    if (!(value instanceof CharSequence)) {
        return value
    }
    String text = value.toString().trim()
    if (!text.startsWith('{')) {
        return value
    }
    try {
        def parsed = new JsonSlurper().parseText(text)
        if (parsed instanceof Map && parsed.containsKey('val')) {
            return parsed.val
        }
    } catch (ignored) {
    }
    value
}

long addTimerSeconds(Long nowEpochSeconds, Long currentDeadlineEpochSeconds, int incrementSeconds, int maxSeconds) {
    long remaining = remainingSeconds(nowEpochSeconds, currentDeadlineEpochSeconds)
    long nextRemaining = Math.min(maxSeconds as long, remaining + incrementSeconds)
    nowEpochSeconds + nextRemaining
}

long remainingSeconds(Long nowEpochSeconds, Long deadlineEpochSeconds) {
    if (!deadlineEpochSeconds) {
        return 0L
    }
    Math.max(0L, deadlineEpochSeconds - nowEpochSeconds)
}

String timerButtonText(long remainingSeconds, int incrementSeconds) {
    if (remainingSeconds <= 0) {
        return "Start ${Math.max(1, Math.round(incrementSeconds / 60D) as int)}m"
    }
    long minutes = Math.floor(remainingSeconds / 60D) as long
    long seconds = remainingSeconds % 60
    "${minutes}:${seconds.toString().padLeft(2, '0')}"
}

boolean settingEnabled(Object value, boolean defaultValue = true) {
    if (value == null) {
        return defaultValue
    }
    if (value instanceof Boolean) {
        return value
    }
    !("${value}".trim().toLowerCase() in ['false', '0', 'no', 'off'])
}
