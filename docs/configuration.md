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
https://raw.githubusercontent.com/NichUK/Hubitat-OpenHASP/main/repository.json
```

Or paste these files manually:

- `apps/openhasp-manager.groovy`
- `drivers/openhasp-connector.groovy`

Optional reusable boost timer support is packaged with:

- `apps/boost-timer.groovy`
- `drivers/boost-timer-device.groovy`

OpenHASP Manager has built-in standard row types. Optional apps can register additional row types by answering the manager's Hubitat Location Event discovery request. The Boost Timer row type appears in the OpenHASP row dropdown after the Boost Timer app has been installed/initialized and has answered discovery.

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

For the timer row, prefer selecting a `Boost Timer Device` created by the generic `Boost Timer` app directly in the row's `Timer target` field. Leave the target blank only for the built-in legacy fallback timer.

If `Boost timer` is not present in the row type dropdown, open or install the `Boost Timer` app once, then press `Refresh optional row types` in OpenHASP Manager.

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

Switch and dimmer are built from native Hubitat capabilities. Boost timer is different: it expects a target device that advertises Boost Timer metadata.

Additional native Hubitat capability row types:

| Type | Direction | Hubitat capability |
| --- | --- | --- |
| Button | OpenHASP to Hubitat | `PushableButton` |
| Lock | Both | `Lock` |
| Temperature | Hubitat to OpenHASP | `TemperatureMeasurement` |
| Humidity | Hubitat to OpenHASP | `RelativeHumidityMeasurement` |
| Illuminance | Hubitat to OpenHASP | `IlluminanceMeasurement` |
| Contact | Hubitat to OpenHASP | `ContactSensor` |
| Motion | Hubitat to OpenHASP | `MotionSensor` |

Target device state changes publish back to OpenHASP command topics. The app deliberately avoids echoing panel-originated commands directly back to the same OpenHASP control; it waits for Hubitat target state.

Timer rows should normally target a generic `Boost Timer Device`. OpenHASP sends a button press, the timer device owns the countdown and target switch, and OpenHASP mirrors the timer device `displayText` and switch state back to label topics.

The optional `Boost Timer` app is installed once. Inside it, add one named timer instance per timed output. Each instance has its own collapsible panel with timer label, target switch, minutes per trigger, maximum minutes, debounce, optional trigger switches/buttons, and one child `Boost Timer Device`.

If a timer row has no selected target, OpenHASP Manager uses its built-in legacy fallback timer. That keeps older installs working, but reusable installations should use a named instance in the separate `Boost Timer` app.

`Boost Timer Device` publishes metadata attributes:

- `integrationType`: `boostTimer`
- `openHaspRowType`: `timerButton`

## 8. Devices Created

Each plate creates:

- `<Plate label> Connector`

When virtual lighting controls are enabled, switch and dimmer rows also create:

- `<Plate label> <Row label> Control`

When a timer row has no selected target and virtual timer is enabled, the OpenHASP fallback creates:

- `<Plate label> <Timer label>`

Each timer instance in the optional `Boost Timer` app creates:

- `<Timer label>`

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
