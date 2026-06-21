/*
 * Copyright 2026 NichUK
 *
 * Licensed under the Apache License, Version 2.0.
 */

import groovy.json.JsonOutput
import groovy.json.JsonSlurper

metadata {
    definition(
        name: 'OpenHASP Panel',
        namespace: 'nichuk',
        author: 'NichUK',
        importUrl: 'https://raw.githubusercontent.com/NichUK/Hubitat-OpenHASP/main/drivers/openhasp-panel.groovy'
    ) {
        capability 'Initialize'
        capability 'Refresh'
        capability 'Actuator'

        command 'publishCommand', [[name: 'command*', type: 'STRING'], [name: 'payload*', type: 'STRING']]
        command 'publishObjectValue', [[name: 'objectId*', type: 'STRING'], [name: 'value*', type: 'STRING']]
        command 'publishObjectText', [[name: 'objectId*', type: 'STRING'], [name: 'text*', type: 'STRING']]
        command 'configureFromManager', [
            [name: 'mqttHost*', type: 'STRING'],
            [name: 'mqttPort*', type: 'NUMBER'],
            [name: 'mqttUsername', type: 'STRING'],
            [name: 'mqttPassword', type: 'STRING'],
            [name: 'plateName*', type: 'STRING'],
            [name: 'idleSeconds*', type: 'NUMBER'],
            [name: 'timerIncrementMinutes*', type: 'NUMBER'],
            [name: 'timerMaxMinutes*', type: 'NUMBER'],
            [name: 'debugLogging', type: 'STRING']
        ]
        command 'clearManagedControls'
        command 'configureManagedControl', [
            [name: 'controlId*', type: 'STRING'],
            [name: 'kind*', type: 'STRING'],
            [name: 'displayName*', type: 'STRING'],
            [name: 'role', type: 'STRING'],
            [name: 'labelObject', type: 'STRING']
        ]
        command 'applyManagedConfiguration'
        command 'loadDefaultBathroomControls'
        command 'rebuildChildDevices'
    }

    preferences {
        input name: 'mqttHost', type: 'text', title: 'MQTT broker host', required: true
        input name: 'mqttPort', type: 'number', title: 'MQTT broker port', defaultValue: 1883, required: true
        input name: 'mqttUsername', type: 'text', title: 'MQTT username', required: false
        input name: 'mqttPassword', type: 'password', title: 'MQTT password', required: false
        input name: 'plateName', type: 'text', title: 'OpenHASP plate name', defaultValue: 'bathroom_panel', required: true
        input name: 'idleSeconds', type: 'number', title: 'Screen-off idle seconds', defaultValue: 60, required: true
        input name: 'backlightBrightness', type: 'number', title: 'Wake brightness', defaultValue: 255, required: true
        input name: 'timerIncrementMinutes', type: 'number', title: 'Timer increment minutes', defaultValue: 1, required: true
        input name: 'timerMaxMinutes', type: 'number', title: 'Timer maximum minutes', defaultValue: 3, required: true
        input name: 'controlConfigJson', type: 'textarea', title: 'Control configuration JSON', defaultValue: defaultControlConfigJson(), required: true
        input name: 'debugLogging', type: 'bool', title: 'Enable debug logging', defaultValue: false
    }
}

void installed() {
    initialize()
}

void updated() {
    unschedule()
    configureChildren()
    initialize()
}

void initialize() {
    configureChildren()
    connectMqtt()
    applyIdleConfig()
    publishCommand('run', '/pages.jsonl')
    publishCommand('page', '1')
    refresh()
}

void configureFromManager(String host, Object port, String username, String password, String plate, Object idle, Object increment, Object max, Object debug) {
    try {
        state.remove('managerConfig')
    } catch (ignored) {
        state.managerConfig = null
    }
    device.updateSetting('mqttHost', [value: host ?: '', type: 'text'])
    device.updateSetting('mqttPort', [value: safeInt(port, 1883), type: 'number'])
    device.updateSetting('mqttUsername', [value: username ?: '', type: 'text'])
    device.updateSetting('mqttPassword', [value: password ?: '', type: 'password'])
    device.updateSetting('plateName', [value: plate ?: 'bathroom_panel', type: 'text'])
    device.updateSetting('idleSeconds', [value: safeInt(idle, 60), type: 'number'])
    device.updateSetting('timerIncrementMinutes', [value: safeInt(increment, 1), type: 'number'])
    device.updateSetting('timerMaxMinutes', [value: safeInt(max, 3), type: 'number'])
    device.updateSetting('debugLogging', [value: truthy(debug), type: 'bool'])
}

void clearManagedControls() {
    state.managerControls = [:]
}

