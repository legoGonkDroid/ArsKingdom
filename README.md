# Ars Kingdom

An [Ars Nouveau](https://www.curseforge.com/minecraft/mc-mods/ars-nouveau) × [MineColonies](https://www.curseforge.com/minecraft/mc-mods/minecolonies) addon for NeoForge 1.21.1: battlemage towers, source economy huts, Ars machine halls, Arcana research, Wilden raids and colony-wide Ars Deco dye.

![Ars Kingdom](art/curseforge_logo.png)

**In development.** Early versions cover only part of the plan.

## Requirements
- Minecraft 1.21.1, NeoForge 21.1+
- Ars Nouveau 5.13+
- [Ars Deco](https://www.curseforge.com/minecraft/mc-mods/ars-deco)
- MineColonies and Structurize
- [Archwood Colonies](https://www.curseforge.com/minecraft/mc-mods/archwood-colonies): its Archwood Collective style is used for every hut it provides, and Ars Kingdom's own huts are built to match it.

## Building
- `./gradlew build` builds the jar into `build/libs/`.
- `./gradlew runGameTestServer` runs the automated in-game tests.
- Pushing a `v<version>` tag publishes the jar to CurseForge and to a GitHub release (see `.github/workflows/release.yml`).

## License
MIT
