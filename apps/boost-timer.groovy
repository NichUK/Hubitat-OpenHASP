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
    description: 'Reusable boost timer that keeps a target switch on for an extendable duration.',
    category: 'Convenience',
    importUrl: 'https://raw.githubusercontent.com/NichUK/Hubitat-OpenHASP/main/apps/boost-timer.groovy',
    iconUrl: '',
    iconX2Url: ''
)

preferences {
    page(name: 'mainPage')
}

def mainPage() {
    dynamicPage(name: 'mainPage', title: 'Boost Timer', install: true, uninstall: true) {
        registerBoostTimerType()
        section('Timer') {
            input 'timerLabel', 'text', title: 'Timer label', defaultValue: 'Boost Timer', required: true
            input 'targetSwitch', 'capability.switch', title: 'Target switch to keep on', multiple: false, required: true
            input 'incrementMinutes', 'number', title: 'Add minutes per trigger', defaultValue: 60, required: true, width: 4
            input 'maximumMinutes', 'number', title: 'Maximum minutes', defaultValue: 180, required: true, width: 4
            input 'debounceMs', 'number', title: 'Debounce milliseconds', defaultValue: 500, required: true, width: 4
        }
        section('Optional triggers') {
            input 'triggerSwitches', 'capability.switch', title: 'Switches that add time when turned on', multiple: true, required: false
            input 'triggerButtons', 'capability.pushableButton', title: 'Buttons that add time when pushed', multiple: true, required: false
        }
        section('Device') {
            paragraph "Timer device: ${timerDevice()?.displayName ?: 'created when saved'}"
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
    deleteChildDeviceIfPresent()
}

void initialize() {
    unschedule()
    unsubscribe()
    subscribe(location, TYPE_REGISTRY_REQUEST_EVENT, typeRegistryRequestHandler)
    registerBoostTimerType()
    ensureTimerDevice()
    if (triggerSwitches) subscribe(triggerSwitches, 'switch.on', triggerHandler)
    if (triggerButtons) subscribe(triggerButtons, 'pushed', triggerHandler)
    updateTimerState()
    if (remainingSeconds() > 0) runIn(1, 'timerTick')
}

void triggerHandler(evt) {
    boost()
    if (evt?.name == 'switch' && evt?.device?.hasCommand('off')) {
        try {
            evt.device.off()
        } catch (ignored) {
            // Trigger reset is best-effort only.
        }
    }
}

void componentBoost(child) {
    boost()
}

void componentCancel(child) {
    cancel()
}

void boost() {
    if (suppressBounce()) return
    long now = nowSeconds()
    int increment = Math.max(1, safeInt(incrementMinutes, 60)) * 60
    int maximum = Math.max(increment, safeInt(maximumMinutes, 180) * 60)
    state.deadline = addTimerSeconds(now, safeLong(state.deadline, 0L), increment, maximum)
    targetSwitch?.on()
    updateTimerState()
    runIn(1, 'timerTick')
}

void cancel() {
    state.deadline = 0L
    targetSwitch?.off()
    updateTimerState()
    unschedule('timerTick')
}

void timerTick() {
    long remaining = remainingSeconds()
    if (remaining <= 0) {
        state.deadline = 0L
        targetSwitch?.off()
        updateTimerState()
        return
    }
    updateTimerState()
    runIn(1, 'timerTick')
}

void updateTimerState() {
    long remaining = remainingSeconds()
    String switchValue = remaining > 0 ? 'on' : 'off'
    String text = timerText(remaining)
    timerDevice()?.setTimerState(switchValue, remaining, text)
}

def ensureTimerDevice() {
    def existing = timerDevice()
    if (existing) {
        if (timerLabel && existing.displayName != timerLabel) existing.setLabel(timerLabel)
        return existing
    }
    addChildDevice('nichuk', 'Boost Timer Device', timerDni(), [name: timerLabel ?: 'Boost Timer', label: timerLabel ?: 'Boost Timer', isComponent: false])
}

def timerDevice() {
    getChildDevice(timerDni())
}

String timerDni() {
    "boost-timer-${app.id}"
}

void deleteChildDeviceIfPresent() {
    def child = timerDevice()
    if (child) deleteChildDevice(child.deviceNetworkId)
}

boolean suppressBounce() {
    long nowMs = now()
    long lastMs = safeLong(state.lastBoostAtMs, 0L)
    if (lastMs && nowMs - lastMs < Math.max(0, safeInt(debounceMs, 500))) return true
    state.lastBoostAtMs = nowMs
    false
}

long remainingSeconds() {
    Math.max(0L, safeLong(state.deadline, 0L) - nowSeconds())
}

long nowSeconds() {
    Math.floor(now() / 1000L) as long
}

long addTimerSeconds(Long nowEpochSeconds, Long currentDeadlineEpochSeconds, int incrementSeconds, int maxSeconds) {
    long nowValue = nowEpochSeconds ?: nowSeconds()
    long current = Math.max(nowValue, currentDeadlineEpochSeconds ?: 0L)
    Math.min(nowValue + maxSeconds, current + incrementSeconds)
}

String timerText(long remaining) {
    if (remaining <= 0) return "Start ${safeInt(incrementMinutes, 60)}m"
    long minutes = Math.floor(remaining / 60L) as long
    long seconds = remaining % 60L
    "${minutes}:${seconds.toString().padLeft(2, '0')}"
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
