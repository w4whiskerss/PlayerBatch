# PlayerBatch

> Bulk-summon, control, and script fake players on Fabric with Carpet.

[![Download on GitHub](https://img.shields.io/badge/Download-GitHub%20Release-2ea44f?style=for-the-badge&logo=github)](https://github.com/w4whiskerss/PlayerBatch/releases/tag/latest-1.21.11)
[![Modrinth](https://img.shields.io/badge/Download-Modrinth-1bd96a?style=for-the-badge&logo=modrinth)](https://modrinth.com/mod/playerbatch)
[![ExtAPI](https://img.shields.io/badge/API-PlayerBatch--ExtAPI-5865f2?style=for-the-badge)](https://github.com/w4whiskerss/PlayerBatch-ExtAPI)
[![Issues](https://img.shields.io/badge/Support-Issues-db61a2?style=for-the-badge&logo=github)](https://github.com/w4whiskerss/PlayerBatch/issues)

`PlayerBatch` is a `Minecraft 1.21.11` Fabric mod and Carpet addon built for summoning and managing large groups of fake players without the normal spam, lag, or command pain.

## Quick Links

- Download latest jar: [GitHub release](https://github.com/w4whiskerss/PlayerBatch/releases/tag/latest-1.21.11)
- Modrinth page: [PlayerBatch on Modrinth](https://modrinth.com/mod/playerbatch)
- Extension API: [PlayerBatch-ExtAPI](https://github.com/w4whiskerss/PlayerBatch-ExtAPI)
- Source code: [GitHub repository](https://github.com/w4whiskerss/PlayerBatch)
- Report bugs: [Issues](https://github.com/w4whiskerss/PlayerBatch/issues)

## What It Does

- Summons lots of fake players using Carpet's real `/player` system
- Queues large spawns to reduce lag spikes
- Supports custom names, formations, kits, and combat options
- Lets you select bots in bulk and control them with commands
- Adds group control, AI modes, bot utilities, and command-focused workflows
- Supports extensions through `PlayerBatch-ExtAPI`

## Install

You need:

- Minecraft `1.21.11`
- Fabric Loader
- Fabric API
- Carpet Mod
- Java `21`

Install steps:

1. Install Fabric Loader for `1.21.11`
2. Put `Fabric API` in your `mods` folder
3. Put `Carpet` in your `mods` folder
4. Download the latest `PlayerBatch` jar
5. Put the jar in your `mods` folder

## Main Commands

### Spawning

```mcfunction
/pb spawn <count>
/pb spawn <count> {name1,name2,...} <formation> <arguments>
/pb presets combat <count> <arguments>
```

### Selection

```mcfunction
/pb wand
/pb selection all
/pb selection range <distance>
/pb selection count <number>
/pb selection clear
/pb selection list
```

### Bot Control

```mcfunction
/pb run <action>
/pb target look <player>
/pb teleport selected <direction> <block>
/pb repair tags
```

### Kits

```mcfunction
/pb kits save <name>
/pb kits load <name>
/pb kits self <name>
/pb kits list
```

### Presets

```mcfunction
/pb presets combat <count> [options]
/pb presets save <name> combat <count> <options>
/pb presets use <name> [count]
/pb presets list
```

### Utility / Testing

```mcfunction
/pb test all
/pb test goto coords <x> <y> <z>
/pb test goto entity <target>
/pb test stop
/pb config limit <number>
/pb config spawns_per_tick <number>
/pb config debug on
/pb config debug off
```

## Example Commands

```mcfunction
/pb spawn 50 {W4Whiskers,PixelCrafter} circle -diamondarmor -diamondtools -reach{6} -damage{true}
/pb spawn 100 {} dense -ironarmor -irontools -blocks{cobblestone*64}
/pb target look W4Whiskers
/pb repair tags
/pb test all
```

## Formations

Built-in formations currently include:

- `circle`
- `filled_circle`
- `dense`
- `square`
- `triangle`
- `random`
- `single_block`

## Why Use PlayerBatch

- Faster testing for PvP and arena setups
- Easier fake-player management than raw Carpet commands alone
- Better large-batch stability
- Cleaner command workflow
- Extension-ready for custom formations, arguments, actions, and behaviors

## Extension API

Want to build add-ons for PlayerBatch?

- API repo: [PlayerBatch-ExtAPI](https://github.com/w4whiskerss/PlayerBatch-ExtAPI)
- Extension entrypoint key: `playerbatch-ext`

## Notes

- `PlayerBatch` is command-first
- It reuses Carpet fake-player spawning instead of reimplementing fake players
- The latest `1.21.11` jar is uploaded to GitHub releases whenever a new build is published
- If the Modrinth page is not live yet, use the GitHub release link above

## Support

If something breaks or acts weird:

- open an issue: [GitHub Issues](https://github.com/w4whiskerss/PlayerBatch/issues)
- include your version, commands used, and logs if possible
