/*
 * Copyright 2026 NichUK
 *
 * Licensed under the Apache License, Version 2.0.
 */

import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import groovy.transform.Field

@Field static final String PASSWORD_MASK = '*********'
@Field static final String TYPE_REGISTRY_EVENT = 'openhasp:typeRegistry'
@Field static final String TYPE_REGISTRY_REQUEST_EVENT = 'openhasp:typeRegistryRequest'
@Field static final String TYPE_REGISTRY_PROTOCOL = 'openhasp.typeRegistry.v1'

definition(
    name: 'OpenHASP Manager',
    namespace: 'nichuk',
    author: 'NichUK',
    description: 'Manages OpenHASP MQTT plates and maps panel widgets to Hubitat devices.',
    category: 'Integrations',
    importUrl: 'https://raw.githubusercontent.com/NichUK/Hubitat-OpenHASP/main/apps/openhasp-manager.groovy',
    iconUrl: '',
    iconX2Url: '',
    singleInstance: true
)

preferences {
    page(name: 'mainPage')
}

def mainPage() {
    dynamicPage(name: 'mainPage', title: 'OpenHASP Manager', install: true, uninstall: true) {
        ensureDefaultPlateState()
        Map mqtt = mqttState()
        Map screen = screenState()
        section('MQTT broker', hideable: true, hidden: false) {
            input mqttSetting('baseTopic'), 'text', title: 'Base topic', defaultValue: mqtt.baseTopic ?: 'hasp', required: true, width: 2
            input mqttSetting('brokerHost'), 'text', title: 'Host', defaultValue: mqtt.brokerHost ?: '10.0.0.65', required: true, width: 3
            input mqttSetting('brokerPort'), 'number', title: 'Port', defaultValue: mqtt.brokerPort ?: 1883, required: true, width: 2
            input mqttSetting('mqttUsername'), 'text', title: 'Username', defaultValue: mqtt.mqttUsername ?: '', required: false, width: 2
            if (hasSettingValue(mqtt.mqttPassword)) {
                input mqttSetting('mqttPasswordDisplay'), 'text', title: 'Password', defaultValue: PASSWORD_MASK, required: false, width: 2
                input mqttSetting('changeMqttPassword'), 'bool', title: 'Change MQTT password', defaultValue: false, required: false, submitOnChange: true, width: 1
                if (settingEnabled(settings[mqttSetting('changeMqttPassword')], false)) {
                    input mqttSetting('mqttPassword'), 'password', title: 'New password', required: false, width: 3
                }
            } else {
                input mqttSetting('mqttPassword'), 'password', title: 'Password', required: false, width: 3
            }
        }
        section('Screen idle/backlight', hideable: true, hidden: false) {
            input screenSetting('idleTopic'), 'text', title: 'Idle state suffix', defaultValue: screen.idleTopic ?: 'state/idle', required: true, width: 3
            input screenSetting('backlightTopic'), 'text', title: 'Backlight command suffix', defaultValue: screen.backlightTopic ?: 'command/backlight', required: true, width: 3
            input screenSetting('guiConfigTopic'), 'text', title: 'GUI config suffix', defaultValue: screen.guiConfigTopic ?: 'config/gui', required: true, width: 3
            input screenSetting('screenIdleSeconds'), 'number', title: 'Idle seconds', defaultValue: screen.screenIdleSeconds ?: 60, required: true, width: 3
            input screenSetting('screenBacklightBrightness'), 'number', title: 'Normal brightness', defaultValue: screen.screenBacklightBrightness ?: 42, required: true, width: 3
            input screenSetting('screenWakeBrightness'), 'number', title: 'Wake brightness', defaultValue: screen.screenWakeBrightness ?: 255, required: true, width: 3
        }
        section('OpenHASP plates') {
            paragraph 'Each plate has its own MQTT connector and mapping rows. The connector uses the shared broker settings above.'
            input 'addPlate', 'button', title: 'Add plate'
        }
        plateIds().eachWithIndex { String plateId, int index ->
            Map plate = plateState(plateId)
            section("${plate.label ?: plate.plateName ?: 'OpenHASP Plate'}", hideable: true, hidden: index > 0) {
                input plateSetting(plateId, 'enabled'), 'bool', title: 'Enabled', defaultValue: plate.enabled != false, required: true, submitOnChange: true
                input plateSetting(plateId, 'label'), 'text', title: 'Plate label', defaultValue: plate.label ?: 'Bathroom Panel', required: true, width: 4
                input plateSetting(plateId, 'plateName'), 'text', title: 'OpenHASP plate name', defaultValue: plate.plateName ?: 'bathroom_panel', required: true, width: 4
                input plateSetting(plateId, 'createVirtualControls'), 'bool', title: 'Create virtual lighting controls for dashboards', defaultValue: true, required: true, width: 4

                paragraph "Connector device: ${connectorDevice(plateId)?.displayName ?: 'created when saved'}"
                paragraph "MQTT topic prefix: ${plateTopicPrefix(effectivePlateForDisplay(plate))}"
                input refreshButtonName(plateId), 'button', title: 'Reconnect / refresh this plate'
                input removePlateButtonName(plateId), 'button', title: 'Remove this plate'

                paragraph '<b>Mapping rows</b>'
                rowIds(plateId).eachWithIndex { String rowId, int rowIndex ->
                    mappingInputs(plateId, rowId, rowIndex + 1)
                }
                input addRowButtonName(plateId), 'button', title: 'Add mapping row'
            }
        }
        section('Options') {
            paragraph "Optional row types discovered: ${registeredExternalTypeOptions().values().join(', ') ?: 'none'}"
            input 'refreshTypeRegistry', 'button', title: 'Refresh optional row types'
            input 'debugLogging', 'bool', title: 'Enable debug logging', defaultValue: false, required: false
        }
    }
}

