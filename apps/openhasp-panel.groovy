/*
 * Copyright 2026 NichUK
 *
 * Licensed under the Apache License, Version 2.0.
 */

import groovy.json.JsonSlurper

definition(
    name: 'OpenHASP Panel',
    namespace: 'nichuk',
    author: 'NichUK',
    description: 'Maps one OpenHASP panel, imported through Hubitat MQTT Import, to Hubitat devices.',
    category: 'Convenience',
    parent: 'nichuk:OpenHASP Manager',
    importUrl: 'https://raw.githubusercontent.com/NichUK/Hubitat-OpenHASP/main/apps/openhasp-panel.groovy',
    iconUrl: '',
    iconX2Url: '',
    singleInstance: false
)

preferences {
    page(name: 'mainPage')
}

def mainPage() {
    dynamicPage(name: 'mainPage', title: panelPageTitle(), install: true, uninstall: true) {
        section('Panel') {
            paragraph 'First map this panel in Hubitat MQTT Import. Then select the imported MQTT devices below and bind them to the Hubitat devices they should control.'
            input 'panelLabel', 'text', title: 'Panel label', defaultValue: 'Bathroom Panel', required: true
            input 'plateName', 'text', title: 'OpenHASP plate name', defaultValue: 'bathroom_panel', required: true
            input 'manageLightingControls', 'bool', title: 'Create virtual lighting controls for dashboards', defaultValue: true, required: true
            input 'manageTextLabels', 'bool', title: 'Create OpenHASP MQTT text label devices', defaultValue: true, required: true, submitOnChange: true
            if (settingEnabled(settingValue('manageTextLabels'), false)) {
                input 'mqttBrokerUri', 'text', title: 'MQTT broker URI for text labels', description: 'Example: tcp://10.0.0.65:1883', required: true
                input 'mqttUsername', 'text', title: 'MQTT username for text labels', required: false
                input 'mqttPassword', 'password', title: 'MQTT password for text labels', required: false
                input 'mqttRetainTextLabels', 'bool', title: 'Retain OpenHASP label text messages', defaultValue: false, required: true
            }
        }

        (1..4).each { Integer index ->
            section("Lighting mapping ${index}") {
                paragraph 'Map one OpenHASP switch/slider group to one Hubitat dimmer. Leave unused rows blank.'
                input "light${index}Name", 'text', title: 'Name', required: false
                input "light${index}PanelSwitch", 'capability.switch', title: 'Panel switch event/command device', multiple: false, required: false
                input "light${index}PanelLevelEvent", 'capability.switch', title: 'Panel slider event device (raw JSON)', multiple: false, required: false
                input "light${index}PanelLevelCommand", 'capability.switchLevel', title: 'Panel slider command device', multiple: false, required: false
                input "light${index}LevelTextObject", 'text', title: 'Panel level label object id', required: false
                input "light${index}LevelTextDevice", 'capability.notification', title: 'Panel level text device (optional)', multiple: false, required: false
                input "light${index}Target", 'capability.switchLevel', title: 'Hubitat light/dimmer to control', multiple: false, required: false
            }
        }

        section('Timer mapping') {
            input 'timerName', 'text', title: 'Timer name', defaultValue: 'Underfloor Heating', required: false
            input 'timerPanelButton', 'capability.pushableButton', title: 'Panel timer button (preferred)', multiple: false, required: false
            input 'timerPanelSwitch', 'capability.switch', title: 'Panel timer switch/button fallback', multiple: false, required: false
            input 'timerTargetSwitch', 'capability.switch', title: 'Hubitat switch to keep on while timer is active', multiple: false, required: false
            input 'manageUfhVirtualSwitch', 'bool', title: 'Create and use a safe virtual timer switch when no target is selected', defaultValue: true, required: true
            input 'timerIncrementMinutes', 'number', title: 'Timer increment minutes', defaultValue: 1, required: true
            input 'timerMaxMinutes', 'number', title: 'Timer maximum minutes', defaultValue: 3, required: true
            input 'timerTextObject', 'text', title: 'Panel timer button text object id', defaultValue: 'p1b21', required: false
            input 'timerStateTextObject', 'text', title: 'Panel timer state text object id', defaultValue: 'p1b13', required: false
            input 'timerTextDevice', 'capability.notification', title: 'Panel timer text device (optional)', multiple: false, required: false
            input 'timerStateTextDevice', 'capability.notification', title: 'Panel timer state text device (optional)', multiple: false, required: false
        }

        section('Options') {
            input 'debugLogging', 'bool', title: 'Enable debug logging', defaultValue: false, required: false
            input 'refreshBindings', 'button', title: 'Refresh subscriptions and child devices'
        }
    }
}

