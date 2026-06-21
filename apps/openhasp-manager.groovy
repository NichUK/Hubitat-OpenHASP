/*
 * Copyright 2026 NichUK
 *
 * Licensed under the Apache License, Version 2.0.
 */

definition(
    name: 'OpenHASP Manager',
    namespace: 'nichuk',
    author: 'NichUK',
    description: 'Adds and manages OpenHASP panels that use Hubitat MQTT Import devices.',
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
        section('Panels') {
            paragraph 'Add one child app per OpenHASP plate. MQTT Import remains the MQTT connection; each panel maps imported controls to Hubitat devices.'
            app(
                name: 'panels',
                appName: 'OpenHASP Panel',
                namespace: 'nichuk',
                title: 'Add OpenHASP panel',
                multiple: true
            )
        }
        section('Options') {
            input 'debugLogging', 'bool', title: 'Enable debug logging for migration helpers', defaultValue: false, required: false
            input 'cleanupLegacyBindings', 'button', title: 'Clean up old flat-app bindings'
            if (!getChildApps()?.size() && hasLegacyPanelSettings()) {
                input 'migrateLegacyPanel', 'button', title: 'Create panel from old settings'
            }
        }
    }
}

void installed() {
    initialize()
}

void updated() {
    initialize()
}

void initialize() {
    migrateLegacySettings()
}

void appButtonHandler(String buttonName) {
    if (buttonName == 'migrateLegacyPanel') {
        migrateLegacySettings()
    } else if (buttonName == 'cleanupLegacyBindings') {
        cleanupLegacyRuntime()
    }
}

void migrateLegacySettings() {
    cleanupLegacyRuntime()
    if (getChildApps()?.size()) return
    if (!hasLegacyPanelSettings()) return

    Map childSettings = [
        plateName: plateName ?: 'bathroom_panel',
        panelLabel: plateName ?: 'bathroom_panel',
        timerIncrementMinutes: timerIncrementMinutes ?: 1,
        timerMaxMinutes: timerMaxMinutes ?: 3,
        manageLightingControls: manageLightingControls != null ? manageLightingControls : true,
        manageTextLabels: manageTextLabels != null ? manageTextLabels : false,
        mqttBrokerUri: mqttBrokerUri,
        mqttUsername: mqttUsername,
        mqttPassword: mqttPassword,
        mqttRetainTextLabels: mqttRetainTextLabels != null ? mqttRetainTextLabels : false,
        manageUfhVirtualSwitch: manageUfhVirtualSwitch != null ? manageUfhVirtualSwitch : true,
        light1Name: 'Office Main',
        light1PanelSwitch: officePanelSwitch,
        light1PanelLevelEvent: officePanelDimmerEvent,
        light1PanelLevelCommand: officePanelDimmer,
        light1LevelTextObject: officeLevelTextObject,
        light1LevelTextDevice: officeLevelTextDevice,
        light1Target: officeTarget,
        light2Name: 'Bedroom Main',
        light2PanelSwitch: bedroomPanelSwitch,
        light2PanelLevelEvent: bedroomPanelDimmerEvent,
        light2PanelLevelCommand: bedroomPanelDimmer,
        light2LevelTextObject: bedroomLevelTextObject,
        light2LevelTextDevice: bedroomLevelTextDevice,
        light2Target: bedroomTarget,
        timerName: 'Bathroom UFH',
        timerPanelButton: ufhPanelButton,
        timerPanelSwitch: ufhPanelSwitch,
        timerTextDevice: ufhTimerTextDevice,
        timerStateTextDevice: ufhStateTextDevice,
        timerTargetSwitch: ufhTargetSwitch,
        debugLogging: debugLogging ?: false
    ]

    try {
        addChildApp('nichuk', 'OpenHASP Panel', "OpenHASP Panel - ${childSettings.plateName}", childSettings)
        if (debugLogging) log.debug "Migrated legacy OpenHASP Manager settings to child panel ${childSettings.plateName}"
    } catch (Exception e) {
        log.warn "Could not migrate legacy OpenHASP Manager settings automatically: ${e.message}"
    }
}

boolean hasLegacyPanelSettings() {
    [
        officePanelSwitch,
        officePanelDimmerEvent,
        officePanelDimmer,
        officeTarget,
        bedroomPanelSwitch,
        bedroomPanelDimmerEvent,
        bedroomPanelDimmer,
        bedroomTarget,
        ufhPanelButton,
        ufhPanelSwitch
    ].any { it != null }
}

void cleanupLegacyRuntime() {
    unsubscribe()
    unschedule()
}

Object legacySetting(String childSettingName) {
    Map aliases = [
        panelLabel: 'plateName',
        plateName: 'plateName',
        timerIncrementMinutes: 'timerIncrementMinutes',
        timerMaxMinutes: 'timerMaxMinutes',
        manageLightingControls: 'manageLightingControls',
        manageTextLabels: 'manageTextLabels',
        mqttBrokerUri: 'mqttBrokerUri',
        mqttUsername: 'mqttUsername',
        mqttPassword: 'mqttPassword',
        mqttRetainTextLabels: 'mqttRetainTextLabels',
        manageUfhVirtualSwitch: 'manageUfhVirtualSwitch',
        light1Name: 'legacyOfficeName',
        light1PanelSwitch: 'officePanelSwitch',
        light1PanelLevelEvent: 'officePanelDimmerEvent',
        light1PanelLevelCommand: 'officePanelDimmer',
        light1LevelTextObject: 'officeLevelTextObject',
        light1LevelTextDevice: 'officeLevelTextDevice',
        light1Target: 'officeTarget',
        light2Name: 'legacyBedroomName',
        light2PanelSwitch: 'bedroomPanelSwitch',
        light2PanelLevelEvent: 'bedroomPanelDimmerEvent',
        light2PanelLevelCommand: 'bedroomPanelDimmer',
        light2LevelTextObject: 'bedroomLevelTextObject',
        light2LevelTextDevice: 'bedroomLevelTextDevice',
        light2Target: 'bedroomTarget',
        timerName: 'legacyTimerName',
        timerPanelButton: 'ufhPanelButton',
        timerPanelSwitch: 'ufhPanelSwitch',
        timerTextDevice: 'ufhTimerTextDevice',
        timerStateTextDevice: 'ufhStateTextDevice',
        timerTargetSwitch: 'ufhTargetSwitch',
        debugLogging: 'debugLogging'
    ]
    String parentName = aliases[childSettingName] as String
    if (!parentName) return null
    if (childSettingName == 'panelLabel') return 'Bathroom Panel'
    if (parentName == 'legacyOfficeName') return 'Office Main'
    if (parentName == 'legacyBedroomName') return 'Bedroom Main'
    if (parentName == 'legacyTimerName') return 'UFH'
    settings[parentName]
}