void configureManagedControl(String controlId, String kind, String displayName, String role = '', String labelObject = '') {
    Map controls = state.managerControls instanceof Map ? state.managerControls as Map : [:]
    controls[controlId] = [
        kind       : kind ?: 'switch',
        name       : displayName ?: controlId,
        role       : role ?: '',
        labelObject: labelObject ?: ''
    ]
    state.managerControls = controls
}

void applyManagedConfiguration() {
    initialize()
}

void refresh() {
    updateTimerDisplay()
}

void connectMqtt() {
    String host = configuredValue('mqttHost', '') as String
    if (!host) {
        log.warn 'MQTT broker host is not configured'
        return
    }
    try {
        interfaces.mqtt.disconnect()
    } catch (ignored) {
    }
    String clientId = "hubitat-openhasp-${device.id ?: device.deviceNetworkId}".replaceAll('[^A-Za-z0-9_-]', '_')
    String uri = "tcp://${host}:${configuredValue('mqttPort', 1883)}"
    try {
        if (configuredValue('mqttUsername', '')) {
            interfaces.mqtt.connect(uri, clientId, configuredValue('mqttUsername', '') as String, configuredValue('mqttPassword', '') as String)
        } else {
            interfaces.mqtt.connect(uri, clientId)
        }
        runIn(2, 'subscribeMqttTopics')
        log.info "Connecting to MQTT for OpenHASP plate ${resolvedPlateName()}"
    } catch (Exception e) {
        log.error "MQTT connect failed: ${e.message}"
        runIn(30, 'initialize')
    }
}

void subscribeMqttTopics() {
    try {
        interfaces.mqtt.subscribe("hasp/${resolvedPlateName()}/state/#")
        interfaces.mqtt.subscribe("hasp/${resolvedPlateName()}/state/idle")
        controlConfig().keySet().each { controlId ->
            interfaces.mqtt.subscribe("hasp/${resolvedPlateName()}/state/${controlId}")
        }
        interfaces.mqtt.subscribe("home/openhasp/${resolvedPlateName()}/ufh/#")
        log.info "Subscribed to MQTT topics for OpenHASP plate ${resolvedPlateName()}"
    } catch (Exception e) {
        log.warn "MQTT subscribe failed: ${e.message}"
        runIn(10, 'subscribeMqttTopics')
    }
}

void mqttClientStatus(String status) {
    if (status?.toLowerCase()?.contains('error')) {
        log.warn "MQTT status: ${status}"
        runIn(30, 'initialize')
    } else if (status?.toLowerCase()?.contains('succeed') || status?.toLowerCase()?.contains('connect')) {
        log.info "MQTT status: ${status}"
        subscribeMqttTopics()
    } else if (debugLoggingEnabled()) {
        log.debug "MQTT status: ${status}"
    }
}

void parse(String message) {
    Map parsed
    try {
        parsed = interfaces.mqtt.parseMessage(message)
    } catch (Exception e) {
        log.warn "Unable to parse MQTT message: ${e.message}"
        return
    }
    handleMqtt(parsed.topic as String, parsed.payload as String)
}

void handleMqtt(String topic, String payload) {
    if (debugLoggingEnabled()) {
        log.debug "MQTT ${topic}: ${payload}"
    }
    String statePrefix = "hasp/${resolvedPlateName()}/state/"
    if (!topic?.startsWith(statePrefix)) {
        return
    }
    String controlId = topic.substring(statePrefix.length())
    if (controlId == 'idle') {
        handleIdle(payload)
        return
    }
    Map event = parseJson(payload)
    if (!isActionEvent(event)) {
        return
    }
    Map control = controlConfig()[controlId] as Map
    if (!control) {
        log.debug "No configured OpenHASP control for ${controlId}"
        return
    }
    String kind = "${control.kind ?: 'switch'}"
    def child = childFor(controlId, control)
    if (!child) {
        return
    }
    if (kind == 'dimmer' || kind == 'setpoint') {
        int level = clamp(safeInt(event.val, kind == 'setpoint' ? 21 : 100), kind == 'setpoint' ? 5 : 1, kind == 'setpoint' ? 40 : 100)
        child.sendEvent(name: 'level', value: level, unit: kind == 'setpoint' ? 'C' : '%', isStateChange: true)
        if (kind == 'dimmer') {
            child.sendEvent(name: 'switch', value: 'on', isStateChange: true)
            publishObjectText(control.labelObject as String, "${level}")
            parent?.handleOpenHaspControlEvent(device, controlId, kind, level)
        } else {
            publishObjectText(control.labelObject as String, "${level} C")
            parent?.handleOpenHaspControlEvent(device, controlId, kind, level)
        }
    } else if (kind == 'button') {
        child.sendEvent(name: 'pushed', value: 1, isStateChange: true)
        if ("${control.role ?: ''}" == 'timerButton') {
            addTimerSeconds()
        } else {
            parent?.handleOpenHaspControlEvent(device, controlId, kind, 'pushed')
        }
    } else {
        String value = truthy(event.val) ? 'on' : 'off'
        child.sendEvent(name: 'switch', value: value, isStateChange: true)
        parent?.handleOpenHaspControlEvent(device, controlId, kind, value)
    }
}

