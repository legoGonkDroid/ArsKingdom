# Ars Kingdom (`ars_kingdom`): implementation plan

## Context
This is a new NeoForge 1.21.1 mod that ties Ars Nouveau into MineColonies for the MagicMod pack. It adds:
- **Battlemage towers:** four schools of battlemage, each with its own tower.
- **Source economy:** a source economy run by citizens.
- **Arcana research:** an Ars research branch.
- **Wilden raids:** raids made of Ars Nouveau's Wilden mobs.
- **Colony dye:** every block that Ars Deco can dye is dyed in the colony's color when it is inside the colony.

Everything was checked against the installed jars and is feasible:
- MineColonies 1.1.1420
- Structurize 1.0.840
- Ars Nouveau 5.13.5
- Ars Deco 5.0.0

The working proofs that show it can be done:
- Trade Post (`armele/mctradepost-retro`) adds its own huts.
- Spell Colonies (`ch1335/Spell-Colonies`) adds a magic guard.
- `archwoodcolonies` ships a style pack inside its jar.

Scope chosen by the user:
- **Colony color:** comes from the MineColonies **team color**, mapped to one of the 16 dye colors.
- **Colony dye (Ars Deco):** every block Ars Deco can dye that sits inside a colony is dyed in the colony color, exactly as if a player had dyed it with Ars Deco. This includes source jars, relays, sourcelinks, ritual braziers, other machines, and sourcestone and archwood decor.
- **Particles:** source transfer particles and ritual particles follow the dyed block's color. Player spells are not recolored.
- **Battlemages:** four schools, each with its own color: **Red, Blue, Green and Purple**.
  - Each school has its own Mage Tower hut and its own blueprints. The school is chosen when the player picks which tower to build.
  - The towers have **3 tiers, which are building levels 1–3**. Each level allows a higher tier of spellbook and Arcanist armor.
  - A battlemage **will not work** without a spellbook and a full set of Arcanist armor. When equipped, its book and armor take on its tower's color.
  - There is **no tinting, repainting or hue masking** anywhere. All colors come from Ars Nouveau's and Ars Deco's own 16-color dye textures.
- **Also in scope:** source economy huts, an Arcana research branch and Wilden raids.
- **Building style: Archwood Colonies.** The colony uses the **Archwood Collective** style pack from the Archwood Colonies mod for every hut it provides. Our own huts get blueprints built to look like part of that pack. Anything else Archwood Colonies already provides (crafter recipes, its Ars research branch) is used, not duplicated. See "Building style" below.
- **Not in scope:** a full style pack of our own. We only make blueprints for our own huts.

## Key findings that shape the design
- **Ars items already come in 16 dye colors.** Spellbooks and all Ars armor store their color in vanilla `DataComponents.BASE_COLOR` (a `DyeColor`).
  - Ars renders each color with its own pre-made texture. Books use `spellbook_<color>.png`. Armor uses `textures/item/medium_armor/arcanists_armor_<color>.png`.
  - So setting `stack.set(BASE_COLOR, dye)` (or calling `IDyeable.onDye`) recolors an item with no tinting.
  - Colors are limited to the 16 `DyeColor`s, so each school's color is a `DyeColor`.
  - Ars Deco adds nothing for items; Ars Nouveau already handles item dyeing.
- **Ars Deco dyes placed blocks.**
  - The blocks it can dye are listed in `DyeableArsBlocks.TARGETS`. They include every sourcelink, relay, splitter, collector, deposit and warp relay, plus source jars, the ritual brazier, brazier relay, turrets, apparatus, imbuement chamber, pedestals, scribes table, potion melder, wixie cauldron, repository, sconces, archwood and sourcestone decor, and the `ars_additions` blocks.
  - We can dye a block from code with `DyeBlockColorApplier.apply(Level, BlockPos, DyeTarget, DyeColor)`, and undye it with `DyeBlockColorApplier.clear(ServerLevel, BlockPos, DyeTarget)`. `DyeableArsBlocks.target(block)` returns the `DyeTarget`.
  - To read a block's color: for a block with a block entity, `DyeableArsBlocks.placedColor(level, pos)`, which reads the `ars_deco_color` field added by mixin. For other blocks, `PlacedDyeColors.get(serverLevel).getColor(pos, id)`, a SavedData synced to clients.
  - Ars Deco already makes relay and sourcelink particles match their dye, through `AbstractSourceMachineDyeColorMixin` on `getColor`.
  - Places it does **not** reach:
    - Jar-to-machine transfers (`SourceUtil`), which hardcode 255,25,180.
    - Ritual particles.
    - **Light colors.** Magelight torches (`ars_nouveau:magelight_torch`, used widely by Archwood Collective) and spell-placed mage lights (`light_block`) aren't Ars Deco targets at all. For sconces, Ars Deco dyes the block but not the flame. Magelight lanterns *are* targets and are already covered.
- **Ars Nouveau lights have their own native color.** `SconceTile` (magelight torches use `MagelightTorchTile extends SconceTile`) and `LightTile` (`light_block`) store a `ParticleColor color`.
  - Ars sets it from the spell that lit or placed the light, through `setColor(ParticleColor)`. That call syncs to clients (`updateBlock()`), and the tiles implement `IWololoable` for recoloring.
  - The default is `ParticleColor.defaultParticleColor()`, Ars's purple.
  - Setting this color is Ars's own mechanism, the same as lighting with a colored spell, so it is **not tinting**.
  - **Found in Chunk 1:** Ars Deco's `DyeBlockColorApplier.clear` only clears its `PlacedDyeColors` SavedData. Block-entity colors (jars, relays…) are never cleared by it, so `ArsDecoBridge.clear` also calls `arsDeco$setColor(null)` on the block entity and sends a block update. The client drops the color because Ars Deco's load mixin resets it when `ars_deco_color` is missing.
  - Dyed blocks keep their color when broken (`onBlockDrops`). Dyed items placed by a player apply their color (`BlockItemPlacementMixin`).
  - Ars Deco has no maven; the code lives in package `lambdamagus.ars_deco.*`, which is not a public API.
- **MineColonies equipment gating:** this works with a registry. `CommonMinecoloniesAPIImpl.EQUIPMENT_TYPES` is a synced registry, and an addon can add types with `EquipmentTypeEntry.Builder().setRegistryName().setDisplayName().setIsEquipment((stack,type)->…).setEquipmentLevel((stack,type)->…).build()`.
  - Once a type is registered, all of these apply to it:
    - `Tool(type, min, max)` requests;
    - `AbstractEntityAIBasic.checkForToolOrWeapon`, which blocks work and sets `JobStatus.STUCK`;
    - `ItemStackUtils.hasEquipmentLevel`;
    - the cap from `getMaxEquipmentLevel()`;
    - the "I need …" request interaction.
  - Guards (`AbstractEntityAIFight`) have public mutable `toolsNeeded`, which blocks in `prepare()`, and `itemsNeeded`, which holds armor `GuardGear` requested in the background.
  - Ars armor is an `ArmorItem`, so it goes into the citizen's armor slots. It renders through `CitizenArmorLayer extends HumanoidArmorLayer`. That class uses `IClientItemExtensions`, so geckolib armor should render. This is **not tested yet** (see risks).
- **Ars tiers:**
  - Spellbooks are fixed per item: `novice_spell_book` = 1, `apprentice_spell_book` = 2, `archmage_spell_book` = 3. `creative_spell_book` is not accepted.
  - Arcanist armor (`arcanist_hood`, `arcanist_robes`, `arcanist_leggings`, `arcanist_boots`; the medium set) stores its tier per stack. `ArmorPerkHolder.getTier()` returns 0..2 (shown as 1..3) through `PerkUtil.getPerkHolder(stack)`. The tier is raised with the apparatus armor-upgrade recipes.
- **The default team color is `WHITE`.** It dyes blocks white like any other color. The "undyed" team color is **Dark Purple**, because undyed Ars blocks already look purple (configurable).
- **Cheap colony lookup:** `IColonyManager.getClaimData(dim, ChunkPos).getOwningColony()` → `getColonyByDimension`. Avoid `getIColony` and `getColonyView`; they load chunks.
- **MineColonies events don't go through NeoForge.** They use their own bus, through `IMinecoloniesAPI.getInstance().getEventBus().subscribe(ExactClass, handler)`, and none can be cancelled. We use:
  - `ColonyTeamColorChangedModEvent`
  - `ColonyDeletedModEvent`
  - `BuildingConstructionModEvent`
  - `BuildingAddedModEvent`
- **Guards already fight Wilden.** All Wilden extend `Monster`, so guards target them with no extra work.
- **The Druid is a ready template for the battlemage.** Use `JobDruid`, `EntityAIDruid` and `DruidCombatAI extends AttackMoveAI` as the model for a ranged mana guard.
- **Hut gotchas:**
  - Override `getRegistryName()`, which defaults to the `minecolonies` namespace.
  - Register our own `BlockEntityType` with a `TileEntityColonyBuilding` subclass.
  - `ModuleProducer` keys must be unique across all mods, so prefix them with our mod id.
  - The crafter key drops the namespace, so job paths must be unique.
  - Blueprints are named `<name>1..N.blueprint`, and the hut block is the anchor.
- **Colony settings:** the town hall settings window is hardcoded, so the rule toggles go on our own hut's `SettingsModule`. All the keys must be added when the module is constructed.
- **Archwood Colonies 1.04** (mod id `archwoodcolonies`, CurseForge project 1628280, file 9232961; installed in MagicMod). It is data only, with no Java classes, and its license is **All Rights Reserved**. It provides:
  - **The Archwood Collective style pack** (`blueprints/archwoodcolonies/archwoodcollective/`). It needs `minecolonies`, `ars_nouveau` and `biomesoplenty`. It has blueprints for almost every MineColonies hut: town hall, builder, residences, every crafter, the mystic huts, education, military (guard tower, barracks, archery, combat academy, gatehouse), mineshafts, roads and decorations. Only the dyer and florist are missing.
    - Categories: `agriculture`, `craftsmanship`, `decorations`, `education`, `fundamentals`, `infrastructure`, `military`, `mystic` and `walls`, each with `icon.png`/`icon_disabled.png`.
    - Its blocks: archwood planks, stairs, slabs, fences and doors; the **four colored archwood logs** (red, blue, green and purple, plus stripped versions) and archwood leaves; stone brick, andesite, cobblestone and deepslate tile foundations; Domum Ornamentum panels, shingles and paper walls; sconces, magelight torches, lanterns, arcane pedestals, sourcestone, spell turrets and bookshelves; and a few Biomes O' Plenty blocks.
  - **Crafter recipes** (`data/archwoodcollective/crafterrecipes/`), gated behind its research `archwoodcollective:arsnouveau/arscraftingrecipes`:
    - The Arcanist, Battlemage and Sorcerer armor sets, at the Fletcher (level 3).
    - Relays, turrets, the wand, spell bow and crossbow, enchanter's tools, flasks, jars and Wilden processing, at the Fletcher, Mechanic, Blacksmith, Crusher, Glassblower and others.
  - **An "Ars Nouveau" research branch** (`archwoodcollective:arsnouveau`) with Ars Crafting Recipes, Ars Wilden Processing and Advanced Ars Wilden Processing.
  - **Citizen names** (`archwood_mages`) and MineColonies item and block tags.
- **Style packs can't be merged.** Structurize stores packs in a map keyed by pack name (`StructurePacks.discoverPackAtPath` → `Map.put`), so a second pack with the same name *replaces* the first instead of adding to it. Our huts therefore go in our own pack, which sits next to Archwood Collective in the build tool.
- **Archwood blueprints as a basis (user decision, 2026-11-23):** the user chose to set Archwood's All Rights Reserved license aside for this project. Archwood blueprints may be used as inspiration and as a basis for ours. In practice, our blueprints are generated by scripts in `tools/` that follow Archwood's layouts and block palette.

## Project: `mods/ArsKingdom`
- **Setup:** copy ArsReSources' setup: ModDevGradle 2.0.148, the Gradle wrapper, the `generateModMetadata` template task, the `gameTestServer` run, `.gitignore` and the `tools/` style.
- **Names:** group `fun.dungeon.debauchery`, package `fun.dungeon.debauchery.arskingdom`, archive `ars_kingdom-1.21.1`.
- **Repositories:**
  - `https://ldtteam.jfrog.io/ldtteam/modding/`, limited to the `com.ldtteam` group.
  - `https://cursemaven.com`, limited to the `curse.maven` group, for Ars Deco.
- **Dependencies:** all non-transitive, and matching the jars installed in MagicMod.
  - `com.ldtteam:minecolonies:1.1.1420-1.21.1`
  - `structurize:1.0.840-1.21.1`
  - `blockui:1.0.212-1.21.1-snapshot`
  - `domum-ornamentum:1.0.237-snapshot`. Try classifier `main` if it doesn't resolve.
  - `localRuntime multipiston:1.2.58-1.21.1`
  - **Ars Deco 5.0.0, now required.**
    - Use `implementation "curse.maven:ars-deco-1557102:8162375"` (the 1.21.1-5.0.0 file; verified in Chunk 0).
    - Fallback: commit the jar to `libs/` and add it as `compileOnly files(...)` plus `localRuntime`, the way ArsReSources already loads Ars Deco from `libs/`.
  - Ars Nouveau, GeckoLib and Curios exactly as in ArsReSources.
  - **Archwood Colonies 1.04, required:** `localRuntime "curse.maven:archwood-colonies-1628280:9232961"`. It has no code, so we never compile against it. It is only needed at runtime so dev clients show its pack and recipes.
  - `localRuntime` Biomes O' Plenty and TerraBlender, matching MagicMod's jars. Without them Structurize disables the Archwood pack, because the pack lists `biomesoplenty` as required.