void mappingInputs(String plateId, String rowId, int rowNumber) {
    Map row = rowState(plateId, rowId)
    String prefix = rowSetting(plateId, rowId, '')
    paragraph "<hr><b>Row ${rowNumber}</b>"
    input "${prefix}enabled", 'bool', title: 'On', defaultValue: row.enabled != false, required: true, submitOnChange: true, width: 1
    input "${prefix}label", 'text', title: 'Label', defaultValue: row.label ?: "Row ${rowNumber}", required: false, width: 2
    input "${prefix}type", 'enum',
        title: 'Type',
        options: rowTypeOptions(row.type ?: 'switch'),
        defaultValue: row.type ?: 'switch',
        required: true,
        submitOnChange: true,
        width: 1
    String type = settingString("${prefix}type", row.type ?: 'switch')
    if (rowAcceptsInput(type)) {
        input "${prefix}incoming", 'text', title: 'Incoming', defaultValue: row.incoming ?: '', required: false, width: 2
    }
    if (rowPublishesOutput(type)) {
        input "${prefix}outgoing", 'text', title: 'Outgoing', defaultValue: row.outgoing ?: '', required: false, width: 2
    }
    if (type == 'dimmer') {
        input "${prefix}targetDimmer", 'capability.switchLevel', title: 'Target', multiple: false, required: false, width: 2
        input "${prefix}labelTopic", 'text', title: 'Level label', defaultValue: row.labelTopic ?: '', required: false, width: 2
    } else if (type == 'button') {
        input "${prefix}targetButton", 'capability.pushableButton', title: 'Target', multiple: false, required: false, width: 2
        input "${prefix}buttonNumber", 'number', title: 'Button #', defaultValue: row.buttonNumber ?: 1, required: true, width: 1
    } else if (type == 'lock') {
        input "${prefix}targetLock", 'capability.lock', title: 'Target', multiple: false, required: false, width: 2
    } else if (type == 'temperatureDisplay') {
        input "${prefix}targetTemperature", 'capability.temperatureMeasurement', title: 'Target', multiple: false, required: false, width: 2
    } else if (type == 'humidityDisplay') {
        input "${prefix}targetHumidity", 'capability.relativeHumidityMeasurement', title: 'Target', multiple: false, required: false, width: 2
    } else if (type == 'illuminanceDisplay') {
        input "${prefix}targetIlluminance", 'capability.illuminanceMeasurement', title: 'Target', multiple: false, required: false, width: 2
    } else if (type == 'contactDisplay') {
        input "${prefix}targetContact", 'capability.contactSensor', title: 'Target', multiple: false, required: false, width: 2
    } else if (type == 'motionDisplay') {
        input "${prefix}targetMotion", 'capability.motionSensor', title: 'Target', multiple: false, required: false, width: 2
    } else if (type == 'timerButton') {
        input "${prefix}targetBoostTimer", 'capability.pushableButton', title: 'Timer target', multiple: false, required: false, width: 2
        if (row.targetBoostTimer && !isBoostTimerDevice(row.targetBoostTimer)) paragraph 'Selected target does not expose Boost Timer metadata, so this row will use the legacy fallback timer.'
        input "${prefix}labelTopic", 'text', title: 'Button label', defaultValue: row.labelTopic ?: '', required: false, width: 2
    } else {
        input "${prefix}targetSwitch", 'capability.switch', title: 'Target', multiple: false, required: false, width: 2
        input "${prefix}labelTopic", 'text', title: 'Label topic', defaultValue: row.labelTopic ?: '', required: false, width: 2
    }
    if (type == 'timerButton') {
        boolean legacyFallback = !isBoostTimerDevice(row.targetBoostTimer)
        if (legacyFallback) {
            paragraph '<b>Legacy fallback timer</b>'
            input "${prefix}timerIncrementMinutes", 'number', title: 'Increment min', defaultValue: row.timerIncrementMinutes ?: 1, required: true, width: 2
            input "${prefix}timerMaxMinutes", 'number', title: 'Max min', defaultValue: row.timerMaxMinutes ?: 3, required: true, width: 2
            input "${prefix}createVirtualTimer", 'bool', title: 'Virtual if blank', defaultValue: true, required: true, width: 2
        }
        input "${prefix}stateLabelTopic", 'text', title: 'State label', defaultValue: row.stateLabelTopic ?: 'command/p1b13.text', required: false, width: 3
    }
    input removeRowButtonName(plateId, rowId), 'button', title: "Remove row ${rowNumber}"
}

void installed() {
    initialize()
}

void updated() {
    initialize()
}

void uninstalled() {
    unschedule()
    unsubscribe()
}

void initialize() {
    ensureDefaultPlateState()
    syncStateFromSettings()
    unsubscribe()
    unschedule()
    subscribe(location, TYPE_REGISTRY_EVENT, typeRegistryEventHandler)
    sendTypeRegistryDiscoveryRequest()
    activePlateConfigs().each { Map plate ->
        def connector = ensureConnector(plate)
        if (connector) {
            configureConnector(connector, plate)
            subscribe(connector, 'message', connectorMessageHandler)
        }
        plate.rows.findAll { it.enabled != false }.each { Map row ->
            def target = rowTarget(row)
            if (target) {
                if (row.type in ['switch', 'dimmer', 'timerButton']) subscribe(target, 'switch', targetSwitchHandler)
                if (row.type == 'dimmer') subscribe(target, 'level', targetLevelHandler)
                if (row.type == 'timerButton') subscribe(target, 'displayText', targetTimerDisplayHandler)
                String attributeName = rowAttributeName(row)
                if (attributeName) subscribe(target, attributeName, targetAttributeHandler)
            }
            def control = managedControl(plate, row)
            if (control) {
                syncControl(plate, row, rowTarget(row)?.currentSwitch ?: 'off', currentTargetLevel(row))
            }
            if (row.type == 'timerButton' && remainingTimerSeconds(row) > 0) {
                runIn(1, 'timerTick')
            }
        }
        publishScreenConfig(plate)
        syncTargetsToPlate(plate)
    }
}

void appButtonHandler(String buttonName) {
    if (buttonName == 'addPlate') {
        addPlate()
    } else if (buttonName.startsWith('addRow__')) {
        addRow(buttonName - 'addRow__')
    } else if (buttonName.startsWith('removePlate__')) {
        removePlate(buttonName - 'removePlate__')
    } else if (buttonName.startsWith('removeRow__')) {
        List parts = (buttonName - 'removeRow__').split('__') as List
        if (parts.size() == 2) removeRow(parts[0], parts[1])
    } else if (buttonName.startsWith('refreshPlate__')) {
        updated()
    } else if (buttonName == 'refreshTypeRegistry') {
        sendTypeRegistryDiscoveryRequest()
    }
}

