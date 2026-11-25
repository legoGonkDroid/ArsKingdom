package fun.dungeon.debauchery.arskingdom.colony.dye;

import it.unimi.dsi.fastutil.longs.Long2ByteMap;
import it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongList;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.world.level.ChunkPos;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Ownership record, one per dimension (and one more for light colors, see {@link #lights}): every position WE dyed and the dye we used. A block whose color
 * still matches its record is ours to recolor or undye; anything else belongs to the player.
 */
public final class AutoDyedData extends SavedData {
    private static final String NAME = "ars_kingdom_auto_dyed";
    private static final String LIGHTS_NAME = "ars_kingdom_auto_lit";
    private static final byte NONE = -1;

    private final Long2ByteOpenHashMap dyes = new Long2ByteOpenHashMap();
    /** Chunk ({@link ChunkPos#toLong()}) → recorded positions in it, so sweeps don't scan every record. */
    private final Long2ObjectOpenHashMap<LongOpenHashSet> byChunk = new Long2ObjectOpenHashMap<>();

    private AutoDyedData() {
        dyes.defaultReturnValue(NONE);
    }

    /** Block dye we applied (Ars Deco). */
    public static AutoDyedData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(new Factory<>(AutoDyedData::new, AutoDyedData::load, null), NAME);
    }

    /** Light colors we applied (M1d): the dye whose particle color we gave a magelight, sconce flame or mage light. */
    public static AutoDyedData lights(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(new Factory<>(AutoDyedData::new, AutoDyedData::load, null), LIGHTS_NAME);
    }

    public Optional<DyeColor> get(BlockPos pos) {
        byte id = dyes.get(pos.asLong());
        return id == NONE ? Optional.empty() : Optional.of(DyeColor.byId(id));
    }

    public void put(BlockPos pos, DyeColor dye) {
        long key = pos.asLong();
        if (dyes.put(key, (byte) dye.getId()) != (byte) dye.getId()) {
            index(key);
            setDirty();
        }
    }

    public void remove(BlockPos pos) {
        long key = pos.asLong();
        if (dyes.remove(key) != NONE) {
            long chunk = ChunkPos.asLong(BlockPos.getX(key) >> 4, BlockPos.getZ(key) >> 4);
            LongOpenHashSet set = byChunk.get(chunk);
            if (set != null && set.remove(key) && set.isEmpty()) {
                byChunk.remove(chunk);
            }
            setDirty();
        }
    }

    /** Recorded positions (as {@link BlockPos#asLong()}) inside one chunk; a copy, safe to iterate while editing. */
    public LongList positionsIn(ChunkPos chunk) {
        LongOpenHashSet set = byChunk.get(chunk.toLong());
        return set == null ? new LongArrayList() : new LongArrayList(set);
    }

    private void index(long key) {
        long chunk = ChunkPos.asLong(BlockPos.getX(key) >> 4, BlockPos.getZ(key) >> 4);
        byChunk.computeIfAbsent(chunk, c -> new LongOpenHashSet()).add(key);
    }

    public int size() {
        return dyes.size();
    }

    /** A snapshot of every recorded position (as {@link BlockPos#asLong()}), safe to iterate while editing. */
    public LongList positions() {
        return new LongArrayList(dyes.keySet());
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        long[] positions = new long[dyes.size()];
        byte[] colors = new byte[dyes.size()];
        int i = 0;
        for (Long2ByteMap.Entry e : dyes.long2ByteEntrySet()) {
            positions[i] = e.getLongKey();
            colors[i] = e.getByteValue();
            i++;
        }
        tag.putLongArray("positions", positions);
        tag.putByteArray("dyes", colors);
        return tag;
    }

    private static AutoDyedData load(CompoundTag tag, HolderLookup.Provider registries) {
        AutoDyedData data = new AutoDyedData();
        long[] positions = tag.getLongArray("positions");
        byte[] colors = tag.getByteArray("dyes");
        for (int i = 0; i < Math.min(positions.length, colors.length); i++) {
            if (colors[i] >= 0 && colors[i] < DyeColor.values().length) {
                data.dyes.put(positions[i], colors[i]);
                data.index(positions[i]);
            }
        }
        return data;
    }
}
