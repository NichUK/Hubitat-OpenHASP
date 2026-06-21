# Hubitat OpenHASP

Reusable Hubitat binding apps for OpenHASP MQTT touch panels.

This package uses Hubitat's built-in MQTT integrations as the Hubitat-facing MQTT layer. MQTT Import maps OpenHASP topics to normal Hubitat devices, and MQTT Export should be available when an external broker is used so Hubitat-originated device state can also be published. `OpenHASP Manager` lets you add one `OpenHASP Panel` child app per plate, and each panel maps imported controls to ordinary Hubitat devices.

- MQTT Import owns OpenHASP event ingestion from MQTT.
- MQTT Export publishes selected Hubitat device state when an external broker is used.
- MQTT Import devices represent panel controls such as switches, dimmer event streams, dimmer commands, and buttons.
- `OpenHASP Manager` is the parent app where panels are added.
- `OpenHASP Panel` child apps subscribe to imported devices, mirror real device state back to the panel, and run timers.
- Optional app-created virtual controls provide Hubitat/dashboard devices that command the real devices and stay mirrored with the panel.
- Optional app-created text label devices publish current level labels such as `p1b44.text` and `p1b54.text`, plus UFH timer labels such as `p1b21.text` and `p1b13.text`.
- Optional screen idle handling turns the OpenHASP backlight off from `state/idle` and wakes it on the next tap.

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
| `idle` | Switch | Screen idle/wake state |

For testing, the timer defaults to a 1 minute increment and a 3 minute maximum. For production, set the timer preferences to 60 and 180 minutes.

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

1. Decide whether OpenHASP will use Hubitat's built-in MQTT service or an external MQTT broker.
2. If using an external broker, install and enable both Hubitat MQTT Import Integration and MQTT Export Integration against the same broker.
3. In Hubitat MQTT Import Integration, map OpenHASP control topics such as `hasp/bathroom_panel/state/p1b42` to Hubitat devices.
4. Open Apps, add `OpenHASP Manager`.
5. Complete the MQTT pre-flight section in `OpenHASP Manager`.
6. Choose `Add OpenHASP panel`.
7. Set the plate name and add mapping rows for each control group.
8. Select the MQTT Import panel devices and the real Hubitat target devices for each row.
9. In screen idle/backlight settings, select the MQTT Import device mapped from `hasp/<plate>/state/idle` and set the idle timeout.
10. Leave `Create virtual lighting controls for dashboards` enabled if you want Hubitat-facing control devices for the bound lights.
11. Leave `Create and use a safe virtual timer switch` enabled until the real heating actuator is identified.
12. Save the panel child app.

The app can create virtual lighting controls and the safe UFH virtual switch when requested. It does not connect directly to MQTT.

Use the app-created controls, the real target devices, or the physical OpenHASP panel for day-to-day control. The MQTT Import panel devices are best treated as transport devices for the OpenHASP controls; commanding them directly may update the panel without producing a panel-originated state event.

For dimmers, use separate MQTT Import devices for the raw OpenHASP slider event and the slider command endpoint. The command endpoint is output-only from the app's point of view, which prevents old dimmer reports from being fed back into the target control path. For switches, the app suppresses command echoes when one MQTT Import switch device is used for both state and command.

On Hubitat 2.5.0.159, MQTT Import does not expose an arbitrary string command capability. Switch and dimmer control can run through MQTT Import alone. For OpenHASP text labels, enable `Create OpenHASP MQTT text label devices`; the package uses the included `OpenHASP Text Label` driver to publish string values from Hubitat to the level and timer label command topics.

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
