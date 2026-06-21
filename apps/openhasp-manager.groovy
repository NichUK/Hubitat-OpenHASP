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
        section('MQTT pre-flight') {
            paragraph mqttPreflightIntro()
            input 'mqttBrokerMode', 'enum',
                title: 'MQTT broker mode',
                options: [
                    hubitatBuiltIn: 'Hubitat built-in MQTT service',
                    external: 'External MQTT broker'
                ],
                defaultValue: 'external',
                required: true,
                submitOnChange: true

            if (mqttBrokerModeValue() == 'hubitatBuiltIn') {
                paragraph 'Using Hubitat built-in MQTT: configure the OpenHASP plate to connect to the Hubitat MQTT service. This gives Hubitat local read/write access through the built-in MQTT integrations.'
            } else {
                paragraph 'Using an external MQTT broker: install and configure both Hubitat MQTT Import Integration and Hubitat MQTT Export Integration against the same broker before adding panels.'
                input 'mqttImportReady', 'bool', title: 'MQTT Import Integration is installed, enabled, and connected to this broker', defaultValue: false, required: true, submitOnChange: true
                input 'mqttExportReady', 'bool', title: 'MQTT Export Integration is installed, enabled, and connected to this broker', defaultValue: false, required: true, submitOnChange: true
                input 'mqttSameBrokerReady', 'bool', title: 'OpenHASP, MQTT Import, and MQTT Export all use the same broker', defaultValue: false, required: true, submitOnChange: true
                paragraph mqttExternalReadinessMessage()
            }
        }
        section('Panels') {
            paragraph panelInstallMessage()
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

String mqttPreflightIntro() {
    'Choose how MQTT is hosted before adding panels. If Hubitat is hosting MQTT, the built-in service provides the broker. If MQTT is external, Hubitat needs Import for OpenHASP events and Export for Hubitat-originated device state publication.'
}

String mqttBrokerModeValue() {
    settings.mqttBrokerMode ?: 'external'
}

boolean mqttExternalReady() {
    mqttBrokerModeValue() != 'external' || (settingEnabled(settings.mqttImportReady, false) && settingEnabled(settings.mqttExportReady, false) && settingEnabled(settings.mqttSameBrokerReady, false))
}

String mqttExternalReadinessMessage() {
    mqttExternalReady()
        ? 'External MQTT pre-flight complete.'
        : 'External MQTT pre-flight is incomplete. Complete the Import, Export, and same-broker checks before relying on panel mappings.'
}

String panelInstallMessage() {
    String base = 'Add one child app per OpenHASP plate. Each panel maps imported controls to Hubitat devices.'
    mqttExternalReady() ? base : "${base} External MQTT pre-flight is still incomplete."
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
        panelHost: settings.panelHost ?: settings.panelIp,
        manageBacklight: manageBacklight != null ? manageBacklight : true,
        idleStateSwitch: idleStateSwitch,
        screenIdleSeconds: idleSeconds ?: 60,
        screenBacklightBrightness: screenBacklightBrightness ?: 42,
        screenWakeBrightness: screenWakeBrightness ?: 255,
        configurePanelGui: configurePanelGui != null ? configurePanelGui : false,
        backlightCommandDevice: backlightCommandDevice,
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
        panelHost: 'panelHost',
        manageBacklight: 'manageBacklight',
        idleStateSwitch: 'idleStateSwitch',
        screenIdleSeconds: 'idleSeconds',
        screenBacklightBrightness: 'screenBacklightBrightness',
        screenWakeBrightness: 'screenWakeBrightness',
        configurePanelGui: 'configurePanelGui',
        backlightCommandDevice: 'backlightCommandDevice',
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
