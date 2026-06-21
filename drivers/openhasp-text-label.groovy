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
        capability 'Notification'
        attribute 'text', 'string'
        command 'setText', [[name: 'Text*', type: 'STRING']]
    }
}

void installed() {
    sendEvent(name: 'text', value: '')
}

void deviceNotification(String message) {
    setText(message)
}

void setText(String message) {
    sendEvent(name: 'text', value: message == null ? '' : "${message}", isStateChange: true)
}