String panelPageTitle() {
    String label = settingValue('panelLabel')
    "OpenHASP Panel${label ? " - ${label}" : ''}"
}

void installed() {
    initialize()
}

void updated() {
    unsubscribe()
    unschedule()
    initialize()
}

void appButtonHandler(String buttonName) {
    if (buttonName == 'refreshBindings') {
        updated()
    }
}

void initialize() {
    if (settingEnabled(settingValue('manageLightingControls'), true)) {
        lightMappings().each { Map mapping -> managedLightingControl(mapping) }
    }
    if (settingEnabled(settingValue('manageTextLabels'), true)) {
        lightMappings().each { Map mapping -> managedLevelTextDevice(mapping, true) }
        managedTimerTextDevice('timer', true)
        managedTimerTextDevice('state', true)
    }
    if (settingEnabled(settingValue('manageUfhVirtualSwitch'), true)) {
        managedTimerSwitch()
    }
    subscribePanelControls()
    subscribeTargets()
    syncAllTargetsToPanel()
    updateTimerOutputs()
    if (remainingTimerSeconds() > 0) {
        runIn(1, 'timerTick')
    }
}

void subscribePanelControls() {
    lightMappings().each { Map mapping ->
        if (mapping.panelSwitch) subscribe(mapping.panelSwitch, 'switch', lightPanelSwitchHandler)
        if (mapping.panelLevelEvent) subscribe(mapping.panelLevelEvent, 'switch', lightPanelLevelEventHandler)
    }
    def button = settingValue('timerPanelButton')
    def timerSwitch = settingValue('timerPanelSwitch')
    if (button) subscribe(button, 'pushed', timerPanelButtonHandler)
    if (timerSwitch) subscribe(timerSwitch, 'switch', timerPanelSwitchHandler)
}

void subscribeTargets() {
    lightMappings().each { Map mapping ->
        if (mapping.target) {
            subscribe(mapping.target, 'switch', lightTargetSwitchHandler)
            subscribe(mapping.target, 'level', lightTargetLevelHandler)
        }
        def control = activeLightingControl(mapping)
        if (control) {
            subscribe(control, 'switch', lightControlSwitchHandler)
            subscribe(control, 'level', lightControlLevelHandler)
        }
    }
    def timer = activeTimerTarget()
    if (timer) {
        subscribe(timer, 'switch', timerTargetSwitchHandler)
    }
}

void lightPanelSwitchHandler(evt) {
    Map mapping = lightMappingForDevice(evt.deviceId, 'panelSwitch')
    if (mapping) {
        String switchValue = normalizeSwitchValue(evt.value)
        if (suppressPanelSwitchEcho(mapping, switchValue)) {
            if (debugLogging) log.debug "Ignoring mirrored ${mapping.name} panel switch ${switchValue}"
            return
        }
        recordPendingTargetSwitch(mapping, switchValue, 'panel')
        syncLightingControl(mapping, switchValue, currentTargetLevel(mapping))
        commandSwitch(mapping.target, switchValue)
    }
}

void lightPanelLevelEventHandler(evt) {
    Map mapping = lightMappingForDevice(evt.deviceId, 'panelLevelEvent')
    if (mapping) {
        int level = normalizeLevelValue(evt.value, 100)
        recordPendingTargetLevel(mapping, level, 'panel')
        syncLightingControl(mapping, 'on', level)
        commandLevel(mapping.target, level)
    }
}

