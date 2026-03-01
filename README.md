# Dragon Egg Hearts

A Fabric mod for Minecraft 1.21.11 where carrying a Dragon Egg grants bonus max health.

## Features

- Bonus hearts while a Dragon Egg is in your player inventory.
- Optional glowing outline while carrying a Dragon Egg.
- Optional red player name while carrying a Dragon Egg.
- Optional restriction to prevent storing Dragon Eggs in containers.
- Configurable bonus heart amount.

## Requirements

- Minecraft `1.21.11`
- Fabric Loader `0.16.10+`
- Fabric API
- Java `21`

## Installation

1. Install Fabric Loader for Minecraft 1.21.11.
2. Install Fabric API.
3. Put the mod `.jar` file in your `mods` folder.
4. Start the game/server once to generate config.

## Configuration

Config file path:

- `config/dragonegghearts.json`

Options:

- `doubleHearts` (boolean): If true, carrying a Dragon Egg doubles base health (+10 hearts / +20 health).
- `outline` (boolean): If true, player gets glowing outline while carrying a Dragon Egg.
- `redPlayerName` (boolean): If true, player name is red while carrying a Dragon Egg.
- `allowStorageInContainers` (boolean): If false, Dragon Eggs cannot be stored in containers/shulkers/bundles.
- `extraHearts` (number): Bonus hearts when `doubleHearts` is false.

## Building

```bash
./gradlew build
```

Built jars are in:

- `build/libs/`

## License

MIT. See [LICENSE](LICENSE).
