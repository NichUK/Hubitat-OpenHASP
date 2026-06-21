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

## 2. Configure Hubitat MQTT Import

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

For switch state value mappings, map OpenHASP payloads such as `{"event":"up","val":1}` to `on` and `{"event":"up","val":0}` to `off`.

For dimmers, OpenHASP publishes slider events as JSON such as `{"event":"changed","val":38}`. MQTT Import does not currently extract JSON fields into numeric attributes, so create a Switch event device with no value mappings for the state topic and a separate SwitchLevel command device for the `.val` command topic. `OpenHASP Manager` parses the raw event device value and mirrors Hubitat state back through the command device.

On Hubitat 2.5.0.159, MQTT Import does not support arbitrary string command capabilities such as `Notification`. The manager app can use text-command devices if another driver exposes them, but MQTT Import alone cannot currently update OpenHASP label text for a live countdown.

## 3. Install the Hubitat Package

Install via Hubitat Package Manager using:

```text
https://raw.githubusercontent.com/NichUK/Hubitat-OpenHASP/main/packageManifest.json
```

Or paste `apps/openhasp-manager.groovy` manually from the package manifest.

## 4. Create the Manager App and Add a Panel

Open Apps and add `OpenHASP Manager`.

Inside `OpenHASP Manager`, choose `Add OpenHASP panel`. Configure the child panel:

- Plate name: `bathroom_panel`
- Panel label: `Bathroom Panel`
- Timer increment minutes: `1` for testing, `60` for production
- Timer maximum minutes: `3` for testing, `180` for production
- Create virtual lighting controls for dashboards: enabled when you want app-created Hubitat control devices for the bound lights

Lighting mapping 1:

- Name: `Office Main`
- Panel switch event/command device: `Bathroom Panel Office Main Switch`
- Panel slider event device: `Bathroom Panel Office Main Dimmer Event`
- Panel slider command device: `Bathroom Panel Office Main Dimmer Command`
- Hubitat light/dimmer to control: `Office Main`

Lighting mapping 2:

- Name: `Bedroom Main`
- Panel switch event/command device: `Bathroom Panel Bedroom Main Switch`
- Panel slider event device: `Bathroom Panel Bedroom Main Dimmer Event`
- Panel slider command device: `Bathroom Panel Bedroom Main Dimmer Command`
- Hubitat light/dimmer to control: `Bedroom Main`

Timer mapping:

- Timer name: `UFH`
- Panel timer button or switch: the MQTT Import device mapped to `p1b21`
- Hubitat switch to keep on while timer is active: blank until the real heating actuator is identified
- Create and use a safe virtual timer switch: enabled for testing
- Timer text/state devices: optional, only if supplied by a driver with an arbitrary text command

Save the child panel app. It does not create an MQTT connection; it uses the MQTT Import devices you selected.

## 5. Check Devices

MQTT Import devices should appear in Hubitat as normal devices. They represent the panel's MQTT controls and are used by the manager app as event and command endpoints.

When virtual lighting controls are enabled, the panel child app creates:

- `<Panel label> Office Main Control`
- `<Panel label> Bedroom Main Control`

Use these app-created controls, the real target devices, or the physical OpenHASP panel for day-to-day Hubitat control. The MQTT Import panel devices can update the panel directly, but they may not emit a panel-originated state event when commanded from Hubitat, so they are not the best dashboard-facing devices.

The panel child app also creates `<Panel label> UFH` when the safe testing switch is enabled.

## 6. Binding Model

The app binds by selected devices, not by direct MQTT topics. This keeps the MQTT connection in Hubitat MQTT Import and makes the child app reusable for any OpenHASP plate whose controls are mapped as Hubitat devices.

## 7. Acceptance Checks

- Tapping Office Main on the panel turns the Hubitat Office Main device on/off.
- Moving the Office Main slider changes the Hubitat Office Main level.
- Hubitat changes to Office Main update `p1b42` and `p1b43` on the panel.
- Commanding `<Panel label> Office Main Control` from Hubitat controls Office Main and updates the panel.
- Bedroom Main behaves the same way with `p1b52` and `p1b53`.
- Commanding `<Panel label> Bedroom Main Control` from Hubitat controls Bedroom Main and updates the panel.
- Tapping the UFH timer button adds 1 minute, then 2 minutes, then caps at 3 minutes.
- While the timer is active, the UFH virtual switch is on.
- When the timer expires, the UFH virtual switch turns off.
- After 60 seconds without touch, the panel backlight turns off.
- A tap wakes the backlight and the panel resumes normal behavior.
