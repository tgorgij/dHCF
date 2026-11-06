# dHCF

HCF, reimagined for modern Minecraft, with a twist of a tech tree!

## Features

- Factions, claims, DTR, allies, faction points, and lives.
- KOTH, Conquest, Destroy the Core, EOTW, scheduled events, and regenerating mountains.
- Archer, Bard, Rogue, and Miner classes, plus ability items.
- Faction tech trees, enchantment and potion limits, economy, and shop signs.
- Combat loggers, deathbans, player timers, staff tools, scoreboards, and a custom tab list.
- Player settings, private messages, statistics, and configurable items and menus.

## Requirements

- Java 25.
- Paper 26.3, the API version currently used by the build.

SQLite and PacketEvents are bundled in the plugin jar. No external database setup is required.

## Building

```sh
bash gradlew build
```

The plugin jar is generated at:

```text
build/libs/dHCF-1.0-SNAPSHOT.jar
```

## Installation

1. Place the jar in your server's `plugins` folder.
2. Start the server to generate the configuration and database.
3. Stop the server, adjust the configuration, and start it again.

## Configuration

Files are generated under `plugins/dHCF/`:

- `config.yml` — gameplay settings and feature toggles under `settings.features`.
- `locale.yml` and `messages.yml` — messages and command text.
- `features/` — item, GUI, scoreboard, tab list, and KitMap kit configuration.
- `storage.db` — player, faction, and event data.

Permission nodes are listed in [permissions.txt](src/main/resources/permissions.txt). Command declarations are in [plugin.yml](src/main/resources/plugin.yml).