void lightControlSwitchHandler(evt) {
    Map mapping = lightMappingForControl(evt.deviceId)
    if (mapping) {
        String switchValue = normalizeSwitchValue(evt.value)
        if (suppressLightingControlSwitchEcho(mapping, switchValue)) {
            if (debugLogging) log.debug "Ignoring synced ${mapping.name} control switch ${switchValue}"
            return
        }
        recordPendingTargetSwitch(mapping, switchValue, 'hubitat')
        commandSwitch(mapping.target, switchValue)
    }
}

void lightControlLevelHandler(evt) {
    Map mapping = lightMappingForControl(evt.deviceId)
    if (mapping) {
        int level = normalizeLevelValue(evt.value, 100)
        if (suppressLightingControlLevelEcho(mapping, level)) {
            if (debugLogging) log.debug "Ignoring synced ${mapping.name} control level ${level}"
            return
        }
        recordPendingTargetLevel(mapping, level, 'hubitat')
        commandLevel(mapping.target, level)
    }
}

void lightTargetSwitchHandler(evt) {
    Map mapping = lightMappingForDevice(evt.deviceId, 'target')
    if (!mapping) return
    String switchValue = normalizeSwitchValue(evt.value)
    String pendingStatus = pendingTargetSwitchStatus(mapping, switchValue)
    if (pendingStatus == 'stale') {
        if (debugLogging) log.debug "Ignoring stale ${mapping.name} switch report ${switchValue}; waiting for pending switch"
        return
    }
    if (pendingStatus != 'matched-panel') {
        commandPanelSwitch(mapping, switchValue)
    }
    int level = currentTargetLevel(mapping)
    sendLightLevelText(mapping, level)
    syncLightingControl(mapping, switchValue, level)
}

void lightTargetLevelHandler(evt) {
    Map mapping = lightMappingForDevice(evt.deviceId, 'target')
    if (!mapping) return
    int level = safeInt(evt.value, 100)
    String pendingStatus = pendingTargetLevelStatus(mapping, level)
    if (pendingStatus == 'stale') {
        if (debugLogging) log.debug "Ignoring stale ${mapping.name} level report ${level}; waiting for pending level"
        return
    }
    if (pendingStatus != 'matched-panel') {
        commandLevelOnly(mapping.panelLevelCommand, level)
    }
    sendLightLevelText(mapping, level)
    syncLightingControl(mapping, mapping.target?.currentSwitch ?: 'on', level)
}

void timerPanelButtonHandler(evt) {
    if (suppressTimerInputBounce(evt)) {
        if (debugLogging) log.debug 'Ignoring duplicate timer button event'
        return
    }
    addTimerIncrement()
}

void timerPanelSwitchHandler(evt) {
    if (!timerSwitchEventIsAction(evt?.value)) {
        if (debugLogging) log.debug "Ignoring non-action timer event ${evt?.value}"
        return
    }
    if (suppressTimerInputBounce(evt)) {
        if (debugLogging) log.debug 'Ignoring duplicate timer switch event'
        return
    }
    addTimerIncrement()
}

void timerTargetSwitchHandler(evt) {
    sendTextCommand(activeTimerStateTextDevice(), evt.value == 'on' ? 'ON' : 'OFF')
}

void syncAllTargetsToPanel() {
    lightMappings().each { Map mapping ->
        if (!mapping.target) return
        lightTargetSwitchHandler([deviceId: mapping.target.id, value: mapping.target.currentSwitch ?: 'off'])
        lightTargetLevelHandler([deviceId: mapping.target.id, value: mapping.target.currentLevel ?: 100])
    }
    def timer = activeTimerTarget()
    if (timer) {
        timerTargetSwitchHandler([value: timer.currentSwitch ?: 'off'])
    }
}