void handleIdle(String payload) {
    String state = "${payload ?: ''}".replace('"', '').trim().toLowerCase()
    if (state == 'long') {
        publishCommand('backlight', 'off')
    } else if (state == 'off') {
        publishCommand('backlight', JsonOutput.toJson([state: 'on', brightness: clamp(safeInt(configuredValue('backlightBrightness', 255), 255), 1, 255)]))
    }
}

void applyIdleConfig() {
    publishCommand('idle', 'off')
    publishCommand('config/gui', JsonOutput.toJson([idle1: 0, idle2: safeInt(configuredValue('idleSeconds', 60), 60)]))
}

void addTimerSeconds() {
    long now = nowSeconds()
    long remaining = Math.max(0L, ((state.timerDeadlineEpochSeconds ?: 0L) as long) - now)
    long increment = safeInt(configuredValue('timerIncrementMinutes', 1), 1) * 60L
    long maxSeconds = safeInt(configuredValue('timerMaxMinutes', 3), 3) * 60L
    state.timerDeadlineEpochSeconds = now + Math.min(maxSeconds, remaining + increment)
    setTimerChild('on')
    updateTimerDisplay()
    runIn(1, 'timerTick')
}

void timerTick() {
    long remaining = remainingTimerSeconds()
    if (remaining <= 0) {
        state.timerDeadlineEpochSeconds = null
        setTimerChild('off')
    }
    updateTimerDisplay()
    if (remaining > 0) {
        runIn(1, 'timerTick')
    }
}

void updateTimerDisplay() {
    long remaining = remainingTimerSeconds()
    Map timerControl = controlConfig().find { k, v -> "${v.role ?: ''}" == 'timerButton' }?.value as Map
    String timerId = controlConfig().find { k, v -> "${v.role ?: ''}" == 'timerButton' }?.key
    if (timerId) {
        publishObjectText(timerId, timerButtonText(remaining))
    }
    if (timerControl?.labelObject) {
        publishObjectText(timerControl.labelObject as String, remaining > 0 ? 'ON' : 'OFF')
    }
}

long remainingTimerSeconds() {
    long deadline = (state.timerDeadlineEpochSeconds ?: 0L) as long
    Math.max(0L, deadline - nowSeconds())
}

void setTimerChild(String switchValue) {
    Map entry = controlConfig().find { k, v -> "${v.role ?: ''}" == 'timerButton' }
    if (!entry) {
        return
    }
    def child = childFor(entry.key as String, entry.value as Map)
    child?.sendEvent(name: 'switch', value: switchValue, isStateChange: true)
    parent?.handleOpenHaspControlEvent(device, entry.key as String, 'timer', switchValue)
}

void publishCommand(String command, String payload) {
    if (!command) {
        return
    }
    publishMqtt("hasp/${resolvedPlateName()}/command/${command}", payload ?: '')
}

void publishObjectValue(String objectId, String value) {
    if (objectId) {
        publishCommand("${objectId}.val", "${value}")
    }
}

void publishObjectText(String objectId, String text) {
    if (objectId) {
        publishCommand("${objectId}.text", text ?: '')
    }
}

void publishMqtt(String topic, String payload) {
    try {
        interfaces.mqtt.publish(topic, payload ?: '')
        if (debugLoggingEnabled()) {
            log.debug "Published ${topic}: ${payload}"
        }
    } catch (Exception e) {
        log.warn "MQTT publish failed for ${topic}: ${e.message}"
    }
}

void childSwitchCommand(String childDni, String switchValue) {
    String controlId = controlIdFromDni(childDni)
    publishObjectValue(controlId, switchValue == 'on' ? '1' : '0')
}

void childLevelCommand(String childDni, Object level) {
    String controlId = controlIdFromDni(childDni)
    int bounded = clamp(safeInt(level, 100), 1, 100)
    publishObjectValue(controlId, "${bounded}")
    Map control = controlConfig()[controlId] as Map
    publishObjectText(control?.labelObject as String, "${bounded}")
}

void childButtonPushed(String childDni) {
    String controlId = controlIdFromDni(childDni)
    Map control = controlConfig()[controlId] as Map
    if ("${control?.role ?: ''}" == 'timerButton') {
        addTimerSeconds()
    }
}

