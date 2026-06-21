# Hubitat OpenHASP

Reusable Hubitat binding apps for OpenHASP MQTT touch panels.

This package uses Hubitat's built-in MQTT Import Integration as the only MQTT connection. MQTT Import maps OpenHASP topics to normal Hubitat devices. `OpenHASP Manager` lets you add one `OpenHASP Panel` child app per plate, and each panel maps imported controls to ordinary Hubitat devices.

- MQTT Import owns the broker connection.
- MQTT Import devices represent panel controls such as switches, dimmer event streams, dimmer commands, and buttons.
- `OpenHASP Manager` is the parent app where panels are added.
- `OpenHASP Panel` child apps subscribe to imported devices, mirror real device state back to the panel, and run timers.
- Optional app-created virtual controls provide Hubitat/dashboard devices that command the real devices and stay mirrored with the panel.

The MCP server used during development is not part of the runtime. Once installed, this package runs entirely on the Hubitat hub and the MQTT broker.

## Current Bathroom Example

The default control map matches a 480x480 `bathroom_panel` page:

| OpenHASP object | MQTT Import device | Default purpose |
| --- | --- | --- |
| `p1b21` | PushableButton or Switch | Underfloor heating start/add-time button |
| `p1b42` | Switch | Office Main switch |
| `p1b43` | Switch raw event + SwitchLevel command | Office Main level |
| `p1b52` | Switch | Bedroom Main switch |
| `p1b53` | Switch raw event + SwitchLevel command | Bedroom Main level |

For testing, the timer defaults to a 1 minute increment and a 3 minute maximum. For production, set the manager app timer preferences to 60 and 180 minutes.

## Installation

### Hubitat Package Manager

Add this repository manifest to HPM:

```text
https://raw.githubusercontent.com/NichUK/Hubitat-OpenHASP/main/packageManifest.json
```

Install `Hubitat OpenHASP`.

### Manual Install

Install the app from the raw GitHub URL listed in `packageManifest.json`:

- `apps/openhasp-manager.groovy`

## Quick Start

1. In Hubitat MQTT Import Integration, connect to your MQTT broker.
2. Map OpenHASP control topics such as `hasp/bathroom_panel/state/p1b42` to Hubitat devices.
3. Open Apps, add `OpenHASP Manager`.
4. In `OpenHASP Manager`, choose `Add OpenHASP panel`.
5. Set the plate name and add mapping rows for each control group.
6. Select the MQTT Import panel devices and the real Hubitat target devices for each row.
7. Leave `Create virtual lighting controls for dashboards` enabled if you want Hubitat-facing control devices for the bound lights.
8. Leave `Create and use a safe virtual timer switch` enabled until the real heating actuator is identified.
9. Save the panel child app.

The app can create virtual lighting controls and the safe UFH virtual switch when requested. It does not connect directly to MQTT.

Use the app-created controls, the real target devices, or the physical OpenHASP panel for day-to-day control. The MQTT Import panel devices are best treated as transport devices for the OpenHASP controls; commanding them directly may update the panel without producing a panel-originated state event.

For dimmers, use separate MQTT Import devices for the raw OpenHASP slider event and the slider command endpoint. The command endpoint is output-only from the app's point of view, which prevents old dimmer reports from being fed back into the target control path. For switches, the app suppresses command echoes when one MQTT Import switch device is used for both state and command.

On Hubitat 2.5.0.159, MQTT Import does not expose an arbitrary string command capability. Switch and dimmer control can run through MQTT Import alone, but OpenHASP text labels such as the live timer countdown need either a future MQTT Import string command capability or an optional OpenHASP-specific MQTT driver.

See [Configuration](docs/configuration.md) for the full walkthrough.

![OpenHASP Manager configuration](docs/images/openhasp-manager-config.png)

## OpenHASP MQTT Requirements

The plate should publish and subscribe using topics like:

```text
hasp/<plateName>/state/<objectId>
hasp/<plateName>/command/<command-or-object-property>
```

For the bathroom panel:

```text
plateName = bathroom_panel
```

The OpenHASP device should already have its MQTT broker set to your broker and its page JSON uploaded. Hubitat MQTT Import should be connected to the same broker.

## Development

Run tests:

```powershell
./gradlew test
```

The core conversion and timer logic lives in `src/main/groovy/uk/co/nichuk/hubitat/openhasp/OpenHaspSupport.groovy`; the Hubitat deployable apps are in `apps/`.

## License

Apache License 2.0. See [LICENSE](LICENSE) and [NOTICE](NOTICE).