void addTimerIncrement() {
    long nowSeconds = epochSeconds()
    int incrementSeconds = Math.max(1, safeInt(settingValue('timerIncrementMinutes'), 1)) * 60
    int maxSeconds = Math.max(incrementSeconds, safeInt(settingValue('timerMaxMinutes'), 3) * 60)
    state.timerDeadlineEpochSeconds = addTimerSeconds(nowSeconds, state.timerDeadlineEpochSeconds as Long, incrementSeconds, maxSeconds)
    commandSwitch(activeTimerTarget(), 'on')
    updateTimerOutputs()
    runIn(1, 'timerTick')
}

boolean timerSwitchEventIsAction(Object value) {
    Map event = openHaspEventPayload(value)
    if (!event.containsKey('event')) {
        return true
    }
    String name = "${event.event ?: ''}".trim().toLowerCase()
    name in ['up', 'changed', 'short', 'release', 'released']
}

boolean suppressTimerInputBounce(evt) {
    long nowMs = now()
    long debounceMs = 500L
    long lastAt = safeLong(state.lastTimerInputAtMs, 0L)
    if (lastAt && nowMs - lastAt < debounceMs) {
        return true
    }
    state.lastTimerInputAtMs = nowMs
    false
}

void timerTick() {
    long remaining = remainingTimerSeconds()
    if (remaining <= 0) {
        state.remove('timerDeadlineEpochSeconds')
        commandSwitch(activeTimerTarget(), 'off')
        updateTimerOutputs()
        return
    }
    updateTimerOutputs()
    runIn(1, 'timerTick')
}

void updateTimerOutputs() {
    long remaining = remainingTimerSeconds()
    int incrementSeconds = Math.max(1, safeInt(settingValue('timerIncrementMinutes'), 1)) * 60
    sendTextCommand(activeTimerTextDevice(), timerButtonText(remaining, incrementSeconds))
    def timer = activeTimerTarget()
    String stateText = remaining > 0 || timer?.currentSwitch == 'on' ? 'ON' : 'OFF'
    sendTextCommand(activeTimerStateTextDevice(), stateText)
}

long remainingTimerSeconds() {
    remainingSeconds(epochSeconds(), state.timerDeadlineEpochSeconds as Long)
}

List<Map> lightMappings() {
    (1..4).collect { Integer index ->
        [
            index: index,
            name: settingValue("light${index}Name") ?: "Light ${index}",
            panelSwitch: settingValue("light${index}PanelSwitch"),
            panelLevelEvent: settingValue("light${index}PanelLevelEvent"),
            panelLevelCommand: settingValue("light${index}PanelLevelCommand"),
            levelTextObject: settingValue("light${index}LevelTextObject") ?: defaultLevelTextObject(index),
            levelTextDevice: settingValue("light${index}LevelTextDevice"),
            target: settingValue("light${index}Target")
        ]
    }.findAll { Map mapping ->
        mapping.target || mapping.panelSwitch || mapping.panelLevelEvent || mapping.panelLevelCommand
    }
}

Object settingValue(String name) {
    def value = settings[name]
    if (hasSettingValue(value)) {
        return value
    }
    try {
        value = this."${name}"
        if (hasSettingValue(value)) {
            return value
        }
    } catch (MissingPropertyException ignored) {
    }
    try {
        return parent?.legacySetting(name)
    } catch (MissingMethodException ignored) {
        return null
    }
}

boolean hasSettingValue(Object value) {
    value != null && (!(value instanceof CharSequence) || "${value}".trim())
}

Map lightMappingForDevice(Object deviceId, String key) {
    lightMappings().find { Map mapping -> sameDevice(mapping[key], deviceId) }
}

Map lightMappingForControl(Object deviceId) {
    lightMappings().find { Map mapping -> sameDevice(activeLightingControl(mapping), deviceId) }
}

boolean sameDevice(device, Object deviceId) {
    device && "${device.id}" == "${deviceId}"
}

void commandPanelSwitch(Map mapping, String switchValue) {
    if (!mapping.panelSwitch) return
    recordPanelSwitchEcho(mapping, switchValue)
    commandSwitch(mapping.panelSwitch, switchValue)
}

