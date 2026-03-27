# Changelog

All notable changes to `PlayerBatch` are documented here in a simple human-readable format.

This changelog is intentionally focused on how the mod evolved, especially from the early "just summon a lot of bots" phase into the more feature-rich versions.

## PlayerBatch-2.0.1-mc1.21.11

Current modern release line.

- Cleaned up the public command system around the newer `/pb` command layout
- Added clearer grouping for spawning, selection, kits, presets, testing, and utility actions
- Added `/pb cancel` to stop queued summon work
- Added the `dense` formation for tightly packed bot crowds
- Added support for `-blocks{...}` so bots can be given starting blocks explicitly instead of receiving hidden free blocks
- Added better test coverage commands, including broader all-in-one testing flows
- Refreshed README / Modrinth page copy and synced docs with the newer command structure

## PlayerBatch-2.0.0

The point where PlayerBatch stopped being just a bulk spawner and started becoming a real bot-control mod.

- Added much broader bot testing tools
- Added movement and pathing test commands
- Added healing tests and safer test routing behavior
- Added combat pathing groundwork
- Added terrain interaction logic for harder situations
- Added staged combat block-breaking support
- Improved debug traces and pickup/pathing behavior
- Started pushing the mod toward real PvP and AI-driven control instead of only mass spawning

## PlayerBatch-1.3.x

The extension and structure phase.

- Added the PlayerBatch extension runtime API
- Wired executable extension hooks into the mod
- Renamed and cleaned up package / namespace structure
- Improved internal organization so future add-ons and integrations were easier to build

## PlayerBatch-1.2.x

The quality-of-life and control phase.

- Added the `look` command so bots could orient toward targets
- Added more formation support, including `filled_circle`
- Fixed `fixtags` behavior so it properly scanned the server player list
- Improved documentation and general project structure

## PlayerBatch-1.1.x

The "more than just one command" phase.

- Expanded command behavior beyond the earliest summon-only flow
- Started introducing formations and more intentional control features
- Improved usability for repeated testing setups

## PlayerBatch-1.0.x

The very basic beginning.

At this stage, PlayerBatch was mostly about one thing:

- summon a lot of fake players quickly

This was the phase where the mod was intentionally simple and lightweight:

- bulk bot spawning was the core feature
- fake players were created through Carpet
- there were very few extra control systems
- no polished AI layer
- no modern command structure
- no kits / presets / advanced testing flow yet

This is the version era for:
"I just want to spawn a ton of bots fast."

## Notes

- Versions above are grouped by feature era, not by every single internal micro-change.
- If needed later, this file can be expanded into a stricter per-version release log.
