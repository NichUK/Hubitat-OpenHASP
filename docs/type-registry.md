# OpenHASP Type Registry

OpenHASP-compatible apps can publish row types through Hubitat Location Events.

```text
request event: openhasp:typeRegistryRequest
response event: openhasp:typeRegistry
```

OpenHASP Manager subscribes to `openhasp:typeRegistry` and stores discovered metadata in its own app `state`. On startup or when the user presses `Refresh optional row types`, the manager sends `openhasp:typeRegistryRequest`. Optional apps subscribe to that request and answer with their row type definitions.

This avoids Hub Variables: Hubitat user apps can read and set existing Hub Variables, but cannot create them programmatically.

## Register Event

```json
{
  "protocol": "openhasp.typeRegistry.v1",
  "action": "register",
  "provider": "Example App:123",
  "providerLabel": "Example App",
  "appId": "123",
  "at": 1782042000000,
  "types": {
    "exampleType": {
      "label": "Example type",
      "source": "Example App",
      "capability": "capability.actuator",
      "direction": "both",
      "handler": "example"
    }
  }
}
```

## Required Fields

- `key`: stable machine-readable row type key
- `label`: user-visible dropdown label
- `source`: app or integration that registered the type
- `capability`: Hubitat capability selector expected by the row
- `direction`: `toHubitat`, `toOpenHASP`, or `both`
- `handler`: behavior family understood by OpenHASP Manager or a future mapper

Additional metadata is allowed. For example, the optional Boost Timer app registers:

```json
{
  "protocol": "openhasp.typeRegistry.v1",
  "action": "register",
  "provider": "Boost Timer:123",
  "providerLabel": "Bathroom UFH Boost",
  "types": {
    "timerButton": {
      "label": "Boost timer",
      "source": "Boost Timer",
      "capability": "capability.pushableButton",
      "direction": "toHubitat",
      "handler": "boostTimer",
      "integrationType": "boostTimer",
      "requiredAttributes": [
        "integrationType",
        "openHaspRowType",
        "displayText",
        "remainingSeconds"
      ],
      "command": "boost"
    }
  }
}
```

## Discovery Pattern

Optional apps should subscribe to discovery requests and publish a register event on startup, update, and request.

```groovy
void initialize() {
    subscribe(location, 'openhasp:typeRegistryRequest', typeRegistryRequestHandler)
    registerOpenHaspTypes()
}

void typeRegistryRequestHandler(evt) {
    Map request = new JsonSlurper().parseText("${evt.value ?: '{}'}")
    if (request.protocol == 'openhasp.typeRegistry.v1' && request.action == 'discover') {
        registerOpenHaspTypes()
    }
}

void registerOpenHaspTypes() {
    sendLocationEvent(
        name: 'openhasp:typeRegistry',
        value: JsonOutput.toJson([
            protocol: 'openhasp.typeRegistry.v1',
            action: 'register',
            provider: "${app.name}:${app.id}",
            providerLabel: app.label ?: app.name,
            types: [
                exampleType: [
                    label: 'Example type',
                    capability: 'capability.actuator',
                    direction: 'both',
                    handler: 'example'
                ]
            ],
            at: now()
        ]),
        isStateChange: true
    )
}
```

## Unregister Event

Apps should publish an unregister event when uninstalled:

```json
{
  "protocol": "openhasp.typeRegistry.v1",
  "action": "unregister",
  "provider": "Example App:123",
  "providerLabel": "Example App",
  "types": {},
  "at": 1782042000000
}
```

OpenHASP Manager always has its built-in standard types. Optional types are added from active providers and already-configured unknown types are preserved as legacy rows.
