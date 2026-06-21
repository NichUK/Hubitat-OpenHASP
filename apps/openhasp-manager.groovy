/*
 * Copyright 2026 NichUK
 *
 * Licensed under the Apache License, Version 2.0.
 */

definition(
    name: 'OpenHASP Manager',
    namespace: 'nichuk',
    author: 'NichUK',
    description: 'Binds reusable OpenHASP panel child devices to Hubitat devices.',
    category: 'Convenience',
    importUrl: 'https://raw.githubusercontent.com/NichUK/Hubitat-OpenHASP/main/apps/openhasp-manager.groovy',
    iconUrl: '',
    iconX2Url: '',
    singleInstance: false
)

preferences {
    page(name: 'mainPage', title: 'OpenHASP Manager', install: true, uninstall: true) {
        section('Panel') {
            input 'managePanelChild', 'bool', title: 'Create and manage the OpenHASP Panel child device', defaultValue: true, required: true
            input 'panelDevice', 'capability.refresh', title: 'Existing OpenHASP Panel device (optional)', multiple: false, required: false
            input 'mqttHost', 'text', title: 'MQTT broker host', required: true
            input 'mqttPort', 'number', title: 'MQTT broker port', defaultValue: 1883, required: true
            input 'mqttUsername', 'text', title: 'MQTT username', required: false
            input 'mqttPassword', 'password', title: 'MQTT password', required: false
            input 'plateName', 'text', title: 'OpenHASP plate name', defaultValue: 'bathroom_panel', required: true
            input 'idleSeconds', 'number', title: 'Screen-off idle seconds', defaultValue: 60, required: true
            input 'timerIncrementMinutes', 'number', title: 'UFH timer increment minutes', defaultValue: 1, required: true
            input 'timerMaxMinutes', 'number', title: 'UFH timer maximum minutes', defaultValue: 3, required: true
        }
        section('Office lighting circuit') {
            input 'officeTarget', 'capability.switchLevel', title: 'Hubitat target dimmer', multiple: false, required: false
            input 'officeSwitchObjectId', 'text', title: 'OpenHASP switch object id', defaultValue: 'p1b42', required: false
            input 'officeDimmerObjectId', 'text', title: 'OpenHASP dimmer object id', defaultValue: 'p1b43', required: false
            input 'officeLevelLabelObjectId', 'text', title: 'OpenHASP level label object id', defaultValue: 'p1b44', required: false
        }
        section('Bedroom lighting circuit') {
            input 'bedroomTarget', 'capability.switchLevel', title: 'Hubitat target dimmer', multiple: false, required: false
            input 'bedroomSwitchObjectId', 'text', title: 'OpenHASP switch object id', defaultValue: 'p1b52', required: false
            input 'bedroomDimmerObjectId', 'text', title: 'OpenHASP dimmer object id', defaultValue: 'p1b53', required: false
            input 'bedroomLevelLabelObjectId', 'text', title: 'OpenHASP level label object id', defaultValue: 'p1b54', required: false
        }
        section('Underfloor heating timer') {
            input 'manageUfhVirtualSwitch', 'bool', title: 'Create and use a safe virtual UFH switch', defaultValue: true, required: true
            input 'ufhTargetSwitch', 'capability.switch', title: 'Existing Hubitat UFH target switch (optional)', multiple: false, required: false
            input 'ufhTimerObjectId', 'text', title: 'OpenHASP timer button object id', defaultValue: 'p1b21', required: false
            input 'ufhStateLabelObjectId', 'text', title: 'OpenHASP state label object id', defaultValue: 'p1b13', required: false
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
    initialize()
}

void initialize() {
    configureManagedDevices()
    subscribeTargets()
    syncAllTargetsToPanel()
}

void configureManagedDevices() {
    def panel = activePanelDevice()
    if (panel) {
        panel.configureFromManager(
            mqttHost ?: '',
            mqttPort ?: 1883,
            mqttUsername ?: '',
            mqttPassword ?: '',
            plateName ?: 'bathroom_panel',
            idleSeconds ?: 60,
            timerIncrementMinutes ?: 1,
            timerMaxMinutes ?: 3,
            "${debugLogging ?: false}"
        )
        panel.clearManagedControls()
        managerControls().each { String controlId, Map control ->
            panel.configureManagedControl(
                controlId,
                control.kind as String,
                control.name as String,
                control.role as String,
                control.labelObject as String
            )
        }
        panel.applyManagedConfiguration()
    }
    if (settings.manageUfhVirtualSwitch != false) {
        managedUfhSwitch()
    }
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
    if (ufhTargetSwitch) {
        subscribe(ufhTargetSwitch, 'switch', ufhTargetSwitchHandler)
    }
}

void handleOpenHaspControlEvent(panel, String controlId, String kind, Object value) {
    if (debugLogging) log.debug "Panel ${panel?.displayName} control ${controlId} ${kind} ${value}"
    if (controlId == (officeSwitchObjectId ?: 'p1b42')) {
        commandSwitch(officeTarget, "${value}")
    } else if (controlId == (officeDimmerObjectId ?: 'p1b43')) {
        commandLevel(officeTarget, value)
    } else if (controlId == (bedroomSwitchObjectId ?: 'p1b52')) {
        commandSwitch(bedroomTarget, "${value}")
    } else if (controlId == (bedroomDimmerObjectId ?: 'p1b53')) {
        commandLevel(bedroomTarget, value)
    } else if (controlId == (ufhTimerObjectId ?: 'p1b21')) {
        commandSwitch(activeUfhTarget(), "${value}")
        activePanelDevice()?.publishObjectText(ufhStateLabelObjectId ?: 'p1b13', "${value}" == 'on' ? 'ON' : 'OFF')
    }
}

void officeTargetSwitchHandler(evt) {
    activePanelDevice()?.publishObjectValue(officeSwitchObjectId ?: 'p1b42', evt.value == 'on' ? '1' : '0')
}

void officeTargetLevelHandler(evt) {
    activePanelDevice()?.publishObjectValue(officeDimmerObjectId ?: 'p1b43', "${evt.value}")
    activePanelDevice()?.publishObjectText(officeLevelLabelObjectId ?: 'p1b44', "${evt.value}")
}

void bedroomTargetSwitchHandler(evt) {
    activePanelDevice()?.publishObjectValue(bedroomSwitchObjectId ?: 'p1b52', evt.value == 'on' ? '1' : '0')
}

void bedroomTargetLevelHandler(evt) {
    activePanelDevice()?.publishObjectValue(bedroomDimmerObjectId ?: 'p1b53', "${evt.value}")
    activePanelDevice()?.publishObjectText(bedroomLevelLabelObjectId ?: 'p1b54', "${evt.value}")
}

void ufhTargetSwitchHandler(evt) {
    activePanelDevice()?.publishObjectText(ufhStateLabelObjectId ?: 'p1b13', evt.value == 'on' ? 'ON' : 'OFF')
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

void commandSwitch(device, String value) {
    if (!device) return
    if (debugLogging) log.debug "Commanding ${device.displayName} ${value}"
    value == 'on' ? device.on() : device.off()
}

void commandLevel(device, Object level) {
    if (!device) return
    int bounded = Math.max(1, Math.min(100, "${level}".toBigDecimal().intValue()))
    if (debugLogging) log.debug "Commanding ${device.displayName} level ${bounded}"
    device.setLevel(bounded)
    device.on()
}

def activePanelDevice() {
    if (panelDevice) {
        return panelDevice
    }
    if (settings.managePanelChild == false) {
        return null
    }
    String dni = "openhasp-${plateName ?: 'bathroom_panel'}"
    def child = getChildDevice(dni)
    if (!child) {
        child = addChildDevice('nichuk', 'OpenHASP Panel', dni, [
            name: "OpenHASP ${plateName ?: 'bathroom_panel'}",
            label: "OpenHASP ${plateName ?: 'bathroom_panel'}",
            isComponent: false
        ])
    }
    return child
}

def activeUfhTarget() {
    ufhTargetSwitch ?: (settings.manageUfhVirtualSwitch == false ? null : managedUfhSwitch())
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

Map managerControls() {
    [
        (ufhTimerObjectId ?: 'p1b21')       : [kind: 'button', name: 'Bathroom UFH Timer', role: 'timerButton', labelObject: ufhStateLabelObjectId ?: 'p1b13'],
        p1b22                              : [kind: 'setpoint', name: 'Bathroom UFH Setpoint', role: 'setpoint', labelObject: 'p1b23'],
        (officeSwitchObjectId ?: 'p1b42')  : [kind: 'switch', name: 'Bathroom Office Main Switch', role: 'officeSwitch'],
        (officeDimmerObjectId ?: 'p1b43')  : [kind: 'dimmer', name: 'Bathroom Office Main Dimmer', role: 'officeDimmer', labelObject: officeLevelLabelObjectId ?: 'p1b44'],
        (bedroomSwitchObjectId ?: 'p1b52') : [kind: 'switch', name: 'Bathroom Bedroom Main Switch', role: 'bedroomSwitch'],
        (bedroomDimmerObjectId ?: 'p1b53') : [kind: 'dimmer', name: 'Bathroom Bedroom Main Dimmer', role: 'bedroomDimmer', labelObject: bedroomLevelLabelObjectId ?: 'p1b54']
    ]
}
