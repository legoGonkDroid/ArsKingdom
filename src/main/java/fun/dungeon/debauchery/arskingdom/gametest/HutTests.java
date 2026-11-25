package fun.dungeon.debauchery.arskingdom.gametest;

import com.ldtteam.structurize.blueprints.v1.Blueprint;
import com.ldtteam.structurize.storage.StructurePacks;
import com.minecolonies.api.colony.buildings.registry.BuildingEntry;
import com.minecolonies.api.items.ItemBlockHut;
import com.minecolonies.apiimp.CommonMinecoloniesAPIImpl;
import com.minecolonies.core.tileentities.TileEntityColonyBuilding;
import fun.dungeon.debauchery.arskingdom.ArsKingdom;
import fun.dungeon.debauchery.arskingdom.building.modules.RulesSettingsModule;
import fun.dungeon.debauchery.arskingdom.colony.ColonyRules;
import fun.dungeon.debauchery.arskingdom.compat.ArsDecoBridge.Category;
import fun.dungeon.debauchery.arskingdom.config.AKConfig;
import fun.dungeon.debauchery.arskingdom.hut.AKTileEntityColonyBuilding;
import fun.dungeon.debauchery.arskingdom.registry.AKBlockEntities;
import fun.dungeon.debauchery.arskingdom.registry.AKBlocks;
import fun.dungeon.debauchery.arskingdom.registry.AKBuildings;
import fun.dungeon.debauchery.arskingdom.registry.AKItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Chunk 5: hut infrastructure and the Source Sanctum's rules module. */
@GameTestHolder(ArsKingdom.MODID)
@PrefixGameTestTemplate(false)
public final class HutTests {
    private static final String EMPTY = "gametest/empty";

    private HutTests() {
    }

    @GameTest(template = EMPTY)
    public static void sanctumRegistered(GameTestHelper helper) {
        Registry<BuildingEntry> buildings = helper.getLevel().registryAccess().registryOrThrow(CommonMinecoloniesAPIImpl.BUILDINGS);
        BuildingEntry entry = buildings.get(ArsKingdom.id("sourcesanctum"));
        helper.assertTrue(entry != null, "ars_kingdom:sourcesanctum missing from MineColonies' building registry");
        helper.assertTrue(entry.getBuildingBlock() == AKBlocks.SOURCE_SANCTUM.get(), "building entry should point at our hut block");
        helper.assertTrue(entry.getModuleProducers().contains(AKBuildings.RULES), "the rules module should be attached");
        helper.assertTrue(AKBlocks.SOURCE_SANCTUM.get().getRegistryName().equals(ArsKingdom.id("blockhutsourcesanctum")),
                "hut registry name should be in our namespace, got " + AKBlocks.SOURCE_SANCTUM.get().getRegistryName());
        helper.assertTrue(AKBlocks.SOURCE_SANCTUM.get().getBlueprintName().equals("sourcesanctum"),
                "blueprint base name should be sourcesanctum, got " + AKBlocks.SOURCE_SANCTUM.get().getBlueprintName());
        helper.assertTrue(AKItems.SOURCE_SANCTUM.get() instanceof ItemBlockHut, "hut item should be an ItemBlockHut");
        helper.assertTrue(AKBlockEntities.BUILDING.get().isValid(AKBlocks.SOURCE_SANCTUM.get().defaultBlockState()),
                "our block entity type must accept the hut block");
        helper.succeed();
    }

    /** Placing the hut block (outside a colony) gives our block entity, tagged with the building's id. */
    @GameTest(template = EMPTY)
    public static void hutBlockEntity(GameTestHelper helper) {
        BlockPos rel = new BlockPos(2, 1, 2);
        helper.setBlock(rel, AKBlocks.SOURCE_SANCTUM.get());
        BlockEntity be = helper.getBlockEntity(rel);
        helper.assertTrue(be instanceof AKTileEntityColonyBuilding, "expected our colony building block entity, got " + be);
        helper.assertTrue(ArsKingdom.id("sourcesanctum").equals(((TileEntityColonyBuilding) be).registryName),
                "block entity should carry ars_kingdom:sourcesanctum");
        helper.succeed();
    }

