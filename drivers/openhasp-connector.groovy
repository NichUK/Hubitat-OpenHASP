/*
 * Copyright 2026 NichUK
 *
 * Licensed under the Apache License, Version 2.0.
 */

import groovy.json.JsonOutput

metadata {
    definition(
        name: 'OpenHASP Connector',
        namespace: 'nichuk',
        author: 'NichUK',
        importUrl: 'https://raw.githubusercontent.com/NichUK/Hubitat-OpenHASP/main/drivers/openhasp-connector.groovy'
    ) {
        capability 'Actuator'
        capability 'Initialize'

        command 'configureFromApp', [
            [name: 'Plate name*', type: 'STRING'],
            [name: 'Base topic*', type: 'STRING'],
            [name: 'Broker host*', type: 'STRING'],
            [name: 'Broker port*', type: 'NUMBER'],
            [name: 'Username', type: 'STRING'],
            [name: 'Password', type: 'STRING']
        ]
        command 'publishTopic', [
            [name: 'Topic*', type: 'STRING'],
            [name: 'Payload', type: 'STRING'],
            [name: 'Retain', type: 'BOOL']
        ]
        command 'publishCommand', [
            [name: 'Command suffix*', type: 'STRING'],
            [name: 'Payload', type: 'STRING'],
            [name: 'Retain', type: 'BOOL']
        ]
        command 'publishConfig', [
            [name: 'Config suffix*', type: 'STRING'],
            [name: 'Payload', type: 'STRING'],
            [name: 'Retain', type: 'BOOL']
        ]

        attribute 'message', 'string'
        attribute 'mqttStatus', 'string'
        attribute 'plateName', 'string'
        attribute 'baseTopic', 'string'
        attribute 'broker', 'string'
        attribute 'lastTopic', 'string'
        attribute 'lastPayload', 'string'
    }

    preferences {
        input name: 'debugLogging', type: 'bool', title: 'Enable debug logging', defaultValue: false, required: false
    }
}

void installed() {
    initialize()
}

void updated() {
    initialize()
}

void uninstalled() {
    disconnectMqtt()
}

void configureFromApp(String plate, String base, String host, Object port, String username = '', String password = '') {
    state.plateName = cleanSegment(plate) ?: 'panel'
    state.baseTopic = cleanSegment(base) ?: 'hasp'
    state.brokerHost = "${host ?: ''}".trim()
    state.brokerPort = safeInt(port, 1883)
    state.username = "${username ?: ''}".trim()
    state.password = "${password ?: ''}"
    sendEvent(name: 'plateName', value: state.plateName)
    sendEvent(name: 'baseTopic', value: state.baseTopic)
    sendEvent(name: 'broker', value: "${state.brokerHost}:${state.brokerPort}")
    initialize()
}

void initialize() {
    connectMqtt()
}

void parse(String description) {
    Map message = parseMqttMessage(description)
    if (!message.topic) return
    state.messageSequence = safeLong(state.messageSequence, 0L) + 1L
    String payload = "${message.payload ?: ''}"
    Map event = [
        sequence: state.messageSequence,
        topic: "${message.topic}",
        payload: payload,
        at: now()
    ]
    if (debugLogging) log.debug "MQTT ${event.topic}: ${payload}"
    sendEvent(name: 'lastTopic', value: event.topic)
    sendEvent(name: 'lastPayload', value: payload.take(1024))
    sendEvent(name: 'message', value: JsonOutput.toJson(event), isStateChange: true)
}

void mqttClientStatus(String message) {
    String value = "${message ?: ''}".take(255)
    sendEvent(name: 'mqttStatus', value: value)
    if (value.toLowerCase().startsWith('error')) {
        runIn(10, 'initialize')
    }
}

void publishCommand(String suffix, String payload = '', Boolean retained = false) {
    publishTopic(topicFor('command', suffix), payload, retained)
}

void publishConfig(String suffix, String payload = '', Boolean retained = false) {
    publishTopic(topicFor('config', suffix), payload, retained)
}

void publishTopic(String topic, String payload = '', Boolean retained = false) {
    if (!topic?.trim()) return
    if (!ensureConnected()) {
        log.warn "Cannot publish ${topic}; MQTT is not connected"
        return
    }
    String body = payload == null ? '' : "${payload}"
    if (debugLogging) log.debug "Publishing ${topic}: ${body}"
    interfaces.mqtt.publish(topic.trim(), body, 0, retained ? true : false)
}

boolean ensureConnected() {
    try {
        if (interfaces.mqtt.isConnected()) return true
    } catch (ignored) {
    }
    connectMqtt()
    try {
        return interfaces.mqtt.isConnected()
    } catch (ignored) {
        return false
    }
}

void connectMqtt() {
    if (!state.brokerHost) {
        sendEvent(name: 'mqttStatus', value: 'Not configured')
        return
    }
    disconnectMqtt()
    try {
        interfaces.mqtt.connect("tcp://${state.brokerHost}:${safeInt(state.brokerPort, 1883)}", clientId(), state.username ?: null, state.password ?: null)
        subscribeTopics()
        sendEvent(name: 'mqttStatus', value: 'Connecting')
    } catch (Exception e) {
        sendEvent(name: 'mqttStatus', value: "Error: ${e.message}".take(255))
        log.warn "MQTT connect failed for ${device.displayName}: ${e.message}"
    }
}

void disconnectMqtt() {
    try {
        interfaces.mqtt.disconnect()
    } catch (ignored) {
    }
}

void subscribeTopics() {
    String root = "${state.baseTopic ?: 'hasp'}/${state.plateName ?: 'panel'}"
    interfaces.mqtt.subscribe("${root}/state/#")
    interfaces.mqtt.subscribe("${root}/LWT")
}

Map parseMqttMessage(String description) {
    try {
        def parsed = interfaces.mqtt.parseMessage(description)
        return [
            topic: parsed.topic,
            payload: parsed.payload
        ]
    } catch (ignored) {
    }
    Map result = [:]
    "${description ?: ''}".split(',').each { String part ->
        int idx = part.indexOf(':')
        if (idx > 0) {
            String key = part.substring(0, idx).trim()
            String value = part.substring(idx + 1).trim()
            if (key in ['topic', 'payload']) result[key] = value
        }
    }
    result
}

String topicFor(String family, String suffix) {
    String clean = "${suffix ?: ''}".trim()
    if (clean.startsWith("${state.baseTopic}/")) return clean
    if (clean.startsWith("${family}/")) clean = clean.substring(family.length() + 1)
    "${state.baseTopic ?: 'hasp'}/${state.plateName ?: 'panel'}/${family}/${clean}"
}

String clientId() {
    "hubitat-openhasp-${device.id ?: device.deviceNetworkId}".replaceAll(/[^A-Za-z0-9_-]/, '-')
}

String cleanSegment(Object value) {
    "${value ?: ''}".trim().replaceAll(/^\/+|\/+$/, '')
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
