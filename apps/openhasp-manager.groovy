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
            input 'panelDevice', 'capability.refresh', title: 'OpenHASP Panel device', multiple: false, required: true
        }
        section('Office lighting circuit') {
            input 'officePanelSwitch', 'capability.switch', title: 'Panel switch child', multiple: false, required: false
            input 'officePanelDimmer', 'capability.switchLevel', title: 'Panel dimmer child', multiple: false, required: false
            input 'officeTarget', 'capability.switchLevel', title: 'Hubitat target dimmer', multiple: false, required: false
            input 'officeSwitchObjectId', 'text', title: 'OpenHASP switch object id', defaultValue: 'p1b42', required: false
            input 'officeDimmerObjectId', 'text', title: 'OpenHASP dimmer object id', defaultValue: 'p1b43', required: false
            input 'officeLevelLabelObjectId', 'text', title: 'OpenHASP level label object id', defaultValue: 'p1b44', required: false
        }
        section('Bedroom lighting circuit') {
            input 'bedroomPanelSwitch', 'capability.switch', title: 'Panel switch child', multiple: false, required: false
            input 'bedroomPanelDimmer', 'capability.switchLevel', title: 'Panel dimmer child', multiple: false, required: false
            input 'bedroomTarget', 'capability.switchLevel', title: 'Hubitat target dimmer', multiple: false, required: false
            input 'bedroomSwitchObjectId', 'text', title: 'OpenHASP switch object id', defaultValue: 'p1b52', required: false
            input 'bedroomDimmerObjectId', 'text', title: 'OpenHASP dimmer object id', defaultValue: 'p1b53', required: false
            input 'bedroomLevelLabelObjectId', 'text', title: 'OpenHASP level label object id', defaultValue: 'p1b54', required: false
        }
        section('Underfloor heating timer') {
            input 'ufhPanelTimer', 'capability.switch', title: 'Panel timer child', multiple: false, required: false
            input 'ufhTargetSwitch', 'capability.switch', title: 'Hubitat UFH target switch', multiple: false, required: false
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
    subscribePanelInputs()
    subscribeTargets()
    syncAllTargetsToPanel()
}

void subscribePanelInputs() {
    if (officePanelSwitch) subscribe(officePanelSwitch, 'switch', officePanelSwitchHandler)
    if (officePanelDimmer) subscribe(officePanelDimmer, 'level', officePanelDimmerHandler)
    if (bedroomPanelSwitch) subscribe(bedroomPanelSwitch, 'switch', bedroomPanelSwitchHandler)
    if (bedroomPanelDimmer) subscribe(bedroomPanelDimmer, 'level', bedroomPanelDimmerHandler)
    if (ufhPanelTimer) subscribe(ufhPanelTimer, 'switch', ufhPanelTimerHandler)
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

void officePanelSwitchHandler(evt) {
    commandSwitch(officeTarget, evt.value)
}

void officePanelDimmerHandler(evt) {
    commandLevel(officeTarget, evt.value)
}

void bedroomPanelSwitchHandler(evt) {
    commandSwitch(bedroomTarget, evt.value)
}

void bedroomPanelDimmerHandler(evt) {
    commandLevel(bedroomTarget, evt.value)
}

void ufhPanelTimerHandler(evt) {
    commandSwitch(ufhTargetSwitch, evt.value)
    panelDevice?.publishObjectText(ufhStateLabelObjectId ?: 'p1b13', evt.value == 'on' ? 'ON' : 'OFF')
}

void officeTargetSwitchHandler(evt) {
    panelDevice?.publishObjectValue(officeSwitchObjectId ?: 'p1b42', evt.value == 'on' ? '1' : '0')
}

void officeTargetLevelHandler(evt) {
    panelDevice?.publishObjectValue(officeDimmerObjectId ?: 'p1b43', "${evt.value}")
    panelDevice?.publishObjectText(officeLevelLabelObjectId ?: 'p1b44', "${evt.value}")
}

void bedroomTargetSwitchHandler(evt) {
    panelDevice?.publishObjectValue(bedroomSwitchObjectId ?: 'p1b52', evt.value == 'on' ? '1' : '0')
}

void bedroomTargetLevelHandler(evt) {
    panelDevice?.publishObjectValue(bedroomDimmerObjectId ?: 'p1b53', "${evt.value}")
    panelDevice?.publishObjectText(bedroomLevelLabelObjectId ?: 'p1b54', "${evt.value}")
}

void ufhTargetSwitchHandler(evt) {
    panelDevice?.publishObjectText(ufhStateLabelObjectId ?: 'p1b13', evt.value == 'on' ? 'ON' : 'OFF')
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
    if (ufhTargetSwitch) {
        ufhTargetSwitchHandler([value: ufhTargetSwitch.currentSwitch ?: 'off'])
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
