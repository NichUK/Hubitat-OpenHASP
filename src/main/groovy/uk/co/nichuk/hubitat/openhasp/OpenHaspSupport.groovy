package uk.co.nichuk.hubitat.openhasp

import groovy.json.JsonSlurper

class OpenHaspSupport {
    static final Set<String> ACTION_EVENTS = ['up', 'changed'] as Set

    static Map parseEvent(String payload) {
        if (payload == null || payload.trim().isEmpty()) {
            return [:]
        }
        def parsed = new JsonSlurper().parseText(payload)
        parsed instanceof Map ? parsed as Map : [:]
    }

    static boolean isActionEvent(Map event) {
        ACTION_EVENTS.contains("${event?.event ?: ''}".toString())
    }

    static int safeInt(Object value, int fallback = 0) {
        try {
            return "${value}".toBigDecimal().intValue()
        } catch (ignored) {
            return fallback
        }
    }

    static BigDecimal safeDecimal(Object value, BigDecimal fallback = 0G) {
        try {
            return "${value}".toBigDecimal()
        } catch (ignored) {
            return fallback
        }
    }

    static int clamp(int value, int min, int max) {
        Math.max(min, Math.min(max, value))
    }

    static int levelToBrightness(Object level) {
        int bounded = clamp(safeInt(level, 100), 1, 100)
        clamp(Math.round(bounded * 254 / 100) as int, 1, 254)
    }

    static int brightnessToLevel(Object brightness) {
        int bounded = clamp(safeInt(brightness, 254), 1, 254)
        clamp(Math.round(bounded * 100 / 254) as int, 1, 100)
    }

    static String switchText(Object value) {
        normalizeSwitchValue(value) == 'on' ? 'ON' : 'OFF'
    }

    static boolean truthy(Object value) {
        if (value instanceof Boolean) {
            return value
        }
        if (value instanceof Number) {
            return value != 0
        }
        String text = "${value}".trim().toLowerCase()
        text in ['1', 'true', 'on', 'yes']
    }

    static String normalizeSwitchValue(Object value) {
        Object normalized = openHaspPayloadValue(value)
        if (normalized instanceof Boolean) {
            return normalized ? 'on' : 'off'
        }
        if (normalized instanceof Number) {
            return normalized != 0 ? 'on' : 'off'
        }
        String text = "${normalized}".trim().toLowerCase()
        text in ['1', 'true', 'on', 'yes'] ? 'on' : 'off'
    }

    static int normalizeLevelValue(Object value, int fallback = 100) {
        safeInt(openHaspPayloadValue(value), fallback)
    }

    static String normalizeIdleValue(Object value) {
        Object normalized = openHaspPayloadValue(value)
        String text = "${normalized ?: ''}".trim().toLowerCase()
        if (text in ['long', 'idle', 'on', '1', 'true', 'yes']) {
            return 'idle'
        }
        if (text in ['off', 'active', '0', 'false', 'no']) {
            return 'active'
        }
        text ? text : 'active'
    }

    static Object openHaspPayloadValue(Object value) {
        if (value == null || !(value instanceof CharSequence)) {
            return value
        }
        String text = value.toString().trim()
        if (!text.startsWith('{')) {
            return value
        }
        try {
            def parsed = new JsonSlurper().parseText(text)
            if (parsed instanceof Map && parsed.containsKey('val')) {
                return parsed.val
            }
        } catch (ignored) {
        }
        value
    }

    static long addTimerSeconds(Long nowEpochSeconds, Long currentDeadlineEpochSeconds, int incrementSeconds, int maxSeconds) {
        long remaining = remainingSeconds(nowEpochSeconds, currentDeadlineEpochSeconds)
        long nextRemaining = Math.min(maxSeconds as long, remaining + incrementSeconds)
        nowEpochSeconds + nextRemaining
    }

    static long remainingSeconds(Long nowEpochSeconds, Long deadlineEpochSeconds) {
        if (!deadlineEpochSeconds) {
            return 0L
        }
        Math.max(0L, deadlineEpochSeconds - nowEpochSeconds)
    }

    static String timerButtonText(long remainingSeconds, int incrementSeconds) {
        if (remainingSeconds <= 0) {
            return "Start ${Math.max(1, Math.round(incrementSeconds / 60D) as int)}m"
        }
        long minutes = Math.floor(remainingSeconds / 60D) as long
        long seconds = remainingSeconds % 60
        "${minutes}:${seconds.toString().padLeft(2, '0')}"
    }

    static boolean settingEnabled(Object value, boolean defaultValue = true) {
        if (value == null) {
            return defaultValue
        }
        if (value instanceof Boolean) {
            return value
        }
        !("${value}".trim().toLowerCase() in ['false', '0', 'no', 'off'])
    }

    static Map defaultBathroomControls() {
        [
            'p1b21': [kind: 'button', name: 'Bathroom UFH Timer', role: 'timerButton', labelObject: 'p1b13'],
            'p1b22': [kind: 'setpoint', name: 'Bathroom UFH Setpoint', role: 'setpoint', labelObject: 'p1b23'],
            'p1b42': [kind: 'switch', name: 'Bathroom Office Main Switch', role: 'officeSwitch'],
            'p1b43': [kind: 'dimmer', name: 'Bathroom Office Main Dimmer', role: 'officeDimmer', labelObject: 'p1b44'],
            'p1b52': [kind: 'switch', name: 'Bathroom Bedroom Main Switch', role: 'bedroomSwitch'],
            'p1b53': [kind: 'dimmer', name: 'Bathroom Bedroom Main Dimmer', role: 'bedroomDimmer', labelObject: 'p1b54']
        ]
    }
}
