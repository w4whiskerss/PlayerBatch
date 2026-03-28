# PlayerBatch

**PlayerBatch** is a lightweight Minecraft mod for **Fabric + Carpet** that lets you spawn, manage, and control large groups of fake players with a much cleaner command workflow.

Instead of fighting raw fake-player commands one by one, PlayerBatch gives you batch spawning, formations, selection tools, kits, combat presets, targeting helpers, and testing utilities for **PvP practice, server testing, automation, technical setups, and large-scale scenes**.

---
![Preview](https://media4.giphy.com/media/v1.Y2lkPTc5MGI3NjExZ3Zvc3Q4Mms0bnJrbWNqcTVpcW03bGRteDF3cG1oeTAxYmhwMHp1dSZlcD12MV9pbnRlcm5hbF9naWZfYnlfaWQmY3Q9Zw/hBl5uy8Zb8K7SFWl4T/giphy.gif)
---

## Features
- Spawn many fake players at once
- Built on top of the **Carpet Mod** fake player system
- Built-in formations like `circle`, `filled_circle`, `dense`, `square`, and more
- Select bots in bulk and control them together
- Save and reuse kits
- Save and reuse combat presets
- Assign explicit combat targets with `/pb target kill`
- Built-in testing commands for movement, healing, and full system checks
- Extension-ready through **PlayerBatch-ExtAPI**
- Queue-based spawning to reduce lag spikes

## Requirements
- **Fabric Loader**
- **Fabric API**
- **Carpet Mod**
- **Java 21**

## Example Usage

```text
/pb spawn 100
```

Example command structure:

```text
/pb spawn <amount>
/pb spawn <amount> {name1,name2,...} <formation> <arguments>
```

Examples:

```text
/pb spawn 50
/pb spawn 128 dense
/pb spawn 20 {Alpha,Beta,Gamma} circle -diamondarmor -diamondtools
/pb spawn 12 random kit{ranked} -blocks{cobblestone*64}
```

This summons the specified number of **fake players** using Carpet's system and applies PlayerBatch's formation, gear, and control features.

## Main Commands

### Spawning
```text
/pb spawn <count>
/pb spawn <count> <setup>
```

### Selection
```text
/pb wand
/pb selection all
/pb selection range <distance>
/pb selection count <number>
/pb selection list
/pb selection clear
```

### Actions / Control
```text
/pb run <action>
/pb target look <player>
/pb target kill <targets>
/pb target clear
/pb teleport selected <direction> <block>
/pb repair tags
```

### Kits / Presets
```text
/pb kits save <name>
/pb kits load <name>
/pb kits self <name>
/pb kits list

/pb presets combat <count> [options]
/pb presets save <name> combat <count> <options>
/pb presets use <name> [count]
/pb presets list
```

### Testing / Utility
```text
/pb test all
/pb test goto coords <x> <y> <z>
/pb test goto entity <target>
/pb test heal
/pb cancel
```

## Use Cases
- **Server stress testing**
- **PvP bot practice**
- **Automation experiments**
- **Technical Minecraft setups**
- **Large fake-player scenes**
- **Arena and event setup**
- **AI and movement testing**

## Performance Tip

[![Recommended Performance Mod](https://cdn.modrinth.com/data/cached_images/513035d1231a86dff137c74e04a5e64633593f10.png)](https://modrinth.com/mod/sodium/versions?l=fabric)

Running lots of bots can be demanding. Installing **Sodium** is recommended for better performance.

## Community & Support

[![Join our Discord](https://cdn.modrinth.com/data/cached_images/de7f2f606702569cd98fc1f6a0b29dbf817dd870.png)](https://discord.gg/NjZg46TRmf)

Join the **Discord server** for updates, help, and discussion.

## Author

Developed by **W4Whiskers**
