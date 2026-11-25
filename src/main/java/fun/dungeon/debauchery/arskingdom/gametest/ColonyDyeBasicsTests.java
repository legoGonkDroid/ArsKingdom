package fun.dungeon.debauchery.arskingdom.gametest;

import fun.dungeon.debauchery.arskingdom.ArsKingdom;
import fun.dungeon.debauchery.arskingdom.colony.ColonyDye;
import fun.dungeon.debauchery.arskingdom.compat.ArsDecoBridge;
import fun.dungeon.debauchery.arskingdom.compat.ArsDecoBridge.Category;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Chunk 1: the Ars Deco bridge (also a canary for Ars Deco API changes) and the team color → dye table.
 */
@GameTestHolder(ArsKingdom.MODID)
@PrefixGameTestTemplate(false)
public final class ColonyDyeBasicsTests {
    private static final String EMPTY = "gametest/empty";

    private ColonyDyeBasicsTests() {
    }

    private static Block ars(String path) {
        return BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath("ars_nouveau", path));
    }

    /** Dye then undye a jar and a relay (block-entity color) and sourcestone (Ars Deco SavedData color). */
    @GameTest(template = EMPTY)
    public static void bridgeAppliesAndClears(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String[] blocks = {"source_jar", "relay", "sourcestone"};
        for (int i = 0; i < blocks.length; i++) {
            Block block = ars(blocks[i]);
            helper.assertTrue(block != Blocks.AIR, "missing block ars_nouveau:" + blocks[i]);
            helper.assertTrue(ArsDecoBridge.isTarget(block), blocks[i] + " should be an Ars Deco target");

            BlockPos rel = new BlockPos(1 + 2 * i, 1, 1);
            helper.setBlock(rel, block);
            BlockPos pos = helper.absolutePos(rel);

            helper.assertTrue(ArsDecoBridge.getColor(level, pos).isEmpty(), blocks[i] + " should start undyed");
            helper.assertTrue(ArsDecoBridge.apply(level, pos, DyeColor.LIME), "apply failed on " + blocks[i]);
            Optional<DyeColor> color = ArsDecoBridge.getColor(level, pos);
            helper.assertTrue(color.equals(Optional.of(DyeColor.LIME)), blocks[i] + " should read back lime, got " + color);
            helper.assertTrue(ArsDecoBridge.clear(level, pos), "clear failed on " + blocks[i]);
            color = ArsDecoBridge.getColor(level, pos);
            helper.assertTrue(color.isEmpty(), blocks[i] + " should be undyed after clear, got " + color);
        }
        helper.assertTrue(!ArsDecoBridge.isTarget(Blocks.STONE), "stone must not be a target");
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void bridgeCategories(GameTestHelper helper) {
        Map<String, Category> expected = Map.of(
                "source_jar", Category.MACHINE,
                "relay", Category.MACHINE,
                "ritual_brazier", Category.MACHINE,
                "enchanting_apparatus", Category.MACHINE,
                "sourcestone", Category.DECOR,
                "archwood_planks", Category.DECOR,
                "archwood_sconce", Category.DECOR);
        expected.forEach((path, cat) -> {
            Optional<Category> actual = ArsDecoBridge.category(ars(path));
            helper.assertTrue(actual.equals(Optional.of(cat)), path + " should be " + cat + ", got " + actual);
        });
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void teamColorTable(GameTestHelper helper) {
        Map<ChatFormatting, DyeColor> map = ColonyDye.parse(ColonyDye.DEFAULT_MAP);
        Map<ChatFormatting, DyeColor> expected = Map.ofEntries(
                Map.entry(ChatFormatting.BLACK, DyeColor.BLACK),
                Map.entry(ChatFormatting.DARK_BLUE, DyeColor.BLUE),
                Map.entry(ChatFormatting.DARK_GREEN, DyeColor.GREEN),
                Map.entry(ChatFormatting.DARK_AQUA, DyeColor.CYAN),
                Map.entry(ChatFormatting.DARK_RED, DyeColor.RED),
                Map.entry(ChatFormatting.GOLD, DyeColor.ORANGE),
                Map.entry(ChatFormatting.GRAY, DyeColor.LIGHT_GRAY),
                Map.entry(ChatFormatting.DARK_GRAY, DyeColor.GRAY),
                Map.entry(ChatFormatting.BLUE, DyeColor.LIGHT_BLUE),
                Map.entry(ChatFormatting.GREEN, DyeColor.LIME),
                Map.entry(ChatFormatting.AQUA, DyeColor.LIGHT_BLUE),
                Map.entry(ChatFormatting.RED, DyeColor.PINK),
                Map.entry(ChatFormatting.LIGHT_PURPLE, DyeColor.MAGENTA),
                Map.entry(ChatFormatting.YELLOW, DyeColor.YELLOW),
                Map.entry(ChatFormatting.WHITE, DyeColor.WHITE));
        helper.assertTrue(map.equals(expected), "default table mismatch: " + map);
        helper.assertTrue(!map.containsKey(ChatFormatting.DARK_PURPLE), "DARK_PURPLE must mean undyed by default");

        // Overrides, 'none', and junk entries.
        Map<ChatFormatting, DyeColor> custom = ColonyDye.parse(List.of(
                "WHITE=white", "RED=red", "RED=none", "BOLD=red", "GOLD=notadye", "garbage"));
        helper.assertTrue(custom.equals(Map.of(ChatFormatting.WHITE, DyeColor.WHITE)), "custom parse: " + custom);
        helper.succeed();
    }
}
