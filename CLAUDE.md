# Ars Kingdom (`ars_kingdom`)

NeoForge 1.21.1 addon tying Ars Nouveau into MineColonies. The full design and the chunked
implementation/testing checklist live in `PLAN.md`. **Work through the checklist in order, and
add every new plan item to the checklist too.**

## Build & test
- `./gradlew build` → `build/libs/ars_kingdom-1.21.1-<version>.jar`
- `./gradlew runGameTestServer` → runs the GameTests in `fun.dungeon.debauchery.arskingdom.gametest`
  and exits (structure template: `data/ars_kingdom/structure/gametest/empty.nbt`).
  The `Missing element ... loot_table` stack traces from MineColonies' `CustomRecipeManager` are harmless noise.
- `./gradlew runClient` → dev client (also enables our gametest namespace).
- Setup copied from `mods/ArsReSources` (ModDevGradle 2.0.148, `generateModMetadata` expands
  `src/main/templates/META-INF/neoforge.mods.toml` from `gradle.properties`).

## Dependencies (versions = the jars in the MagicMod instance, see `gradle.properties`)
- Required (also in `neoforge.mods.toml`): Ars Nouveau, **Ars Deco**, MineColonies, Structurize, **Archwood Colonies**.
- LDTTeam maven (`com.ldtteam` only): minecolonies, structurize, blockui, `domum-ornamentum:<v>:main`, multipiston (runtime).
- CurseMaven (`curse.maven` only): Ars Deco (compiled against), Archwood Colonies + Biomes O' Plenty +
  TerraBlender + GlitchCore (runtime only; Archwood is data-only and its pack requires BoP), and Ars Additions
  (runtime only: the Mage Tower blueprints use its `sourcestone_wall`, so the tower needs Ars Additions installed).
- Everything is non-transitive.

## Repository & releases
- Git repo: `git@github.com:legoGonkDroid/ArsKingdom.git` (branch `main`, MIT license).
- Release flow (same as ArsReSources):
  1. Bump `mod_version` in `gradle.properties` and commit.
  2. `git tag -a v<version> -m ... && git push origin v<version>`.
  3. `.github/workflows/release.yml` checks that the tag matches `mod_version`, builds, runs the GameTests, and publishes
     the jar with `Kir-Antipov/mc-publish` to CurseForge (required: Ars Nouveau, Ars Deco, MineColonies, Structurize,
     Archwood Colonies) and to a GitHub release.
- Required repo settings: the secret `CURSEFORGE_TOKEN` and the variable `CURSEFORGE_PROJECT_ID` (the numeric Project ID).

## Rules
- Only `compat/ArsDecoBridge` may touch `lambdamagus.ars_deco.*` (not a public API).
- No tinting, repainting or hue masks: colors come only from the native 16-dye textures
  (Ars `BASE_COLOR` on items, Ars Deco block dye).
- Use Archwood Collective for every hut it provides; never ship a blueprint for one of those huts.
  Archwood Colonies is All Rights Reserved, but the user chose (2026-11-23) to use its blueprints as inspiration/basis for
  ours; re-check before any public release.

## Blueprint style guide (Archwood Collective)
Our huts ship in our own pack "Ars Kingdom (Archwood Collective)" (Structurize can't merge packs)
and must look like they belong in Archwood Collective, modelled on Archwood's buildings.
- Frame/walls: archwood planks, stairs, slabs, fences; colored archwood logs (bark on) as posts and beams.
  **Never use stripped archwood logs**: the final pack adds a mod that makes archwood logs glow, and stripping removes it.
  The generators assert this, and so does the blueprint gametest.
- Size: our huts' plots are big enough for later levels to grow into (every level of a hut shares one plot size).
  The Source Sanctum is 29x23x29, a step below Archwood's town hall (35x27x36); level 1 uses the middle of it.
- Foundations: stone bricks, polished andesite, cobblestone, deepslate tiles; cobblestone/andesite walls.
- Roofs/detail: Domum Ornamentum shingles, panels, paper walls; archwood and fancy doors; glass panes.
- Lighting/interior: sconces, magelight torches, lanterns, bookshelves, archwood chests, arcane pedestals.
- Planting: archwood leaves, glow lichen, cave vines.
- Mage Tower accent = the school's archwood log: red / blue / green / purple `*_archwood_log`. The Source Sanctum
  (centrepiece) uses all four, one per corner (red NW, blue NE, green SW, purple SE).
- Lights in blueprints must be placed lit: state `level=15` plus a block entity (`ars_nouveau:sconce` /
  `ars_nouveau:magelight_torch`) with `lit: 1` and colour 255,25,180. Pedestals stand with `facing=up`.
  Settings labels: about 20 characters max; put the long text in `com.minecolonies.coremod.setting.tooltip.<id>`.
- Folders: towers in `military/`, other huts in `mystic/`. Avoid Biomes O' Plenty blocks.

## Huts
- A new hut needs: a block in `AKBlocks` (extends `hut/AKBlockHut`), an `ItemBlockHut` in `AKItems`, the block added to
  the valid blocks of `AKBlockEntities.BUILDING`, a `BuildingEntry` in `AKBuildings`, a blockstate (`facing=` variants),
  models, a loot table and lang (`block.ars_kingdom.<hut>`, `com.ars_kingdom.building.<id>`).
- A hut block whose name depends on a constructor argument (like `BlockHutMageTower`) can't use a field in
  `getHutName()`: MineColonies' block constructor calls it first. Pass the value through a static during construction.
- Each job needs citizen voice lines: `AKJobs.registerCitizenSounds` covers every job in `AKJobs.JOBS`.
- A hut with a color of its own (kept under the colony dye sweep, like Mage Towers): register an entry in
  `colony/BuildingColors` (`register(id, building -> Optional<DyeColor>)`, during mod construction) and add lang for
  its Sanctum toggle: `com.minecolonies.coremod.setting[.tooltip].ars_kingdom:building_color/<id>`. To make it start
  on, add its id to the `building_colors` config default.
- Module producer keys are global across mods: prefix them `ars_kingdom_`. Settings labels use
  `com.minecolonies.coremod.setting.ars_kingdom:<key>`.
- Blueprints are generated by scripts in `tools/` (`gen_sanctum_blueprint.py`, `gen_magetower_blueprint.py [school ...]`, default red only while the towers are redesigned;
  shared block helpers and the blueprint writer are in `bpgen.py`; `nbt.py` reads/writes the gzipped NBT:
  blocks are palette indices packed two shorts per int in y, z, x order; the hut's block entity carries `type`,
  `pack`, `path` and `blueprintDataProvider` with corners and a `groundlevel` tag). Re-run the script after edits.
- Blueprints: the file name is `<building id><level>.blueprint` (e.g. `sourcesanctum1`), anchored on the hut block, in
  `src/main/resources/blueprints/ars_kingdom/arskingdom/{military,mystic}/`. They're scanned in game with the
  Structurize scan tool (scans land in `<game dir>/blueprints/<player>/scans/`). Pack icons: `tools/gen_pack_icons.py`. CurseForge logo: `tools/render_logo.py` → `art/curseforge_logo.png`.