void typeRegistryEventHandler(evt) {
    Map event = parseJsonMap(evt?.value)
    if (event.protocol != TYPE_REGISTRY_PROTOCOL) return
    String provider = "${event.provider ?: ''}".trim()
    if (!provider) return
    Map registry = (state.typeRegistry ?: [types: [:], providers: [:]]) as Map
    Map types = (registry.types ?: [:]) as Map
    Map providers = (registry.providers ?: [:]) as Map
    if (event.action == 'unregister') {
        providers.remove(provider)
        types.each { String key, Map definition ->
            Map typeProviders = (definition.providers ?: [:]) as Map
            typeProviders.remove(provider)
            definition.providers = typeProviders
        }
        types = types.findAll { String key, Map definition -> ((definition.providers ?: [:]) as Map) }
    } else if (event.action == 'register') {
        providers[provider] = [label: event.providerLabel ?: provider, updatedAt: now()]
        Map incomingTypes = (event.types ?: [:]) as Map
        incomingTypes.each { String key, Map definition ->
            Map current = (types[key] ?: [:]) as Map
            Map merged = current + definition + [key: key]
            Map typeProviders = (merged.providers ?: [:]) as Map
            typeProviders[provider] = [label: event.providerLabel ?: provider, updatedAt: now()]
            merged.providers = typeProviders
            types[key] = merged
        }
    }
    registry.types = types
    registry.providers = providers
    registry.updatedAt = now()
    state.typeRegistry = registry
}

void sendTypeRegistryDiscoveryRequest() {
    try {
        sendLocationEvent(
            name: TYPE_REGISTRY_REQUEST_EVENT,
            value: JsonOutput.toJson([protocol: TYPE_REGISTRY_PROTOCOL, action: 'discover', requester: registryProviderKey(), at: now()]),
            isStateChange: true
        )
    } catch (Exception e) {
        log.warn "Could not send OpenHASP type discovery request: ${e.class.simpleName}: ${e.message}"
    }
}

void connectorMessageHandler(evt) {
    Map event = parseJsonMap(evt?.value)
    String topic = "${event.topic ?: ''}"
    String payload = "${event.payload ?: ''}"
    Map plate = activePlateConfigs().find { topicMatchesPlate(it, topic) }
    if (!plate) return
    String suffix = topicSuffixForPlate(plate, topic)
    if (suffix == plate.idleTopic) {
        handleIdleMessage(plate, payload)
        return
    }
    Map row = plate.rows.find { it.enabled != false && it.incoming == suffix }
    if (!row) return
    if (row.type == 'switch') {
        handleSwitchMessage(plate, row, payload)
    } else if (row.type == 'dimmer') {
        handleDimmerMessage(plate, row, payload)
    } else if (row.type == 'button') {
        handleButtonMessage(plate, row, payload)
    } else if (row.type == 'lock') {
        handleLockMessage(plate, row, payload)
    } else if (row.type == 'timerButton') {
        handleTimerMessage(plate, row, payload)
    }
}

void targetSwitchHandler(evt) {
    activePlateConfigs().each { Map plate ->
        plate.rows.findAll { rowTarget(it)?.id?.toString() == evt.deviceId?.toString() }.each { Map row ->
            if (pendingTargetStatus(row, 'switch', evt.value) != 'stale') {
                if (row.type == 'switch') {
                    publishRowSwitch(plate, row, normalizeSwitchValue(evt.value))
                    syncControl(plate, row, normalizeSwitchValue(evt.value), currentTargetLevel(row))
                } else if (row.type == 'dimmer') {
                    publishRowLevel(plate, row, currentTargetLevel(row))
                    syncControl(plate, row, normalizeSwitchValue(evt.value), currentTargetLevel(row))
                } else if (row.type == 'timerButton') {
                    publishTimerState(plate, row)
                } else if (row.type == 'lock') {
                    publishRowAttribute(plate, row, 'lock', evt.value)
                }
            }
        }
    }
}

void targetLevelHandler(evt) {
    activePlateConfigs().each { Map plate ->
        plate.rows.findAll { it.type == 'dimmer' && rowTarget(it)?.id?.toString() == evt.deviceId?.toString() }.each { Map row ->
            int level = safeInt(evt.value, 100)
            if (pendingTargetStatus(row, 'level', level) != 'stale') {
                publishRowLevel(plate, row, level)
                syncControl(plate, row, rowTarget(row)?.currentSwitch ?: 'on', level)
            }
        }
    }
}

void targetTimerDisplayHandler(evt) {
    activePlateConfigs().each { Map plate ->
        plate.rows.findAll { it.type == 'timerButton' && rowTarget(it)?.id?.toString() == evt.deviceId?.toString() }.each { Map row ->
            publishTimerState(plate, row)
        }
    }
}

void targetAttributeHandler(evt) {
    activePlateConfigs().each { Map plate ->
        plate.rows.findAll { rowAttributeName(it) == evt.name && rowTarget(it)?.id?.toString() == evt.deviceId?.toString() }.each { Map row ->
            publishRowAttribute(plate, row, evt.name, evt.value)
        }
    }
}

void controlSwitchHandler(evt) {
    if (suppressPendingControl(evt, 'switch')) return
    Map match = rowForControl(evt.deviceId)
    if (!match) return
    commandTargetFromRow(match.row as Map, normalizeSwitchValue(evt.value), currentTargetLevel(match.row as Map), 'hubitat')
}

void controlLevelHandler(evt) {
    if (suppressPendingControl(evt, 'level')) return
    Map match = rowForControl(evt.deviceId)
    if (!match) return
    commandTargetFromRow(match.row as Map, 'on', safeInt(evt.value, 100), 'hubitat')
}

void handleSwitchMessage(Map plate, Map row, String payload) {
    if (!openHaspEventIsAction(payload)) return
    commandTargetFromRow(row, normalizeSwitchValue(payload), currentTargetLevel(row), 'panel')
}

void handleDimmerMessage(Map plate, Map row, String payload) {
    if (!openHaspEventIsAction(payload)) return
    int level = Math.max(1, Math.min(100, normalizeLevelValue(payload, 100)))
    commandTargetFromRow(row, 'on', level, 'panel')
}

void handleButtonMessage(Map plate, Map row, String payload) {
    if (!openHaspEventIsAction(payload)) return
    def target = rowTarget(row)
    if (!target) return
    target.push(Math.max(1, safeInt(row.buttonNumber, 1)))
}

void handleLockMessage(Map plate, Map row, String payload) {
    if (!openHaspEventIsAction(payload)) return
    def target = rowTarget(row)
    if (!target) return
    normalizeLockValue(payload) == 'locked' ? target.lock() : target.unlock()
}