void recordPendingTargetSwitch(Map mapping, String switchValue, String source) {
    Map pending = (state.pendingTargetSwitches ?: [:]) as Map
    pending["${mapping.index}"] = [
        value: normalizeSwitchValue(switchValue),
        source: source,
        until: now() + 8000L
    ]
    state.pendingTargetSwitches = pending
}

String pendingTargetSwitchStatus(Map mapping, String reportedValue) {
    Map pending = (state.pendingTargetSwitches ?: [:]) as Map
    Map entry = pending["${mapping.index}"] as Map
    if (!entry) {
        return 'none'
    }
    if (now() > safeLong(entry.until, 0L)) {
        pending.remove("${mapping.index}")
        state.pendingTargetSwitches = pending
        return 'none'
    }
    String desiredValue = normalizeSwitchValue(entry.value)
    if (normalizeSwitchValue(reportedValue) == desiredValue) {
        String source = "${entry.source ?: 'hubitat'}"
        pending.remove("${mapping.index}")
        state.pendingTargetSwitches = pending
        return source == 'panel' ? 'matched-panel' : 'matched-hubitat'
    }
    'stale'
}

void recordPanelSwitchEcho(Map mapping, String switchValue) {
    Map pending = (state.pendingPanelSwitchEchoes ?: [:]) as Map
    pending["${mapping.index}"] = [
        value: normalizeSwitchValue(switchValue),
        until: now() + 3000L
    ]
    state.pendingPanelSwitchEchoes = pending
}

boolean suppressPanelSwitchEcho(Map mapping, String reportedValue) {
    Map pending = (state.pendingPanelSwitchEchoes ?: [:]) as Map
    Map entry = pending["${mapping.index}"] as Map
    if (!entry) {
        return false
    }
    if (now() > safeLong(entry.until, 0L)) {
        pending.remove("${mapping.index}")
        state.pendingPanelSwitchEchoes = pending
        return false
    }
    if (normalizeSwitchValue(reportedValue) == normalizeSwitchValue(entry.value)) {
        pending.remove("${mapping.index}")
        state.pendingPanelSwitchEchoes = pending
        return true
    }
    false
}

void recordLightingControlEcho(Map mapping, String switchValue, int levelValue) {
    Map switchPending = (state.pendingControlSwitchEchoes ?: [:]) as Map
    switchPending["${mapping.index}"] = [
        value: normalizeSwitchValue(switchValue),
        until: now() + 3000L
    ]
    state.pendingControlSwitchEchoes = switchPending

    Map levelPending = (state.pendingControlLevelEchoes ?: [:]) as Map
    levelPending["${mapping.index}"] = [
        level: Math.max(1, Math.min(100, levelValue)),
        until: now() + 3000L
    ]
    state.pendingControlLevelEchoes = levelPending
}

boolean suppressLightingControlSwitchEcho(Map mapping, String reportedValue) {
    Map pending = (state.pendingControlSwitchEchoes ?: [:]) as Map
    Map entry = pending["${mapping.index}"] as Map
    if (!entry) {
        return false
    }
    if (now() > safeLong(entry.until, 0L)) {
        pending.remove("${mapping.index}")
        state.pendingControlSwitchEchoes = pending
        return false
    }
    if (normalizeSwitchValue(reportedValue) == normalizeSwitchValue(entry.value)) {
        pending.remove("${mapping.index}")
        state.pendingControlSwitchEchoes = pending
        return true
    }
    false
}

boolean suppressLightingControlLevelEcho(Map mapping, int reportedLevel) {
    Map pending = (state.pendingControlLevelEchoes ?: [:]) as Map
    Map entry = pending["${mapping.index}"] as Map
    if (!entry) {
        return false
    }
    if (now() > safeLong(entry.until, 0L)) {
        pending.remove("${mapping.index}")
        state.pendingControlLevelEchoes = pending
        return false
    }
    int desiredLevel = safeInt(entry.level, reportedLevel)
    if (Math.abs(reportedLevel - desiredLevel) <= 1) {
        pending.remove("${mapping.index}")
        state.pendingControlLevelEchoes = pending
        return true
    }
    false
}