- **`neoforge.mods.toml`:** required dependencies, all with ordering `AFTER`:
  - `ars_nouveau` `[5.13,)`
  - **`ars_deco` `[5.0,)`**
  - `minecolonies` `[1.1.1400,)`
  - `structurize`
  - **`archwoodcolonies` `[1.04,)`**
- **Mixins:** `ars_kingdom.mixins.json`. Only two, both into Ars Nouveau; none into MineColonies or Ars Deco.
- **One class talks to Ars Deco:** all Ars Deco calls go through `compat/ArsDecoBridge`. It wraps `DyeableArsBlocks.target`, `DyeBlockColorApplier.apply`/`clear`, the color reads and `DyeParticleColors.particleColor`. If an Ars Deco update moves things around, only this file changes.

## Building style: Archwood Collective
**Rule:** for every hut that Archwood Colonies' **Archwood Collective** pack provides, we use its blueprint; we never ship our own version of a hut it covers. Every hut this mod adds gets blueprints that look like they belong in Archwood Collective.

**Using the pack:**
- `archwoodcolonies` is a required dependency (see above), so the pack is always present.
- The player picks Archwood Collective in the build tool for the town hall and all standard huts. Our huts sit in our own pack, **"Ars Kingdom (Archwood Collective)"**, because Structurize can't merge packs. Its `desc` tells players to use Archwood Collective for everything else.
- Anything else Archwood Colonies provides is used rather than duplicated:
  - Its crafter recipes for Ars gear (see M3 and M3b).
  - Its "Ars Nouveau" research branch (see M4).
- **Colony dye works on Archwood buildings too.** Their sconces, pedestals, turrets, sourcestone and archwood decor are Ars Deco targets, so M1 dyes them in the colony color when a builder finishes them. That needs no extra code.

**Style guide for our blueprints** (taken from Archwood Collective's block palette; all native blocks, with no tinting):
- **Frame and walls:** archwood planks, stairs, slabs and fences, with colored archwood logs (bark on) as posts and beams.
- **Never use stripped archwood logs** (user requirement): the final pack adds a mod that makes archwood logs glow, and stripping a log removes the glow.
- **Plot size:** every level of a hut shares one plot size, so a hut's plot must leave room for its higher levels to grow. Archwood's town hall does this: all five levels are 35×27×36, and level 1 uses part of the plot. The Source Sanctum is **29×23×29**, a step below the town hall, and its level 1 uses the middle.
- **Foundations:** stone bricks, polished andesite, cobblestone and deepslate tiles, with cobblestone and andesite walls.
- **Roofs and detail:** Domum Ornamentum shingles, panels and paper walls; archwood and fancy doors; archwood trapdoors and glass panes.
- **Lighting:** sconces, magelight torches and lanterns. Bookshelves, archwood chests and arcane pedestals for interiors.
- **Planting:** archwood leaves and a little greenery (glow lichen, cave vines), as the pack does.
- **Category folders and icons:** the same as Archwood's. The Mage Towers go in `military/`; the Source Sanctum, Imbuement Hall, Artificer's Hall and Arcanist Workshop go in `mystic/`. Icons are drawn in the same style as Archwood's category icons.
- **Footprints and heights** are similar to the matching Archwood hut. For example, a Mage Tower is close to Archwood's `guardtower` and `barrackstower` in size, and the halls are close to its `enchanter` and `alchemist`.
- **School color for Mage Towers:** each tower uses the **colored archwood log of its school**: `red_archwood_log` for Red, `blue_archwood_log` for Blue, `green_archwood_log` for Green and `purple_archwood_log` for Purple, plus their leaf variants (never the stripped logs). Archwood Collective already uses all four, so the towers fit in. The M1 zone dye then dyes the Ars Deco targets inside the tower in the school's `DyeColor`.
- **Use only blocks from mods the pack depends on:** MineColonies, Ars Nouveau, Ars Deco, Domum Ornamentum and vanilla. Biomes O' Plenty blocks are allowed but avoided, so our pack's `mods` list stays `["minecolonies","ars_nouveau","ars_deco","archwoodcolonies"]`.
- **Scanning:** build each blueprint in a dev client that has Archwood Colonies loaded, with an Archwood building of the same category placed next to it as a visual reference. Scan only our own build.

### Package layout
```
arskingdom/ArsKingdom.java               @Mod, DeferredRegisters, MC-bus subscriptions in common setup
config/AKConfig                          SERVER (rule defaults, team-color→dye map, dark purple=undyed, sweep budget, raid chance, skip_archwood_duplicates)
compat/ArsDecoBridge                     the ONLY class that touches lambdamagus.ars_deco.*
compat/ArsLightBridge                    reads/sets the native ParticleColor of SconceTile/MagelightTorchTile/LightTile
colony/dye/AutoLitData                   SavedData per dimension: pos → dye whose light color WE applied (ownership record)
colony/ColonyLookup                      claim-data lookup + per-tick chunk memo
colony/ColonyDye                         team color → DyeColor; zone color (mage tower bounds → school dye, else colony dye)
colony/ColonyRules                       reads Sanctum SettingsModule, config fallback
colony/dye/ColonyDyer                    decides + applies/clears Ars Deco dye on one position (pure decision in DyeDecider)
colony/dye/DyeSweepQueue                 chunk-incremental sweeps with a per-tick budget
colony/dye/AutoDyedData                  SavedData per dimension: pos → dye WE applied (ownership record)
colony/dye/ColonyDyeEvents               place/break/drops/chunk-load/colony-event hooks
data/ArsKingdomSavedData                 per (dim, colonyId) state (sweep version, raid nights)
registry/ AKBlocks AKItems AKBlockEntities AKJobs AKGuardTypes AKBuildings AKModules AKEquipmentTypes AKColonyEvents AKCreativeTab
hut/AKBlockHut, AKTileEntityColonyBuilding, blocks/BlockHutMageTower (one class, 4 instances), BlockHut{ArcanistWorkshop,SourceSanctum,ImbuementHall,ArtificerHall}
building/ BuildingMageTower (school-parameterized), Building{ArcanistWorkshop,SourceSanctum,ImbuementHall,ArtificerHall}
building/modules/ ArsMachineModule, ImbuerCustomModule, ArtificerCustomModule
crafting/ ArsRecipeImporter (Ars imbuement/apparatus recipes → MineColonies CustomRecipes)
battlemage/ MageSchool (enum RED/BLUE/GREEN/PURPLE: DyeColor, glyphs, skills, ids), SchoolSpells, BattlemageGear
job/ JobBattlemage (abstract) + JobBattlemage{Red,Blue,Green,Purple}, JobArcanist, JobSourceKeeper, JobImbuer, JobArtificer
ai/ EntityAIBattlemage BattlemageCombatAI EntityAIWorkArcanist EntityAIWorkSourceKeeper EntityAIWorkImbuer EntityAIWorkArtificer
raid/ WildenRaidEvent WildenRaidScheduler WildenRaidEntityTracker
research/ AKResearchEffects ManaBonusHandler
mixin/ EntityFollowProjectileMixin RitualBrazierTileMixin
gametest/
```

## Milestones (each one can be tested in game on its own)

### M0: skeleton
Set up the build with the dependencies above (including Ars Deco) and a trivial gametest.

**Done when:**
- `./gradlew build` and `runGameTestServer` pass.
- `runClient` starts with MineColonies, Ars Nouveau and Ars Deco loaded.

### M1: colony dye (Ars Deco)
**Goal:** every block Ars Deco can dye that sits inside a colony's claimed chunks is dyed in the colony color, exactly as if a player had used Ars Deco on it. Rendering, the dyed textures and relay/sourcelink particle colors are all handled by Ars Deco itself. We only decide **which** blocks get dyed and **when**.

**Colony color.** `ColonyDye.colonyDye(colony)` maps the team color `ChatFormatting` to a `DyeColor`. The mapping is configurable. **DARK_PURPLE means "undyed"** by default: Ars blocks keep their native purple look, and every other team color, WHITE included, applies a real dye.

| Team color | Dye |
|---|---|
| BLACK | BLACK |
| DARK_BLUE | BLUE |
| DARK_GREEN | GREEN |
| DARK_AQUA | CYAN |
| DARK_RED | RED |
| DARK_PURPLE | undyed (native Ars purple) |
| GOLD | ORANGE |
| GRAY | LIGHT_GRAY |
| DARK_GRAY | GRAY |
| BLUE | LIGHT_BLUE |
| GREEN | LIME |
| AQUA | LIGHT_BLUE |
| RED | PINK |
| LIGHT_PURPLE | MAGENTA |
| YELLOW | YELLOW |
| WHITE | WHITE |

The pairs are open to tuning: RED→PINK avoids two team colors both becoming RED.

**Zone color.** `ColonyDye.zoneDye(level, pos)`:
- Inside the bounds of a Mage Tower (`building.getCorners()`), the color is the **tower's school dye**.
- Anywhere else in the colony, it is the colony dye.

This keeps each school's tower in its own color, even though colony sweeps would otherwise recolor it.

**Colored buildings (extensible).** Mage Towers are the first entry of `colony/BuildingColors`, a list of building types with a color of their own. Each entry is an id plus `IBuilding → Optional<DyeColor>`, and gets its own toggle in the Source Sanctum's rules tab (setting key `ars_kingdom:building_color/<id>`; the server config's `building_colors` lists the types that start on, default `ars_kingdom:mage_towers`). `zoneDye` uses the color of the smallest switched-on colored building containing the position, else the colony dye; whether a block is dyed at all still follows `dye_machines` / `dye_decor` / `dye_lights`. To add a building type later: `BuildingColors.register(id, building -> color)` during mod construction, plus lang for its label and tooltip.

**Ownership: who chose the color.** `AutoDyedData` records `pos → dye` for every block **we** dyed. `DyeDecider` (pure and gametested) decides what to do with each target block:
- **Undyed:** apply the zone dye and record it.
- **Dyed by us** (the current color equals our record): if the zone dye changed, re-apply it; otherwise leave it.
- **Dyed by a player** (dyed but not recorded, or the color differs from our record): leave it and drop any stale record. The exception is `override_custom_colors`, which repaints these too.
- **The position left the colony**, or the colony was deleted, and `undye_on_leave` is on: `clear` the dye and drop the record.

**When we dye** (`ColonyDyeEvents`):
1. **Placing a block:** `BlockEvent.EntityPlaceEvent` at LOW priority, which runs after Ars Deco's own placement handling. An item a player dyed before placing it counts as player-dyed.
2. **Builders finishing:** MineColonies builders place blocks through Structurize, which does not fire place events. So on `BuildingConstructionModEvent` (build, upgrade or repair finished), we queue a sweep of that building's bounds.
3. **Colony color change** (`ColonyTeamColorChangedModEvent`): bump the colony's `dyeVersion` and queue a sweep of all its claimed chunks.
4. **Chunk load:** if the chunk is inside a colony and its stored `dyeVersion` is older than the colony's, queue it. Tower blueprints may carry their own Ars Deco colors (stored on the block entity); the sweep treats those as player choices.
5. **New claims** (`BuildingAddedModEvent`): queue the newly claimed chunks.
6. **Colony deleted** (`ColonyDeletedModEvent`): if `undye_on_leave` is on, clear every recorded position. Otherwise just drop the records.
7. **Breaking a block:** `BlockDropsEvent` at LOWEST priority. If the position is in `AutoDyedData`, remove `BASE_COLOR` from the drop and drop the record. That way the colony's color doesn't follow the item out of the colony; player-chosen dyes still do, as in normal Ars Deco.
8. **Backstop:** a slow periodic sweep of loaded colony chunks every N minutes (configurable) catches anything missed.

**`DyeSweepQueue`:** processes a few chunks per server tick (the budget is configurable).
- For each chunk it checks the chunk's block entities against the Ars Deco targets.
- It only scans a chunk section for non-block-entity targets (sourcestone, archwood) when `LevelChunkSection.maybeHas(state -> ArsDecoBridge.isTarget(state.getBlock()))` says the section might contain one.

