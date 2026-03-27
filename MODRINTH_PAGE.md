# PlayerBatch

PlayerBatch is a Fabric + Carpet addon for summoning, managing, and controlling large groups of fake players without the normal command spam.

It keeps big summon batches stable by queueing them across ticks, gives you selection and group tools for mass control, and adds a cleaner command workflow for kits, presets, AI, and testing.

## Requirements

- Minecraft `1.21.11`
- Fabric Loader
- Fabric API
- Carpet Mod
- Java `21`

## What PlayerBatch Does

- Summons large fake-player batches through Carpet's real `/player` system
- Queues spawns to reduce lag spikes
- Supports formations, names, kits, combat options, and explicit block loadouts
- Lets you select bots in bulk and run actions on them
- Adds saved kits and combat presets
- Includes testing commands for pathing, healing, and full-system checks
- Supports extensions through `PlayerBatch-ExtAPI`

## Main Commands

### Spawning

```mcfunction
/pb spawn <count>
/pb spawn <count> {name1,name2,...} <formation> <arguments>
```

Examples:

```mcfunction
/pb spawn 50 {W4Whiskers,PixelCrafter} circle -diamondarmor -diamondtools -reach{6} -damage{true}
/pb spawn 100 {} dense -ironarmor -irontools -blocks{cobblestone*64}
/pb spawn 8 random kit{ranked}
```

### Selection

```mcfunction
/pb wand
/pb selection all
/pb selection range <distance>
/pb selection count <number>
/pb selection list
/pb selection clear
```

### Actions / Targeting

```mcfunction
/pb run <action>
/pb target look <player>
/pb teleport selected <direction> <block>
/pb repair tags
```

### Gear / Effects

```mcfunction
/pb gear item <slot> <item> [count]
/pb gear effect <effect> <seconds> [amplifier]
/pb gear clear_effects
```

### Presets / Kits

```mcfunction
/pb presets combat <count> [options]
/pb presets save <name> combat <count> <options>
/pb presets use <name> [count]
/pb presets list

/pb kits save <name>
/pb kits load <name>
/pb kits self <name>
/pb kits list
```

### Testing / Config

```mcfunction
/pb test all
/pb test goto coords <x> <y> <z>
/pb test goto entity <target>
/pb test stop
/pb test wall
/pb test gap
/pb test climb
/pb test course
/pb test heal
/pb test drop <item> [count]

/pb config limit <number>
/pb config spawns_per_tick <number>
/pb config debug on
/pb config debug off
/pb cancel
```

## Built-In Formations

- `circle`
- `filled_circle`
- `dense`
- `square`
- `triangle`
- `random`
- `single_block`

## Common Summon Arguments

- `kit{name}`
- `-diamondarmor`
- `-diamondtools`
- `-reach{3}`
- `-damage{true|false}`
- `-crits{true|false}`
- `-healingitems{true,...}`
- `-blocks{cobblestone*64}`

`-blocks{...}` is optional. Bots do not get free hidden blocks by default. If they need blocks for necessary placement, you have to give them blocks through a summon argument, a kit, or a manual loadout.

## Why Use PlayerBatch

- Faster PvP and arena testing
- Cleaner fake-player control than raw Carpet alone
- Better large-batch stability
- Repeatable kit/preset workflows
- Extension-ready through `PlayerBatch-ExtAPI`

## Links

- GitHub: [PlayerBatch](https://github.com/w4whiskerss/PlayerBatch)
- API: [PlayerBatch-ExtAPI](https://github.com/w4whiskerss/PlayerBatch-ExtAPI)
- Issues: [Bug reports / support](https://github.com/w4whiskerss/PlayerBatch/issues)
