package fun.dungeon.debauchery.arskingdom.colony.dye;

import fun.dungeon.debauchery.arskingdom.colony.ColonyLookup;
import fun.dungeon.debauchery.arskingdom.compat.ArsDecoBridge;
import fun.dungeon.debauchery.arskingdom.compat.ArsLightBridge;
import fun.dungeon.debauchery.arskingdom.config.AKConfig;
import fun.dungeon.debauchery.arskingdom.data.ArsKingdomSavedData;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongLinkedOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Chunk-incremental colony dye sweeps with a per-tick budget.
 *
 * <p>Two kinds of entries, per dimension:
 * <ul>
 *   <li><b>checks</b>: sweep the chunk only if its stamp is out of date (cheap; many per tick).</li>
 *   <li><b>forced</b>: sweep the chunk regardless (a builder just placed blocks there).</li>
 * </ul>
 * Chunks that aren't loaded when their turn comes are skipped; they're checked again when they load.
 */
public final class DyeSweepQueue {
    /** Stamp comparisons per tick; each is a map lookup, so this can be generous. */
    private static final int CHECKS_PER_TICK = 512;

    private static final Map<ResourceKey<Level>, Queues> QUEUES = new HashMap<>();

    private static final class Queues {
        final LongLinkedOpenHashSet checks = new LongLinkedOpenHashSet();
        final LongLinkedOpenHashSet forced = new LongLinkedOpenHashSet();
    }

    private DyeSweepQueue() {
    }

    private static Queues queues(ResourceKey<Level> dim) {
        return QUEUES.computeIfAbsent(dim, d -> new Queues());
    }

    /** Sweep the chunk if it's out of date. */
    public static void check(ResourceKey<Level> dim, ChunkPos chunk) {
        queues(dim).checks.add(chunk.toLong());
    }

    /** Sweep the chunk even if it's up to date. */
    public static void force(ResourceKey<Level> dim, ChunkPos chunk) {
        Queues q = queues(dim);
        q.checks.remove(chunk.toLong());
        q.forced.add(chunk.toLong());
    }

    public static int pending() {
        return QUEUES.values().stream().mapToInt(q -> q.checks.size() + q.forced.size()).sum();
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (QUEUES.isEmpty()) {
            return;
        }
        MinecraftServer server = event.getServer();
        int budget = AKConfig.SWEEP_CHUNKS_PER_TICK.get();
        for (Map.Entry<ResourceKey<Level>, Queues> entry : QUEUES.entrySet()) {
            ServerLevel level = server.getLevel(entry.getKey());
            Queues q = entry.getValue();
            if (level == null) {
                q.checks.clear();
                q.forced.clear();
                continue;
            }
            budget = drain(level, q, budget);
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        QUEUES.clear();
    }

    private static int drain(ServerLevel level, Queues q, int budget) {
        while (budget > 0 && !q.forced.isEmpty()) {
            long key = q.forced.removeFirstLong();
            if (sweepIfLoaded(level, new ChunkPos(key))) {
                budget--;
            }
        }
        ArsKingdomSavedData state = ArsKingdomSavedData.get(level);
        int checks = CHECKS_PER_TICK;
        while (budget > 0 && checks-- > 0 && !q.checks.isEmpty()) {
            ChunkPos chunk = new ChunkPos(q.checks.removeFirstLong());
            int owner = ColonyLookup.colonyId(level.dimension(), chunk);
            if (!state.isCurrent(chunk, owner) && sweepIfLoaded(level, chunk)) {
                budget--;
            }
        }
        return budget;
    }

    private static boolean sweepIfLoaded(ServerLevel level, ChunkPos chunk) {
        LevelChunk loaded = level.getChunkSource().getChunkNow(chunk.x, chunk.z);
        if (loaded == null) {
            return false;
        }
        sweepChunk(level, loaded);
        return true;
    }

    /**
     * Runs {@link ColonyDyer#process} on every Ars Deco target and Ars light in the chunk, plus every position we have a
     * record for (so blocks that left the colony, or were replaced, are cleaned up), then stamps the chunk.
     */
    public static void sweepChunk(ServerLevel level, LevelChunk chunk) {
        LongOpenHashSet positions = new LongOpenHashSet();

        for (BlockEntity be : chunk.getBlockEntities().values()) {
            if (ArsDecoBridge.isTarget(be.getBlockState().getBlock()) || ArsLightBridge.isLight(be)) {
                positions.add(be.getBlockPos().asLong());
            }
        }

        // Targets without a block entity (sourcestone, archwood...). maybeHas skips sections whose palette has none.
        Predicate<BlockState> plainTarget = s -> !s.hasBlockEntity() && ArsDecoBridge.isTarget(s.getBlock());
        LevelChunkSection[] sections = chunk.getSections();
        int baseX = chunk.getPos().getMinBlockX();
        int baseZ = chunk.getPos().getMinBlockZ();
        for (int i = 0; i < sections.length; i++) {
            LevelChunkSection section = sections[i];
            if (section.hasOnlyAir() || !section.maybeHas(plainTarget)) {
                continue;
            }
            int baseY = chunk.getSectionYFromSectionIndex(i) << 4;
            for (int y = 0; y < 16; y++) {
                for (int z = 0; z < 16; z++) {
                    for (int x = 0; x < 16; x++) {
                        if (plainTarget.test(section.getBlockState(x, y, z))) {
                            positions.add(BlockPos.asLong(baseX + x, baseY + y, baseZ + z));
                        }
                    }
                }
            }
        }

        positions.addAll(AutoDyedData.get(level).positionsIn(chunk.getPos()));
        positions.addAll(AutoDyedData.lights(level).positionsIn(chunk.getPos()));

        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (long p : new LongArrayList(positions)) {
            ColonyDyer.process(level, pos.set(p).immutable());
        }
        ArsKingdomSavedData.get(level).markSwept(chunk.getPos(), ColonyLookup.colonyId(level.dimension(), chunk.getPos()));
    }
}