void handleTimerMessage(Map plate, Map row, String payload) {
    if (!openHaspEventIsAction(payload) || suppressTimerBounce(row)) return
    def target = rowTarget(row)
    if (target) {
        commandBoostTarget(target)
        publishTimerState(plate, row)
        return
    }
    long nowSeconds = epochSeconds()
    int incrementSeconds = Math.max(1, safeInt(row.timerIncrementMinutes, 1)) * 60
    int maxSeconds = Math.max(incrementSeconds, safeInt(row.timerMaxMinutes, 3) * 60)
    Map timers = (state.timers ?: [:]) as Map
    timers[row.key] = addTimerSeconds(nowSeconds, safeLong(timers[row.key], 0L), incrementSeconds, maxSeconds)
    state.timers = timers
    commandSwitch(managedTimerSwitch(plate, row), 'on')
    publishTimerState(plate, row)
    runIn(1, 'timerTick')
}

void handleIdleMessage(Map plate, String payload) {
    String idleValue = normalizeIdleValue(payload)
    if (idleValue == 'idle') {
        publishPlateTopic(plate, plate.backlightTopic, 'off', false)
    } else if (idleValue == 'active') {
        int brightness = Math.max(1, Math.min(255, safeInt(plate.screenWakeBrightness, 255)))
        publishPlateTopic(plate, plate.backlightTopic, JsonOutput.toJson([state: 'on', brightness: brightness]), false)
    }
}

void timerTick() {
    boolean anyRemaining = false
    activePlateConfigs().each { Map plate ->
        plate.rows.findAll { it.type == 'timerButton' && !rowTarget(it) }.each { Map row ->
            long remaining = remainingTimerSeconds(row)
            if (remaining <= 0) {
                Map timers = (state.timers ?: [:]) as Map
                if (timers[row.key]) {
                    timers.remove(row.key)
                    state.timers = timers
                    commandSwitch(managedTimerSwitch(plate, row), 'off')
                    publishTimerState(plate, row)
                }
            } else {
                anyRemaining = true
                publishTimerState(plate, row)
            }
        }
    }
    if (anyRemaining) runIn(1, 'timerTick')
}

void commandTargetFromRow(Map row, String switchValue, int level, String source) {
    def target = rowTarget(row)
    if (!target) return
    Map plate = plateForRow(row)
    if (row.type == 'dimmer') {
        recordPending(row, 'level', level, source)
        target.setLevel(Math.max(1, Math.min(100, level)))
        target.on()
    } else {
        recordPending(row, 'switch', switchValue, source)
        commandSwitch(target, switchValue)
    }
}

void publishRowSwitch(Map plate, Map row, String value) {
    if (!row.outgoing) return
    publishPlateTopic(plate, row.outgoing, value == 'on' ? '1' : '0', false)
}

void publishRowLevel(Map plate, Map row, int level) {
    if (row.outgoing) publishPlateTopic(plate, row.outgoing, "${Math.max(1, Math.min(100, level))}", false)
    if (row.labelTopic) publishPlateTopic(plate, row.labelTopic, "${Math.max(1, Math.min(100, level))}", false)
}

void publishRowAttribute(Map plate, Map row, String attributeName, Object value) {
    if (!row.outgoing) return
    publishPlateTopic(plate, row.outgoing, formatAttributeValue(row, attributeName, value), false)
}

void publishTimerState(Map plate, Map row) {
    def target = rowTarget(row)
    if (target && hasTimerDisplay(target)) {
        if (row.labelTopic) publishPlateTopic(plate, row.labelTopic, "${target.currentValue('displayText') ?: 'Start'}", false)
        if (row.stateLabelTopic) publishPlateTopic(plate, row.stateLabelTopic, target.currentSwitch == 'on' ? 'ON' : 'OFF', false)
        return
    }
    long remaining = remainingTimerSeconds(row)
    int incrementSeconds = Math.max(1, safeInt(row.timerIncrementMinutes, 1)) * 60
    if (row.labelTopic) publishPlateTopic(plate, row.labelTopic, timerButtonText(remaining, incrementSeconds), false)
    if (row.stateLabelTopic) {
        def timerTarget = rowTarget(row) ?: managedTimerSwitch(plate, row)
        publishPlateTopic(plate, row.stateLabelTopic, remaining > 0 || timerTarget?.currentSwitch == 'on' ? 'ON' : 'OFF', false)
    }
}

void commandBoostTarget(target) {
    try {
        target.boost()
        return
    } catch (ignored) {
        // Fall through to switch-style targets.
    }
    commandSwitch(target, 'on')
}

String formatAttributeValue(Map row, String attributeName, Object value) {
    if (value == null) return ''
    if (row.type == 'temperatureDisplay') return "${value}°"
    if (row.type == 'humidityDisplay') return "${value}%"
    if (row.type == 'illuminanceDisplay') return "${value} lx"
    "${value}".toUpperCase()
}

String normalizeLockValue(Object value) {
    Object normalized = openHaspPayloadValue(value)
    if (normalized instanceof Number) return normalized != 0 ? 'locked' : 'unlocked'
    if (normalized instanceof Boolean) return normalized ? 'locked' : 'unlocked'
    String text = "${normalized ?: ''}".trim().toLowerCase()
    text in ['1', 'true', 'on', 'locked', 'lock'] ? 'locked' : 'unlocked'
}

boolean hasTimerDisplay(target) {
    hasSettingValue(target?.currentValue('displayText')) || target?.currentValue('remainingSeconds') != null
}

void publishScreenConfig(Map plate) {
    Map body = [
        idle1: 0,
        idle2: Math.max(0, safeInt(plate.screenIdleSeconds, 60)),
        bckl: Math.max(1, Math.min(255, safeInt(plate.screenBacklightBrightness, 42))),
        bcklinv: 0
    ]
    publishPlateTopic(plate, plate.guiConfigTopic, JsonOutput.toJson(body), true)
}

void publishPlateTopic(Map plate, String suffix, String payload, boolean retained) {
    def connector = connectorDevice(plate.id)
    if (!connector) return
    String topic = fullTopic(plate, suffix)
    connector.publishTopic(topic, payload, retained)
}

String fullTopic(Map plate, String suffix) {
    String clean = cleanTopic(suffix)
    String base = cleanTopic(plate.baseTopic ?: 'hasp')
    String plateName = cleanTopic(plate.plateName ?: 'panel')
    clean.startsWith("${base}/${plateName}/") ? clean : "${base}/${plateName}/${clean}"
}

String plateTopicPrefix(Map plate) {
    "${cleanTopic(plate.baseTopic ?: 'hasp')}/${cleanTopic(plate.plateName ?: 'panel')}/"
}