    /** A new rules module starts from the config; with no colony, ColonyRules falls back to the config. */
    @GameTest(template = EMPTY)
    public static void rulesSeededFromConfig(GameTestHelper helper) {
        RulesSettingsModule module = new RulesSettingsModule();
        helper.assertTrue(module.get(RulesSettingsModule.DYE_DECOR, !AKConfig.DYE_DECOR.get()) == AKConfig.DYE_DECOR.get(), "dye_decor seed");
        helper.assertTrue(module.get(RulesSettingsModule.OVERRIDE_CUSTOM_COLORS, !AKConfig.OVERRIDE_CUSTOM_COLORS.get())
                == AKConfig.OVERRIDE_CUSTOM_COLORS.get(), "override_custom_colors seed");
        helper.assertTrue(module.get(RulesSettingsModule.WILDEN_RAIDS, !AKConfig.WILDEN_RAIDS.get()) == AKConfig.WILDEN_RAIDS.get(), "wilden_raids seed");
        helper.assertTrue(ColonyRules.dyes(null, Category.DECOR) == AKConfig.DYE_DECOR.get(), "no colony: config");
        helper.assertTrue(ColonyRules.undyeOnLeave(null) == AKConfig.UNDYE_ON_LEAVE.get(), "no colony: config");
        helper.succeed();
    }

    /**
     * The shipped level-1 blueprint loads through Structurize from our pack, is anchored on the Sanctum hut,
     * and every block in its palette resolved (an unknown id would load as air).
     */
    @GameTest(template = EMPTY, timeoutTicks = 400)
    public static void sanctumBlueprintLoads(GameTestHelper helper) {
        StructurePacks.waitUntilFinishedLoading();
        String pack = "Ars Kingdom (Archwood Collective)";
        helper.assertTrue(StructurePacks.hasPack(pack), "our structure pack should be registered");
        Blueprint bp = StructurePacks.getBlueprint(pack, "mystic/sourcesanctum1.blueprint", helper.getLevel().registryAccess());
        helper.assertTrue(bp != null, "mystic/sourcesanctum1.blueprint not found in " + pack);
        BlockState anchor = bp.getBlockState(bp.getPrimaryBlockOffset());
        helper.assertTrue(anchor.is(AKBlocks.SOURCE_SANCTUM.get()), "anchor should be the Sanctum hut, got " + anchor);
        long airInPalette = java.util.Arrays.stream(bp.getPalette()).filter(st -> st.is(Blocks.AIR)).count();
        helper.assertTrue(airInPalette == 1, "only plain air may be air; an unknown block id loaded as air (" + airInPalette + ")");
        helper.assertTrue(bp.getSizeX() == 29 && bp.getSizeZ() == 29, "plot should be 29x29, got " + bp.getSizeX() + "x" + bp.getSizeZ());
        helper.assertTrue(java.util.Arrays.stream(bp.getPalette()).noneMatch(s -> s.getBlock().getDescriptionId().contains("stripped")),
                "no stripped logs: the pack makes archwood logs glow");
        for (BlockState st : bp.getPalette()) {
            String id = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(st.getBlock()).getPath();
            if (id.endsWith("sconce") || id.equals("magelight_torch")) {
                var level = st.getBlock().getStateDefinition().getProperty("level");
                helper.assertTrue(level != null && st.getValue(level).toString().equals("15"), id + " must be placed lit (level 15): " + st);
            }
            if (id.equals("arcane_pedestal")) {
                var facing = st.getBlock().getStateDefinition().getProperty("facing");
                helper.assertTrue(facing == null || st.getValue(facing).toString().equals("up"), "pedestals must stand up: " + st);
            }
        }
        long litLights = java.util.Arrays.stream(bp.getTileEntities())
                .flatMap(java.util.Arrays::stream).flatMap(java.util.Arrays::stream)
                .filter(t -> t != null && t.getBoolean("lit")).count();
        helper.assertTrue(litLights > 0, "light block entities should be saved as lit");
        for (String color : new String[] {"red", "blue", "green", "purple"}) {
            helper.assertTrue(java.util.Arrays.stream(bp.getPalette()).anyMatch(st ->
                    net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(st.getBlock()).getPath().equals(color + "_archwood_log")),
                    "the Sanctum should use " + color + " archwood logs");
        }
        helper.succeed();
    }
}
