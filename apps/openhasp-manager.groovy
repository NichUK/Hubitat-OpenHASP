/*
 * Copyright 2026 NichUK
 *
 * Licensed under the Apache License, Version 2.0.
 */

import groovy.json.JsonOutput
import groovy.json.JsonSlurper

definition(
    name: 'OpenHASP Manager',
    namespace: 'nichuk',
    author: 'NichUK',
    description: 'Manages OpenHASP MQTT plates and maps panel widgets to Hubitat devices.',
    category: 'Convenience',
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
        section('OpenHASP plates') {
            paragraph 'Each plate has its own MQTT connector and mapping rows. The connector talks directly to the OpenHASP hasp/<plate>/... topics.'
            input 'addPlate', 'button', title: 'Add plate'
        }
        plateIds().eachWithIndex { String plateId, int index ->
            Map plate = plateState(plateId)
            section("${plate.label ?: plate.plateName ?: 'OpenHASP Plate'}", hideable: true, hidden: index > 0) {
                input plateSetting(plateId, 'enabled'), 'bool', title: 'Enabled', defaultValue: plate.enabled != false, required: true, submitOnChange: true
                input plateSetting(plateId, 'label'), 'text', title: 'Plate label', defaultValue: plate.label ?: 'Bathroom Panel', required: true
                input plateSetting(plateId, 'plateName'), 'text', title: 'OpenHASP plate name', defaultValue: plate.plateName ?: 'bathroom_panel', required: true
                input plateSetting(plateId, 'baseTopic'), 'text', title: 'MQTT base topic', defaultValue: plate.baseTopic ?: 'hasp', required: true
                input plateSetting(plateId, 'brokerHost'), 'text', title: 'MQTT broker host', defaultValue: plate.brokerHost ?: '10.0.0.65', required: true
                input plateSetting(plateId, 'brokerPort'), 'number', title: 'MQTT broker port', defaultValue: plate.brokerPort ?: 1883, required: true
                input plateSetting(plateId, 'mqttUsername'), 'text', title: 'MQTT username', required: false
                input plateSetting(plateId, 'mqttPassword'), 'password', title: 'MQTT password', required: false
                input plateSetting(plateId, 'createVirtualControls'), 'bool', title: 'Create virtual lighting controls for dashboards', defaultValue: true, required: true

                paragraph "Connector device: ${connectorDevice(plateId)?.displayName ?: 'created when saved'}"
                input refreshButtonName(plateId), 'button', title: 'Reconnect / refresh this plate'
                input removePlateButtonName(plateId), 'button', title: 'Remove this plate'

                paragraph '<b>Screen idle/backlight</b>'
                input plateSetting(plateId, 'idleTopic'), 'text', title: 'Idle state topic suffix', defaultValue: plate.idleTopic ?: 'state/idle', required: true
                input plateSetting(plateId, 'backlightTopic'), 'text', title: 'Backlight command topic suffix', defaultValue: plate.backlightTopic ?: 'command/backlight', required: true
                input plateSetting(plateId, 'guiConfigTopic'), 'text', title: 'GUI config topic suffix', defaultValue: plate.guiConfigTopic ?: 'config/gui', required: true
                input plateSetting(plateId, 'screenIdleSeconds'), 'number', title: 'Turn screen off after idle seconds', defaultValue: plate.screenIdleSeconds ?: 60, required: true
                input plateSetting(plateId, 'screenBacklightBrightness'), 'number', title: 'Normal backlight brightness (1-255)', defaultValue: plate.screenBacklightBrightness ?: 42, required: true
                input plateSetting(plateId, 'screenWakeBrightness'), 'number', title: 'Wake brightness (1-255)', defaultValue: plate.screenWakeBrightness ?: 255, required: true

                paragraph '<b>Mapping rows</b>'
                rowIds(plateId).eachWithIndex { String rowId, int rowIndex ->
                    mappingInputs(plateId, rowId, rowIndex + 1)
                }
                input addRowButtonName(plateId), 'button', title: 'Add mapping row'
            }
        }
        section('Options') {
            input 'debugLogging', 'bool', title: 'Enable debug logging', defaultValue: false, required: false
        }
    }
}

