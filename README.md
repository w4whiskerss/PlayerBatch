# PlayerSummonBulk

`PlayerSummonBulk` is a Fabric mod for Minecraft `1.21.11` that works as a Carpet addon for bulk fake-player management.

## Features

- `/playersummon <count> {optional,names}` to queue large fake-player summons through Carpet's existing `/player <name> spawn` flow
- `/playerbatch` GUI for live control
- Dynamic summon limit and spawn-per-tick settings with instant config persistence
- Selection wand for fake players with glowing toggle
- Batch actions for selected fake players
- Boss bar progress tracking with accurate success/failure summaries
- Queue-based spawning to reduce lag spikes
- Name planning with requested names, NameMC fetches, fallback pool, and generated names
- Debug mode for summon and selection logging

## Commands

- `/playersummon <count>`
- `/playersummon <count> {name1, name2, ...}`
- `/playerbatch`
- `/playerbatch summon <count> {name1, name2, ...}`
- `/playerbatch limit <number>`
- `/playerbatch spawnspertick <number>`
- `/playerbatch debug on`
- `/playerbatch debug off`
- `/playerbatch wand`
- `/playerbatch clearselection`
- `/playerbatch listselection`
- `/playerbatch command <action>`
- `/playerbatch tp type=wand:selected <direction> <blocks>`

## Requirements

- Minecraft `1.21.11`
- Fabric Loader
- Fabric API
- Carpet Mod
- Java `21`

## Development

Build:

```powershell
.\gradlew.bat build
```

Run client:

```powershell
.\gradlew.bat runClient
```

Run server:

```powershell
.\gradlew.bat runServer
```

## Config

The mod writes its config to:

`config/playersummonbulk.properties`

Current live settings:

- `maxSummonCount`
- `maxSpawnsPerTick`
- `debugEnabled`

## Project Structure

- `src/main/java/com/zahen/playersummonbulk/command` command registration
- `src/main/java/com/zahen/playersummonbulk/core` shared backend services
- `src/main/java/com/zahen/playersummonbulk/name` name planning, validation, and fallback generation
- `src/main/java/com/zahen/playersummonbulk/network` client/server sync for the GUI
- `src/main/java/com/zahen/playersummonbulk/client` client GUI and Mod Menu integration

## Notes

- This mod reuses Carpet's fake-player spawning instead of re-implementing fake players.
- The queue and boss bar are server authoritative.
- Mod Menu integration is optional and provides a config screen entry when Mod Menu is installed.
