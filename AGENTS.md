# Railway Announcer

This repository is a separately built addon for Create: Railway Signage, Minecraft
1.21.1, NeoForge and Create 6.0.10. Keep all audio code, station audio saved data,
speaker registrations, voice packs, JAVE dependencies and door mixins here.
Signage exposes station/line domain data and generic controller extension APIs;
never add a dependency from Signage back to this addon.

Use LDLib2 through Signage's `LdlibUi`. Station and onboard voice settings,
physical devices, event channels and listener audiences must stay distinct.
Single-player pause must stop both audio sources and sequence time. Use actual
Create state changes for doors rather than estimating conditional schedule waits.

Use FastCtx for local inspection. Preserve existing changes. After edits run
`compileJava`, `check`, `build` and resource validation; resource changes also need
`runData`. Reverse-test new validators. Report automated and in-game validation
separately. Commit each related round and report the hash and clean status.

Build the sibling Signage first, or supply `-PsignageJar=<absolute jar path>`.
The dependency is never embedded in the addon jar. Source-level integration uses
Signage's public registry/domain classes, `StationControllerActions` and
`RailwayDataUpdatedEvent`; avoid copying Signage implementations.