void mappingInputs(String plateId, String rowId, int rowNumber) {
    Map row = rowState(plateId, rowId)
    String prefix = rowSetting(plateId, rowId, '')
    paragraph "<b>Row ${rowNumber}</b>"
    input "${prefix}enabled", 'bool', title: 'Enabled', defaultValue: row.enabled != false, required: true, submitOnChange: true
    input "${prefix}label", 'text', title: 'Label', defaultValue: row.label ?: "Row ${rowNumber}", required: false
    input "${prefix}type", 'enum',
        title: 'Type',
        options: [switch: 'Switch', dimmer: 'Dimmer', timerButton: 'Timer button'],
        defaultValue: row.type ?: 'switch',
        required: true,
        submitOnChange: true
    input "${prefix}incoming", 'text', title: 'Incoming topic suffix', defaultValue: row.incoming ?: '', required: false
    input "${prefix}outgoing", 'text', title: 'Outgoing command topic suffix', defaultValue: row.outgoing ?: '', required: false
    input "${prefix}labelTopic", 'text', title: 'Optional label topic suffix', defaultValue: row.labelTopic ?: '', required: false
    String type = settingString("${prefix}type", row.type ?: 'switch')
    if (type == 'dimmer') {
        input "${prefix}targetDimmer", 'capability.switchLevel', title: 'Target dimmer', multiple: false, required: false
    } else {
        input "${prefix}targetSwitch", 'capability.switch', title: type == 'timerButton' ? 'Timer target switch' : 'Target switch', multiple: false, required: false
    }
    if (type == 'timerButton') {
        input "${prefix}timerIncrementMinutes", 'number', title: 'Timer increment minutes', defaultValue: row.timerIncrementMinutes ?: 1, required: true
        input "${prefix}timerMaxMinutes", 'number', title: 'Timer maximum minutes', defaultValue: row.timerMaxMinutes ?: 3, required: true
        input "${prefix}stateLabelTopic", 'text', title: 'Optional state label topic suffix', defaultValue: row.stateLabelTopic ?: 'command/p1b13.text', required: false
        input "${prefix}createVirtualTimer", 'bool', title: 'Create virtual timer switch when target is blank', defaultValue: true, required: true
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
    activePlateConfigs().each { Map plate ->
        def connector = ensureConnector(plate)
        if (connector) {
            configureConnector(connector, plate)
            subscribe(connector, 'message', connectorMessageHandler)
        }
        plate.rows.findAll { it.enabled != false }.each { Map row ->
            def target = rowTarget(row)
            if (target) {
                subscribe(target, 'switch', targetSwitchHandler)
                if (row.type == 'dimmer') subscribe(target, 'level', targetLevelHandler)
            }
            def control = managedControl(plate, row)
            if (control) {
                subscribe(control, 'switch', controlSwitchHandler)
                if (row.type == 'dimmer') subscribe(control, 'level', controlLevelHandler)
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

void handleTimerMessage(Map plate, Map row, String payload) {
    if (!openHaspEventIsAction(payload) || suppressTimerBounce(row)) return
    long nowSeconds = epochSeconds()
    int incrementSeconds = Math.max(1, safeInt(row.timerIncrementMinutes, 1)) * 60
    int maxSeconds = Math.max(incrementSeconds, safeInt(row.timerMaxMinutes, 3) * 60)
    Map timers = (state.timers ?: [:]) as Map
    timers[row.key] = addTimerSeconds(nowSeconds, safeLong(timers[row.key], 0L), incrementSeconds, maxSeconds)
    state.timers = timers
    commandSwitch(rowTarget(row) ?: managedTimerSwitch(plate, row), 'on')
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
        plate.rows.findAll { it.type == 'timerButton' }.each { Map row ->
            long remaining = remainingTimerSeconds(row)
            if (remaining <= 0) {
                Map timers = (state.timers ?: [:]) as Map
                if (timers[row.key]) {
                    timers.remove(row.key)
                    state.timers = timers
                    commandSwitch(rowTarget(row) ?: managedTimerSwitch(plate, row), 'off')
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
        if (plate) syncControl(plate, row, 'on', level)
    } else {
        recordPending(row, 'switch', switchValue, source)
        commandSwitch(target, switchValue)
        if (plate) syncControl(plate, row, switchValue, currentTargetLevel(row))
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

void publishTimerState(Map plate, Map row) {
    long remaining = remainingTimerSeconds(row)
    int incrementSeconds = Math.max(1, safeInt(row.timerIncrementMinutes, 1)) * 60
    if (row.labelTopic) publishPlateTopic(plate, row.labelTopic, timerButtonText(remaining, incrementSeconds), false)
    if (row.stateLabelTopic) {
        def target = rowTarget(row) ?: managedTimerSwitch(plate, row)
        publishPlateTopic(plate, row.stateLabelTopic, remaining > 0 || target?.currentSwitch == 'on' ? 'ON' : 'OFF', false)
    }
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
        }
    }
}

def rowTarget(Map row) {
    row.type == 'dimmer' ? row.targetDimmer : row.targetSwitch
}

int currentTargetLevel(Map row) {
    Math.max(1, Math.min(100, safeInt(rowTarget(row)?.currentLevel, 100)))
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
    Map plates = (state.plates ?: [:]) as Map
    plates.keySet().each { String plateId ->
        Map plate = (plates[plateId] ?: [:]) as Map
        plate.enabled = settingEnabled(settings[plateSetting(plateId, 'enabled')], plate.enabled != false)
        ['label', 'plateName', 'baseTopic', 'brokerHost', 'mqttUsername', 'mqttPassword', 'idleTopic', 'backlightTopic', 'guiConfigTopic'].each { String key ->
            if (settings.containsKey(plateSetting(plateId, key))) plate[key] = settings[plateSetting(plateId, key)]
        }
        ['brokerPort', 'screenIdleSeconds', 'screenBacklightBrightness', 'screenWakeBrightness'].each { String key ->
            if (settings.containsKey(plateSetting(plateId, key))) plate[key] = safeInt(settings[plateSetting(plateId, key)], safeInt(plate[key], 0))
        }
        plate.createVirtualControls = settingEnabled(settings[plateSetting(plateId, 'createVirtualControls')], settingEnabled(plate.createVirtualControls, true))
        plate.rows = ((plate.rows ?: []) as List).collect { Map row ->
            String rowId = row.id
            String prefix = rowSetting(plateId, rowId, '')
            row.enabled = settingEnabled(settings["${prefix}enabled"], row.enabled != false)
            ['label', 'type', 'incoming', 'outgoing', 'labelTopic', 'stateLabelTopic'].each { String key ->
                if (settings.containsKey("${prefix}${key}")) row[key] = settings["${prefix}${key}"]
            }
            ['timerIncrementMinutes', 'timerMaxMinutes'].each { String key ->
                if (settings.containsKey("${prefix}${key}")) row[key] = safeInt(settings["${prefix}${key}"], safeInt(row[key], 1))
            }
            row.createVirtualTimer = settingEnabled(settings["${prefix}createVirtualTimer"], settingEnabled(row.createVirtualTimer, true))
            row.targetSwitch = settings["${prefix}targetSwitch"]
            row.targetDimmer = settings["${prefix}targetDimmer"]
            row.key = "${plateId}:${rowId}"
            row
        }
        plates[plateId] = plate
    }
    state.plates = plates
}

List<Map> activePlateConfigs() {
    syncStateFromSettings()
    ((state.plates ?: [:]) as Map).collect { String id, Map plate ->
        Map copy = new LinkedHashMap(plate)
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
        baseTopic: 'hasp',
        brokerHost: '10.0.0.65',
        brokerPort: 1883,
        enabled: true,
        createVirtualControls: true,
        idleTopic: 'state/idle',
        backlightTopic: 'command/backlight',
        guiConfigTopic: 'config/gui',
        screenIdleSeconds: 60,
        screenBacklightBrightness: 42,
        screenWakeBrightness: 255,
        rows: []
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
