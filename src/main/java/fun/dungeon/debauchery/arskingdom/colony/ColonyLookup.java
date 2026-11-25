package fun.dungeon.debauchery.arskingdom.colony;

import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.colony.claim.IChunkClaimData;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Cheap "which colony owns this position" lookup, server side. Reads MineColonies' chunk claim data
 * directly (never loads chunks, unlike {@code getIColony}/{@code getColonyView}) and memoizes the
 * owning colony id per chunk for the rest of the server tick.
 */
public final class ColonyLookup {
    /** MineColonies' owning-colony id for unclaimed chunks (colony ids start at 1). */
    public static final int NO_COLONY = 0;

    private static final Map<ResourceKey<Level>, Long2IntOpenHashMap> MEMO = new HashMap<>();

    private ColonyLookup() {
    }

    /** The id of the colony that owns the chunk containing {@code pos}, or {@link #NO_COLONY}. */
    public static int colonyId(Level level, BlockPos pos) {
        return colonyId(level.dimension(), new ChunkPos(pos));
    }

    public static int colonyId(ResourceKey<Level> dim, ChunkPos chunk) {
        Long2IntOpenHashMap memo = MEMO.computeIfAbsent(dim, d -> {
            Long2IntOpenHashMap m = new Long2IntOpenHashMap();
            m.defaultReturnValue(-1);
            return m;
        });
        long key = chunk.toLong();
        int id = memo.get(key);
        if (id < 0) {
            IChunkClaimData data = IColonyManager.getInstance().getClaimData(dim, chunk);
            id = data == null ? NO_COLONY : Math.max(NO_COLONY, data.getOwningColony());
            memo.put(key, id);
        }
        return id;
    }

    /** The colony owning {@code pos}, or null when the position is outside every colony. */
    public static IColony colony(Level level, BlockPos pos) {
        int id = colonyId(level, pos);
        return id == NO_COLONY ? null : IColonyManager.getInstance().getColonyByDimension(id, level.dimension());
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Pre event) {
        MEMO.values().forEach(Long2IntOpenHashMap::clear);
    }
}
