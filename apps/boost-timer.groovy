/*
 * Copyright 2026 NichUK
 *
 * Licensed under the Apache License, Version 2.0.
 */

import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import groovy.transform.Field

@Field static final String TYPE_REGISTRY_EVENT = 'openhasp:typeRegistry'
@Field static final String TYPE_REGISTRY_REQUEST_EVENT = 'openhasp:typeRegistryRequest'
@Field static final String TYPE_REGISTRY_PROTOCOL = 'openhasp.typeRegistry.v1'

definition(
    name: 'Boost Timer',
    namespace: 'nichuk',
    author: 'NichUK',
    description: 'Reusable boost timers that keep target switches on for extendable durations.',
    category: 'Convenience',
    importUrl: 'https://raw.githubusercontent.com/NichUK/Hubitat-OpenHASP/main/apps/boost-timer.groovy',
    iconUrl: '',
    iconX2Url: '',
    singleInstance: true
)

preferences {
    page(name: 'mainPage')
}

def mainPage() {
    dynamicPage(name: 'mainPage', title: '', install: true, uninstall: true) {
        ensureDefaultTimerState()
        applyPendingRemoveSettings()
        registerBoostTimerType()
        section('') {
            input 'addTimer', 'button', title: 'Add timer'
        }
        timerIds().eachWithIndex { String timerId, int index ->
            Map timer = timerState(timerId) ?: defaultTimer(timerId)
            String label = settingString(timerSetting(timerId, 'label'), timer.label ?: 'Boost Timer')
            section(label, hideable: true, hidden: index > 0) {
                input timerSetting(timerId, 'label'), 'text', title: 'Timer label', defaultValue: label, required: true, submitOnChange: true, width: 12
                input timerSetting(timerId, 'targetSwitch'), 'capability.switch', title: 'Target switch to keep on', multiple: false, required: false, width: 6
                input timerSetting(timerId, 'incrementMinutes'), 'number', title: 'Add minutes per trigger', defaultValue: timer.incrementMinutes ?: 60, required: true, width: 3
                input timerSetting(timerId, 'maximumMinutes'), 'number', title: 'Maximum minutes', defaultValue: timer.maximumMinutes ?: 180, required: true, width: 3
                input timerSetting(timerId, 'debounceMs'), 'number', title: 'Debounce milliseconds', defaultValue: timer.debounceMs ?: 500, required: true, width: 4
                input timerSetting(timerId, 'triggerSwitches'), 'capability.switch', title: 'Switches that add time when turned on', multiple: true, required: false, width: 12
                input timerSetting(timerId, 'triggerButtons'), 'capability.pushableButton', title: 'Buttons that add time when pushed', multiple: true, required: false, width: 12
                paragraph "Timer device: ${timerDevice(timerId)?.displayName ?: 'created when saved'}"
                if (timerId != 'default') input removeTimerButtonName(timerId), 'button', title: 'Remove timer'
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

void uninstalled() {
    unschedule()
    unsubscribe()
    unregisterBoostTimerType()
    childDevices.each { child ->
        try {
            if ("${child.deviceNetworkId}".startsWith("boost-timer-${app.id}")) deleteChildDevice(child.deviceNetworkId)
        } catch (ignored) {
            // Best effort during uninstall.
        }
    }
}

void appButtonHandler(String buttonName) {
    buttonName = normalizeButtonName(buttonName)
    if (buttonName == 'addTimer') {
        addTimer()
    } else if (buttonName.startsWith('removeTimer__')) {
        removeTimer(buttonName - 'removeTimer__')
    }
}

String normalizeButtonName(Object buttonName) {
    String value = "${buttonName ?: ''}"
    if (value.startsWith('settings[') && value.endsWith(']')) return value[9..-2]
    value
}

void applyPendingRemoveSettings() {
    timerIds().findAll { it != 'default' }.each { String timerId ->
        if (settings.containsKey(removeTimerButtonName(timerId))) removeTimer(timerId)
    }
}

void initialize() {
    unschedule()
    unsubscribe()
    ensureDefaultTimerState()
    syncStateFromSettings()
    subscribe(location, TYPE_REGISTRY_REQUEST_EVENT, typeRegistryRequestHandler)
    registerBoostTimerType()
    ensureTimerDevices()
    subscribeTimerTriggers()
    updateAllTimerStates()
    if (anyTimerRunning()) runIn(1, 'timerTick')
}

void subscribeTimerTriggers() {
    timerConfigs().each { Map timer ->
        def triggerSwitches = timerSettingDevices(timer.id, 'triggerSwitches')
        def triggerButtons = timerSettingDevices(timer.id, 'triggerButtons')
        if (triggerSwitches) subscribe(triggerSwitches, 'switch.on', triggerHandler)
        if (triggerButtons) subscribe(triggerButtons, 'pushed', triggerHandler)
    }
}

void triggerHandler(evt) {
    timerConfigs().findAll { Map timer -> triggerMatchesTimer(timer, evt) }.each { Map timer ->
        boostTimer(timer.id)
    }
    if (evt?.name == 'switch' && evt?.device?.hasCommand('off')) {
        try {
            evt.device.off()
        } catch (ignored) {
            // Trigger reset is best-effort only.
        }
    }
}

boolean triggerMatchesTimer(Map timer, evt) {
    String eventDeviceId = "${evt?.deviceId ?: evt?.device?.id ?: ''}"
    if (!eventDeviceId) return false
    (timerSettingDevices(timer.id, 'triggerSwitches') + timerSettingDevices(timer.id, 'triggerButtons')).any { "${it.id}" == eventDeviceId }
}

void componentBoost(child) {
    String timerId = timerIdForChild(child)
    if (timerId) boostTimer(timerId)
}

void componentCancel(child) {
    String timerId = timerIdForChild(child)
    if (timerId) cancelTimer(timerId)
}

void boost() {
    boostTimer('default')
}

void cancel() {
    cancelTimer('default')
}

void boostTimer(String timerId) {
    Map timer = timerState(timerId)
    if (!timer || suppressBounce(timer)) return
    long nowValue = nowSeconds()
    int increment = Math.max(1, safeInt(timer.incrementMinutes, 60)) * 60
    int maximum = Math.max(increment, safeInt(timer.maximumMinutes, 180) * 60)
    Map deadlines = deadlineState()
    deadlines[timerId] = addTimerSeconds(nowValue, safeLong(deadlines[timerId], 0L), increment, maximum)
    state.deadlines = deadlines
    timerTarget(timer)?.on()
    updateTimerState(timer)
    runIn(1, 'timerTick')
}

void cancelTimer(String timerId) {
    Map timer = timerState(timerId)
    if (!timer) return
    Map deadlines = deadlineState()
    deadlines[timerId] = 0L
    state.deadlines = deadlines
    timerTarget(timer)?.off()
    updateTimerState(timer)
    if (!anyTimerRunning()) unschedule('timerTick')
}

void timerTick() {
    boolean keepRunning = false
    timerConfigs().each { Map timer ->
        long remaining = remainingSeconds(timer)
        if (remaining <= 0 && safeLong(deadlineState()[timer.id], 0L) > 0L) {
            Map deadlines = deadlineState()
            deadlines[timer.id] = 0L
            state.deadlines = deadlines
            timerTarget(timer)?.off()
        }
        updateTimerState(timer)
        if (remainingSeconds(timer) > 0) keepRunning = true
    }
    if (keepRunning) runIn(1, 'timerTick')
}

void updateAllTimerStates() {
    timerConfigs().each { Map timer -> updateTimerState(timer) }
}

void updateTimerState(Map timer) {
    long remaining = remainingSeconds(timer)
    String switchValue = remaining > 0 ? 'on' : 'off'
    String text = timerText(timer, remaining)
    timerDevice(timer.id)?.setTimerState(switchValue, remaining, text)
}

void ensureTimerDevices() {
    timerConfigs().each { Map timer -> ensureTimerDevice(timer) }
}

def ensureTimerDevice(Map timer) {
    def existing = timerDevice(timer.id)
    String label = timer.label ?: 'Boost Timer'
    if (existing) {
        if (existing.displayName != label) existing.setLabel(label)
        return existing
    }
    addChildDevice('nichuk', 'Boost Timer Device', timerDni(timer.id), [name: label, label: label, isComponent: false])
}

def timerDevice(String timerId) {
    getChildDevice(timerDni(timerId))
}

String timerDni(String timerId) {
    timerId == 'default' ? "boost-timer-${app.id}" : "boost-timer-${app.id}-${timerId}"
}

void addTimer() {
    ensureDefaultTimerState()
    String id = "timer${now()}"
    Map timers = timerStateMap()
    timers[id] = defaultTimer(id, nextTimerLabel(timers.size() + 1))
    state.timers = timers
}

void removeTimer(String timerId) {
    if (timerId == 'default') return
    Map timers = timerStateMap()
    timers.remove(timerId)
    state.timers = timers
    Map deadlines = deadlineState()
    deadlines.remove(timerId)
    state.deadlines = deadlines
    Map lastBoosts = lastBoostState()
    lastBoosts.remove(timerId)
    state.lastBoostAtMs = lastBoosts
    def child = timerDevice(timerId)
    if (child) deleteChildDevice(child.deviceNetworkId)
}

boolean suppressBounce(Map timer) {
    long nowMs = now()
    Map lastBoosts = lastBoostState()
    long lastMs = safeLong(lastBoosts[timer.id], 0L)
    if (lastMs && nowMs - lastMs < Math.max(0, safeInt(timer.debounceMs, 500))) return true
    lastBoosts[timer.id] = nowMs
    state.lastBoostAtMs = lastBoosts
    false
}

long remainingSeconds(Map timer) {
    Math.max(0L, safeLong(deadlineState()[timer.id], 0L) - nowSeconds())
}

boolean anyTimerRunning() {
    timerConfigs().any { remainingSeconds(it) > 0 }
}

long nowSeconds() {
    Math.floor(now() / 1000L) as long
}

long addTimerSeconds(Long nowEpochSeconds, Long currentDeadlineEpochSeconds, int incrementSeconds, int maxSeconds) {
    long nowValue = nowEpochSeconds ?: nowSeconds()
    long current = Math.max(nowValue, currentDeadlineEpochSeconds ?: 0L)
    Math.min(nowValue + maxSeconds, current + incrementSeconds)
}

String timerText(Map timer, long remaining) {
    if (remaining <= 0) return "Start ${safeInt(timer.incrementMinutes, 60)}m"
    long minutes = Math.floor(remaining / 60L) as long
    long seconds = remaining % 60L
    "${minutes}:${seconds.toString().padLeft(2, '0')}"
}

void ensureDefaultTimerState(boolean addIfEmpty = true) {
    if (state.timers || !addIfEmpty) return
    state.timers = [default: defaultTimer('default', settingString('timerLabel', 'Boost Timer'))]
}

void syncStateFromSettings() {
    Map timers = timerStateMap()
    timers.keySet().each { String timerId ->
        Map timer = (timers[timerId] ?: [:]) as Map
        timer.id = timerId
        timer.label = settingString(timerSetting(timerId, 'label'), timer.label ?: 'Boost Timer')
        timer.incrementMinutes = safeInt(settings[timerSetting(timerId, 'incrementMinutes')], safeInt(timer.incrementMinutes, 60))
        timer.maximumMinutes = safeInt(settings[timerSetting(timerId, 'maximumMinutes')], safeInt(timer.maximumMinutes, 180))
        timer.debounceMs = safeInt(settings[timerSetting(timerId, 'debounceMs')], safeInt(timer.debounceMs, 500))
        timers[timerId] = timer
    }
    state.timers = timers
}

Map timerState(String timerId) {
    (timerStateMap()[timerId] ?: defaultTimer(timerId)) as Map
}

Map timerStateMap() {
    ((state.timers ?: [:]) as Map).collectEntries { key, value -> [("${key}"): (value ?: [:]) as Map] }
}

List<String> timerIds() {
    timerStateMap()
        .keySet()
        .collect { "${it}" }
        .findAll { !timerRemoveRequested(it) }
        .sort { a, b -> a == 'default' ? -1 : b == 'default' ? 1 : a <=> b }
}

List<Map> timerConfigs() {
    timerIds().collect { String timerId ->
        Map timer = timerState(timerId)
        timer.id = timerId
        timer.label = settingString(timerSetting(timerId, 'label'), timer.label ?: 'Boost Timer')
        timer.incrementMinutes = safeInt(settings[timerSetting(timerId, 'incrementMinutes')], safeInt(timer.incrementMinutes, 60))
        timer.maximumMinutes = safeInt(settings[timerSetting(timerId, 'maximumMinutes')], safeInt(timer.maximumMinutes, 180))
        timer.debounceMs = safeInt(settings[timerSetting(timerId, 'debounceMs')], safeInt(timer.debounceMs, 500))
        timer
    }
}

Map defaultTimer(String timerId, String label = 'Boost Timer') {
    [
        id: timerId,
        label: label ?: 'Boost Timer',
        incrementMinutes: safeInt(settings[timerSetting(timerId, 'incrementMinutes')], 60),
        maximumMinutes: safeInt(settings[timerSetting(timerId, 'maximumMinutes')], 180),
        debounceMs: safeInt(settings[timerSetting(timerId, 'debounceMs')], 500)
    ]
}

String nextTimerLabel(int index) {
    "Boost Timer ${Math.max(1, index)}"
}

Map deadlineState() {
    (state.deadlines ?: [:]) as Map
}

Map lastBoostState() {
    (state.lastBoostAtMs instanceof Map ? state.lastBoostAtMs : [:]) as Map
}

def timerTarget(Map timer) {
    settings[timerSetting(timer.id, 'targetSwitch')]
}

List timerSettingDevices(String timerId, String key) {
    def value = settings[timerSetting(timerId, key)]
    if (!value) return []
    value instanceof Collection ? value.findAll { it } : [value]
}

String timerSetting(String timerId, String key) {
    if (timerId == 'default') {
        Map legacy = [
            label: 'timerLabel',
            targetSwitch: 'targetSwitch',
            incrementMinutes: 'incrementMinutes',
            maximumMinutes: 'maximumMinutes',
            debounceMs: 'debounceMs',
            triggerSwitches: 'triggerSwitches',
            triggerButtons: 'triggerButtons'
        ]
        return legacy[key] ?: "timer_${timerId}_${key}"
    }
    "timer_${timerId}_${key}"
}

String removeTimerButtonName(String timerId) {
    "removeTimer__${timerId}"
}

boolean timerRemoveRequested(String timerId) {
    timerId != 'default' && (settings[removeTimerButtonName(timerId)] != null || staleBlankTimer(timerId))
}

boolean staleBlankTimer(String timerId) {
    if (timerId == 'default') return false
    if (settings[timerSetting(timerId, 'targetSwitch')]) return false
    if (!timerId.startsWith('timer')) return false
    long createdAt = safeLong(timerId - 'timer', 0L)
    createdAt > 0L && now() - createdAt > 300000L
}

String timerIdForChild(child) {
    String dni = "${child?.deviceNetworkId ?: ''}"
    timerIds().find { timerDni(it) == dni }
}

String settingString(String key, String fallback) {
    def value = settings[key]
    value == null || "${value}".trim() == '' ? fallback : "${value}".trim()
}

int safeInt(Object value, int fallback) {
    try {
        return value == null ? fallback : "${value}".toInteger()
    } catch (ignored) {
        return fallback
    }
}

long safeLong(Object value, long fallback) {
    try {
        return value == null ? fallback : "${value}".toLong()
    } catch (ignored) {
        return fallback
    }
}

void registerBoostTimerType() {
    publishTypeRegistryEvent('register', boostTimerTypeDefinitions())
}

void unregisterBoostTimerType() {
    publishTypeRegistryEvent('unregister', [:])
}

void typeRegistryRequestHandler(evt) {
    Map request = parseJsonMap(evt?.value)
    if (request.protocol != TYPE_REGISTRY_PROTOCOL) return
    if (request.action == 'discover') registerBoostTimerType()
}

void publishTypeRegistryEvent(String action, Map types) {
    try {
        sendLocationEvent(
            name: TYPE_REGISTRY_EVENT,
            value: JsonOutput.toJson([
                protocol: TYPE_REGISTRY_PROTOCOL,
                action: action,
                provider: registryProviderKey(),
                providerLabel: app.label ?: app.name,
                appId: "${app.id ?: ''}",
                types: types,
                at: now()
            ]),
            isStateChange: true
        )
    } catch (Exception e) {
        log.warn "Could not publish OpenHASP type registry event: ${e.class.simpleName}: ${e.message}"
    }
}

Map boostTimerTypeDefinitions() {
    [
        timerButton: [
            label: 'Boost timer',
            source: 'Boost Timer',
            capability: 'capability.pushableButton',
            direction: 'toHubitat',
            handler: 'boostTimer',
            integrationType: 'boostTimer',
            requiredAttributes: ['integrationType', 'openHaspRowType', 'displayText', 'remainingSeconds'],
            command: 'boost'
        ]
    ]
}

Map parseJsonMap(Object value) {
    try {
        def parsed = new JsonSlurper().parseText("${value ?: '{}'}")
        parsed instanceof Map ? parsed as Map : [:]
    } catch (ignored) {
        [:]
    }
}

String registryProviderKey() {
    "${app.name ?: 'Boost Timer'}:${app.id ?: app.label ?: 'timer'}"
}