void recordPendingTargetLevel(Map mapping, int level, String source) {
    Map pending = (state.pendingTargetLevels ?: [:]) as Map
    pending["${mapping.index}"] = [
        level: Math.max(1, Math.min(100, level)),
        source: source,
        until: now() + 8000L
    ]
    state.pendingTargetLevels = pending
}

String pendingTargetLevelStatus(Map mapping, int reportedLevel) {
    Map pending = (state.pendingTargetLevels ?: [:]) as Map
    Map entry = pending["${mapping.index}"] as Map
    if (!entry) {
        return 'none'
    }
    if (now() > safeLong(entry.until, 0L)) {
        pending.remove("${mapping.index}")
        state.pendingTargetLevels = pending
        return 'none'
    }
    int desiredLevel = safeInt(entry.level, reportedLevel)
    if (Math.abs(reportedLevel - desiredLevel) <= 1) {
        String source = "${entry.source ?: 'hubitat'}"
        pending.remove("${mapping.index}")
        state.pendingTargetLevels = pending
        return source == 'panel' ? 'matched-panel' : 'matched-hubitat'
    }
    'stale'
}

def activeLightingControl(Map mapping) {
    settingEnabled(settingValue('manageLightingControls'), true) ? managedLightingControl(mapping) : null
}

def activeLevelTextDevice(Map mapping) {
    mapping.levelTextDevice ?: (settingEnabled(settingValue('manageTextLabels'), true) ? managedLevelTextDevice(mapping, false) : null)
}

def activeTimerTextDevice() {
    settingValue('timerTextDevice') ?: (settingEnabled(settingValue('manageTextLabels'), true) ? managedTimerTextDevice('timer', false) : null)
}

def activeTimerStateTextDevice() {
    settingValue('timerStateTextDevice') ?: (settingEnabled(settingValue('manageTextLabels'), true) ? managedTimerTextDevice('state', false) : null)
}

def managedLevelTextDevice(Map mapping, boolean configure = false) {
    if (!mapping.levelTextObject) return null
    String plate = settingValue('plateName') ?: 'panel'
    String labelBase = settingValue('panelLabel') ?: plate ?: 'OpenHASP'
    String dni = "openhasp-${plate}-light-${mapping.index}-level-label"
    String label = "${labelBase} ${mapping.name} Level Label"
    def child = getChildDevice(dni)
    boolean created = false
    if (!child) {
        child = addChildDevice('nichuk', 'OpenHASP Text Label', dni, [
            name: label,
            label: label,
            isComponent: false
        ])
        created = true
    } else if (child.displayName != label) {
        try {
            child.setLabel(label)
        } catch (Exception ignored) {
        }
    }
    if (configure || created) {
        try {
            child.configureFromApp(
                "${settingValue('mqttBrokerUri') ?: ''}",
                "${settingValue('mqttUsername') ?: ''}",
                "${settingValue('mqttPassword') ?: ''}",
                "hasp/${plate}/command/${mapping.levelTextObject}.text",
                settingEnabled(settingValue('mqttRetainTextLabels'), false)
            )
        } catch (Exception e) {
            log.warn "Could not configure ${label}: ${e.message}"
        }
    }
    child
}

def managedLightingControl(Map mapping) {
    String plate = settingValue('plateName') ?: 'panel'
    String labelBase = settingValue('panelLabel') ?: plate ?: 'OpenHASP'
    String dni = "openhasp-${plate}-light-${mapping.index}-control"
    String label = "${labelBase} ${mapping.name} Control"
    def child = getChildDevice(dni)
    if (!child) {
        child = addChildDevice('hubitat', 'Virtual Dimmer', dni, [
            name: label,
            label: label,
            isComponent: false
        ])
    } else if (child.displayName != label) {
        try {
            child.setLabel(label)
        } catch (Exception ignored) {
        }
    }
    return child
}

