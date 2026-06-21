# Hubitat OpenHASP

Reusable Hubitat integration for OpenHASP MQTT touch panels.

This package makes each OpenHASP plate a normal Hubitat device tree:

- `OpenHASP Panel` is the MQTT parent driver and owns the connection to the MQTT broker.
- Child devices represent panel controls such as switches, dimmers, setpoints, and timer buttons.
- `OpenHASP Manager` binds those child devices to ordinary Hubitat devices.

The MCP server used during development is not part of the runtime. Once installed, this package runs entirely on the Hubitat hub and the MQTT broker.

## Current Bathroom Example

The default control map matches a 480x480 `bathroom_panel` page:

| OpenHASP object | Child device | Default purpose |
| --- | --- | --- |
| `p1b21` | OpenHASP Timer Button | Underfloor heating start/add-time button |
| `p1b22` | OpenHASP Child Dimmer | Underfloor heating setpoint |
| `p1b42` | OpenHASP Child Switch | Office Main switch |
| `p1b43` | OpenHASP Child Dimmer | Office Main level |
| `p1b52` | OpenHASP Child Switch | Bedroom Main switch |
| `p1b53` | OpenHASP Child Dimmer | Bedroom Main level |

For testing, the timer defaults to a 1 minute increment and a 3 minute maximum. For production, set the panel driver's timer preferences to 60 and 180 minutes.

## Installation

### Hubitat Package Manager

Add this repository manifest to HPM:

```text
https://raw.githubusercontent.com/NichUK/Hubitat-OpenHASP/main/packageManifest.json
```

Install `Hubitat OpenHASP`.

### Manual Install

Install the app and drivers from the raw GitHub URLs listed in `packageManifest.json`:

- `apps/openhasp-manager.groovy`
- `drivers/openhasp-panel.groovy`
- `drivers/openhasp-child-switch.groovy`
- `drivers/openhasp-child-dimmer.groovy`
- `drivers/openhasp-timer-button.groovy`

## Quick Start

1. Create a Hubitat virtual switch for safe underfloor-heating testing.
2. Create a virtual device using the `OpenHASP Panel` driver.
3. Configure MQTT broker host, port, username, password, and `plateName`.
4. Keep the default control JSON for the bathroom example, or edit it for another plate.
5. Save preferences, then run `Initialize`.
6. Open Apps, add `OpenHASP Manager`, select the panel device, panel child devices, Office Main, Bedroom Main, and the UFH virtual switch.

See [Configuration](docs/configuration.md) for the full walkthrough.

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

The OpenHASP device should already have its MQTT broker set to your broker and its page JSON uploaded.

## Development

Run tests:

```powershell
./gradlew test
```

The core conversion and timer logic lives in `src/main/groovy/uk/co/nichuk/hubitat/openhasp/OpenHaspSupport.groovy`; Hubitat deployable files are in `apps/` and `drivers/`.

## License

Apache License 2.0. See [LICENSE](LICENSE) and [NOTICE](NOTICE).