Map effectivePlateForDisplay(Map plate) {
    Map effective = new LinkedHashMap(plate)
    effective.putAll(mqttState())
    effective
}

void syncTargetsToPlate(Map plate) {
    plate.rows.each { Map row ->
        def target = rowTarget(row)
        if (row.type == 'switch' && target) {
            publishRowSwitch(plate, row, target.currentSwitch ?: 'off')
            syncControl(plate, row, target.currentSwitch ?: 'off', currentTargetLevel(row))
        } else if (row.type == 'dimmer' && target) {
            publishRowLevel(plate, row, currentTargetLevel(row))
            syncControl(plate, row, target.currentSwitch ?: 'on', currentTargetLevel(row))
        } else if (row.type == 'timerButton') {
            publishTimerState(plate, row)
        } else if (rowPublishesOutput(row.type) && target) {
            String attributeName = rowAttributeName(row)
            if (attributeName) publishRowAttribute(plate, row, attributeName, target.currentValue(attributeName))
        }
    }
}

def rowTarget(Map row) {
    if (row.type == 'button') return row.targetButton
    if (row.type == 'lock') return row.targetLock
    if (row.type == 'temperatureDisplay') return row.targetTemperature
    if (row.type == 'humidityDisplay') return row.targetHumidity
    if (row.type == 'illuminanceDisplay') return row.targetIlluminance
    if (row.type == 'contactDisplay') return row.targetContact
    if (row.type == 'motionDisplay') return row.targetMotion
    if (row.type == 'timerButton') {
        if (isBoostTimerDevice(row.targetBoostTimer)) return row.targetBoostTimer
    }
    row.type == 'dimmer' ? row.targetDimmer : row.targetSwitch
}

int currentTargetLevel(Map row) {
    Math.max(1, Math.min(100, safeInt(rowTarget(row)?.currentLevel, 100)))
}

Map rowTypeOptions(String currentType = 'switch') {
    Map options = standardTypeOptions()
    options.putAll(registeredExternalTypeOptions())
    if (currentType && !(options.containsKey(currentType))) {
        options[currentType] = "${currentType} (legacy)"
    }
    options
}

Map standardTypeOptions() {
    standardTypeDefinitions().collectEntries { String key, Map definition ->
        [(key): definition.label]
    }
}

Map standardTypeDefinitions() {
    [
        switch: [
            label: 'Switch',
            source: 'OpenHASP Manager',
            capability: 'capability.switch',
            direction: 'both',
            handler: 'switch'
        ],
        dimmer: [
            label: 'Dimmer',
            source: 'OpenHASP Manager',
            capability: 'capability.switchLevel',
            direction: 'both',
            handler: 'dimmer'
        ],
        button: [
            label: 'Button',
            source: 'OpenHASP Manager',
            capability: 'capability.pushableButton',
            direction: 'toHubitat',
            handler: 'button'
        ],
        lock: [
            label: 'Lock',
            source: 'OpenHASP Manager',
            capability: 'capability.lock',
            direction: 'both',
            handler: 'lock'
        ],
        temperatureDisplay: [
            label: 'Temperature',
            source: 'OpenHASP Manager',
            capability: 'capability.temperatureMeasurement',
            direction: 'toOpenHASP',
            handler: 'attribute',
            attribute: 'temperature'
        ],
        humidityDisplay: [
            label: 'Humidity',
            source: 'OpenHASP Manager',
            capability: 'capability.relativeHumidityMeasurement',
            direction: 'toOpenHASP',
            handler: 'attribute',
            attribute: 'humidity'
        ],
        illuminanceDisplay: [
            label: 'Illuminance',
            source: 'OpenHASP Manager',
            capability: 'capability.illuminanceMeasurement',
            direction: 'toOpenHASP',
            handler: 'attribute',
            attribute: 'illuminance'
        ],
        contactDisplay: [
            label: 'Contact',
            source: 'OpenHASP Manager',
            capability: 'capability.contactSensor',
            direction: 'toOpenHASP',
            handler: 'attribute',
            attribute: 'contact'
        ],
        motionDisplay: [
            label: 'Motion',
            source: 'OpenHASP Manager',
            capability: 'capability.motionSensor',
            direction: 'toOpenHASP',
            handler: 'attribute',
            attribute: 'motion'
        ]
    ]
}

Map registeredExternalTypeOptions() {
    Map registry = (state.typeRegistry ?: [:]) as Map
    Map types = (registry.types ?: [:]) as Map
    types.findAll { String key, Map definition ->
        ((definition.providers ?: [:]) as Map)
    }.collectEntries { String key, Map definition ->
        [(key): definition.label ?: key]
    }
}

String registryProviderKey() {
    "${app.name ?: 'OpenHASP'}:${app.id ?: app.label ?: 'manager'}"
}

boolean rowAcceptsInput(String type) {
    type in ['switch', 'dimmer', 'button', 'lock', 'timerButton']
}

boolean rowPublishesOutput(String type) {
    type in ['switch', 'dimmer', 'lock', 'temperatureDisplay', 'humidityDisplay', 'illuminanceDisplay', 'contactDisplay', 'motionDisplay']
}

String rowAttributeName(Map row) {
    [
        lock: 'lock',
        temperatureDisplay: 'temperature',
        humidityDisplay: 'humidity',
        illuminanceDisplay: 'illuminance',
        contactDisplay: 'contact',
        motionDisplay: 'motion'
    ][row.type] as String
}

boolean isBoostTimerDevice(device) {
    if (!device) return false
    if ("${device.currentValue('integrationType') ?: ''}" == 'boostTimer') return true
    if ("${device.currentValue('openHaspRowType') ?: ''}" == 'timerButton') return true
    try {
        if (device.hasCommand('boost') && device.currentValue('displayText') != null) return true
    } catch (ignored) {
        // Some Hubitat test doubles/devices do not expose command metadata.
    }
    false
}

void commandSwitch(device, Object value) {
    if (!device) return
    normalizeSwitchValue(value) == 'on' ? device.on() : device.off()
}

def managedControl(Map plate, Map row) {
    if (!settingEnabled(plate.createVirtualControls, true) || !(row.type in ['switch', 'dimmer'])) return null
    String dni = "openhasp-${plate.id}-${row.id}-control"
    String label = "${plate.label} ${row.label} Control"
    String driver = row.type == 'dimmer' ? 'Virtual Dimmer' : 'Virtual Switch'
    def child = getChildDevice(dni)
    if (!child) {
        child = addChildDevice('hubitat', driver, dni, [name: label, label: label, isComponent: false])
    } else if (child.displayName != label) {
        try { child.setLabel(label) } catch (ignored) {}
    }
    child
}

