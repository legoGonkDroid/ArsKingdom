package fun.dungeon.debauchery.arskingdom.gametest;

import fun.dungeon.debauchery.arskingdom.ArsKingdom;
import fun.dungeon.debauchery.arskingdom.colony.dye.AutoDyedData;
import fun.dungeon.debauchery.arskingdom.colony.dye.ColonyDyer;
import fun.dungeon.debauchery.arskingdom.colony.dye.DyeDecider;
import fun.dungeon.debauchery.arskingdom.colony.dye.DyeDecider.Action;
import fun.dungeon.debauchery.arskingdom.colony.dye.DyeDecider.Decision;
import fun.dungeon.debauchery.arskingdom.compat.ArsDecoBridge;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.DyeColor;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Chunk 2: the dye ownership rules ({@link DyeDecider}) and {@link ColonyDyer} acting on real blocks. */
@GameTestHolder(ArsKingdom.MODID)
@PrefixGameTestTemplate(false)
public final class ColonyDyeEventTests {
    private static final String EMPTY = "gametest/empty";
    private static final Optional<DyeColor> NONE = Optional.empty();
    private static final Optional<DyeColor> LIME = Optional.of(DyeColor.LIME);
    private static final Optional<DyeColor> BLUE = Optional.of(DyeColor.BLUE);
    private static final Optional<DyeColor> RED = Optional.of(DyeColor.RED);

    private ColonyDyeEventTests() {
    }

    private static void expect(GameTestHelper helper, String name, Decision d, Action action, DyeColor dye) {
        helper.assertTrue(d.action() == action && d.dye() == dye,
                name + ": expected " + action + (dye == null ? "" : " " + dye) + ", got " + d);
    }

    /** Every ownership case: (desired, current, recorded, override, undyeOnLeave) → action. */
    @GameTest(template = EMPTY)
    public static void deciderCases(GameTestHelper helper) {
        // Undyed block in a colony: dye it.
        expect(helper, "undyed", DyeDecider.decide(LIME, NONE, NONE, false, true), Action.APPLY, DyeColor.LIME);
        // Ours, already right: nothing.
        expect(helper, "ours same", DyeDecider.decide(LIME, LIME, LIME, false, true), Action.NONE, null);
        // Ours, colony color changed: re-dye.
        expect(helper, "ours changed", DyeDecider.decide(BLUE, LIME, LIME, false, true), Action.APPLY, DyeColor.BLUE);
        // Player-dyed (no record): leave it.
        expect(helper, "player", DyeDecider.decide(LIME, RED, NONE, false, true), Action.NONE, null);
        // Player re-dyed one of ours: leave it and drop our record.
        expect(helper, "player redyed ours", DyeDecider.decide(LIME, RED, LIME, false, true), Action.FORGET, null);
        // Player-dyed with override on: take it over.
        expect(helper, "override", DyeDecider.decide(LIME, RED, NONE, true, true), Action.APPLY, DyeColor.LIME);
        expect(helper, "override same color", DyeDecider.decide(LIME, LIME, NONE, true, true), Action.APPLY, DyeColor.LIME);
        // Block left the colony (or rule off / color unset): undye ours, or just forget it.
        expect(helper, "leave undye", DyeDecider.decide(NONE, LIME, LIME, false, true), Action.CLEAR, null);
        expect(helper, "leave keep", DyeDecider.decide(NONE, LIME, LIME, false, false), Action.FORGET, null);
        // Outside a colony, player-dyed or undyed: nothing.
        expect(helper, "outside player", DyeDecider.decide(NONE, RED, NONE, false, true), Action.NONE, null);
        expect(helper, "outside undyed", DyeDecider.decide(NONE, NONE, NONE, false, true), Action.NONE, null);
        // Record but the block was undyed meanwhile (e.g. replaced): re-dye / forget.
        expect(helper, "stale record in colony", DyeDecider.decide(LIME, NONE, LIME, false, true), Action.APPLY, DyeColor.LIME);
        expect(helper, "stale record outside", DyeDecider.decide(NONE, NONE, LIME, false, true), Action.FORGET, null);
        helper.succeed();
    }

    /** ColonyDyer on a real jar: dye, recolor, respect a player's dye, undye on leave. */
    @GameTest(template = EMPTY)
    public static void dyerOnRealJar(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos rel = new BlockPos(2, 1, 2);
        helper.setBlock(rel, BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath("ars_nouveau", "source_jar")));
        BlockPos pos = helper.absolutePos(rel);
        AutoDyedData data = AutoDyedData.get(level);

        ColonyDyer.processWith(level, pos, LIME, false, true);
        helper.assertTrue(ArsDecoBridge.getColor(level, pos).equals(LIME), "jar should be lime");
        helper.assertTrue(data.get(pos).equals(LIME), "lime should be recorded as ours");

        ColonyDyer.processWith(level, pos, BLUE, false, true);
        helper.assertTrue(ArsDecoBridge.getColor(level, pos).equals(BLUE), "colony color change should re-dye");

        ArsDecoBridge.apply(level, pos, DyeColor.RED); // a player dyes it
        ColonyDyer.processWith(level, pos, BLUE, false, true);
        helper.assertTrue(ArsDecoBridge.getColor(level, pos).equals(RED), "player's red must stay");
        helper.assertTrue(data.get(pos).isEmpty(), "record should be dropped once the player re-dyes");

        ColonyDyer.processWith(level, pos, NONE, false, true);
        helper.assertTrue(ArsDecoBridge.getColor(level, pos).equals(RED), "leaving must not undye a player's color");

        ArsDecoBridge.clear(level, pos);
        ColonyDyer.processWith(level, pos, LIME, false, true);
        ColonyDyer.processWith(level, pos, NONE, false, true);
        helper.assertTrue(ArsDecoBridge.getColor(level, pos).isEmpty(), "our dye should be removed on leave");
        helper.assertTrue(data.get(pos).isEmpty(), "record should be gone after undye");
        helper.succeed();
    }

    /** Sourcestone (no block entity) goes through Ars Deco's SavedData store. */
    @GameTest(template = EMPTY)
    public static void dyerOnSourcestone(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos rel = new BlockPos(2, 1, 2);
        helper.setBlock(rel, BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath("ars_nouveau", "sourcestone")));
        BlockPos pos = helper.absolutePos(rel);

        ColonyDyer.processWith(level, pos, LIME, false, true);
        helper.assertTrue(ArsDecoBridge.getColor(level, pos).equals(LIME), "sourcestone should be lime");
        ColonyDyer.processWith(level, pos, NONE, false, true);
        helper.assertTrue(ArsDecoBridge.getColor(level, pos).isEmpty(), "sourcestone should be undyed on leave");
        helper.succeed();
    }
}
