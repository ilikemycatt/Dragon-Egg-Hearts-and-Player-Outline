# Dragon Egg Hearts

A Fabric mod for Minecraft 1.21.11 where carrying a Dragon Egg grants bonus max health.

## Features

- Bonus hearts while a Dragon Egg is in your player inventory.
- Bonus max health stays persistent across relog/rejoin while carrying the egg.
- Optional glowing outline while carrying a Dragon Egg.
- Optional red player name while carrying a Dragon Egg.
- Optional restriction to prevent storing Dragon Eggs in containers/shulkers/bundles.
- Optional hopper insertion blocking for Dragon Eggs.
- Optional allay interaction blocking and fox pickup blocking for Dragon Eggs.
- Optional item frame interaction blocking for Dragon Eggs.
- Optional Enderman stare-aggro immunity for Dragon Egg carriers.
- Optional mob-targeting behavior where neutral mobs aggro and hostiles prioritize egg carriers.
- Optional Dragon Egg coordinate chat announcements after placement/teleport tracking.
- Optional Dragon Egg restore-to-End-portal protection for void/hazard loss.
- Configurable bonus heart amount.

## Requirements

- Minecraft `1.21.11`
- Fabric Loader `0.17.3+`
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
- `extraHearts` (number): Bonus hearts when `doubleHearts` is false.
- `outline` (boolean): If true, player gets glowing outline while carrying a Dragon Egg.
- `redPlayerName` (boolean): If true, player name is red while carrying a Dragon Egg.
- `allowStorageInContainers` (boolean): If false, Dragon Eggs cannot be stored in containers/shulkers/bundles.
- `blockHopperEggInsertion` (boolean): If true, hoppers cannot insert Dragon Eggs.
- `blockAllayEggInteractions` (boolean): If true, allays cannot take, hold, or pick up Dragon Eggs.
- `blockEggUseOnItemFrames` (boolean): If true, using a Dragon Egg on item frames is blocked.
- `blockFoxEggPickup` (boolean): If true, foxes cannot pick up Dragon Eggs.
- `endermanIgnoreStareForEggCarriers` (boolean): If true, endermen ignore stare aggro checks for egg carriers.
- `angerNeutralMobsToEggCarriers` (boolean): If true, nearby neutral angerable mobs aggro egg carriers.
- `prioritizeHostilesToEggCarriers` (boolean): If true, nearby hostiles prioritize egg carriers as targets.
- `announceEggCoordinates` (boolean): If true, the mod periodically broadcasts Dragon Egg coordinates.
- `eggCoordsMessageIntervalTicks` (number): Tick interval between coordinate broadcasts (minimum 20).
- `restoreEggToEndPortalOnLoss` (boolean): If true, eggs lost to void/hazard are restored to the End portal area.
- `debugLogging` (boolean): Enables extra server log output for troubleshooting.

## Building

```bash
./gradlew build
```

Built jars are in:

- `build/libs/`

## License

MIT. See [LICENSE](LICENSE).