Map rowForControl(Object deviceId) {
    Map found = null
    activePlateConfigs().find { Map plate ->
        plate.rows.find { Map row ->
            boolean matched = managedControl(plate, row)?.id?.toString() == deviceId?.toString()
            if (matched) {
                found = [plate: plate, row: row]
            }
            matched
        }
        found != null
    }
    found
}

Map plateForRow(Map row) {
    String plateId = "${row.key ?: ''}".contains(':') ? "${row.key}".split(':')[0] : ''
    activePlateConfigs().find { it.id == plateId }
}

void syncControl(Map plate, Map row, Object switchValue, Object levelValue) {
    def control = managedControl(plate, row)
    if (control) {
        recordPendingControl(control, '*', '*')
        String normalizedSwitch = normalizeSwitchValue(switchValue)
        recordPendingControl(control, 'switch', normalizedSwitch)
        control.sendEvent(name: 'switch', value: normalizedSwitch, isStateChange: false)
        if (row.type == 'dimmer') {
            int normalizedLevel = Math.max(1, Math.min(100, safeInt(levelValue, 100)))
            recordPendingControl(control, 'level', normalizedLevel)
            control.sendEvent(name: 'level', value: normalizedLevel, unit: '%', isStateChange: false)
        }
    }
}

def managedTimerSwitch(Map plate, Map row) {
    if (!settingEnabled(row.createVirtualTimer, true)) return null
    String dni = "openhasp-${plate.id}-${row.id}-timer"
    String label = "${plate.label} ${row.label}"
    def child = getChildDevice(dni)
    if (!child) {
        child = addChildDevice('hubitat', 'Virtual Switch', dni, [name: label, label: label, isComponent: false])
    } else if (child.displayName != label) {
        try { child.setLabel(label) } catch (ignored) {}
    }
    child
}

def ensureConnector(Map plate) {
    String dni = "openhasp-${plate.id}-connector"
    String label = "${plate.label} Connector"
    def child = getChildDevice(dni)
    if (!child) {
        child = addChildDevice('nichuk', 'OpenHASP Connector', dni, [name: label, label: label, isComponent: false])
    } else if (child.displayName != label) {
        try { child.setLabel(label) } catch (ignored) {}
    }
    child
}

def connectorDevice(String plateId) {
    getChildDevice("openhasp-${plateId}-connector")
}

void configureConnector(connector, Map plate) {
    connector.configureFromApp(
        plate.plateName,
        plate.baseTopic,
        plate.brokerHost,
        safeInt(plate.brokerPort, 1883),
        plate.mqttUsername ?: '',
        plate.mqttPassword ?: ''
    )
}

void addPlate() {
    ensureDefaultPlateState(false)
    String id = newId()
    Map plates = (state.plates ?: [:]) as Map
    plates[id] = defaultPlate(id, "Panel ${plates.size() + 1}", "panel_${plates.size() + 1}")
    state.plates = plates
}

void removePlate(String plateId) {
    Map plates = (state.plates ?: [:]) as Map
    plates.remove(plateId)
    state.plates = plates
}

void addRow(String plateId) {
    Map plates = (state.plates ?: [:]) as Map
    Map plate = (plates[plateId] ?: defaultPlate(plateId, 'Panel', 'panel')) as Map
    List rows = (plate.rows ?: []) as List
    rows << defaultRow(newId(), "Row ${rows.size() + 1}", 'switch', '', '', '')
    plate.rows = rows
    plates[plateId] = plate
    state.plates = plates
}

void removeRow(String plateId, String rowId) {
    Map plates = (state.plates ?: [:]) as Map
    Map plate = plates[plateId] as Map
    if (!plate) return
    plate.rows = ((plate.rows ?: []) as List).findAll { it.id != rowId }
    plates[plateId] = plate
    state.plates = plates
}

void ensureDefaultPlateState(boolean addIfEmpty = true) {
    if (state.plates || !addIfEmpty) return
    String id = 'bathroom'
    state.plates = [(id): defaultBathroomPlate(id)]
}

void syncStateFromSettings() {
    syncGlobalStateFromSettings()
    Map plates = (state.plates ?: [:]) as Map
    plates.keySet().each { String plateId ->
        Map plate = (plates[plateId] ?: [:]) as Map
        plate.enabled = settingEnabled(settings[plateSetting(plateId, 'enabled')], plate.enabled != false)
        ['label', 'plateName'].each { String key ->
            if (settings.containsKey(plateSetting(plateId, key))) plate[key] = settings[plateSetting(plateId, key)]
        }
        plate.createVirtualControls = settingEnabled(settings[plateSetting(plateId, 'createVirtualControls')], settingEnabled(plate.createVirtualControls, true))
        plate.rows = ((plate.rows ?: []) as List).collect { Map row ->
            String rowId = row.id
            String prefix = rowSetting(plateId, rowId, '')
            row.enabled = settingEnabled(settings["${prefix}enabled"], row.enabled != false)
            ['label', 'type', 'incoming', 'outgoing', 'labelTopic', 'stateLabelTopic'].each { String key ->
                if (settings.containsKey("${prefix}${key}")) row[key] = settings["${prefix}${key}"]
            }
            ['timerIncrementMinutes', 'timerMaxMinutes', 'buttonNumber'].each { String key ->
                if (settings.containsKey("${prefix}${key}")) row[key] = safeInt(settings["${prefix}${key}"], safeInt(row[key], 1))
            }
            row.createVirtualTimer = settingEnabled(settings["${prefix}createVirtualTimer"], settingEnabled(row.createVirtualTimer, true))
            row.targetSwitch = settings["${prefix}targetSwitch"]
            row.targetDimmer = settings["${prefix}targetDimmer"]
            row.targetBoostTimer = settings["${prefix}targetBoostTimer"]
            row.targetButton = settings["${prefix}targetButton"]
            row.targetLock = settings["${prefix}targetLock"]
            row.targetTemperature = settings["${prefix}targetTemperature"]
            row.targetHumidity = settings["${prefix}targetHumidity"]
            row.targetIlluminance = settings["${prefix}targetIlluminance"]
            row.targetContact = settings["${prefix}targetContact"]
            row.targetMotion = settings["${prefix}targetMotion"]
            row.key = "${plateId}:${rowId}"
            row
        }
        plates[plateId] = plate
    }
    state.plates = plates
}

