# PlayerBatch

`PlayerBatch` is a Fabric + Carpet addon for Minecraft `1.21.11` that lets you summon, select, and control large groups of fake players from commands or a GUI.

## What It Does

- Summons many fake players at once by reusing Carpet's existing `/player <name> spawn` command
- Queues large batches safely to reduce lag spikes
- Shows live summon progress with a boss bar
- Lets you select fake players with a wand and control them in bulk
- Includes an in-game GUI for quick batch control
- Supports named summons, fallback names, and generated names when needed

## Main Features

- Bulk summoning with `/playersummon <count>`
- Named summoning with `/playersummon <count> {name1, name2, ...}`
- Live queue progress and accurate success/fail summary
- Editable live limits with `/playerbatch limit <number>`
- Configurable spawn rate with `/playerbatch spawnspertick <number>`
- Selection wand with glowing selected bots
- Batch actions for selected fake players
- Optional Mod Menu config entry
- Debug logging toggle for troubleshooting

## Commands

### Summoning

- `/playersummon <count>`
- `/playersummon <count> {name1, name2, ...}`
- `/playerbatch summon <count> {name1, name2, ...}`

### PlayerBatch Menu

- `/playerbatch`

Opens the PlayerBatch GUI.

### Settings

- `/playerbatch limit <number>`
- `/playerbatch spawnspertick <number>`
- `/playerbatch debug on`
- `/playerbatch debug off`

### Selection

- `/playerbatch wand`
- `/playerbatch clearselection`
- `/playerbatch listselection`

### Selected Bot Actions

- `/playerbatch command <action>`
- `/playerbatch tp type=wand:selected <direction> <blocks>`

## Installation

You need:

- Minecraft `1.21.11`
- Fabric Loader
- Fabric API
- Carpet Mod
- Java `21`

Install steps:

1. Install Fabric Loader for `1.21.11`
2. Put Fabric API in your `mods` folder
3. Put Carpet Mod in your `mods` folder
4. Download the latest `PlayerBatch` jar from the releases page
5. Put the `PlayerBatch` jar in your `mods` folder

## Config

PlayerBatch writes its config to:

`config/playerbatch.properties`

Current settings:

- `maxSummonCount`
- `maxSpawnsPerTick`
- `debugEnabled`

These settings can be changed live in game and are saved automatically.

## How Selection Works

1. Run `/playerbatch wand`
2. Right-click a fake player with the wand
3. The fake player becomes selected and glows
4. Right-click again to remove it from selection
5. Use batch commands on the selected group

## Name Rules

When summoning bots:

- Given names are used first
- Extra names are fetched from NameMC when possible
- If NameMC does not provide enough valid names, PlayerBatch falls back to a built-in pool and generated names

Requested names must:

- Be between `3` and `16` characters
- Use valid Minecraft username characters
- Not start with `MHF_`
- Look like normal usernames instead of random gibberish

## FAQ

### Does this create fake players by itself?

No. PlayerBatch uses Carpet's fake-player command system instead of reimplementing fake players.

### Does it work on dedicated servers?

Yes. The mod is server-authoritative and is designed to work with dedicated servers.

### Why is summoning queued instead of instant?

Large bot batches can lag or crash a server if they all spawn at once. PlayerBatch spreads work across ticks to keep it stable.

### Can I change the summon limit without restarting?

Yes. Use the GUI or `/playerbatch limit <number>`.

### Do I need Mod Menu?

No. Mod Menu is optional. If you have it installed, PlayerBatch can show a config screen entry there.

## Downloads

Latest builds and the latest `1.21.11` jar are published on GitHub:

- Releases: [GitHub Releases](https://github.com/w4whiskerss/PlayerBatch/releases)

## Notes

- PlayerBatch targets `Minecraft 1.21.11`
- The latest GitHub workflow uploads the current `1.21.11` jar automatically
