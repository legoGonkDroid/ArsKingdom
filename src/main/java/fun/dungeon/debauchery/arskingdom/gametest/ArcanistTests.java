package fun.dungeon.debauchery.arskingdom.gametest;

import com.google.gson.JsonParser;
import com.ldtteam.structurize.blueprints.v1.Blueprint;
import com.ldtteam.structurize.storage.StructurePacks;
import java.util.Arrays;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import com.hollingsworth.arsnouveau.common.block.tile.SourceJarTile;
import com.minecolonies.api.colony.buildings.registry.BuildingEntry;
import com.minecolonies.apiimp.CommonMinecoloniesAPIImpl;
import com.minecolonies.core.colony.crafting.CustomRecipe;
import com.minecolonies.core.colony.crafting.CustomRecipeManager;
import fun.dungeon.debauchery.arskingdom.ArsKingdom;
import fun.dungeon.debauchery.arskingdom.arcanist.ArcanistSource;
import fun.dungeon.debauchery.arskingdom.arcanist.ArcanistSourceCosts;
import fun.dungeon.debauchery.arskingdom.building.BuildingArcanistWorkshop;
import fun.dungeon.debauchery.arskingdom.registry.AKBlocks;
import fun.dungeon.debauchery.arskingdom.registry.AKBuildings;
import fun.dungeon.debauchery.arskingdom.registry.AKItems;
import fun.dungeon.debauchery.arskingdom.registry.AKJobs;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Chunk 11: the Arcanist Workshop's registration, recipes and source costs. */
@GameTestHolder(ArsKingdom.MODID)
@PrefixGameTestTemplate(false)
public final class ArcanistTests {
    private static final String EMPTY = "gametest/empty";
    private static final Set<String> BOOKS = Set.of(
            "ars_nouveau:novice_spell_book", "ars_nouveau:apprentice_spell_book", "ars_nouveau:archmage_spell_book");

    private ArcanistTests() {
    }

    private static Item item(String id) {
        return BuiltInRegistries.ITEM.get(ResourceLocation.parse(id));
    }

    @GameTest(template = EMPTY)
    public static void registered(GameTestHelper helper) {
        BuildingEntry entry = helper.getLevel().registryAccess().registryOrThrow(CommonMinecoloniesAPIImpl.BUILDINGS)
                .get(ArsKingdom.id(BuildingArcanistWorkshop.SCHEMATIC));
        helper.assertTrue(entry != null && entry == AKBuildings.ARCANIST_WORKSHOP.get(), "workshop building registered");
        helper.assertTrue(entry.getModuleProducers().contains(AKBuildings.ARCANIST_CRAFT),
                "workshop has the Arcanist crafting module");
        helper.assertTrue(AKJobs.ARCANIST.get().getKey().equals(ArsKingdom.id("arcanist")), "job id ars_kingdom:arcanist");
        helper.assertTrue(AKBlocks.ARCANIST_WORKSHOP.get().getBlueprintName().equals(BuildingArcanistWorkshop.SCHEMATIC),
                "hut blueprint name");
        helper.assertTrue(AKItems.ARCANIST_WORKSHOP.get() != null, "hut item");
        helper.succeed();
    }

    /** The three spellbook recipes load under the Arcanist's crafter key, and nothing else is keyed there by accident. */
    @GameTest(template = EMPTY)
    public static void recipesParse(GameTestHelper helper) {
        Set<CustomRecipe> recipes = CustomRecipeManager.getInstance().getRecipes("arcanist_custom");
        Set<String> outputs = recipes.stream()
                .map(r -> BuiltInRegistries.ITEM.getKey(r.getPrimaryOutput().getItem()).toString())
                .collect(Collectors.toSet());
        helper.assertTrue(outputs.equals(BOOKS), "arcanist_custom outputs should be the three spellbooks, got " + outputs);
        for (CustomRecipe recipe : recipes) {
            helper.assertTrue(!recipe.getInputs().isEmpty(), recipe.getRecipeId() + " has inputs");
            helper.assertTrue(recipe.getRecipeStorage() != null, recipe.getRecipeId() + " builds a recipe storage");
        }
        helper.succeed();
    }