void syncGlobalStateFromSettings() {
    Map mqtt = mqttState()
    ['baseTopic', 'brokerHost', 'mqttUsername'].each { String key ->
        if (settings.containsKey(mqttSetting(key))) mqtt[key] = settings[mqttSetting(key)]
    }
    if (settings.containsKey(mqttSetting('mqttPassword'))) {
        String submittedPassword = "${settings[mqttSetting('mqttPassword')] ?: ''}"
        if (submittedPassword && submittedPassword != PASSWORD_MASK) {
            mqtt.mqttPassword = submittedPassword
        }
    }
    if (settings.containsKey(mqttSetting('brokerPort'))) {
        mqtt.brokerPort = safeInt(settings[mqttSetting('brokerPort')], safeInt(mqtt.brokerPort, 1883))
    }

    Map screen = screenState()
    ['idleTopic', 'backlightTopic', 'guiConfigTopic'].each { String key ->
        if (settings.containsKey(screenSetting(key))) screen[key] = settings[screenSetting(key)]
    }
    ['screenIdleSeconds', 'screenBacklightBrightness', 'screenWakeBrightness'].each { String key ->
        if (settings.containsKey(screenSetting(key))) screen[key] = safeInt(settings[screenSetting(key)], safeInt(screen[key], 0))
    }

    state.mqtt = mqtt
    state.screen = screen
}

List<Map> activePlateConfigs() {
    syncStateFromSettings()
    Map mqtt = mqttState()
    Map screen = screenState()
    ((state.plates ?: [:]) as Map).collect { String id, Map plate ->
        Map copy = new LinkedHashMap(plate)
        copy.putAll(mqtt)
        copy.putAll(screen)
        copy.id = id
        copy.rows = ((copy.rows ?: []) as List).collect { Map row ->
            Map rowCopy = new LinkedHashMap(row)
            rowCopy.key = "${id}:${rowCopy.id}"
            rowCopy
        }
        copy
    }.findAll { it.enabled != false }
}

List<String> plateIds() {
    ((state.plates ?: [:]) as Map).keySet() as List<String>
}

Map plateState(String plateId) {
    ((state.plates ?: [:]) as Map)[plateId] ?: [:]
}

Map mqttState() {
    Map existing = (state.mqtt ?: [:]) as Map
    Map source = firstPlateConfig()
    defaultMqttConfig() + [
        baseTopic: existing.baseTopic ?: source.baseTopic,
        brokerHost: existing.brokerHost ?: source.brokerHost,
        brokerPort: existing.brokerPort ?: source.brokerPort,
        mqttUsername: existing.mqttUsername ?: source.mqttUsername,
        mqttPassword: existing.mqttPassword ?: source.mqttPassword
    ].findAll { it.value != null }
}

Map screenState() {
    Map existing = (state.screen ?: [:]) as Map
    Map source = firstPlateConfig()
    defaultScreenConfig() + [
        idleTopic: existing.idleTopic ?: source.idleTopic,
        backlightTopic: existing.backlightTopic ?: source.backlightTopic,
        guiConfigTopic: existing.guiConfigTopic ?: source.guiConfigTopic,
        screenIdleSeconds: existing.screenIdleSeconds ?: source.screenIdleSeconds,
        screenBacklightBrightness: existing.screenBacklightBrightness ?: source.screenBacklightBrightness,
        screenWakeBrightness: existing.screenWakeBrightness ?: source.screenWakeBrightness
    ].findAll { it.value != null }
}

Map firstPlateConfig() {
    (((state.plates ?: [:]) as Map).values() as List).find { it instanceof Map } ?: [:]
}

List<String> rowIds(String plateId) {
    ((plateState(plateId).rows ?: []) as List).collect { it.id as String }
}

Map rowState(String plateId, String rowId) {
    (((plateState(plateId).rows ?: []) as List).find { it.id == rowId } ?: [:]) as Map
}

Map defaultBathroomPlate(String id) {
    Map plate = defaultPlate(id, 'Bathroom Panel', 'bathroom_panel')
    plate.rows = [
        defaultRow('officeSwitch', 'Office Main', 'switch', 'state/p1b42', 'command/p1b42.val', ''),
        defaultRow('officeDimmer', 'Office Main Level', 'dimmer', 'state/p1b43', 'command/p1b43.val', 'command/p1b44.text'),
        defaultRow('bedroomSwitch', 'Bedroom Main', 'switch', 'state/p1b52', 'command/p1b52.val', ''),
        defaultRow('bedroomDimmer', 'Bedroom Main Level', 'dimmer', 'state/p1b53', 'command/p1b53.val', 'command/p1b54.text'),
        defaultRow('ufhTimer', 'Underfloor Heating', 'timerButton', 'state/p1b21', '', 'command/p1b21.text') + [stateLabelTopic: 'command/p1b13.text', createVirtualTimer: true]
    ]
    plate
}

Map defaultPlate(String id, String label, String plateName) {
    [
        label: label,
        plateName: plateName,
        enabled: true,
        createVirtualControls: true,
        rows: []
    ]
}

Map defaultMqttConfig() {
    [
        baseTopic: 'hasp',
        brokerHost: '10.0.0.65',
        brokerPort: 1883
    ]
}

Map defaultScreenConfig() {
    [
        idleTopic: 'state/idle',
        backlightTopic: 'command/backlight',
        guiConfigTopic: 'config/gui',
        screenIdleSeconds: 60,
        screenBacklightBrightness: 42,
        screenWakeBrightness: 255
    ]
}

Map defaultRow(String id, String label, String type, String incoming, String outgoing, String labelTopic) {
    [
        id: id,
        enabled: true,
        label: label,
        type: type,
        incoming: incoming,
        outgoing: outgoing,
        labelTopic: labelTopic,
        buttonNumber: 1,
        timerIncrementMinutes: 1,
        timerMaxMinutes: 3
    ]
}

boolean topicMatchesPlate(Map plate, String topic) {
    topic.startsWith("${cleanTopic(plate.baseTopic)}/${cleanTopic(plate.plateName)}/")
}

String topicSuffixForPlate(Map plate, String topic) {
    topic - "${cleanTopic(plate.baseTopic)}/${cleanTopic(plate.plateName)}/"
}

String cleanTopic(Object value) {
    "${value ?: ''}".trim().replaceAll(/^\/+|\/+$/, '')
}

String plateSetting(String plateId, String key) {
    "plate_${plateId}_${key}"
}

String mqttSetting(String key) {
    "mqtt_${key}"
}