**Rule toggles** (on the Source Sanctum's settings tab, see M3; config defaults apply until a Sanctum exists):
- `dye_machines`: functional Ars blocks such as jars, relays, sourcelinks, braziers, turrets and apparatus. Default on.
- `dye_decor`: sourcestone, archwood, sconces and grates. Default on.
- `override_custom_colors`: repaint blocks players dyed. Default off.
- `undye_on_leave`: undo our dye when a block leaves the colony or the colony is deleted. Default on.
- `recolor_transfer_particles`: see M1b. Default on.
- `recolor_rituals`: see M1c. Default on.
- `dye_lights`: see M1d. Recolors magelight torches, sconce flames and mage lights. Default on.
- `wilden_raids`: see M5. Default on.

**M1b: the gap in transfer particles.** Ars Deco recolors relay and sourcelink particles, but not jar-to-machine transfers, which hardcode 255,25,180.
- `EntityFollowProjectileMixin` wraps the static 6-arg `EntityFollowProjectile.spawn` on the server. It uses `@WrapMethod`, or a HEAD cancel with a re-entry guard.
- If the incoming color is the default and the block at `from` is Ars Deco-dyed, it calls the original with `DyeParticleColors.particleColor(dye)`. That is the same color Ars Deco gives dyed relays.
- This covers jars feeding the apparatus, imbuement chamber and similar machines, and the battlemage's own mana refill.

**M1c: the gap in ritual particles.** `RitualBrazierTileMixin` uses `@WrapOperation` on both `makeParticle` calls in `RitualBrazierTile.tick()`, client side. If the brazier is Ars Deco-dyed (its block-entity `ars_deco_color`, synced through the block-entity update packet), it uses that dye's particle color for both the center and outer particles.

**M1d: light colors (magelights, sconce flames, mage lights).** These blocks keep a native Ars light color, so the colony dye sets that color instead of a texture.
- **Which blocks:** `magelight_torch` and every sconce (`SconceTile`/`MagelightTorchTile`), plus `light_block` (`LightTile`). `temporary_light_block` is skipped, since it expires on its own.
- **The color:** the zone dye's `DyeParticleColors.particleColor(dye)`, the same color Ars Deco gives dyed relays, so lights match the dyed blocks around them. It is written with the tile's own `setColor`, which syncs to clients.
- **Ownership:** this follows the same rules as block dye. `AutoLitData` records `pos → dye` for every light we recolored, and `DyeDecider` makes the decision.
  - "Undyed" means the flame the light **draws** is still Ars's default purple (`defaultParticleColor()`), so we may color it. This is judged by the timeline's particle-type color, not the tile's `color` field. **Found after Chunk 5:** Archwood Collective's blueprints save `color` as white while the flame draws the default, so judging by `color` left every Archwood light alone.
  - "Ours" means its color equals the particle color of the dye we recorded.
  - Any other color is a player's choice, such as a light lit or placed with a colored spell, and is left alone unless `override_custom_colors` is on.
- **Undyeing:** on leave, colony deletion or `dye_lights` turned off, our lights are reset to `defaultParticleColor()`, if `undye_on_leave` is on.
- **Unlit sconces:** only the stored color changes. We never light or unlight anything, so an unlit sconce shows the colony color once a player lights it.
- **Drops:** the light color lives in the block entity, not the item, so drops need no handling.
- **Triggers:** the same as block dye. On placement, the magelight torch is placed as a block. All sweeps include these tiles, which are found in the block-entity scan.
- **Found in Chunk 4:** the flame is drawn by the tile's particle emitter from its saved `LightTimeline`, and the `color` field is only a copy. Every light saves a timeline, and the timeline has two colors: a top-level color property and the particle type's own sub-color, which is what `colorProp()` returns. `ArsLightBridge.setColor` writes `color`, drops `timeline`, and reloads the tile, so Ars rebuilds the timeline from the color. It then also sets the particle type's color, and calls the tile's `setColor`, which syncs to clients. The client rebuilds its emitter from the synced data.

**Removed from the earlier draft:** the jar-liquid tint, the grey textures, `ClientColonyColorCache`, and the colony-color particle lookup. Ars Deco's own dyed textures replace all of them. If Ars Deco's dyed jar texture doesn't recolor the liquid itself, we accept that; there is no tinting.

### Shared hut infrastructure (needed by M2 and M3)
Follow Trade Post's `MCTPBaseBlockHut` and `ModBuildingsInitializer`.
- **`AKBlockHut`:**
  - overrides `getRegistryName()` to return `ars_kingdom:<hutName>`;
  - overrides `newBlockEntity`, which uses `AKBlockEntities.BUILDING` and sets `registryName`;
  - overrides `onBlockPlacedByBuildTool`.
- **Registration:**
  - Buildings: `CommonMinecoloniesAPIImpl.BUILDINGS` with `BuildingEntry.Builder`.
  - Jobs: `CommonMinecoloniesAPIImpl.JOBS` with `JobEntry.Builder`. Check whether 1420 still needs `ModJobs.jobs.add`.
- **Translations:** `com.ars_kingdom.building.<path>` and `com.ars_kingdom.job.<path>`.
- **Hut block look:** no new or repainted textures.
  - Each Mage Tower hut block model points at Ars Deco's existing texture for its school, e.g. `ars_deco:block/<sourcestone key>/red.png`, along with Ars Nouveau's own textures.
  - The Workshop and Sanctum huts use plain Ars Nouveau textures.
- **Blueprints** (you scan these in game). **All of them follow the Archwood Collective style guide above.**
  - They ship at `blueprints/ars_kingdom/arskingdom/{military,mystic}/<name><N>.blueprint`. The `pack.json` has `name: "Ars Kingdom (Archwood Collective)"`, `pack-format: "1"` and `mods: ["minecolonies","ars_nouveau","ars_deco","archwoodcolonies"]`, plus a pack icon and category icons in Archwood's style.
  - **Mage Towers:** four sets in `military/`, `magetower<school>1..3`, for **12 blueprints**. Each is anchored on its school's hut block.
    - Build them in the Archwood Collective style, with the school's colored archwood logs as the accent color, plus Ars Deco targets such as sconces, pedestals, sourcestone and jars.
    - **Redesign (2026-11-25):** one round design for every school, differing only in the school's color. Level 1 is an 11-block-wide circular tower with two storeys 8 blocks tall: school archwood log frame, sourcestone large brick foundation, archwood plank walls. Ground floor, entering by the door: the hut on the left wall, a ladder straight across from the door, and on the right a source jar, a scribe's table and an arcane pedestal. Upper floor: the battlemage's bed in the school color. Levels 2–3 and the other schools are built up from this design next.
    - The M1 zone rule re-applies the school dye inside the tower's bounds after each build or upgrade. So Ars Deco colors on blocks without a block entity, which a blueprint doesn't store, still come out right.
  - Other huts start with `getMaxBuildingLevel() = 1`. Raise it only when the next-level blueprints exist.
  - To make each blueprint: either generate it with a script in `tools/` (as `tools/gen_sanctum_blueprint.py` does for the Sanctum, modelled on Archwood's buildings), or build it in game around the hut block, scan it with the Structurize scan tool, and copy the file into resources.
  - **Never ship a blueprint for a hut that Archwood Collective already covers.**

### M2: Mage Towers and the four battlemage schools
**The school is chosen at build time.** There are four separate hut blocks and `BuildingEntry`s, one per school. Each is its own hut in the build tool, has its own blueprints, and hires only its own school. A tower's school never changes.

**`MageSchool` enum:** the single source of truth for each school. It holds the school's id, its **`DyeColor`**, its glyphs, its skills, and its registry ids.

| School | Hut / guard type id | DyeColor | Spell (MethodProjectile + …) | Role |
|---|---|---|---|---|
| Red | `magetowerred` / `battlemage_red` | RED | EffectIgnite + EffectHarm | fire damage over time |
| Blue | `magetowerblue` / `battlemage_blue` | BLUE | EffectFreeze + EffectHarm (Snare costs 100 mana alone) | slows enemies |
| Green | `magetowergreen` / `battlemage_green` | GREEN | EffectKnockback + EffectHarm; EffectCrush (tier 2) added from tower level 2 | earth damage, crowd control |
| Purple | `magetowerpurple` / `battlemage_purple` | PURPLE | EffectHarm + Amplify | highest raw arcane damage |

The glyphs are a starting point to tune.

**One school, one color.** The school's `DyeColor` is applied to everything:

| Thing | How it gets the color |
|---|---|
| **Hut block** | Its model uses Ars Deco's existing `<school color>` texture. |
| **Tower build** | Built in the Archwood Collective style with the school's colored archwood logs (red, blue, green or purple). The Ars Deco targets inside are dyed with the school dye, which the M1 zone rule keeps in place. |
| **Robes** (Arcanist armor) | `BASE_COLOR` is set to the school dye when the armor is equipped. Ars renders its own `arcanists_armor_<color>` texture. |
| **Spellbook** | `BASE_COLOR` is set to the school dye when the book is equipped. Ars renders its own `spellbook_<color>` texture. |
| **Spells** | Every particle slot in the cached `Spell`'s timeline is set to the school dye's particle color (`DyeParticleColors.particleColor(dye)`). That is the same color mapping Ars Deco uses, so spells match dyed blocks. |
| **Mana refill particles** | Taken from the dyed jar's color (M1b). These are source transfers, not spells. |

**Tiers = tower building levels 1–3.** `getMaxBuildingLevel()` returns 3, and each level has its own blueprint.

| Tower level | Spellbook allowed (max) | Arcanist armor tier allowed (max) | Spell tier |
|---|---|---|---|
| 1 | Novice | tier 1 | 1 |
| 2 | Apprentice | tier 2 | 2 |
| 3 | Archmage | tier 3 | 3 |

**Equipment types** (`AKEquipmentTypes`; a `DeferredRegister` on `CommonMinecoloniesAPIImpl.EQUIPMENT_TYPES`):
- `ars_kingdom:spellbook`
  - Matches: `stack.getItem() instanceof SpellBook`, excluding the creative book. Accepted items come from the tag `#ars_kingdom:battlemage/spellbooks`.
  - Level: `SpellTier` 1..3.
- `ars_kingdom:arcanist_hood`, `arcanist_robes`, `arcanist_leggings`, `arcanist_boots`
  - Matches: the item tag `#ars_kingdom:battlemage/<slot>`, which by default contains the Arcanist piece. Pack makers can add the Sorcerer or Battlemage sets to the tag.
  - Level: `PerkUtil.getPerkHolder(stack).getTier() + 1`, which is 1..3.
- `BuildingMageTower.getMaxEquipmentLevel()` is overridden to return exactly the building level. The default returns MAX at the building's max level, which would allow anything.
- Every request is `Tool(type, 1, buildingLevel)`. Gear above the tower's tier is never requested or accepted. Upgrading the tower raises the cap, and the battlemage then picks up better gear from the hut or asks for it.

**"Not allowed to work" gating** (`EntityAIBattlemage` constructor and `prepare`):
- **Clear the default guard gear:** empty the inherited `itemsNeeded`, which holds the default leather-to-diamond `GuardGear`, so no vanilla armor is ever requested. Also empty the default `toolsNeeded`.
- **Add our five types to `toolsNeeded`:** `spellbook` plus the four armor types. `AbstractEntityAIFight.prepare()` checks each with `checkForToolOrWeapon`. If anything is missing, the citizen:
  - stays blocked (`JobStatus.STUCK`), so it does not patrol or fight;
  - creates a `Tool` request, which shows the normal "I need …" request interaction and lets couriers or players deliver it.
- **Show a clear status:** while blocked, also show our own BLOCKING `StandardInteraction` with the text "I'm not allowed to work without a spellbook and a full set of Arcanist armor (tier ≤ N)". Show a custom `VisibleCitizenStatus` too, `ars_kingdom:unequipped`, whose icon reuses an Ars Nouveau texture.
- **Equip:** once everything is present, put the four armor pieces into the armor slots with `InventoryCitizen.transferArmorToSlot`, and the book into the main hand with `setItemSlot(MAINHAND)`.
  - `BattlemageGear.applySchoolColor(stack, school)` sets `BASE_COLOR` at that moment. That is the "hut color applied when equipped" rule. The color stays on the item afterwards, as with any dye.
- **Re-check every cycle in `prepare`:** if a piece is removed, the work stops again.

**The rest of M2:**
- **Guard types:** registered under the path `battlemage_<school>`. Skills are Mana and Focus, the sound is `druid`, and the job entry is the school's job.
- **Jobs:** `JobBattlemage extends AbstractJobGuard` is abstract and has `getSchool()`. There are four one-line subclasses, because `GuardType.setClazz` needs a distinct class for each type.
  - The model is `ModModelTypes.DRUID_ID`; the Arcanist armor renders over it.
  - They store `storedMana` in their saved data.
- **`EntityAIBattlemage extends AbstractEntityAIGuard`** (modeled on `EntityAIDruid`):
  - At the hut it refills mana with `SourceUtil.takeSourceMultipleWithParticles` from jars.
  - The mana cap scales with the book tier and with research.
- **`BattlemageCombatAI extends AttackMoveAI<EntityCitizen>`:**
  - It casts `SchoolSpells.get(school, bookTier)`, a cached `Spell` per school and tier. Tier 1 is the base spell; tiers 2 and 3 add Amplify, AOE or Extend Time. Only glyphs valid for that book tier are used.
  - Spells are fired through `SpellContext(level, spell, citizen, LivingCaster.from(citizen))`, a silent `SpellResolver` and `EntityProjectileSpell`.
  - Targeting uses `AbstractEntityAIGuard.isAttackableTarget`.
  - Fallback: an "emulated bolt" (Ars particles in the school color plus `indirectMagic` damage plus the school's effect).
- **`BuildingMageTower extends AbstractBuildingGuards`:** one class that takes the school.
  - The schematic is `magetower<school>`, max level 3.
  - The module key is `ars_kingdom_battlemage_<school>_work`.
  - Modules:
    - `GuardBuildingModule(<school guard type>, true, b -> level)`
    - `GUARD_ENTITY_LIST`
    - `GUARD_SETTINGS`
    - `MIN_STOCK`
    - `BED`
    - `STATS_MODULE`
    - No `GUARD_TOOL`: our equipment types replace it.

    The view is `AbstractBuildingGuards.View`.
- **Hardcoded guard checks:** the `instanceof JobKnight/JobRanger` checks in `EntityCitizen` don't apply to us, the same as for the Druid. No mixin.

### M3: Source economy
- **Arcanist Workshop** (job `arcanist`, a crafter):
  - `AbstractJobCrafter` with `AbstractEntityAICrafting`.
  - It pays each output's source cost from jars near the hut, using `data/ars_kingdom/arcanist_source_costs/*.json`. When the jars can't pay, it shows a "needs source" status.
  - Modules: `WorkerBuildingModule(ARCANIST, Mana, Focus, false, b->1)`, `ArcanistCustomModule` (crafter key `arcanist_custom`), `CRAFT_TASK_VIEW`, `MIN_STOCK` and `STATS_MODULE`.
  - Recipes live in `data/ars_kingdom/crafterrecipes/arcanist/*.json`.
  - **No duplicates of Archwood Colonies recipes.** Archwood already lets the Fletcher (level 3, after its "Ars Crafting Recipes" research) craft the Arcanist, Battlemage and Sorcerer armor, so the battlemages' armor comes from there. Its Fletcher, Mechanic, Blacksmith and other crafters also cover relays, turrets, wands and more.
    - The Arcanist only crafts Ars items that no Archwood recipe covers, starting with the **spellbooks**.
    - `tools/check_archwood_overlap.py` lists every output in Archwood's `crafterrecipes` and fails the build if one of our Arcanist recipes makes the same item.
  - **Ars Deco:** spellbooks the Arcanist crafts, and armor from Archwood's Fletcher, come out undyed. The school color is applied only when a battlemage equips them.
- **Source Sanctum** (job `sourcekeeper`; this is also the rules hut):
  - The keeper turns fuel into source in jars inside the building.
  - It carries the `ars_kingdom_rules` `SettingsModule` with the M1 toggles.
  - **Ars Deco:** the jars it fills are colony-dyed through M1, and its transfer particles follow the jar dye through M1b.
  - Stretch goal: a colony reservoir through `SourceManager.INSTANCE.addInterface`.
  - **Keeping the machine halls supplied:** the keeper also tops up the jars that the Imbuement Hall and Artificer's Hall register (below), in order of lowest fill first, so those halls don't run dry.

### M3b: Ars machine halls (Imbuement Chamber and Enchanting Apparatus)
None of the earlier buildings can make imbuement or apparatus recipes; the Arcanist Workshop only does MineColonies' own crafter recipes. These two halls add that. Their citizens use the **real Ars blocks** placed by the blueprint, the same way MineColonies' Alchemist uses real brewing stands.

**What we found in the Ars and MineColonies jars:**
- **Both Ars machines can be run from code without a player.**
  - Imbuement Chamber (`ImbuementTile`):
    - Pedestals within 1 block (3×3×3) act as catalysts and are **not consumed**.
    - The chamber only accepts a reagent (`canPlaceItem`) if the pedestals around it already match a recipe, so pedestals go first.
    - `setItem(0, reagent)` starts the craft: 100 ticks, plus pulling `recipe.source` from jars within 2 blocks at 200 per second. With no jars it makes 10 source per second on its own.
    - The output replaces the input in slot 0.
  - Enchanting Apparatus (`EnchantingApparatusTile`):
    - Uses pedestals within 3 blocks (7×7×7).
    - `setItem(0, reagent)` calls `attemptCraft(reagent, null)`, which takes the **whole** source cost up front from jars within 10 blocks (`SourceUtil.takeSourceMultipleWithParticles`).
    - The craft takes 210 ticks. The result appears in the core, and the pedestal items are consumed down to their crafting remainders (buckets and the like).
    - `craftingPossible(reagent, null)` checks the recipe and the source before inserting.
- **Recipe counts:**
  - 13 `ars_nouveau:imbuement` recipes: the essences, amethyst, lapis and the arrows.
  - 82 plain `ars_nouveau:enchanting_apparatus` recipes. In 22 of them the result keeps the reagent's data (`keepNbtOfReagent`), but the output item is still fixed.
  - The dynamic apparatus types are separate recipe types: `enchantment` (96), `reactive_enchantment`, `spell_write` and `armor_upgrade`.
- **MineColonies' recipe screen can't teach these.** It can only teach crafting-grid, furnace and brewing recipes.
  - The supported way to give a crafter custom recipes is `CustomRecipeManager`: datapack `crafterrecipes` JSON, or adding them in code with `addRecipe`. The building's crafting module then learns them automatically (`checkForWorkerSpecificRecipes`).
  - `IGenericRecipe` has no field for a source cost. The AI enforces it at the machine instead.
- **Precedent:** the Alchemist overrides `craft()` when the recipe's `intermediate` block is `BREWING_STAND`, and runs the real brewing stands registered on its building. Blocks placed by a blueprint are reported to modules that implement `IModuleWithExternalBlocks.onBlockPlacedInBuilding`; that is how the Smelter's furnace module finds its furnaces.
- **Correction to the earlier draft:** `minecolonies-emi-integration` does **not** show crafter recipes. These halls' recipes, and the Arcanist's, show in the hut's own recipe list, not in EMI. The Chunk 11 test is updated below.

**Shared pieces:**
- **`ArsMachineModule implements IModuleWithExternalBlocks, IPersistentModule`:** records the positions of `ImbuementBlock`, `EnchantingApparatusBlock`, `ArcanePedestal`/`ArcanePlatform` and source jars that the builder places, and saves them. When a position no longer holds that block, it is dropped (the Alchemist's `removeBrewingStand` pattern).
- **`ArsRecipeImporter`:** on MineColonies' `CustomRecipesReloadedEvent`, which runs after every datapack reload, it reads Ars recipes from the `RecipeManager` and adds one `CustomRecipe` for each through `CustomRecipeManager.addRecipe`.
  - Imbuement recipes go to crafter key `imbuer_custom` with intermediate `BlockRegistry.IMBUEMENT_BLOCK`. The inputs are the reagent plus the pedestal catalysts, and the catalysts come back as secondary outputs because they aren't consumed.
  - Plain apparatus recipes (including the keep-data ones) go to `artificer_custom` with intermediate `ENCHANTING_APP_BLOCK`. The inputs are the reagent plus the pedestal items, and any crafting remainders come back as secondary outputs.
  - The minimum building level comes from the recipe's source cost, using configurable thresholds. Pedestal count also matters: a recipe needing more pedestals than the hut level has is skipped for that level.
  - **Check:** that `CustomRecipesReloadedEvent` fires before the recipe sync to clients, so the GUI lists them. If not, write the recipes out as generated datapack JSON with `tools/gen_ars_crafter_recipes.py` instead.
- **Reading recipes from other mods:** this also picks up imbuement and apparatus recipes added by other Ars addons (Ars Elemental, Ars Additions and others), because it reads the live `RecipeManager`.
- **Archwood Colonies comes first:** when an Archwood Colonies crafter recipe already makes the same item (for example Arcanist armor at the Fletcher), the importer skips it, so the item is made where Archwood puts it. It finds these by looking for `CustomRecipe`s whose id is in the `archwoodcollective` namespace. The config option `skip_archwood_duplicates` (default on) controls this.
- **Job names:** `imbuer` and `artificer`. They must not clash with MineColonies' own job paths (it already has `enchanter`), because the crafter key drops the namespace.

**Imbuement Hall** (hut `blockhutimbuementhall`, job `imbuer`, max level 3):
- **Classes:** `JobImbuer extends AbstractJobCrafter`. `EntityAIWorkImbuer extends AbstractEntityAICrafting` overrides `craft()`; when the recipe's intermediate isn't `IMBUEMENT_BLOCK` it falls through to the normal craft.
- **A craft, step by step:**
  1. Pick a free chamber from `ArsMachineModule`. Building level N allows N chambers to run at once.
  2. Walk there and place each catalyst on an empty pedestal next to the chamber.
  3. Check `canPlaceItem`, then insert the reagent.
  4. Leave it; the worker can start other chambers meanwhile.
  5. Come back when `getStack()` equals the recipe output.
  6. Take the result, take the catalysts back, and do the delivery bookkeeping by hand, as the Alchemist does (`addDelivery`, the craft counter, `finishRequest`).
- **If it can't run:**
  - No chamber registered: a BLOCKING `StandardInteraction`, "This hall has no Imbuement Chamber".
  - Not enough pedestals: an interaction saying how many pedestals are free and how many the recipe needs.
- **Modules:** `WorkerBuildingModule(IMBUER, Mana, Knowledge, false, b->1)`, `ImbuerCustomModule extends AbstractCraftingBuildingModule.Custom` (crafter key `imbuer_custom`), `ArsMachineModule`, `CRAFT_TASK_VIEW`, `MIN_STOCK` and `STATS_MODULE`.
- **Blueprints:** `imbuementhall1..3`. Level N has N chambers, each ringed by 8 pedestals and with jars within 2 blocks.

**Artificer's Hall** (hut `blockhutartificerhall`, job `artificer`, max level 3):
- **Classes:** `JobArtificer extends AbstractJobCrafter`. `EntityAIWorkArtificer` overrides `craft()` for recipes whose intermediate is `ENCHANTING_APP_BLOCK`.
- **A craft, step by step:**
  1. Pick a free apparatus.
  2. Fill its pedestals with the recipe's pedestal items.
  3. Check `craftingPossible(reagent, null)`. If the source isn't there, show a "not enough source near the apparatus" interaction and wait.
  4. Insert the reagent; the craft starts by itself.
  5. Wait while `isCrafting`, about 10.5 seconds.
  6. Take the result from the core and collect the pedestal remainders.
  7. Do the delivery bookkeeping.
- **Modules:** `WorkerBuildingModule(ARTIFICER, Mana, Dexterity, false, b->1)`, `ArtificerCustomModule` (crafter key `artificer_custom`), `ArsMachineModule`, `CRAFT_TASK_VIEW`, `MIN_STOCK` and `STATS_MODULE`.
- **Blueprints:** `artificerhall1..3`. Level N has N apparatuses with 8 pedestals each, and the levels 2 and 3 builds also have more jars within 10 blocks.
- **Stretch: dynamic apparatus recipes**, as a later chunk.
  - **Battlemage armor upgrades** (`armor_upgrade`): a special order in the hut for "upgrade Arcanist piece to tier N". It runs the real `ArmorUpgradeRecipe` on the apparatus, so the colony can make tier-2 and tier-3 battlemage gear itself.
  - **Enchanting** (`enchantment`): "add enchantment X level N to item Y" orders.
  - These can't be plain custom recipes, because their inputs depend on the item's data.

**Ars Deco and the colony rules:**
- Chambers, apparatuses, pedestals and jars are all Ars Deco targets, so M1 dyes them in the colony color. The machine particles follow the jar's dye through M1b.
- M1 dyeing is purely cosmetic and never changes machine behavior.

### M4: Arcana research
- **Branch: Archwood Colonies' existing "Ars Nouveau" branch** (`archwoodcollective:arsnouveau`), instead of a new branch. Its subtitle says it is meant to be shared ("Used by Archwood Collective (and potentially others)").
  - Our research files go in `data/ars_kingdom/researches/arcana/*.json`, with `"branch": "archwoodcollective:arsnouveau"`. We don't ship a branch file of our own.
  - Research that unlocks gear-related huts (the Mage Towers, Arcanist Workshop and Artificer's Hall) has `archwoodcollective:arsnouveau/arscraftingrecipes` as its parent, because that research also unlocks Archwood's Arcanist armor recipes.
  - We don't recreate anything already in the branch (crafting recipes, Wilden processing).
  - **Check:** that MineColonies accepts research files from another namespace in that branch, with a parent from Archwood's namespace. If it doesn't, fall back to our own `ars_kingdom:arcana` branch, with no parents from Archwood.
- **Effects:**
  - Hut unlocks, one per school tower (`ars_kingdom:effects/blockhutmagetower<school>`), plus `…/blockhutarcanistworkshop`, `…/blockhutimbuementhall` and `…/blockhutartificerhall`. The Sanctum stays unlocked.
  - `battlemagedamage` and `battlemagemana`. The old `battlemagetier` effect is dropped, because the tier now comes from the book and tower level.
  - `colonymanaregen` and `colonymaxmana`, applied by `ManaBonusHandler` through `ManaRegenCalcEvent` and `MaxManaCalcEvent` to colony members inside their own colony.
  - Optional: a "Colony Heraldry" research that unlocks `dye_decor`, while `dye_machines` is always available.

### M5: Wilden raids
- **Registration:** `ColonyEventTypeRegistryEntry(WildenRaidEvent::loadFromNBT, ars_kingdom:wilden_raid, true)`.
- **`WildenRaidEvent implements IColonyRaidEvent`:** it spawns Hunters, Stalkers and Guardians, plus the Chimera at a high raid level. Mobs are tagged with event and colony ids and registered with `registerEntity`. It ends when all are dead or it times out, and it is saved through NBT.
- **`WildenRaidEntityTracker`:** forwards `LivingDeathEvent` to `onEntityDeath`, and re-registers mobs on `EntityJoinLevelEvent`.
- **`WildenRaidScheduler`:** a chance at each night start. It runs only when:
  - `canHaveRaiderEvents` is true;
  - the colony is not already raided;
  - no Wilden raid is active;
  - the `wilden_raids` rule is on;
  - enough nights have passed.

  There is also a test command, `/arskingdom raid <colonyId>`.
- **Ars Deco:** nothing to change. Blocks broken in a raid lose only the colony dye (M1 drop rule).

## Verification
- **Gametests** (`./gradlew runGameTestServer`):
  - `DyeDecider`: undyed, our dye, player dye, override, leaving the colony.
  - The team color → dye map.
  - Zone dye (inside a tower's bounds vs. outside).
  - `BattlemageGear`: equipment-level lambdas for each book and armor tier, max level = tower level, and the creative book rejected.
  - `MageSchool` ↔ `DyeColor`, the source-cost table and the raid-scheduler gating.
  - JSON and tag parsing.
  - One gametest places Ars Deco target blocks and calls `ColonyDyer` directly, to check that `ArsDecoBridge` applies and clears the dye. It runs with Ars Deco on the gametest classpath.
- **In-game checks** (dev client, then MagicMod):
  - **M1:** set the team color to GREEN (which becomes LIME).
    - Source jars, relays, sourcelinks, braziers and other machines, plus sourcestone and archwood decor inside the borders, look exactly like Ars Deco LIME-dyed blocks. Outside the borders they are untouched.
    - Relay, sourcelink and jar-to-machine particles and ritual particles are LIME.
    - Pre-placed magelight torches, sconce flames and mage lights in the colony glow LIME. A light a player lit with a red spell stays red.
    - A block a player dyed RED stays RED.
    - Changing the team color re-dyes only our blocks.
    - Blocks finished by a builder are dyed once the build completes.
    - Breaking a colony-dyed jar drops an undyed jar.
    - Deleting the colony undyes our blocks.
    - Unloaded chunks catch up when they load.
    - No TPS spikes during a large sweep (check with `/neoforge tps`).
  - **M2:**
    - The build tool lists four Mage Towers. Each one hires only its own school.
    - A battlemage without a book and four Arcanist pieces shows "not allowed to work", posts requests and does not patrol.
    - At tower level 1, an apprentice book or tier-2 armor is refused. After upgrading to level 2 it is accepted.
    - Once equipped, the book and armor take the school color (Ars textures). The armor renders on the citizen. The spells are in the school color.
    - The tower blocks stay in the school color even with a different team color.
    - Removing a piece stops work. Battlemages drain jars, kill zombies and Wilden without hurting citizens, and the rally banner works.
  - **M3:** the Arcanist and keeper work, and the rules tab toggles the M1 behavior live.
  - **M3b:** the Imbuement Hall and Artificer's Hall list every Ars imbuement and apparatus recipe, including addon recipes, in their recipe screens. A request for an essence or an apparatus item is crafted on the real machines, with visible pedestal use and source drain. Catalysts come back to the worker, and remainders are collected. A missing machine or missing source shows an interaction.
  - **Style:** the build tool shows Archwood Collective and "Ars Kingdom (Archwood Collective)" side by side. An Archwood town hall, builder and guard tower next to our towers and halls look like one consistent style. Archwood buildings' Ars blocks (sconces, pedestals, turrets) are colony-dyed once built.
  - **M4:** our research shows up in Archwood's "Ars Nouveau" branch, the towers unlock, and the mana bonuses work.
  - **M5:** `/arskingdom raid` works, the raid survives a relog, and it doesn't overlap a MineColonies raid.
- **Testing in MagicMod:** copy the built jar into `minecraft/mods/`. Ars Deco 5.0.0, Archwood Colonies 1.04 and Biomes O' Plenty are already installed there. For release, publish on CurseForge and add it through Prism.
- **Docs:** add an Ars Kingdom row to the root `CLAUDE.md`, and write `mods/ArsKingdom/CLAUDE.md`.

## Risks / open items
- **Geckolib Ars armor and spellbooks on citizens.** That they render through `CitizenArmorLayer`/`ItemInHandLayer` is inferred from the code, not tested. **Spike this first in M2.** If it fails, fix it with a small client render layer for battlemages. That is still not tinting: it renders Ars's own dyed textures.
- **Ars Deco's internal API** (`lambdamagus.ars_deco.*`) is not a public API. Mitigations: pin `[5.0,)`, isolate it in `ArsDecoBridge`, and use the gametest above as a canary. The CurseMaven ids for Ars Deco still need looking up; the fallback is a jar in `libs/`.
- **Sweep cost** on large colonies with lots of decor: use the per-tick budget, the `maybeHas` section filter, and `dye_decor` can be turned off.
- **Builders don't fire place events:** this relies on `BuildingConstructionModEvent` plus the periodic backstop. Partly-built blocks stay undyed until the build completes.
- **The team-color → dye map** collapses 16 chat colors onto 16 dyes imperfectly; it is configurable.
- **Item colors are permanent:** equipping recolors a player's book or armor for good, the same as dyeing it. Say so in the tower's tooltip or GUI.
- **Battlemage friendly fire** and Ars mana checks for non-player casters: prototype early, with the emulated-bolt fallback ready.
- **Unverified details:**
  - whether `ModJobs.jobs.add` is still needed;
  - the MixinExtras version needed for `@WrapMethod`;
  - ~~the Domum artifact name~~ (resolved in Chunk 0: `domum-ornamentum:1.0.237-snapshot:main`);
  - the "no colony" id being 0;
  - whether Ars Deco syncs `ars_deco_color` to the client in block-entity update tags (needed by M1c);
  - ~~whether sconce and magelight flames render from `SconceTile.color` or from its `LightTimeline`~~ (resolved in Chunk 4: the timeline; both of its colors are set);
  - whether the flame visibly changes in game: the gametests check the saved data, not the rendering.
- **MineColonies internals:** much of what we use is in `core.*`, not the public API. Pin `[1.1.1400,)` and retest on updates.
- **Machine halls:**
  - Whether recipes added in `CustomRecipesReloadedEvent` reach clients. The fallback is generated datapack JSON.
  - Hoppers or other mods pulling items out of a chamber mid-craft. Imbuement `removeItem` is never locked, so tell players not to automate the halls' machines.
  - Apparatus source is spent up front, so a recipe that stops matching mid-craft wastes it. The worker never touches the pedestals during a craft.
- **Blueprints:** none exist yet. The towers need 12 (4 schools × 3 levels), and the other huts need at least a level-1 blueprint each.
- **Archwood Colonies:**
  - **License (All Rights Reserved):** the user chose to set this aside and use Archwood's blueprints as a basis (2026-11-23). This matters again if Ars Kingdom is published: check before a public release.
  - **Two packs in the build tool:** players switch to our pack to place our huts. MineColonies saves each building's pack, so upgrades still use the right blueprints.
  - **Archwood updates** could rename its research ids or recipe namespaces. The M4 parent links and the duplicate check depend on them; pin `[1.04,)` and recheck on updates.
  - **Biomes O' Plenty** is needed for the Archwood pack to load. MagicMod already has it.

## Implementation & testing checklist
Each chunk below can be built and tested on its own. Do them in order, and finish a chunk's **Test** items before starting the next chunk. **Every future addition to this plan must also be added here**, either as new items in an existing chunk or as a new chunk in the right place.

### Chunk 0: project skeleton
- [x] Create `mods/ArsKingdom` from the ArsReSources setup: Gradle wrapper, ModDevGradle 2.0.148, `generateModMetadata`, `.gitignore`, `CLAUDE.md`.
- [x] Add the repositories (ldtteam, cursemaven) and the dependencies: MineColonies, Structurize, BlockUI, Domum, Multipiston (runtime), Ars Nouveau, GeckoLib, Curios and **Ars Deco**. Fall back to `libs/` for Ars Deco if CurseMaven doesn't work.
- [x] Add `localRuntime` **Archwood Colonies** (`curse.maven:archwood-colonies-1628280:9232961`), Biomes O' Plenty and TerraBlender.
- [x] `neoforge.mods.toml` with required `ars_nouveau`, `ars_deco`, `minecolonies`, `structurize` and `archwoodcolonies` (all AFTER). Add an empty `ars_kingdom.mixins.json`.
- [x] `ArsKingdom` main class, `AKConfig` (server config, with placeholders), and a trivial gametest.
- [x] **Test:** `./gradlew build` passes, and `runGameTestServer` passes.
- [x] **Test:** `runClient` reaches the title screen, and the mod list shows ars_kingdom, MineColonies, Ars Nouveau, Ars Deco and Archwood Colonies.
- [x] **Test:** in a dev world, the Structurize build tool lists the Archwood Collective pack, and the log has no "pack disabled, missing mod" warning for it.

### Chunk 1: Ars Deco bridge and colony lookup
- [x] `compat/ArsDecoBridge`: `isTarget(block)`, `target(block)`, `getColor(level,pos)`, `apply(level,pos,dye)`, `clear(level,pos)` and `particleColor(dye)`. It also splits targets into machine and decor categories.
- [x] `colony/ColonyLookup` (claim-data lookup plus a per-tick memo). Confirmed: the "no colony" id is 0 (`ChunkClaimData.owningColony` defaults to 0; colony ids start at 1).
- [x] `colony/ColonyDye`: team color → dye map (from config; DARK_PURPLE means undyed, WHITE dyes white). A config still holding the old default table, where WHITE was unset, is migrated automatically. Zone dye is a stub for now and returns the colony dye.
- [x] **Test (gametest):** place a source jar, a relay and a sourcestone block, then apply and clear the dye through the bridge. The color reads back correctly; this test also catches breaking changes in an Ars Deco update.
- [x] **Test (gametest):** the team color → dye mapping table.
- [x] **Test (in game, MagicMod):** a debug command `/arskingdom where` (op level 2) prints the colony id and dye at the player's position, inside and outside a colony.

### Chunk 2: colony dye, event-driven
- [x] `DyeDecider` (pure), covering: undyed, our dye, player dye, override and leaving the colony.
- [x] `AutoDyedData` (pos → dye ownership record) and `ColonyDyer.process(level,pos)`.
- [x] `ColonyDyeEvents`: `EntityPlaceEvent` (LOW priority) and `BlockDropsEvent` (LOWEST priority; strips `BASE_COLOR` from colony-dyed drops).
- [x] Rule toggles read from config: `dye_machines`, `dye_decor`, `override_custom_colors`, `undye_on_leave`. They go through `colony/ColonyRules`, which Chunk 5 points at the Sanctum's settings. A disabled category is treated like "no colony dye here", so our dye is removed when `undye_on_leave` is on.
- [x] **Test (gametest):** every `DyeDecider` case, plus `ColonyDyer` on a real jar and on sourcestone (dye, recolor, player dye kept, undye).
- [x] **Test (in game):**
  - [x] Blocks placed inside a colony are dyed in the colony color; blocks outside are not.
  - [x] A block the player dyed before placing keeps its color.
  - [x] Breaking a colony-dyed jar drops an undyed jar, while a player-dyed jar keeps its dye.
  - [x] Turning `dye_decor` off leaves sourcestone undyed.

### Chunk 3: colony dye, sweeps
- [x] `DyeSweepQueue`: a per-tick budget, a scan of block entities, and a scan of chunk sections filtered with `maybeHas`. It also revisits every position recorded in `AutoDyedData` for the chunk (now indexed per chunk). Queue entries are either *checks* (sweep only if the chunk's stamp is stale, up to 512 per tick) or *forced* sweeps; unloaded chunks are skipped and caught when they load.
- [x] A per-colony `dyeVersion` in `ArsKingdomSavedData`. Re-sweep on team color change, on chunk load when the chunk is out of date, on `BuildingAddedModEvent` (new claims) and `BuildingRemovedModEvent` (claims shrinking), and on `BuildingConstructionModEvent` (the building's bounds). Each swept chunk is stamped with (owning colony, version); a config epoch, bumped when the dye config's hash changes (checked at server start and on config reload), is added to every colony's version.
- [x] When a colony is deleted, undye the blocks we dyed (if `undye_on_leave`) and drop the records. Add the periodic backstop sweep. Config: `sweep_chunks_per_tick` (default 2) and `backstop_sweep_minutes` (default 30, 0 = off). Debug command `/arskingdom sweep` force-sweeps the colony you stand in.
- [x] **Test (gametest):** a chunk sweep outside any colony undyes our jar (block entity) and sourcestone (found by the section scan), drops the records, and leaves a player-dyed relay alone; the stamps follow colony versions.
- [x] **Test (in game):**
  - [x] Changing the team color re-dyes our blocks only; player-dyed blocks stay.
  - [x] After a builder finishes a hut that contains Ars blocks, they are dyed. Test this with an Archwood Collective hut, such as its guard tower or enchanter: its sconces, pedestals and sourcestone take the colony color. (Its lights failed at first; fixed in Chunk 4's "Fix (after Chunk 5 testing)" and confirmed in game.)
  - [x] Chunks that were unloaded during a color change catch up when they load.
  - [x] Deleting the colony undyes our blocks.
- [x] **Test (performance):** sweep a colony with a large amount of sourcestone and check that `/neoforge tps` shows no spike.

### Chunk 4: particle gaps
- [x] `EntityFollowProjectileMixin`: default-colored particles leaving an Ars Deco-dyed source block use the dye's particle color. Uses `@WrapMethod` (NeoForge ships MixinExtras 0.5.5).
- [x] `RitualBrazierTileMixin`: ritual particles follow the brazier's dye. It wraps `makeParticle`, which also draws a decorative brazier's flame and is how a brazier makes its brazier relays mimic it, so each relay uses its own dye. A decorative brazier that a player gave an explicit color keeps it. The client reads the dye from the block entity, which is synced: Ars Deco's dyed block-entity textures already render on clients.
- [x] Rule toggles: `recolor_transfer_particles` and `recolor_rituals` (through `ColonyRules`).
- [x] **Light colors (M1d):** `compat/ArsLightBridge` (is-light, read, set and reset `ParticleColor` on `SconceTile`/`MagelightTorchTile`/`LightTile`) and `AutoLitData` (ownership record). Route lights through `DyeDecider`, where "undyed" means `defaultParticleColor()`. Hook them into the place event, the chunk sweep's block-entity scan, colony deletion and undye-on-leave. Add the `dye_lights` toggle and include it in the config hash.
- [x] First check which field drives the flame render (`color` or the `LightTimeline`), and set both if needed. Answer: the timeline, and both of its colors are now set (see M1d).
- [x] **Fix (after Chunk 5 testing):** lights are judged by the colour they draw, not the `color` field, so Archwood Collective's lights (white `color` field, default flame) now take the colony colour. Gametest `archwoodStyleLightIsUncolored` covers this. `ColonyDyeSweeps.SWEEP_LOGIC_VERSION` (now 2) is part of the config hash, so a fix like this re-sweeps existing colonies on the next start; bump it whenever sweep behaviour changes.
- [x] **Test (gametest):** a magelight torch, an archwood sconce and a `light_block` with the default color get the dye's particle color and are recorded, and the saved timeline no longer holds the default color. A fresh light is unaffected, so no color objects are shared. A light with a non-default (player) color is left alone. Undye restores `defaultParticleColor()`. Both mixin targets are force-loaded in the smoke test, so a mixin that doesn't apply fails the run.
- [x] **Test (in game):**
  - [x] A dyed jar feeding an apparatus or imbuement chamber shows particles in the dye color, and an undyed jar shows purple.
  - [x] A ritual on a dyed brazier shows particles in the dye color.
  - [x] Relays keep using Ars Deco's own colors.
  - [x] Pre-placed magelight torches and sconces in an Archwood Collective building glow in the colony color after a sweep, and newly placed ones do so at once.
  - [x] A sconce lit with a colored spell keeps the spell's color.
  - [x] Switching the team color to Dark Purple, or turning `dye_lights` off, returns our lights to Ars purple.

### Chunk 5: hut infrastructure, with the Source Sanctum as the first hut
- [x] `AKBlockHut`, `AKTileEntityColonyBuilding`, the `AKBlockEntities.BUILDING` type, and the building registration (`AKBlocks`, `AKItems` with an "Ars Kingdom" creative tab and `ItemBlockHut` items, `AKBuildings` on `CommonMinecoloniesAPIImpl.BUILDINGS`). Job registration comes with the first worker (Chunk 6). Each new hut block must be added to the valid blocks of `AKBlockEntities.BUILDING`.
- [x] Blueprint pack skeleton: `blueprints/ars_kingdom/arskingdom/pack.json` (name "Ars Kingdom (Archwood Collective)", `mods` including `archwoodcolonies`, and a `desc` pointing players to Archwood Collective for the standard huts), plus `military/` and `mystic/` folders with icons in Archwood's style. The icons are original pixel art drawn by `tools/gen_pack_icons.py` (400×400 pack icon, 20×20 category icons plus greyed `_disabled` versions, the same sizes as Archwood's). Structurize logs "Registered structure pack: Ars Kingdom (Archwood Collective)".
- [x] Write the Archwood Collective style guide (block palette, foundations, roofs, lighting and the school-log colors) into `mods/ArsKingdom/CLAUDE.md`, so every later blueprint follows it.
- [x] Source Sanctum: hut block, building and an empty worker module. Its settings module `ars_kingdom_rules` holds every rule toggle. `ColonyRules` reads the Sanctum's settings and falls back to the config.
  - The worker module is deferred to Chunk 6: a `WorkerBuildingModule` needs a registered job, and a hired citizen with no AI would idle.
  - The hut model is a cube of plain Ars Nouveau textures: sourcestone large bricks on the sides, gilded sourcestone mosaic on top.
  - The building id is `ars_kingdom:sourcesanctum`, so the blueprint is `sourcesanctum1.blueprint`. Max level is 1 for now.
  - Rules use `RulesSettingsModule`, seeded from the config. A new Sanctum starts with the server config's values.
  - Editing a rule, or a Sanctum being built or removed, calls `ColonyDyeSweeps.rulesChanged`, which bumps the colony's dye version and re-sweeps its chunks.
  - `recolor_rituals` stays config-only, because ritual particles are drawn client side, where colony data isn't available.
- [x] **Blueprint:** `sourcesanctum1` is generated by `tools/gen_sanctum_blueprint.py` (`tools/nbt.py` reads and writes Structurize's gzipped NBT). It's a 29×23×29 plot, a step below Archwood's 35×27×36 town hall. Like the town hall, level 1 uses the middle of the plot and leaves room for levels 2–3. It's modelled on Archwood Collective's buildings:
  - a fenced courtyard with stone-brick pillars topped by magelights, a gate and cobble/andesite paths, including a ring path;
  - an 11×11 central hall with a sourcestone base course, an archwood **log** frame (bark on, never stripped), archwood plank walls, tall windows and a single centred door under a log lintel;
  - an archwood-stair hip roof with log hip lines and a magelight spire, plus a front porch with a slab canopy on log posts and sconces on the hall wall beside the door;
  - four corner "source wells" (each a sourcestone plinth with a log post, magelights and four jars) as footings for the corner towers of later levels;
  - **all four archwood colours, one per corner** (red NW, blue NE, green SW, purple SE): the hall's corner posts and roof hip lines, the corner wells, and four trees (red and blue in the back gardens, green and purple flanking the front). Other frame logs are purple;
  - inside: the hut on the back wall, bookshelves, 6 source jars, a ritual brazier ringed by 4 pedestals standing up (`facing=up`), 2 racks, a chest and sconces.
  - Lights are placed lit, as Archwood's are: block state `level=15` plus a block entity with `lit: 1` and the default colour. The colony colour then applies to them after the build.
  - The hut entry carries `type: ars_kingdom:sourcesanctum`, the pack, the corners and a `groundlevel` tag at layer 1. Block-entity positions are `Short`, as in Archwood's files. The generator refuses stripped logs.
- [x] **Fixes from the first in-game build:** removed the path stairs that left a quarter-block gap, removed the unattached porch sconces, made the door a single door (the building is centred on one block), added all four archwood colours, placed the lights lit, and stood the pedestals up.
- [x] **Rules tab text:** settings labels are one line of about 20 characters, so the labels are now short ("Dye machines:", "Undye on leaving:", ...). The full wording is in MineColonies' tooltip key `com.minecolonies.coremod.setting.tooltip.ars_kingdom:<key>`.
- [x] **Test (gametest):** Structurize loads `mystic/sourcesanctum1.blueprint` from our pack; it is anchored on the Sanctum hut, no palette entry resolved to air, the plot is 29×29, it has no stripped logs, every sconce and magelight is at level 15 with lit block entities, pedestals stand up, and red, blue, green and purple archwood logs are all used.
- [ ] **Blueprints for levels 2–3** (later): grow into the same 29×23×29 plot, for example by turning the corner wells into towers, adding wings and a taller spire; then raise `getMaxBuildingLevel()`.
- [x] **Test (gametest):** the building is in MineColonies' registry with the rules module, the hut has our registry name and blueprint name `sourcesanctum`, the item is an `ItemBlockHut`, a placed hut gets our block entity tagged `ars_kingdom:sourcesanctum`, and a new rules module is seeded from the config.
- [x] **Test (in game):**
  - [x] The Sanctum appears in the build tool under the "Ars Kingdom (Archwood Collective)" pack, and a builder builds it.
  - [x] Placed next to an Archwood Collective town hall and enchanter, the Sanctum looks like it is part of the same pack.
  - [x] The hut window opens, the rules tab is shown, and toggling `dye_decor` there changes the dyeing behavior live.
  - [x] Breaking and replacing the hut causes no crashes, and the world reloads cleanly.

### Chunk 6: Source Sanctum keeper
- [x] `JobSourceKeeper` and `EntityAIWorkSourceKeeper`: request fuel through `MIN_STOCK` and turn it into source in the jars inside the building. The rate is building level × Mana skill.
  - **Fuel** is any furnace fuel that leaves nothing behind (no lava buckets). One item gives `burnTime / 12` source, the same rate as Ars's Volcanic Sourcelink (coal = 133). That is multiplied by Sanctum level × (1 + Mana ÷ 50) × `keeper_efficiency` (config, default 1.0). Rules are in `building/SourceKeeping`.
  - **Loop** (`DECIDE → GET_FUEL → FILL_JAR`): if no jar inside the building's bounds has room, idle. With fuel in hand, keep burning into the current target jar until it is full (or gone), then pick the emptiest jar with room. One item is burned per action. Otherwise take up to a stack of fuel from the Sanctum's storage. If there is none, request `Burnable(16)` ("any fuel") from couriers once. A blocking interaction says when the Sanctum has no jars at all.
  - Focus (secondary skill) shortens the pause between fuel items (60 → 20 ticks). Each fuel item gives 0.5 XP.
  - The building keeps up to 64 fuel in the keeper's hands (`keepX`), so it isn't dumped back into storage. Its modules are: worker (`ars_kingdom_sourcekeeper_work`: Mana/Focus, 1 worker), rules tab, `MIN_STOCK` (players can set a fuel stock) and `STATS_MODULE`.
  - Particles fly from the keeper into the jar in the jar's dye colour, when the colony's `recolor_transfer_particles` rule allows it.
  - The job is `ars_kingdom:sourcekeeper` (`AKJobs`, `DefaultJobView`). No extra job-list registration is needed in 1420.
- [x] **Test (gametest):** the fuel rules (coal and logs are fuel, lava buckets and dirt are not; coal = 133, doubled by Mana 50 or by level 2), the jar finder (only jars with room inside the box, emptiest first), and the job and worker-module registration.
- [x] **Test (in game):**
  - [x] The keeper is hired and requests fuel; delivered fuel fills the jars.
  - [x] The transfer particles follow the jar's dye.
  - [x] The keeper's Mana skill goes up.
- [x] **Fix (after in-game testing):** MineColonies keys citizen voice lines by job path in `ModSoundEvents.CITIZEN_SOUND_EVENTS`, built only from its own jobs, so a skill level-up NPE'd and paused the keeper for 100 s. `AKJobs.registerCitizenSounds()` (common setup) gives every job of ours the Enchanter's voice set. Gametest `keeperHasVoice` covers this.

### Chunk 7: battlemage equipment types and gear rules
- [x] `AKEquipmentTypes`: `spellbook` (excluding the creative book) and `arcanist_hood/robes/leggings/boots`, with level lambdas (book tier, and `ArmorPerkHolder` tier + 1).
  - Registered on `CommonMinecoloniesAPIImpl.EQUIPMENT_TYPES`; display names `com.ars_kingdom.equipment.<type>`. An item that doesn't match a type gets level -1.
  - The creative book is rejected in code (`SpellTier.CREATIVE`), so adding it to the tag doesn't let it through. Armor without perk data counts as level 1.
- [x] Item tags `#ars_kingdom:battlemage/{spellbooks,hood,robes,leggings,boots}` (the Arcanist pieces by default).
  - `spellbooks` holds the novice, apprentice and archmage books. Pack makers can add the Sorcerer or Battlemage sets to the armor tags (`replace: false`).
- [x] `MageSchool` enum (RED/BLUE/GREEN/PURPLE, with `DyeColor`, ids and skills) and `BattlemageGear.applySchoolColor`.
  - Ids come from the school: `hutId()` = `magetower<school>`, `guardTypeId()` = `battlemage_<school>` (also the job path), `workModuleKey()` = `ars_kingdom_battlemage_<school>_work`. Every school uses Mana and Focus for now; the per-school skill accessors let them diverge later. `byDye` maps a colour back to its school.
  - `applySchoolColor` sets vanilla `DataComponents.BASE_COLOR` on Ars armor (`AnimatedMagicArmor`) and spellbooks, which Ars's renderers read (default purple). Ars's `IDyeable` is deprecated for removal, so it isn't used.
- [x] **Test (gametest):** `BattlemageGearTests`
  - [x] Equipment levels for each book and armor tier (and all five types registered).
  - [x] The creative book is rejected, and non-Arcanist armor (diamond, Sorcerer, Battlemage) is rejected; each Arcanist piece matches only its own slot.
  - [x] `applySchoolColor` sets `BASE_COLOR` for every school on the book and all four pieces; non-Ars items are left alone.
  - [x] The `MageSchool` ↔ `DyeColor` mapping (unique, round-trips through `byDye`), plus the derived ids.

### Chunk 8: one Mage Tower (Red), gating only
- [x] Hut block for the Red tower; its model uses Ars Deco's red texture.
  - `BlockHutMageTower.create(school)` (`blockhutmagetower<school>`): MineColonies' block constructor calls `getHutName()` before a subclass field is set, so the school is handed over through a static during construction. Model: Ars Deco's red `sourcestone_large_bricks` sides, red `gilded_sourcestone_mosaic` top, red `smooth_sourcestone` bottom.
  - Schools are registered from `BattlemageSchools.REGISTERED` (Red only for now; Chunk 10 adds the rest there). Blocks, items, buildings, jobs and guard types are `EnumMap`s keyed by `MageSchool`.
- [x] `BuildingMageTower` (school-parameterized, max level 3; `getMaxEquipmentLevel()` returns the building level).
  - Building id `ars_kingdom:magetower<school>`, claim radius 2/3/4. Like the guard tower it reports itself to `guardBuildingChangedAt` on upgrade and destruction. View: `AbstractBuildingGuards.View`.
  - Modules: `ars_kingdom_battlemage_<school>_work` (one battlemage per tower level), `GUARD_ENTITY_LIST`, `GUARD_SETTINGS`, `MIN_STOCK`, `BED`, `STATS_MODULE`. No `GUARD_TOOL`.
  - The work module is `BattlemageBuildingModule`: MineColonies' `GuardBuildingModule` only auto-hires when `random.nextInt(<guard modules>) != 0`, which never holds for a tower with one guard type, so it does the plain worker hiring itself.
- [x] `battlemage_red` guard type, `JobBattlemageRed`, and an `EntityAIBattlemage` with no combat yet.
  - `JobBattlemage<J>` (abstract, `getSchool()`, druid model, health from Mana like the druid) with a one-line subclass per school. Guard type: Mana/Focus, `druid` worker sound, translation keys `com.ars_kingdom.job.battlemage_<school>` and `com.ars_kingdom.gui.workerhuts.battlemage_<school>`. The job also gets the Enchanter's citizen voice lines (`AKJobs.registerCitizenSounds`).
  - Until Chunk 9 adds combat, an `ATTACKING → NO_TARGET` transition keeps the battlemage from standing in the attack state.
- [x] Remove the default guard gear (`itemsNeeded` and `toolsNeeded`) and add our five equipment types. Requests are `Tool(type, 1, level)`. Add the BLOCKING "not allowed to work" interaction and the `ars_kingdom:unequipped` status.
  - Armor is in MineColonies' own guard gear list: `itemsNeeded` holds, for each tower level L, the four Arcanist types at levels 1..L. That reuses MineColonies' take-from-hut, request, equip and upgrade logic. Equipped armor leaves the citizen's item handler, so armor can't go in `toolsNeeded`.
  - The spellbook isn't a weapon to MineColonies (`GuardGear` only puts swords in the main hand), so `EntityAIBattlemage.atBuildingActions` takes it from the hut or requests it, and requests any armor piece still missing.
  - The gate is `BattlemageEquipment.isFullyEquipped` (book in the inventory, all four pieces worn, each ≤ tower level). `hasTool()` and `decide()` use it: while it fails the battlemage walks back to the tower to prepare, shows the BLOCKING interaction ("...tier N or lower") and the `UNEQUIPPED` status (Ars's purple spellbook icon), and doesn't patrol. The interaction's validator (registered in common setup) clears it once equipped.
  - `BattlemageStatus.UNEQUIPPED` is created during mod construction: MineColonies numbers statuses in creation order.
- [x] Equip the gear: armor goes into the armor slots and the book into the main hand, and `applySchoolColor` runs when it's equipped. Recheck every cycle.
  - Every `decide` and every preparing pass holds the best allowed book and recolours the book and worn armor if their `BASE_COLOR` isn't the school's. The worn stacks are changed in place; vanilla resends equipment whose components changed.
- [x] **Blueprint:** scan the `magetowerred1` blueprint, in the Archwood Collective style with red archwood logs as the accent color. Use Archwood's `guardtower` as a size and style reference only.
  - Generated by `tools/gen_magetower_blueprint.py [school ...]` (shared helpers in `tools/bpgen.py`, also used by the Sanctum generator, whose output is unchanged). Plot 13×26×13 (Archwood's guard tower is 9×20×9 at every level; ours is wider for up to three battlemages and tall enough for levels 2–3).
  - A 7×7 two-storey tower on a stone-brick plinth: sourcestone base course, archwood plank walls, red log corner posts and floor bands, a hip roof with red log hip lines, a log spire and a magelight. Ground floor: hut on the back wall, one red bed, rack, archwood chest, two source jars, sconces, a ladder up. Upper floor: bookshelves, a pedestal standing up, a jar, windows, sconces. Outside: ring path, door path with sconces, magelight lamp posts on the corners, red archwood shrubs. Lights placed lit.
- [x] **Fix (after in-game testing):** missing gear now shows in the battlemage's request tab. A guard's `startWorkingAtOwnBuilding` returns straight to `DECIDE` without walking or preparing, so an unequipped battlemage looped and never reached `atBuildingActions`. Now `decide` posts a `Tool(type, 1, towerLevel)` request for each missing piece (the book and any armor that isn't worn, carried or in the tower) wherever the battlemage is, then walks to the tower and prepares there.
- [x] **Armor repair (requested after in-game testing):** worn Arcanist armor regenerates durability as on a player. Ars does it from the armor's `inventoryTick` in a player's armor slots, paid from the player's mana, neither of which a citizen has. `BattlemageUpkeep` (server `EntityTickEvent.Post`, citizens with a `JobBattlemage` only) runs Ars's rule every 200 ticks per damaged worn piece: repair `BASE_ARMOR_REPAIR_RATE` + the highest Repairing perk level among the worn pieces (read from the citizen's armor inventory), for 20 mana; no mana, no repair.
  - This pulls Chunk 9's mana pool forward: `JobBattlemage.storedMana` (saved as `ars_kingdom_mana`), refilled every 100 ticks while the battlemage is within 10 blocks of the hut, from source jars within 6 blocks of the hut (Ars transfer particles, the jar's dye colour).
  - **Revised after Chunk 9 testing** (battlemages kept walking home: no regen, a 100 cap, 40-mana spells and repairs draining the pool): the pool is now a player's for the same gear, from Ars's server config and the worn stacks (citizens have no Ars mana capability or attributes): max = `baseMax` + `tierMax` × book tier + the armor's `MAX_MANA` modifiers (Arcanist: 30 × (tier + 1) per piece); regen every second = `baseRegen` + `tierRegen` × book tier + the armor's `MANA_REGEN_BONUS` modifiers (tier + 1 per piece). A novice battlemage in a tier-1 set: 270 max, 10/s. No book, no mana. Repairs only spend mana above a reserve of two attacks (`RESERVE_ATTACKS`), so combat comes first. Gametests: `manaLikeAPlayer`, `repairKeepsCombatReserve`.
  - Gametests: `armorRepairCostsMana` (base rate per piece, 20 mana each, nothing below 20 or for undamaged armor) and `manaCapFollowsBook`.
- [x] **Colored buildings (requested):** Mage Towers keep their school color under the colony dye sweep, toggled in the Source Sanctum ("Towers keep color:", one toggle for all schools, on by default). Built as an extensible list (`colony/BuildingColors`): each entry is a building type with its own color and its own Sanctum toggle; `ColonyDye.zoneDye` uses the smallest switched-on colored building containing the position (memoized per colony per tick). A colored building being built, upgraded or removed, or a toggle change, bumps the colony's dye version and re-sweeps; `building_colors` is in the config hash and `SWEEP_LOGIC_VERSION` is 3, so existing towers recolor on the next start. `/arskingdom where` reports the building color. Gametests: `BuildingColorTests` (zone picking incl. nesting, Mage Tower entry and default, a Sanctum toggle per entry, a new entry registered at runtime).
- [x] **Test (in game, colored buildings):**
  - [x] With a non-purple team color, a Red Mage Tower's jars, lights and sourcestone turn red while the rest of the colony stays in the colony color; `/arskingdom where` inside and outside the tower agrees.
  - [x] Turning "Towers keep color" off in the Sanctum gives the tower the colony color; on again gives red.
  - [x] A block a player dyed inside the tower is left alone.
  - [x] Removing the tower returns its leftover blocks to the colony color.
- [x] **Spike:** check that geckolib Ars armor and the spellbook render on the citizen. If they don't, add the client render layer here.
  - In game, only a garbled hood showed. GeckoLib renders its armor from a mixin on vanilla `HumanoidArmorLayer.render`, but MineColonies' `CitizenArmorLayer` overrides `render` and calls `renderArmorPiece` itself, so vanilla drew its plain armor cubes with the GeckoLib texture. Client mixin `CitizenArmorLayerMixin` (HEAD of `CitizenArmorLayer.renderArmorPiece`) now hands GeckoLib armor to `InternalUtil.tryRenderGeoArmorPiece`, as GeckoLib's own mixin does; other armor goes on to MineColonies' path. Its signature is the same in GeckoLib 4.6.6 (compiled against) and 4.9.3 (MagicMod). The held book uses the standard `ItemInHandLayer` and GeckoLib's item renderer, so it needs nothing.
- [x] **Test (gametest):** `MageTowerTests`: registration (building, modules, no `GUARD_TOOL`, guard type → job, voice lines, hut name and blueprint name, item, block entity), the hut's block entity, the gear gate (nothing; no book; creative book; apprentice book or tier-2 armor at level 1 refused but fine at level 2/3; each missing slot; a diamond helmet), and the `magetowerred1` blueprint (loads, anchored on the red hut, 13×13, no unresolved or stripped blocks, lit lights, pedestals up, red logs, a bed).
- [x] **Test (in game):**
  - [x] A battlemage hired with nothing shows "not allowed to work" and posts the requests, and it doesn't patrol.
  - [x] After you deliver a novice book and tier-1 Arcanist armor, it equips them and they turn red, both on the citizen and when inspected as items.
  - [x] At level 1, an apprentice book or tier-2 armor is refused.
  - [x] Removing one piece blocks it again.

### Chunk 9: battlemage combat
- [x] `SchoolSpells` (a cached spell per school and tier, with timeline colors set from the school dye) and `BattlemageCombatAI` (projectile spell, with the emulated-bolt fallback ready).
  - Spells (tier = held book tier, capped by the tower): Red Ignite + Harm, Blue Snare + Harm, Green Crush + Harm, Purple Harm + Amplify; higher tiers add Extend Time on the status effect and Amplify on the damage. No AOE. Glyphs that are disabled in Ars's config or above the tier are dropped (an effect takes its augments with it; without Projectile the spell is empty). The spell's default color and all four stages of its `ProjectileTimeline` use the school dye's Ars Deco particle color. Cache cleared on server start.
  - Casting goes through Ars's own `SpellResolver.onCast` with `BattlemageCaster` (an `IWrappedCaster` around `LivingCaster` whose mana is the battlemage's `storedMana`; `LivingCaster` needs the player mana capability). The citizen is turned to face the target's middle first, since Ars launches along the caster's look.
  - `BattlemageCombatAI extends AttackMoveAI<EntityCitizen>` follows MineColonies' druid/ranger AIs for targeting, movement, persecution and waking sleeping guards. Range 10 + tower level (+ Mana), delay 60 ticks minus Focus/2 (min 30). XP 0.05 per cast, 15 per kill. Can only attack fully equipped and with mana for one cast.
  - Fallback (spell empty, or Ars refuses the cast): an emulated bolt costing 10 mana: school-coloured `PropertyParticleOptions` particles along the line, `indirectMagic` damage 4 + 2 × tier (+2 Purple) and the school's effect (Red fire, Blue slowness, Green weakness).
  - `BattlemageSpellGuard` (Ars `SpellResolveEvent.Pre`): a battlemage's spell never resolves on blocks (no fires from Ignite, no walls crushed) or on citizens, players, villagers and owned pets that MineColonies doesn't count as attackers.
  - Out of mana it simply can't attack (`canAttack`) until its player-like regen (or the tower's jars) covers a cast; it no longer walks home. (The first version sent it home whenever it was below one cast, which with no regen meant constantly.)
- [x] Mana refill from jars at the hut, plus `storedMana` and the mana cap. (Done early, in Chunk 8's armor repair: `BattlemageUpkeep`. Research scaling of the cap comes with Chunk 14; spellcasting spends from the same pool.)
- [x] **Test (in game):**
  - [x] The battlemage kills zombies and Wilden with red spells and doesn't hurt citizens or players (and sets no fires).
  - [x] It refills from jars, and the particles follow the jar's dye.
  - [x] It keeps patrolling and fighting without running home; with an empty pool it pauses attacking until regen covers a cast.
  - [x] Patrol, follow and the rally banner work.
  - [x] Its XP rises.
- [x] **Test (gametest):** the spells for each tier are built only from glyphs valid for that tier, and every timeline color equals the school's particle color. (`SchoolSpellTests`: every school/tier is a valid projectile + Harm spell of allowed glyphs, coloured on all timeline stages, cached, stronger at tier 3; the glyph filter; a real cast through `BattlemageCaster` fails without mana and with mana spends it and launches an Ars projectile.)

### Chunk 10: all four schools and three tiers
- [x] Register the Blue, Green and Purple schools in a loop: hut blocks, buildings, jobs, guard types and models.
  - `JobBattlemageBlue/Green/Purple` added to `BattlemageSchools.REGISTERED`; every registry (blocks, items, buildings, work modules, jobs, guard types, voice lines) already looped over it. Hut models use Ars Deco's `<school>` sourcestone textures; lang for each hut, building, job and guard button.
  - Spells revised: Blue uses Freeze (15 mana, tier 1) instead of Snare (100 mana); Green uses Knockback + Harm at tier 1 (Crush is a tier-2 glyph) and adds Crush from tier 2.
- [x] Tower levels 2–3 raise the gear cap and the spell tier. (Already in place: `getMaxEquipmentLevel()` = tower level caps gear and requests; the spell tier is the held book's tier capped by it; one battlemage per level. Levels 2–3 are now buildable with their blueprints.)
- [x] Mage Tower zone dye: positions inside a tower's bounds use the school dye, in `ColonyDye.zoneDye`. (Done in Chunk 8 as `BuildingColors.MAGE_TOWERS`, which covers every school's tower.)
- [x] **Blueprints:** scan the remaining 11 tower blueprints (`magetower<school>1..3`), in the Archwood Collective style with each school's colored archwood logs (blue, green or purple).
  - All 12 generated by `tools/gen_magetower_blueprint.py` (default: all schools, levels 1–3). One design for every school; the school's color is used for every colored block: archwood logs and leaves, beds, carpets, the banner over the door, stained-glass windows and the hut. 13×26×13 plot at every level.
  - Level 1: the two-storey tower (now with a carpet ring round the study's pedestal and a school banner over the door). Level 2: a third storey (barracks: second bed, chest, carpet runner, stained-glass windows) under the same roof. Level 3: a fourth barracks storey (third bed) and a lookout top instead of the roof: a parapet of school-log posts and archwood fences, corner magelights, a small central roof and spire. The ladder runs through every floor (onto the deck at level 3).
- [x] **Test (gametest):** `MageTowerTests.allTowersRegistered` (every school: building, modules, guard type → its own job, voice lines, hut, item, block entity) and `towerBlueprintsLoad` (all 12 load, anchored on their hut, 13×26×13, no unresolved or stripped blocks, lit lights, standing pedestals, the school's logs, one bed per level, and every color-named block in the school's color).
- [x] **Test (gametest):** zone dye inside a tower's bounds versus outside. (`BuildingColorTests.zonePicking`.)
- [ ] **Mage Tower redesign** (user request, 2026-11-25): the earlier 12 square-tower blueprints are removed; the towers are rebuilt from a new round design, level by level.
  - [x] `magetowerred1`: an 11-wide round tower (13×20×13 plot), two 8-block storeys: red archwood log posts and bands, sourcestone large brick foundation/floor, archwood plank walls. On top, a flat open-air roof deck (archwood planks) with a red-log railing round the edge and magelights on the four diagonal posts; the ladder runs up through both floors onto the deck (user request: no sloped roof). Ground floor from the south door: hut on the left (west) wall, ladder on the far (north) wall, and on the right (east), from the door inwards, a source jar, a scribe's table and an arcane pedestal in the back nook. Upper floor: a red bed. Windows: west and east on both floors and the front upstairs; the back (north) wall is solid planks on both floors because the ladder hangs on it (user request: a ladder can't hang on glass, so the upstairs back window broke it). The ladder is one continuous column from the ground floor through the upper floor and the deck to the railing. Lighting (user request): on each floor, lit archwood sconces on both interior faces of the four diagonal log posts (the posts that stand into the room), 3 and 6 blocks above the floor, 16 per floor; two more outside the door. Generated by `tools/gen_magetower_blueprint.py` (default: red).
  - [x] `BuildingMageTower.getMaxBuildingLevel()` returns `BLUEPRINT_LEVELS` (1) until the level 2–3 blueprints exist, so the upgrade button never asks for a missing blueprint. `MAX_LEVEL` (3) still drives gear tiers.
  - [x] **Test (gametest):** `towerBlueprintsLoad` checks only the blueprints that exist (Red, up to `BLUEPRINT_LEVELS`), plot 13×20×13.
  - [ ] **Test (in game):** the Red tower builds as described (circle, two floors, hut left, ladder ahead, jar/table/pedestal right, red bed upstairs, ladder onto the railed roof deck) and its battlemage still works.
  - [ ] Level 2 and 3 Red blueprints (design to be decided with the user), then raise `BLUEPRINT_LEVELS`.
  - [ ] Blue, Green and Purple blueprints from the same design (`gen_magetower_blueprint.py blue green purple`).
- [ ] **Test (in game):**
  - [ ] The build tool lists four towers, and each one hires only its own school.
  - [ ] Upgrading a tower to levels 2 and 3 accepts apprentice and then archmage gear, and the spells get stronger; each level adds a bed and a battlemage.
  - [ ] Each tower's blocks stay in the school color even when the team color is different.
  - [ ] For every school, the hut, the build, the robes, the book and the spells all match.
  - [ ] The four towers placed next to Archwood's guard tower and barracks look like part of the same pack.

### Chunk 11: Arcanist Workshop
- [ ] Hut, `JobArcanist`, `EntityAIWorkArcanist`, and the custom crafting module (crafter key `arcanist_custom`).
- [ ] Source-cost table (`data/ars_kingdom/arcanist_source_costs`), the "needs source" status, and recipes in `crafterrecipes/arcanist`: spellbooks and other Ars items that **no Archwood Colonies recipe covers**. Armor comes from Archwood's Fletcher.
- [ ] `tools/check_archwood_overlap.py`: fails if any of our crafter recipes outputs an item that Archwood's `crafterrecipes` already make. Run it as part of `./gradlew build`.
- [ ] **Blueprint:** scan `arcanistworkshop1`, in the Archwood Collective style.
- [ ] **Test (gametest):** source-cost table lookup, and the recipe JSON parses.
- [ ] **Test (build):** the overlap check passes, and fails when a deliberate duplicate (Arcanist robes) is added.
- [ ] **Test (in game):**
  - The recipes appear in the hut's recipe list. They do not show in EMI, because the EMI addon doesn't display crafter recipes.
  - Crafting waits until the jars hold enough source and then drains them.
  - Crafted spellbooks, and Arcanist armor from Archwood's Fletcher (after its Ars Crafting Recipes research), are undyed until a battlemage equips them.

### Chunk 12: Imbuement Hall
- [ ] `ArsMachineModule`: register chamber, apparatus, pedestal and jar positions from blueprint placement, save them, and prune positions whose block is gone.
- [ ] `ArsRecipeImporter`: on `CustomRecipesReloadedEvent`, turn imbuement recipes into `imbuer_custom` CustomRecipes. Catalysts come back as secondary outputs, and the minimum level comes from source cost and pedestal count. Check that clients receive them; fall back to generated JSON if they don't.
- [ ] Hut, `JobImbuer`, and `EntityAIWorkImbuer`: drive the real chamber (catalysts on pedestals, then reagent, then wait, then retrieve), run up to N chambers in parallel, and show the "no chamber" and "not enough pedestals" interactions.
- [ ] Sanctum keeper also tops up the jars in this hall.
- [ ] `ArsRecipeImporter` skips outputs that already have an `archwoodcollective` crafter recipe (`skip_archwood_duplicates`, default on).
- [ ] **Blueprints:** scan `imbuementhall1..3`, in the Archwood Collective style. Use Archwood's `enchanter` and `alchemist` as size references only.
- [ ] **Test (gametest):** the importer turns a known imbuement recipe into the expected `CustomRecipe` (inputs, outputs, intermediate, minimum level).
- [ ] **Test (gametest):** place a chamber with pedestals and a jar, then drive `ImbuementTile` through the same helper the AI uses. The essence comes out and the catalysts are untouched.
- [ ] **Test (in game):**
  - The hut lists all 13 Ars imbuement recipes, plus any from Ars Elemental or Ars Additions.
  - A courier request for Fire Essence is crafted on the real chamber: you can see the pedestals filled and the jar drained.
  - The catalysts come back to the worker.
  - Breaking the chamber shows the interaction.
  - A level-2 hall runs two chambers at once.

### Chunk 13: Artificer's Hall
- [ ] `ArsRecipeImporter`: turn plain apparatus recipes (including the keep-data ones) into `artificer_custom` CustomRecipes. Pedestal remainders come back as secondary outputs.
- [ ] Hut, `JobArtificer`, and `EntityAIWorkArtificer`: fill the pedestals, check `craftingPossible(reagent, null)`, insert the reagent, wait while `isCrafting`, retrieve the result, and collect the remainders. Add the "not enough source" interaction.
- [ ] Sanctum keeper also tops up the jars in this hall.
- [ ] **Blueprints:** scan `artificerhall1..3`, in the Archwood Collective style.
- [ ] **Test (gametest):** the importer turns a known apparatus recipe into the expected `CustomRecipe`. Driving `EnchantingApparatusTile` with a null player produces the result after 210 ticks and empties the pedestals to their remainders.
- [ ] **Test (gametest):** with `skip_archwood_duplicates` on, the importer skips an apparatus output that has an `archwoodcollective` crafter recipe (Arcanist robes). With it off, it imports that output.
- [ ] **Test (in game):**
  - The hut lists the Ars apparatus recipes, except items Archwood already gives a crafter, such as Arcanist armor (at the Fletcher).
  - Requests for an apparatus item, and for a keep-data item that Archwood doesn't cover, are crafted on the real apparatus, with source taken from the jars within 10 blocks.
  - With empty jars, the worker shows the interaction and waits.
  - The whole flow survives a relog mid-craft.
- [ ] **Stretch (later chunk):** battlemage armor-upgrade orders (the `armor_upgrade` recipe for tier 2 and tier 3 Arcanist pieces) and enchantment orders. Each needs its own design pass before it's added here.

### Chunk 14: Arcana research
- [ ] Research and effect JSON **in Archwood's `archwoodcollective:arsnouveau` branch**, with no branch file of our own: tower unlocks for each school, the workshop, Imbuement Hall and Artificer's Hall unlocks, `battlemagedamage`, `battlemagemana`, `colonymanaregen` and `colonymaxmana`. Optionally, Colony Heraldry, which unlocks `dye_decor`.
- [ ] The tower, workshop and Artificer's Hall unlocks have `archwoodcollective:arsnouveau/arscraftingrecipes` as their parent. Nothing already in the branch (crafting recipes, Wilden processing) is recreated.
- [ ] Spike first: check that MineColonies loads our namespaced research into Archwood's branch with a parent from Archwood's namespace. If it doesn't, use our own `ars_kingdom:arcana` branch.
- [ ] `ManaBonusHandler`, plus battlemage damage and mana scaling from the research effects.
- [ ] **Test (in game):**
  - Our research appears in the University's "Ars Nouveau" branch next to Archwood's, and the towers and workshop stay locked until researched.
  - The tower unlocks can't be started before Ars Crafting Recipes is done.
  - Inside the colony, max mana and regen go up after the research.
  - Battlemage damage goes up after the research.

### Chunk 15: Wilden raids
- [ ] `WildenRaidEvent`, the event type registration, `WildenRaidEntityTracker`, `WildenRaidScheduler`, and the `/arskingdom raid` command. They respect the `wilden_raids` rule.
- [ ] **Test (gametest):** the scheduler's gating rules.
- [ ] **Test (in game):**
  - `/arskingdom raid` spawns Wilden, and guards and battlemages engage them.
  - The raid ends when all the Wilden are dead, and it survives a relog mid-raid.
  - It doesn't start while a MineColonies raid is active.
  - Blocks broken during the raid drop undyed.

### Chunk 16: release
- [ ] Full regression run in MagicMod with the built jar: walk through every **Test** item above.
- [ ] **Style pass:** build a whole colony with Archwood Collective for the standard huts and our pack for ours. Check that every hut of ours matches the style guide, and that we ship no blueprint for a hut that Archwood covers.
- [ ] **License check before a public release:** Archwood Colonies is All Rights Reserved, and the user set that aside for their own use (2026-11-23). Before publishing, decide whether any blueprint that closely follows an Archwood one needs the authors' permission.
- [ ] Update the docs: the root `CLAUDE.md` mods table and `mods/ArsKingdom/CLAUDE.md`.
- [ ] Set up the GitHub repo and a CurseForge release on `v*` tags (like ArsReSources), then add it to MagicMod through Prism.
