# Configuration

## 1. Configure OpenHASP

On the OpenHASP device, configure MQTT with a node topic in this shape:

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
/hasp/bathroom_panel/state/#
```

MQTT Import publishes panel commands to:

```text
hasp/bathroom_panel/command/...
```

## 2. Configure Hubitat MQTT Integrations

Choose one MQTT topology before mapping panel controls:

- Hubitat built-in MQTT service: configure the OpenHASP plate to use Hubitat's MQTT service.
- External MQTT broker: install and enable both Hubitat MQTT Import Integration and Hubitat MQTT Export Integration, and point both integrations plus the OpenHASP plate at the same broker.

The `OpenHASP Manager` app includes a MQTT pre-flight section for this choice. For an external broker, complete the Import, Export, and same-broker confirmations before adding panel child apps.

## 3. Configure Hubitat MQTT Import

Install and enable Hubitat MQTT Import Integration, then connect it to the same broker as the OpenHASP plate.

Map OpenHASP topics to Hubitat devices. The bathroom panel uses these mappings:

| Device | Capability | State topic | Attribute | Command topic | Command payload |
| --- | --- | --- | --- | --- | --- |
| Bathroom Panel Office Main Switch | Switch | `hasp/bathroom_panel/state/p1b42` | `switch` | `hasp/bathroom_panel/command/p1b42.val` | `on=1`, `off=0` |
| Bathroom Panel Office Main Dimmer Event | Switch | `hasp/bathroom_panel/state/p1b43` | `switch` | optional | optional |
| Bathroom Panel Office Main Dimmer Command | SwitchLevel | none required | none | `hasp/bathroom_panel/command/p1b43.val` | `setLevel={{value}}` |
| Bathroom Panel Bedroom Main Switch | Switch | `hasp/bathroom_panel/state/p1b52` | `switch` | `hasp/bathroom_panel/command/p1b52.val` | `on=1`, `off=0` |
| Bathroom Panel Bedroom Main Dimmer Event | Switch | `hasp/bathroom_panel/state/p1b53` | `switch` | optional | optional |
| Bathroom Panel Bedroom Main Dimmer Command | SwitchLevel | none required | none | `hasp/bathroom_panel/command/p1b53.val` | `setLevel={{value}}` |
| Bathroom UFH Timer Button | PushableButton preferred, Switch fallback | `hasp/bathroom_panel/state/p1b21` | `pushed` or `switch` | none required | none |
| Bathroom Panel Idle State | Switch | `hasp/bathroom_panel/state/idle` | `switch` | none required | none |

For switch state value mappings, map OpenHASP payloads such as `{"event":"up","val":1}` to `on` and `{"event":"up","val":0}` to `off`. If the same MQTT Import switch device is used for both state events and commands, the panel app suppresses the command echo so mirrored panel updates do not command the target device a second time.

For the idle state device, map OpenHASP payload `long` to `on` and payload `off` to `off`. The panel app treats `on`/`long` as idle and `off` as active.

For dimmers, OpenHASP publishes slider events as JSON such as `{"event":"changed","val":38}`. MQTT Import does not currently extract JSON fields into numeric attributes, so create a Switch event device with no value mappings for the state topic and a separate SwitchLevel command device for the `.val` command topic. `OpenHASP Manager` parses the raw event device value and mirrors Hubitat state back through the command device. Do not subscribe the panel app to the SwitchLevel command device as an input; command devices are output endpoints and can echo older values back into the binding path.

On Hubitat 2.5.0.159, MQTT Import does not support arbitrary string command capabilities such as `Notification`. The manager app can use text-command devices if another driver exposes them, but MQTT Import alone cannot currently update OpenHASP label text for a live countdown.

To update labels from Hubitat, enable `Create OpenHASP MQTT text label devices` in the panel app and enter the broker URI, username, and password. The app will create `OpenHASP Text Label` child devices for rows that have a level label object id, then publish the target device's current level to topics such as `hasp/bathroom_panel/command/p1b44.text` and `hasp/bathroom_panel/command/p1b54.text`. It also creates timer label devices for `p1b21.text` and `p1b13.text` by default.

The same text-command driver is used for the OpenHASP backlight command topic. When screen idle handling is enabled, the app publishes `off` to `hasp/<plate>/command/backlight` when the imported idle state becomes `long`, and publishes `{"state":"on","brightness":255}` or the configured brightness when the idle state returns to `off`.

## 4. Install the Hubitat Package

Install via Hubitat Package Manager using:

```text
https://raw.githubusercontent.com/NichUK/Hubitat-OpenHASP/main/packageManifest.json
```

Or paste `apps/openhasp-manager.groovy` manually from the package manifest.

## 5. Create the Manager App and Add a Panel

Open Apps and add `OpenHASP Manager`.

In the MQTT pre-flight section:

- Select `Hubitat built-in MQTT service` if OpenHASP is using Hubitat's MQTT broker.
- Select `External MQTT broker` if OpenHASP is using another broker, then confirm that MQTT Import, MQTT Export, and OpenHASP are all on that broker.

Inside `OpenHASP Manager`, choose `Add OpenHASP panel`. Configure the child panel:

- Plate name: `bathroom_panel`
- Panel label: `Bathroom Panel`
- OpenHASP panel host/IP: `10.0.0.122` if you want Hubitat to configure the OpenHASP idle time over HTTP
- Timer increment minutes: `1` for testing, `60` for production
- Timer maximum minutes: `3` for testing, `180` for production
- Create virtual lighting controls for dashboards: enabled when you want app-created Hubitat control devices for the bound lights
- Manage screen backlight from OpenHASP idle state: enabled
- Panel idle state device: `Bathroom Panel Idle State`
- Turn screen off after idle seconds: `60`
- Normal backlight brightness: `42`
- Wake brightness: `255`
- Configure OpenHASP idle time over HTTP when saved: enabled only when the panel host/IP is set

Lighting mapping 1:

- Name: `Office Main`
- Panel switch event/command device: `Bathroom Panel Office Main Switch`
- Panel slider event device: `Bathroom Panel Office Main Dimmer Event`
- Panel slider command device: `Bathroom Panel Office Main Dimmer Command`
- Panel level label object id: `p1b44`
- Hubitat light/dimmer to control: `Office Main`

Lighting mapping 2:

- Name: `Bedroom Main`
- Panel switch event/command device: `Bathroom Panel Bedroom Main Switch`
- Panel slider event device: `Bathroom Panel Bedroom Main Dimmer Event`
- Panel slider command device: `Bathroom Panel Bedroom Main Dimmer Command`
- Panel level label object id: `p1b54`
- Hubitat light/dimmer to control: `Bedroom Main`

Timer mapping:

- Timer name: `UFH`
- Panel timer button or switch: the MQTT Import device mapped to `p1b21`
- Hubitat switch to keep on while timer is active: blank until the real heating actuator is identified
- Create and use a safe virtual timer switch: enabled for testing
- Timer button text object id: `p1b21`
- Timer state text object id: `p1b13`
- Timer text/state devices: optional overrides; leave blank to use the app-created text label devices

Save the child panel app. It does not create an MQTT connection; it uses the MQTT Import devices you selected.

## 6. Check Devices

MQTT Import devices should appear in Hubitat as normal devices. They represent the panel's MQTT controls and are used by the manager app as event and command endpoints.

When virtual lighting controls are enabled, the panel child app creates:

- `<Panel label> Office Main Control`
- `<Panel label> Bedroom Main Control`

Use these app-created controls, the real target devices, or the physical OpenHASP panel for day-to-day Hubitat control. The MQTT Import panel devices can update the panel directly, but they may not emit a panel-originated state event when commanded from Hubitat, so they are not the best dashboard-facing devices.

The panel child app also creates `<Panel label> UFH` when the safe testing switch is enabled.

## 7. Binding Model

The app binds by selected devices, not by direct MQTT topics. This keeps the MQTT connection in Hubitat MQTT Import and makes the child app reusable for any OpenHASP plate whose controls are mapped as Hubitat devices.

## 8. Acceptance Checks

- Tapping Office Main on the panel turns the Hubitat Office Main device on/off.
- Moving the Office Main slider changes the Hubitat Office Main level.
- Hubitat changes to Office Main update `p1b42` and `p1b43` on the panel.
- Commanding `<Panel label> Office Main Control` from Hubitat controls Office Main and updates the panel.
- Bedroom Main behaves the same way with `p1b52` and `p1b53`.
- Commanding `<Panel label> Bedroom Main Control` from Hubitat controls Bedroom Main and updates the panel.
- Tapping the UFH timer button adds 1 minute, then 2 minutes, then caps at 3 minutes.
- The UFH button text counts down while active and returns to `Start 1m` when idle.
- The UFH state label shows `ON` while the timer switch is on and `OFF` after expiry.
- Button down/up chatter and duplicate timer switch events inside the debounce window are ignored, so one physical tap adds one increment.
- While the timer is active, the UFH virtual switch is on.
- When the timer expires, the UFH virtual switch turns off.
- After 60 seconds without touch, the panel backlight turns off.
- A tap wakes the backlight and the panel resumes normal behavior.