String screenSetting(String key) {
    "screen_${key}"
}

String rowSetting(String plateId, String rowId, String key) {
    "row_${plateId}_${rowId}_${key}"
}

String addRowButtonName(String plateId) {
    "addRow__${plateId}"
}

String removeRowButtonName(String plateId, String rowId) {
    "removeRow__${plateId}__${rowId}"
}

String removePlateButtonName(String plateId) {
    "removePlate__${plateId}"
}

String refreshButtonName(String plateId) {
    "refreshPlate__${plateId}"
}

String newId() {
    UUID.randomUUID().toString().take(8).replace('-', '')
}

String settingString(String name, String fallback = '') {
    hasSettingValue(settings[name]) ? "${settings[name]}" : fallback
}

String passwordDisplayValue(Object savedPassword) {
    hasSettingValue(savedPassword) ? PASSWORD_MASK : ''
}

boolean hasSettingValue(Object value) {
    value != null && (!(value instanceof CharSequence) || "${value}".trim())
}

Map parseJsonMap(Object value) {
    try {
        def parsed = new JsonSlurper().parseText("${value ?: '{}'}")
        return parsed instanceof Map ? parsed as Map : [:]
    } catch (ignored) {
        return [:]
    }
}

boolean openHaspEventIsAction(Object value) {
    Map event = openHaspEventPayload(value)
    if (!event.containsKey('event')) return true
    "${event.event ?: ''}".toLowerCase() in ['up', 'changed', 'short', 'release', 'released']
}

boolean suppressTimerBounce(Map row) {
    long nowMs = now()
    Map last = (state.lastTimerInputAtMs ?: [:]) as Map
    long prior = safeLong(last[row.key], 0L)
    if (prior && nowMs - prior < 500L) return true
    last[row.key] = nowMs
    state.lastTimerInputAtMs = last
    false
}

long remainingTimerSeconds(Map row) {
    Map timers = (state.timers ?: [:]) as Map
    remainingSeconds(epochSeconds(), safeLong(timers[row.key], 0L))
}

void recordPending(Map row, String kind, Object value, String source) {
    Map pending = (state.pendingTargets ?: [:]) as Map
    pending["${row.key}:${kind}"] = [value: value, source: source, until: now() + 8000L]
    state.pendingTargets = pending
}

String pendingTargetStatus(Map row, String kind, Object reportedValue) {
    Map pending = (state.pendingTargets ?: [:]) as Map
    String key = "${row.key}:${kind}"
    Map entry = pending[key] as Map
    if (!entry) return 'none'
    if (now() > safeLong(entry.until, 0L)) {
        pending.remove(key)
        state.pendingTargets = pending
        return 'none'
    }
    boolean matched = kind == 'level'
        ? Math.abs(safeInt(entry.value, 0) - safeInt(reportedValue, 0)) <= 1
        : normalizeSwitchValue(entry.value) == normalizeSwitchValue(reportedValue)
    if (matched) {
        pending.remove(key)
        state.pendingTargets = pending
        return "${entry.source ?: 'hubitat'}" == 'panel' ? 'matched-panel' : 'matched-hubitat'
    }
    'stale'
}

void recordPendingControl(device, String kind, Object value) {
    if (!device) return
    Map pending = (state.pendingControls ?: [:]) as Map
    pending["${device.id}:${kind}"] = [value: value, until: now() + 2000L]
    state.pendingControls = pending
}

boolean suppressPendingControl(evt, String kind) {
    Map pending = (state.pendingControls ?: [:]) as Map
    String anyKey = "${evt.deviceId}:*"
    Map anyEntry = pending[anyKey] as Map
    if (anyEntry) {
        if (now() <= safeLong(anyEntry.until, 0L)) return true
        pending.remove(anyKey)
        state.pendingControls = pending
    }
    String key = "${evt.deviceId}:${kind}"
    Map entry = pending[key] as Map
    if (!entry) return false
    if (now() > safeLong(entry.until, 0L)) {
        pending.remove(key)
        state.pendingControls = pending
        return false
    }
    boolean matched = kind == 'level'
        ? Math.abs(safeInt(entry.value, 0) - safeInt(evt.value, 0)) <= 1
        : normalizeSwitchValue(entry.value) == normalizeSwitchValue(evt.value)
    if (matched) {
        pending.remove(key)
        state.pendingControls = pending
        return true
    }
    false
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
    if (normalized instanceof Boolean) return normalized ? 'on' : 'off'
    if (normalized instanceof Number) return normalized != 0 ? 'on' : 'off'
    String text = "${normalized}".trim().toLowerCase()
    text in ['1', 'true', 'on', 'yes'] ? 'on' : 'off'
}

String normalizeIdleValue(Object value) {
    Object normalized = openHaspPayloadValue(value)
    String text = "${normalized ?: ''}".trim().toLowerCase()
    if (text in ['long', 'idle', 'on', '1', 'true', 'yes']) return 'idle'
    if (text in ['off', 'active', '0', 'false', 'no']) return 'active'
    text ? text : 'active'
}

int normalizeLevelValue(Object value, int fallback = 100) {
    safeInt(openHaspPayloadValue(value), fallback)
}

Object openHaspPayloadValue(Object value) {
    if (value == null || !(value instanceof CharSequence)) return value
    String text = value.toString().trim()
    if (!text.startsWith('{')) return value
    try {
        def parsed = new JsonSlurper().parseText(text)
        if (parsed instanceof Map && parsed.containsKey('val')) return parsed.val
    } catch (ignored) {
    }
    value
}

Map openHaspEventPayload(Object value) {
    if (value == null || !(value instanceof CharSequence)) return [:]
    String text = value.toString().trim()
    if (!text.startsWith('{')) return [:]
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
    if (!deadlineEpochSeconds) return 0L
    Math.max(0L, deadlineEpochSeconds - nowEpochSeconds)
}

String timerButtonText(long remainingSeconds, int incrementSeconds) {
    if (remainingSeconds <= 0) return "Start ${Math.max(1, Math.round(incrementSeconds / 60D) as int)}m"
    long minutes = Math.floor(remainingSeconds / 60D) as long
    long seconds = remainingSeconds % 60
    "${minutes}:${seconds.toString().padLeft(2, '0')}"
}

boolean settingEnabled(Object value, boolean defaultValue = true) {
    if (value == null) return defaultValue
    if (value instanceof Boolean) return value
    !("${value}".trim().toLowerCase() in ['false', '0', 'no', 'off'])
}
