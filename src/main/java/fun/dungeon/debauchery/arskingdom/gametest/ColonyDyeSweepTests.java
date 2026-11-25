package fun.dungeon.debauchery.arskingdom.gametest;

import fun.dungeon.debauchery.arskingdom.ArsKingdom;
import fun.dungeon.debauchery.arskingdom.colony.dye.AutoDyedData;
import fun.dungeon.debauchery.arskingdom.colony.dye.ColonyDyer;
import fun.dungeon.debauchery.arskingdom.colony.dye.DyeSweepQueue;
import fun.dungeon.debauchery.arskingdom.compat.ArsDecoBridge;
import fun.dungeon.debauchery.arskingdom.data.ArsKingdomSavedData;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Chunk 3: chunk sweeps and the sweep stamps. */
@GameTestHolder(ArsKingdom.MODID)
@PrefixGameTestTemplate(false)
public final class ColonyDyeSweepTests {
    private static final String EMPTY = "gametest/empty";

    private ColonyDyeSweepTests() {
    }

    private static Block ars(String path) {
        return BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath("ars_nouveau", path));
    }

    /**
     * A sweep outside any colony undyes OUR jar (block entity) and sourcestone (found by the section scan,
     * no block entity), drops their records, and leaves a player's red relay alone.
     * Own batch: sweeps touch whole chunks, which other tests' blocks may share.
     */
    @GameTest(template = EMPTY, batch = "ars_kingdom_sweep")
    public static void sweepUndyesOursOutsideColony(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos jarRel = new BlockPos(1, 1, 1);
        BlockPos stoneRel = new BlockPos(3, 1, 1);
        BlockPos relayRel = new BlockPos(5, 1, 1);
        helper.setBlock(jarRel, ars("source_jar"));
        helper.setBlock(stoneRel, ars("sourcestone"));
        helper.setBlock(relayRel, ars("relay"));
        BlockPos jar = helper.absolutePos(jarRel);
        BlockPos stone = helper.absolutePos(stoneRel);
        BlockPos relay = helper.absolutePos(relayRel);

        ColonyDyer.processWith(level, jar, Optional.of(DyeColor.LIME), false, true);
        ColonyDyer.processWith(level, stone, Optional.of(DyeColor.LIME), false, true);
        ArsDecoBridge.apply(level, relay, DyeColor.RED);
        AutoDyedData data = AutoDyedData.get(level);
        helper.assertTrue(data.get(jar).isPresent() && data.get(stone).isPresent(), "jar and sourcestone should be recorded");

        Set<ChunkPos> chunks = Stream.of(jar, stone, relay).map(ChunkPos::new).collect(Collectors.toSet());
        for (ChunkPos chunk : chunks) {
            DyeSweepQueue.sweepChunk(level, level.getChunk(chunk.x, chunk.z));
        }

        helper.assertTrue(ArsDecoBridge.getColor(level, jar).isEmpty(), "our jar should be undyed outside a colony");
        helper.assertTrue(ArsDecoBridge.getColor(level, stone).isEmpty(), "our sourcestone should be undyed outside a colony");
        helper.assertTrue(ArsDecoBridge.getColor(level, relay).equals(Optional.of(DyeColor.RED)), "the player's red relay must stay");
        helper.assertTrue(data.get(jar).isEmpty() && data.get(stone).isEmpty(), "records should be dropped");
        helper.assertTrue(data.positionsIn(new ChunkPos(jar)).isEmpty() || !data.positionsIn(new ChunkPos(jar)).contains(jar.asLong()),
                "chunk index should no longer list the jar");
        helper.succeed();
    }

    /** Stamps: a chunk is current until its colony's version (or the config epoch) changes. */
    @GameTest(template = EMPTY)
    public static void stampsTrackVersions(GameTestHelper helper) {
        ArsKingdomSavedData state = ArsKingdomSavedData.get(helper.getLevel());
        int colony = 2_000_000_000; // an id no real colony uses
        ChunkPos chunk = new ChunkPos(1_000_000, 1_000_000);

        helper.assertTrue(state.isCurrent(chunk, 0), "an unswept, unclaimed chunk is current");
        helper.assertTrue(!state.isCurrent(chunk, colony), "an unswept claimed chunk is out of date");
        state.markSwept(chunk, colony);
        helper.assertTrue(state.isCurrent(chunk, colony), "just swept: current");
        helper.assertTrue(!state.isCurrent(chunk, 0), "left the colony: out of date");
        helper.assertTrue(state.chunksStampedWith(colony).contains(chunk.toLong()), "should be listed for its colony");

        state.bumpColony(colony);
        helper.assertTrue(!state.isCurrent(chunk, colony), "color change: out of date");
        state.markSwept(chunk, colony);
        helper.assertTrue(state.isCurrent(chunk, colony), "re-swept: current");

        state.markSwept(chunk, 0);
        helper.assertTrue(state.isCurrent(chunk, 0), "swept while unclaimed: current, stamp removed");
        helper.assertTrue(!state.chunksStampedWith(colony).contains(chunk.toLong()), "no longer listed for the colony");
        helper.succeed();
    }
}