void syncLightingControl(Map mapping, Object switchValue, Object levelValue) {
    def child = activeLightingControl(mapping)
    if (!child) return
    String normalizedSwitch = normalizeSwitchValue(switchValue)
    int normalizedLevel = Math.max(1, Math.min(100, safeInt(levelValue, 100)))
    recordLightingControlEcho(mapping, normalizedSwitch, normalizedLevel)
    child.sendEvent(name: 'switch', value: normalizedSwitch, isStateChange: false)
    child.sendEvent(name: 'level', value: normalizedLevel, unit: '%', isStateChange: false)
}

void sendLightLevelText(Map mapping, int level) {
    sendTextCommand(activeLevelTextDevice(mapping), "${Math.max(1, Math.min(100, level))}")
}

int currentTargetLevel(Map mapping) {
    Math.max(1, Math.min(100, safeInt(mapping.target?.currentLevel, 100)))
}

String defaultLevelTextObject(Integer index) {
    Map defaults = [
        1: 'p1b44',
        2: 'p1b54'
    ]
    defaults[index]
}

def managedTimerTextDevice(String kind, boolean configure = false) {
    String objectId = timerTextObjectId(kind)
    if (!objectId) return null
    String plate = settingValue('plateName') ?: 'panel'
    String labelBase = settingValue('panelLabel') ?: plate ?: 'OpenHASP'
    String suffix = kind == 'state' ? 'UFH State Label' : 'UFH Timer Label'
    String dni = "openhasp-${plate}-timer-${kind}-label"
    String label = "${labelBase} ${suffix}"
    def child = getChildDevice(dni)
    boolean created = false
    if (!child) {
        child = addChildDevice('nichuk', 'OpenHASP Text Label', dni, [
            name: label,
            label: label,
            isComponent: false
        ])
        created = true
    } else if (child.displayName != label) {
        try {
            child.setLabel(label)
        } catch (Exception ignored) {
        }
    }
    if (configure || created) {
        try {
            child.configureFromApp(
                "${settingValue('mqttBrokerUri') ?: ''}",
                "${settingValue('mqttUsername') ?: ''}",
                "${settingValue('mqttPassword') ?: ''}",
                "hasp/${plate}/command/${objectId}.text",
                settingEnabled(settingValue('mqttRetainTextLabels'), false)
            )
        } catch (Exception e) {
            log.warn "Could not configure ${label}: ${e.message}"
        }
    }
    child
}

String timerTextObjectId(String kind) {
    String settingName = kind == 'state' ? 'timerStateTextObject' : 'timerTextObject'
    def value = settings[settingName]
    hasSettingValue(value) ? "${value}" : (kind == 'state' ? 'p1b13' : 'p1b21')
}

def activeTimerTarget() {
    settingValue('timerTargetSwitch') ?: (settingEnabled(settingValue('manageUfhVirtualSwitch'), true) ? managedTimerSwitch() : null)
}

def managedTimerSwitch() {
    String plate = settingValue('plateName') ?: 'panel'
    String labelBase = settingValue('panelLabel') ?: plate ?: 'OpenHASP'
    String dni = "openhasp-${plate}-timer"
    String label = "${labelBase} ${settingValue('timerName') ?: 'Timer'}"
    def child = getChildDevice(dni)
    if (!child) {
        child = addChildDevice('hubitat', 'Virtual Switch', dni, [
            name: label,
            label: label,
            isComponent: false
        ])
    } else if (child.displayName != label) {
        try {
            child.setLabel(label)
        } catch (Exception ignored) {
        }
    }
    return child
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

long safeLong(Object value, long fallback = 0L) {
    try {
        return "${value}".toBigDecimal().longValue()
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
    if (value == null || !(value instanceof CharSequence)) {
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

Map openHaspEventPayload(Object value) {
    if (value == null || !(value instanceof CharSequence)) {
        return [:]
    }
    String text = value.toString().trim()
    if (!text.startsWith('{')) {
        return [:]
    }
    try {
        def parsed = new JsonSlurper().parseText(text)
        return parsed instanceof Map ? parsed as Map : [:]
    } catch (ignored) {
        return [:]
    }
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
