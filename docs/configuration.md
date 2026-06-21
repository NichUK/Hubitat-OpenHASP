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

The Hubitat driver subscribes to:

```text
hasp/bathroom_panel/state/#
```

and publishes commands to:

```text
hasp/bathroom_panel/command/...
```

## 2. Install the Hubitat Package

Install via Hubitat Package Manager using:

```text
https://raw.githubusercontent.com/NichUK/Hubitat-OpenHASP/main/packageManifest.json
```

Or paste the app and driver code manually from the files listed in the package manifest.

## 3. Create the Panel Device

Create a new virtual device:

- Type: `OpenHASP Panel`
- MQTT host: your broker host
- MQTT port: usually `1883`
- MQTT username/password: your broker credentials
- Plate name: `bathroom_panel`
- Screen-off idle seconds: `60`
- Timer increment minutes: `1` for testing, `60` for production
- Timer maximum minutes: `3` for testing, `180` for production

Save the device and run `Initialize`.

## 4. Check Child Devices

The panel driver creates child devices for the configured OpenHASP controls. For the bathroom example, expect:

- Bathroom UFH Timer
- Bathroom UFH Setpoint
- Bathroom Office Main Switch
- Bathroom Office Main Dimmer
- Bathroom Bedroom Main Switch
- Bathroom Bedroom Main Dimmer

## 5. Bind Hubitat Devices

Install `OpenHASP Manager` from Apps and select:

- OpenHASP Panel device: the parent panel device
- Office panel switch/dimmer children
- Office target: `Office Main`
- Bedroom panel switch/dimmer children
- Bedroom target: `Bedroom Main`
- UFH panel timer child
- UFH target: a safe virtual switch for testing

The default object IDs are already set for the bathroom panel page.

## 6. Acceptance Checks

- Tapping Office Main on the panel turns the Hubitat Office Main device on/off.
- Moving the Office Main slider changes the Hubitat Office Main level.
- Hubitat changes to Office Main update `p1b42`, `p1b43`, and `p1b44` on the panel.
- Bedroom Main behaves the same way with `p1b52`, `p1b53`, and `p1b54`.
- Tapping the UFH timer button adds 1 minute, then 2 minutes, then caps at 3 minutes.
- While the timer is active, the UFH virtual switch is on and `p1b13` shows `ON`.
- When the timer expires, the UFH virtual switch turns off and `p1b13` shows `OFF`.
- After 60 seconds without touch, the panel backlight turns off.
- A tap wakes the backlight and the panel resumes normal behavior.