    /** The shipped table prices the books; the parser skips unknown items; stacks multiply. */
    @GameTest(template = EMPTY)
    public static void sourceCostLookup(GameTestHelper helper) {
        helper.assertTrue(ArcanistSourceCosts.cost(item("ars_nouveau:novice_spell_book")) == 500, "novice book costs 500");
        helper.assertTrue(ArcanistSourceCosts.cost(item("ars_nouveau:apprentice_spell_book")) == 1500, "apprentice book costs 1500");
        helper.assertTrue(ArcanistSourceCosts.cost(item("ars_nouveau:archmage_spell_book")) == 4000, "archmage book costs 4000");
        helper.assertTrue(ArcanistSourceCosts.cost(item("minecraft:stick")) == 0, "unpriced items are free");
        helper.assertTrue(ArcanistSourceCosts.cost(new ItemStack(item("ars_nouveau:novice_spell_book"), 2)) == 1000, "per item");

        Map<Item, Integer> parsed = new HashMap<>();
        ArcanistSourceCosts.parse(ArsKingdom.id("test"), JsonParser.parseString(
                "{\"costs\": {\"minecraft:book\": 7, \"nosuchmod:thing\": 9}}").getAsJsonObject(), parsed);
        helper.assertTrue(parsed.size() == 1 && parsed.get(item("minecraft:book")) == 7, "parsed " + parsed);
        helper.succeed();
    }

    /** Paying takes everything or nothing, from jars inside the box only. */
    @GameTest(template = EMPTY)
    public static void sourceAllOrNothing(GameTestHelper helper) {
        var jarBlock = BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath("ars_nouveau", "source_jar"));
        BlockPos a = new BlockPos(1, 1, 1), b = new BlockPos(3, 1, 1), outside = new BlockPos(7, 1, 1);
        for (BlockPos p : new BlockPos[]{a, b, outside}) {
            helper.setBlock(p, jarBlock);
        }
        SourceJarTile jarA = helper.getBlockEntity(a);
        SourceJarTile jarB = helper.getBlockEntity(b);
        SourceJarTile jarOut = helper.getBlockEntity(outside);
        jarA.setSource(300);
        jarB.setSource(400);
        jarOut.setSource(5000);
        BlockPos lo = helper.absolutePos(new BlockPos(0, 0, 0)), hi = helper.absolutePos(new BlockPos(4, 2, 2));

        helper.assertTrue(ArcanistSource.available(helper.getLevel(), lo, hi) == 700, "700 inside the box");
        helper.assertTrue(!ArcanistSource.take(helper.getLevel(), lo, hi, 800), "can't pay 800 from 700");
        helper.assertTrue(jarA.getSource() == 300 && jarB.getSource() == 400, "a failed payment takes nothing");
        helper.assertTrue(ArcanistSource.take(helper.getLevel(), lo, hi, 500), "pays 500");
        helper.assertTrue(jarA.getSource() + jarB.getSource() == 200, "500 taken, left " + (jarA.getSource() + jarB.getSource()));
        helper.assertTrue(jarOut.getSource() == 5000, "the jar outside the box is untouched");
        helper.succeed();
    }

    /** The level 1 blueprint loads from our pack, anchored on the hut, with jars inside to pay source from. */
    @GameTest(template = EMPTY)
    public static void blueprintLoads(GameTestHelper helper) {
        StructurePacks.waitUntilFinishedLoading();
        String file = "mystic/arcanistworkshop1.blueprint";
        Blueprint bp = StructurePacks.getBlueprint("Ars Kingdom (Archwood Collective)", file, helper.getLevel().registryAccess());
        helper.assertTrue(bp != null, file + " not found");
        BlockState anchor = bp.getBlockState(bp.getPrimaryBlockOffset());
        helper.assertTrue(anchor.is(AKBlocks.ARCANIST_WORKSHOP.get()), file + ": anchor should be the workshop hut, got " + anchor);
        long air = Arrays.stream(bp.getPalette()).filter(st -> st.is(Blocks.AIR)).count();
        helper.assertTrue(air == 1, file + ": an unknown block id loaded as air (" + air + ")");
        boolean jar = false;
        for (BlockState st : bp.getPalette()) {
            String path = BuiltInRegistries.BLOCK.getKey(st.getBlock()).getPath();
            helper.assertTrue(!path.contains("stripped"), file + ": no stripped logs (" + path + ")");
            jar |= path.equals("source_jar");
            if (path.endsWith("sconce") || path.equals("magelight_torch")) {
                var lvl = st.getBlock().getStateDefinition().getProperty("level");
                helper.assertTrue(lvl != null && st.getValue(lvl).toString().equals("15"), file + ": " + path + " must be placed lit");
            }
        }
        helper.assertTrue(jar, file + ": needs source jars inside for the Arcanist to pay from");
        helper.succeed();
    }
}