void configureChildren() {
    controlConfig().each { String controlId, Map control ->
        childFor(controlId, control)
    }
}

def childFor(String controlId, Map control) {
    String dni = "${device.deviceNetworkId}-${controlId}"
    def child = getChildDevice(dni)
    if (child) {
        return child
    }
    String kind = "${control.kind ?: 'switch'}"
    String driverName = kind == 'dimmer' || kind == 'setpoint' ? 'OpenHASP Child Dimmer' : kind == 'button' ? 'OpenHASP Timer Button' : 'OpenHASP Child Switch'
    try {
        return addChildDevice('nichuk', driverName, dni, [
            name: "${control.name ?: controlId}",
            label: "${control.name ?: controlId}",
            isComponent: false
        ])
    } catch (Exception e) {
        log.warn "Unable to create child ${controlId}: ${e.message}"
        return null
    }
}

void rebuildChildDevices() {
    getChildDevices()?.each { deleteChildDevice(it.deviceNetworkId) }
    configureChildren()
}

void loadDefaultBathroomControls() {
    device.updateSetting('controlConfigJson', [value: defaultControlConfigJson(), type: 'textarea'])
    configureChildren()
}

Map controlConfig() {
    if (state.managerControls instanceof Map && state.managerControls) {
        return state.managerControls as Map
    }
    try {
        def parsed = new JsonSlurper().parseText(configuredValue('controlConfigJson', defaultControlConfigJson()) as String)
        return parsed instanceof Map ? parsed as Map : [:]
    } catch (Exception e) {
        log.warn "Control configuration JSON is invalid: ${e.message}"
        return [:]
    }
}

Map parseJson(String text) {
    try {
        def parsed = new JsonSlurper().parseText(text ?: '{}')
        return parsed instanceof Map ? parsed as Map : [:]
    } catch (ignored) {
        return [:]
    }
}

boolean isActionEvent(Map event) {
    "${event?.event ?: ''}" in ['up', 'changed']
}

boolean truthy(Object value) {
    if (value instanceof Boolean) return value
    if (value instanceof Number) return value != 0
    "${value}".trim().toLowerCase() in ['1', 'true', 'on', 'yes']
}

int safeInt(Object value, int fallback = 0) {
    try {
        return "${value}".toBigDecimal().intValue()
    } catch (ignored) {
        return fallback
    }
}

int clamp(int value, int min, int max) {
    Math.max(min, Math.min(max, value))
}

Object configuredValue(String name, Object fallback = null) {
    Map managerConfig = state.managerConfig instanceof Map ? state.managerConfig as Map : [:]
    if (managerConfig.containsKey(name) && managerConfig[name] != null && "${managerConfig[name]}" != '') {
        return managerConfig[name]
    }
    def settingValue = settings[name]
    if (settingValue != null && "${settingValue}" != '') {
        return settingValue
    }
    fallback
}

boolean debugLoggingEnabled() {
    truthy(configuredValue('debugLogging', false))
}

long nowSeconds() {
    Math.floor(now() / 1000D) as long
}

String timerButtonText(long remainingSeconds) {
    int increment = safeInt(configuredValue('timerIncrementMinutes', 1), 1)
    if (remainingSeconds <= 0) {
        return "Start ${increment}m"
    }
    long minutes = Math.floor(remainingSeconds / 60D) as long
    long seconds = remainingSeconds % 60
    return "${minutes}:${seconds.toString().padLeft(2, '0')}"
}

String controlIdFromDni(String dni) {
    dni?.tokenize('-')?.last()
}

String resolvedPlateName() {
    configuredValue('plateName', 'bathroom_panel') as String
}

String defaultControlConfigJson() {
    JsonOutput.prettyPrint(JsonOutput.toJson([
        p1b21: [kind: 'button', name: 'Bathroom UFH Timer', role: 'timerButton', labelObject: 'p1b13'],
        p1b22: [kind: 'setpoint', name: 'Bathroom UFH Setpoint', role: 'setpoint', labelObject: 'p1b23'],
        p1b42: [kind: 'switch', name: 'Bathroom Office Main Switch', role: 'officeSwitch'],
        p1b43: [kind: 'dimmer', name: 'Bathroom Office Main Dimmer', role: 'officeDimmer', labelObject: 'p1b44'],
        p1b52: [kind: 'switch', name: 'Bathroom Bedroom Main Switch', role: 'bedroomSwitch'],
        p1b53: [kind: 'dimmer', name: 'Bathroom Bedroom Main Dimmer', role: 'bedroomDimmer', labelObject: 'p1b54']
    ]))
}
