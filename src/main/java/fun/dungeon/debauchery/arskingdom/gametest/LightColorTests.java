package fun.dungeon.debauchery.arskingdom.gametest;

import com.hollingsworth.arsnouveau.client.particle.ParticleColor;
import fun.dungeon.debauchery.arskingdom.ArsKingdom;
import fun.dungeon.debauchery.arskingdom.colony.dye.AutoDyedData;
import fun.dungeon.debauchery.arskingdom.colony.dye.ColonyDyer;
import fun.dungeon.debauchery.arskingdom.colony.dye.DyeDecider.Action;
import fun.dungeon.debauchery.arskingdom.compat.ArsLightBridge;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Chunk 4 / M1d: native Ars light colors on magelight torches, sconces and mage lights. */
@GameTestHolder(ArsKingdom.MODID)
@PrefixGameTestTemplate(false)
public final class LightColorTests {
    private static final String EMPTY = "gametest/empty";
    private static final Optional<DyeColor> LIME = Optional.of(DyeColor.LIME);

    private LightColorTests() {
    }

    private static BlockEntity place(GameTestHelper helper, BlockPos rel, String path) {
        helper.setBlock(rel, BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath("ars_nouveau", path)));
        BlockEntity be = helper.getLevel().getBlockEntity(helper.absolutePos(rel));
        helper.assertTrue(be != null && ArsLightBridge.isLight(be), path + " should be a light");
        return be;
    }

    /** Runs the whole light cycle on one block: color, keep a player's color, undye. */
    private static void cycle(GameTestHelper helper, BlockPos rel, String path) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(rel);
        BlockEntity be = place(helper, rel, path);
        AutoDyedData lights = AutoDyedData.lights(level);
        int lime = ArsLightBridge.rgbFor(DyeColor.LIME);

        helper.assertTrue(ArsLightBridge.color(be).isEmpty(), path + " should start with Ars's default color");
        Action a = ColonyDyer.processLightWith(level, pos, LIME, false, true);
        be = level.getBlockEntity(pos);
        helper.assertTrue(a == Action.APPLY && ArsLightBridge.color(be).equals(Optional.of(lime)),
                path + " should be lime, got " + a + " / " + ArsLightBridge.color(be));
        helper.assertTrue(lights.get(pos).equals(LIME), path + " should be recorded");

        // The flame is drawn from the saved timeline: it must have been rebuilt with our color.
        CompoundTag tag = be.saveWithoutMetadata(level.registryAccess());
        helper.assertTrue(tag.contains("timeline"), path + " should still save a timeline");
        String timeline = String.valueOf(tag.get("timeline"));
        helper.assertTrue(!timeline.contains("b:180,g:25,r:255"), path + ": timeline still has Ars's default color: " + timeline);
        ArsKingdom.LOGGER.info("[light test] {} timeline after coloring: {}", path, tag.get("timeline"));

        // A fresh light of the same kind must be unaffected (no shared color objects between tiles).
        BlockEntity fresh = place(helper, rel.offset(2, 0, 0), path);
        String freshTimeline = String.valueOf(fresh.saveWithoutMetadata(level.registryAccess()).get("timeline"));
        helper.assertTrue(ArsLightBridge.color(fresh).isEmpty() && !freshTimeline.contains("b:1,g:255,r:1"),
                path + ": coloring one light leaked into a fresh one: " + freshTimeline);

        // A player's spell color is left alone, and our record dropped.
        ArsLightBridge.setColor(be, new ParticleColor(255, 0, 0));
        a = ColonyDyer.processLightWith(level, pos, LIME, false, true);
        helper.assertTrue(a == Action.FORGET && lights.get(pos).isEmpty(), path + ": player color should only drop the record, got " + a);
        helper.assertTrue(ArsLightBridge.color(level.getBlockEntity(pos)).equals(Optional.of(new ParticleColor(255, 0, 0).getColor())),
                path + ": player's red must stay");

        // Ours again, then leave the colony: back to Ars's default.
        ArsLightBridge.reset(level.getBlockEntity(pos));
        ColonyDyer.processLightWith(level, pos, LIME, false, true);
        a = ColonyDyer.processLightWith(level, pos, Optional.empty(), false, true);
        helper.assertTrue(a == Action.CLEAR && ArsLightBridge.color(level.getBlockEntity(pos)).isEmpty(),
                path + " should be back to default after leaving, got " + a);
        helper.assertTrue(lights.get(pos).isEmpty(), path + ": record should be gone");
    }

    @GameTest(template = EMPTY)
    public static void magelightTorch(GameTestHelper helper) {
        cycle(helper, new BlockPos(1, 1, 1), "magelight_torch");
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void sconce(GameTestHelper helper) {
        cycle(helper, new BlockPos(1, 1, 1), "archwood_sconce");
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void mageLight(GameTestHelper helper) {
        cycle(helper, new BlockPos(1, 1, 1), "light_block");
        helper.succeed();
    }

    /**
     * Archwood Collective's blueprints save a light's {@code color} field as white while its flame is Ars's
     * default; such a light must count as uncolored, so the colony color applies to it.
     */
    @GameTest(template = EMPTY)
    public static void archwoodStyleLightIsUncolored(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos rel = new BlockPos(1, 1, 1);
        BlockEntity be = place(helper, rel, "sconce");
        ((com.hollingsworth.arsnouveau.common.block.tile.SconceTile) be).setColor(new ParticleColor(255, 255, 255));
        helper.assertTrue(ArsLightBridge.color(be).isEmpty(), "white color field with a default flame should count as uncolored");
        Action a = ColonyDyer.processLightWith(level, helper.absolutePos(rel), LIME, false, true);
        helper.assertTrue(a == Action.APPLY, "the colony color should apply, got " + a);
        helper.assertTrue(ArsLightBridge.color(level.getBlockEntity(helper.absolutePos(rel))).equals(Optional.of(ArsLightBridge.rgbFor(DyeColor.LIME))),
                "flame should now be lime");
        helper.succeed();
    }
}
