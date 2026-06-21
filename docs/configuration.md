# Configuration

## 1. Configure OpenHASP

On the OpenHASP device, configure MQTT so topics have this shape:

```text
hasp/%hostname%/%topic%
```

Set the plate name to the value you will use in Hubitat. The bathroom example uses:

```text
bathroom_panel
```

OpenHASP publishes state events to:

```text
hasp/bathroom_panel/state/#
```

Hubitat publishes commands/config back to:

```text
hasp/bathroom_panel/command/...
hasp/bathroom_panel/config/...
```

## 2. Install the Hubitat Package

Install via Hubitat Package Manager using:

```text
https://raw.githubusercontent.com/NichUK/Hubitat-OpenHASP/main/packageManifest.json
```

Or paste these files manually:

- `apps/openhasp-manager.groovy`
- `drivers/openhasp-connector.groovy`

## 3. Create the Manager App

Open Apps and add `OpenHASP Manager`.

The page contains one collapsible section per OpenHASP plate. Each section contains:

- plate label and plate name
- connector status and reconnect/refresh button
- compact mapping rows
- add/remove mapping controls

MQTT broker settings and screen idle/backlight settings live in their own collapsible sections above the plate sections.

The app stores plate configuration using stable internal plate IDs, so labels can be renamed without breaking child devices.

## 4. Bathroom Plate Defaults

The first install creates a `Bathroom Panel` section using:

- Plate name: `bathroom_panel`
- Timer increment minutes: `1` for testing, `60` for production
- Timer maximum minutes: `3` for testing, `180` for production
- Create virtual lighting controls: enabled
- Create virtual timer switch when target is blank: enabled

Mapping rows:

| Type | Label | Incoming | Outgoing | Label topic |
| --- | --- | --- | --- | --- |
| switch | Office Main | `state/p1b42` | `command/p1b42.val` | |
| dimmer | Office Main Level | `state/p1b43` | `command/p1b43.val` | `command/p1b44.text` |
| switch | Bedroom Main | `state/p1b52` | `command/p1b52.val` | |
| dimmer | Bedroom Main Level | `state/p1b53` | `command/p1b53.val` | `command/p1b54.text` |
| timerButton | Underfloor Heating | `state/p1b21` | | `command/p1b21.text` |

For the lighting rows, select the real Hubitat devices:

- Office switch and Office dimmer rows: `Office Main`
- Bedroom switch and Bedroom dimmer rows: `Bedroom Main`

For the timer row, leave the target blank to use the virtual UFH switch until the real heating actuator is identified.

## 5. MQTT Broker

Set these once for the manager:

- Base topic: `hasp`
- Host: your MQTT broker host or IP
- Port: usually `1883`
- Username/password: optional, depending on your broker

Each plate connector inherits these shared broker settings.

## 6. Screen Idle And Backlight

Default screen settings:

- Idle topic suffix: `state/idle`
- Backlight command topic suffix: `command/backlight`
- GUI config topic suffix: `config/gui`
- Turn screen off after idle seconds: `60`
- Normal backlight brightness: `42`
- Wake brightness: `255`

On save/reconnect, the app publishes retained GUI config JSON to:

```text
hasp/<plate>/config/gui
```

When the plate publishes `long`, `idle`, `on`, `1`, or `true` to `state/idle`, the app publishes `off` to `command/backlight`.

When the plate publishes `off`, `active`, `0`, or `false` to `state/idle`, the app publishes wake JSON to `command/backlight`.

## 7. Binding Model

Incoming MQTT messages are routed by plate topic prefix, then matched to a row by incoming suffix.

Switch rows parse OpenHASP `val` values and command the selected Hubitat switch.

Dimmer rows parse OpenHASP slider `val` values and command the selected Hubitat dimmer level.

Target device state changes publish back to OpenHASP command topics. The app deliberately avoids echoing panel-originated commands directly back to the same OpenHASP control; it waits for Hubitat target state.

Timer rows add the configured increment on each valid press, cap at the configured maximum, keep the selected or virtual timer switch on while active, and switch it off when the countdown expires.

## 8. Devices Created

Each plate creates:

- `<Plate label> Connector`

When virtual lighting controls are enabled, switch and dimmer rows also create:

- `<Plate label> <Row label> Control`

When a timer row has no selected target and virtual timer is enabled, the app creates:

- `<Plate label> <Timer label>`

## 9. Acceptance Checks

- Tapping Office Main on the panel turns the Hubitat Office Main device on/off.
- Moving the Office Main slider changes the Hubitat Office Main level.
- Hubitat changes to Office Main update `p1b42`, `p1b43`, and `p1b44` on the panel.
- Bedroom Main behaves the same way with `p1b52`, `p1b53`, and `p1b54`.
- Tapping the UFH timer button adds 1 minute, then 2 minutes, then caps at 3 minutes.
- The UFH button text counts down while active and returns to `Start 1m` when idle.
- The UFH state label shows `ON` while active and `OFF` after expiry.
- Button chatter inside the debounce window is ignored.
- After 60 seconds without touch, the panel backlight turns off.
- A tap wakes the backlight and the panel resumes normal behavior.
