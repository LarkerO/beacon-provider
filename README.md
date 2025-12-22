# beacon-provider

> This project is tightly coupled with the Beacon Plugin.

Beacon Provider exposes shared telemetry logic that can be shipped on both Fabric and Forge without depending on legacy Architectury shims. The repository is intentionally structured per Minecraft release so that each loader can evolve on its own upgrade cadence.

Beacon Provider is designed to work in conjuction with Beacon Plugin. It only provides modpack-specific data to Beacon Plugin, while Beacon Plugin is responsible for initialing communication via Socket.IO to expose Minecraft server information to the backend or other comsumers.

Beacon Provider communicate with Beacon Plugin via Netty. The Provider will automatically starts a Netty server allowing the Beacon Plugin to scan the Provider configuration files in `/config` and attempt to connect. DO NOT REPORT ISSUES RELATED TO THE BEACON PLUGIN HERE.

## Layout

```
root
├── common/           # shared logic compiled once (Java 8 target)
├── fabric-1.16.5/    # Fabric loader entrypoint for MC 1.16.5
├── fabric-1.18.2/
├── fabric-1.20.1/
├── forge-1.16.5/     # Forge loader entrypoint for MC 1.16.5
├── forge-1.18.2/
└── forge-1.20.1/
```

## Building

- Single module: `./gradlew :fabric-1.20.1:build` or `./gradlew :forge-1.18.2:build`
- Whole Minecraft target: `./gradlew buildTarget_1_18_2`
- Everything: `./gradlew buildAllTargets`

Each loader jar automatically bundles the compiled `common` classes/resources, so deployables remain self-contained.

## Actions

An Action is the basic request unit of the mod. For detailed definitions and usage, see `docs/Beacon Actions.md`.
