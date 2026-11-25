package fun.dungeon.debauchery.arskingdom.data;

import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2LongMap;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongList;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Per-dimension Ars Kingdom state.
 *
 * <p>Dye sweeps: every colony has a dye version, bumped when anything that changes its blocks' colors
 * changes (team color, rules). A config epoch is bumped when the dye config changes. Every swept chunk is
 * stamped with the owning colony and the version it was swept at; a chunk whose stamp doesn't match is
 * out of date and gets swept again (on load, or when its colony changes).
 */
public final class ArsKingdomSavedData extends SavedData {
    private static final String NAME = "ars_kingdom";

    private final Int2IntOpenHashMap colonyVersions = new Int2IntOpenHashMap();
    /** Chunk → (colonyId << 32 | version). Missing = never swept, or swept while unclaimed. */
    private final Long2LongOpenHashMap chunkStamps = new Long2LongOpenHashMap();
    private int configEpoch;
    private int configHash;

    private ArsKingdomSavedData() {
    }

    public static ArsKingdomSavedData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(new Factory<>(ArsKingdomSavedData::new, ArsKingdomSavedData::load, null), NAME);
    }

    /** The current dye version of a colony (changes whenever the colony's or the config's version is bumped). */
    public int dyeVersion(int colonyId) {
        return colonyVersions.get(colonyId) + configEpoch;
    }

    public void bumpColony(int colonyId) {
        colonyVersions.addTo(colonyId, 1);
        setDirty();
    }

    /** Bumps the config epoch when the dye-relevant config hash changed. Returns true if it did. */
    public boolean updateConfigHash(int hash) {
        if (hash == configHash) {
            return false;
        }
        configHash = hash;
        configEpoch++;
        setDirty();
        return true;
    }

    public static long stamp(int colonyId, int version) {
        return ((long) colonyId << 32) | (version & 0xFFFFFFFFL);
    }

    public static int stampColony(long stamp) {
        return (int) (stamp >>> 32);
    }

    /** True when the chunk was last swept for this owner at its current dye version. */
    public boolean isCurrent(ChunkPos chunk, int ownerId) {
        long expected = ownerId == 0 ? 0L : stamp(ownerId, dyeVersion(ownerId));
        return chunkStamps.get(chunk.toLong()) == expected;
    }

    /** Records that the chunk was just swept for {@code ownerId} (0 = no colony). */
    public void markSwept(ChunkPos chunk, int ownerId) {
        long key = chunk.toLong();
        if (ownerId == 0) {
            if (chunkStamps.remove(key) != 0L) {
                setDirty();
            }
        } else if (chunkStamps.put(key, stamp(ownerId, dyeVersion(ownerId))) != stamp(ownerId, dyeVersion(ownerId))) {
            setDirty();
        }
    }

    /** Chunks last swept for {@code colonyId}, including ones it no longer claims. */
    public LongList chunksStampedWith(int colonyId) {
        LongList out = new LongArrayList();
        for (Long2LongMap.Entry e : chunkStamps.long2LongEntrySet()) {
            if (stampColony(e.getLongValue()) == colonyId) {
                out.add(e.getLongKey());
            }
        }
        return out;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        int[] ids = new int[colonyVersions.size()];
        int[] versions = new int[colonyVersions.size()];
        int i = 0;
        for (Int2IntMap.Entry e : colonyVersions.int2IntEntrySet()) {
            ids[i] = e.getIntKey();
            versions[i++] = e.getIntValue();
        }
        long[] chunks = new long[chunkStamps.size()];
        long[] stamps = new long[chunkStamps.size()];
        i = 0;
        for (Long2LongMap.Entry e : chunkStamps.long2LongEntrySet()) {
            chunks[i] = e.getLongKey();
            stamps[i++] = e.getLongValue();
        }
        tag.putIntArray("colony_ids", ids);
        tag.putIntArray("colony_versions", versions);
        tag.putLongArray("chunks", chunks);
        tag.putLongArray("chunk_stamps", stamps);
        tag.putInt("config_epoch", configEpoch);
        tag.putInt("config_hash", configHash);
        return tag;
    }

    private static ArsKingdomSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        ArsKingdomSavedData data = new ArsKingdomSavedData();
        int[] ids = tag.getIntArray("colony_ids");
        int[] versions = tag.getIntArray("colony_versions");
        for (int i = 0; i < Math.min(ids.length, versions.length); i++) {
            data.colonyVersions.put(ids[i], versions[i]);
        }
        long[] chunks = tag.getLongArray("chunks");
        long[] stamps = tag.getLongArray("chunk_stamps");
        for (int i = 0; i < Math.min(chunks.length, stamps.length); i++) {
            data.chunkStamps.put(chunks[i], stamps[i]);
        }
        data.configEpoch = tag.getInt("config_epoch");
        data.configHash = tag.getInt("config_hash");
        return data;
    }
}
