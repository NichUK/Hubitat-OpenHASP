/*
 * Copyright 2026 NichUK
 *
 * Licensed under the Apache License, Version 2.0.
 */

metadata {
    definition(
        name: 'OpenHASP Text Label',
        namespace: 'nichuk',
        author: 'NichUK',
        importUrl: 'https://raw.githubusercontent.com/NichUK/Hubitat-OpenHASP/main/drivers/openhasp-text-label.groovy'
    ) {
        capability 'Actuator'
        capability 'Initialize'
        capability 'Notification'

        command 'setText', [[name: 'Text*', type: 'STRING']]
        command 'configureFromApp', [
            [name: 'Broker URI*', type: 'STRING'],
            [name: 'Username', type: 'STRING'],
            [name: 'Password', type: 'STRING'],
            [name: 'Command topic*', type: 'STRING'],
            [name: 'Retain text', type: 'BOOL']
        ]

        attribute 'text', 'string'
        attribute 'mqttStatus', 'string'
    }

    preferences {
        input name: 'brokerUri', type: 'text', title: 'MQTT broker URI', description: 'Example: tcp://10.0.0.65:1883', required: false
        input name: 'username', type: 'text', title: 'MQTT username', required: false
        input name: 'password', type: 'password', title: 'MQTT password', required: false
        input name: 'commandTopic', type: 'text', title: 'OpenHASP text command topic', description: 'Example: hasp/bathroom_panel/command/p1b44.text', required: false
        input name: 'retainText', type: 'bool', title: 'Retain label text messages', defaultValue: false, required: true
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
    try {
        interfaces.mqtt.disconnect()
    } catch (ignored) {
    }
}

void initialize() {
    connectMqtt()
}

void configureFromApp(String broker, String mqttUsername, String mqttPassword, String topic, Boolean retained = false) {
    if (hasText(broker)) {
        state.appBrokerUri = broker.trim()
    }
    if (hasText(mqttUsername)) {
        state.appUsername = mqttUsername.trim()
    }
    if (hasText(mqttPassword)) {
        state.appPassword = mqttPassword
    }
    if (hasText(topic)) {
        state.appCommandTopic = topic.trim()
    }
    state.appRetainText = retained ? true : false
    initialize()
}

void deviceNotification(String message) {
    setText(message)
}

void setText(String message) {
    publishText(message)
}

void publishText(String message) {
    String topic = resolvedCommandTopic()
    if (!topic) {
        log.warn 'Cannot publish OpenHASP label text because no command topic is configured'
        return
    }
    if (!ensureConnected()) {
        log.warn 'Cannot publish OpenHASP label text because MQTT is not connected'
        return
    }
    String text = message == null ? '' : "${message}"
    if (debugLogging) log.debug "Publishing label text to ${topic}: ${text}"
    interfaces.mqtt.publish(topic, text, 0, resolvedRetainText())
    sendEvent(name: 'text', value: text)
}

void parse(String description) {
    if (debugLogging) log.debug "MQTT message ignored: ${description}"
}

void mqttClientStatus(String message) {
    sendEvent(name: 'mqttStatus', value: "${message}".take(255))
    if ("${message}".startsWith('Error')) {
        runIn(10, 'initialize')
    }
}

boolean ensureConnected() {
    try {
        if (interfaces.mqtt.isConnected()) {
            return true
        }
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
    String broker = resolvedBrokerUri()
    if (!broker) {
        sendEvent(name: 'mqttStatus', value: 'Not configured')
        return
    }
    try {
        interfaces.mqtt.disconnect()
    } catch (ignored) {
    }
    try {
        interfaces.mqtt.connect(broker, mqttClientId(), resolvedUsername(), resolvedPassword())
        sendEvent(name: 'mqttStatus', value: 'Connecting')
    } catch (Exception e) {
        sendEvent(name: 'mqttStatus', value: "Error: ${e.message}".take(255))
        log.warn "MQTT connect failed for ${device.displayName}: ${e.message}"
    }
}

String mqttClientId() {
    "hubitat-openhasp-label-${device.id ?: device.deviceNetworkId}".replaceAll(/[^A-Za-z0-9_-]/, '-')
}

String resolvedBrokerUri() {
    firstText(brokerUri, state.appBrokerUri)
}

String resolvedUsername() {
    firstText(username, state.appUsername) ?: null
}

String resolvedPassword() {
    firstText(password, state.appPassword) ?: null
}

String resolvedCommandTopic() {
    firstText(commandTopic, state.appCommandTopic)
}

boolean resolvedRetainText() {
    if (retainText != null) {
        return retainText instanceof Boolean ? retainText : "${retainText}".toBoolean()
    }
    state.appRetainText ? true : false
}

String firstText(Object first, Object second) {
    [first, second].find { Object value -> value != null && "${value}".trim() }?.toString()?.trim()
}

boolean hasText(Object value) {
    value != null && "${value}".trim()
}
