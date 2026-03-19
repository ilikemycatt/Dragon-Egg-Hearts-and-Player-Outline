# Changelog

## 1.0.1 - 2026-03-19
- Added Dragon Egg coordinate broadcasts in chat with placement/teleport tracking.
- Endermen now ignore stare aggro checks for Dragon Egg carriers.
- Nearby neutral angerable mobs can aggro onto Dragon Egg carriers.
- Nearby hostile mobs can prioritize Dragon Egg carriers as targets.
- Added Dragon Egg loss protection by restoring destroyed/lost eggs to the End portal area.
- Blocked Dragon Egg use on item frames.
- Blocked Dragon Egg interactions with allays and blocked fox Dragon Egg pickup.
- Blocked Dragon Egg insertion into hoppers.
- Kept Dragon Egg bonus max health persistent across relog/rejoin while carrying the egg.
- Added config toggles for these behaviors:
  - `endermanIgnoreStareForEggCarriers`
  - `angerNeutralMobsToEggCarriers`
  - `prioritizeHostilesToEggCarriers`
  - `blockAllayEggInteractions`
  - `blockEggUseOnItemFrames`
  - `blockFoxEggPickup`
  - `blockHopperEggInsertion`
  - `announceEggCoordinates`
  - `restoreEggToEndPortalOnLoss`
- Updated README configuration docs.

## 1.0.0
- Initial release for Minecraft 1.21.11 (Fabric).
- Dragon Egg grants bonus max health while carried in player inventory.
- Added config file for:
  - `doubleHearts`
  - `outline`
  - `redPlayerName`
  - `allowStorageInContainers`
  - `extraHearts`
