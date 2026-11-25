package fun.dungeon.debauchery.arskingdom.colony.dye;

import com.minecolonies.api.IMinecoloniesAPI;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.colony.claim.IChunkClaimData;
import com.minecolonies.api.eventbus.EventBus;
import com.minecolonies.api.eventbus.events.colony.ColonyDeletedModEvent;
import com.minecolonies.api.eventbus.events.colony.ColonyTeamColorChangedModEvent;
import com.minecolonies.api.eventbus.events.colony.buildings.BuildingAddedModEvent;
import com.minecolonies.api.eventbus.events.colony.buildings.BuildingConstructionModEvent;
import com.minecolonies.api.eventbus.events.colony.buildings.BuildingRemovedModEvent;
import fun.dungeon.debauchery.arskingdom.ArsKingdom;
import fun.dungeon.debauchery.arskingdom.building.BuildingSourceSanctum;
import fun.dungeon.debauchery.arskingdom.colony.BuildingColors;
import fun.dungeon.debauchery.arskingdom.colony.ColonyRules;
import fun.dungeon.debauchery.arskingdom.config.AKConfig;
import fun.dungeon.debauchery.arskingdom.data.ArsKingdomSavedData;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiConsumer;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Tuple;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

/** Decides WHEN chunks get swept (Chunk 3). {@link DyeSweepQueue} does the sweeping. */
public final class ColonyDyeSweeps {
    /**
     * Bump when a code change alters what a sweep does to existing blocks, so worlds re-sweep their colony
     * chunks on the next start. 2: lights are judged by the colour they draw (fixes Archwood lights).
     * 3: colored buildings (Mage Towers) keep their own color.
     */
    private static final int SWEEP_LOGIC_VERSION = 3;

    private static long ticksSinceBackstop;

    private ColonyDyeSweeps() {
    }

    /** Subscribes to MineColonies' own event bus. Call once from common setup. */
    public static void subscribeMineColonies() {
        EventBus bus = IMinecoloniesAPI.getInstance().getEventBus();
        bus.subscribe(ColonyTeamColorChangedModEvent.class, e -> onColonyColorChanged(e.getColony()));
        bus.subscribe(ColonyDeletedModEvent.class, e -> onColonyDeleted(e.getColony()));
        bus.subscribe(BuildingConstructionModEvent.class, e -> {
            onBuildingConstructed(e.getBuilding());
            // A Sanctum changes the rules; a colored building changes the colors inside its (new) bounds.
            if (e.getBuilding() instanceof BuildingSourceSanctum || BuildingColors.isColored(e.getBuilding())) {
                rulesChanged(e.getColony());
            }
        });
        bus.subscribe(BuildingAddedModEvent.class, e -> queueColony(e.getColony(), false));
        bus.subscribe(BuildingRemovedModEvent.class, e -> {
            if (e.getBuilding() instanceof BuildingSourceSanctum || BuildingColors.isColored(e.getBuilding())) {
                rulesChanged(e.getColony());
            } else {
                queueColony(e.getColony(), false);
            }
        });
    }

    /**
     * The colony's rules changed (its Sanctum's tab was edited, or a Sanctum was built or removed): every
     * chunk of the colony is out of date.
     */
    public static void rulesChanged(IColony colony) {
        ColonyRules.invalidate();
        ServerLevel level = level(colony.getDimension());
        if (level == null) {
            return;
        }
        ArsKingdomSavedData.get(level).bumpColony(colony.getID());
        queueColony(colony, false);
    }

    private static ServerLevel level(ResourceKey<Level> dim) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        return server == null ? null : server.getLevel(dim);
    }

    /** Team color changed: everything we dyed for this colony needs the new color. */
    private static void onColonyColorChanged(IColony colony) {
        ServerLevel level = level(colony.getDimension());
        if (level == null) {
            return;
        }
        ArsKingdomSavedData.get(level).bumpColony(colony.getID());
        queueColony(colony, false);
    }

    /** Colony deleted: its chunks are now unowned, so sweeping them undyes (or forgets) our blocks. */
    private static void onColonyDeleted(IColony colony) {
        ServerLevel level = level(colony.getDimension());
        if (level == null) {
            return;
        }
        ArsKingdomSavedData state = ArsKingdomSavedData.get(level);
        state.bumpColony(colony.getID());
        for (long chunk : state.chunksStampedWith(colony.getID())) {
            ChunkPos pos = new ChunkPos(chunk);
            if (level.hasChunk(pos.x, pos.z)) {
                DyeSweepQueue.force(level.dimension(), pos);
            }
        }
    }

    /** Builders place blocks through Structurize, which fires no place events: sweep the building's footprint. */
    private static void onBuildingConstructed(IBuilding building) {
        IColony colony = building.getColony();
        ServerLevel level = level(colony.getDimension());
        if (level == null) {
            return;
        }
        Tuple<BlockPos, BlockPos> corners = building.getCorners();
        BlockPos a = corners.getA();
        BlockPos b = corners.getB();
        for (int cx = Math.min(a.getX(), b.getX()) >> 4; cx <= Math.max(a.getX(), b.getX()) >> 4; cx++) {
            for (int cz = Math.min(a.getZ(), b.getZ()) >> 4; cz <= Math.max(a.getZ(), b.getZ()) >> 4; cz++) {
                DyeSweepQueue.force(level.dimension(), new ChunkPos(cx, cz));
            }
        }
    }

    /** Queues every loaded chunk the colony claims or that was last swept for it (claims may have shrunk). */
    public static void queueColony(IColony colony, boolean force) {
        ServerLevel level = level(colony.getDimension());
        if (level == null) {
            return;
        }
        int id = colony.getID();
        BiConsumer<ResourceKey<Level>, ChunkPos> queue = force ? DyeSweepQueue::force : DyeSweepQueue::check;
        forEachClaimedLoaded(level, (pos, owner) -> {
            if (owner == id) {
                queue.accept(level.dimension(), pos);
            }
        });
        for (long chunk : ArsKingdomSavedData.get(level).chunksStampedWith(id)) {
            ChunkPos pos = new ChunkPos(chunk);
            if (level.hasChunk(pos.x, pos.z)) {
                queue.accept(level.dimension(), pos);
            }
        }
    }

    private static void forEachClaimedLoaded(ServerLevel level, BiConsumer<ChunkPos, Integer> action) {
        Map<ChunkPos, IChunkClaimData> claims = IColonyManager.getInstance().getClaimData(level.dimension());
        if (claims == null) {
            return;
        }
        claims.forEach((pos, data) -> {
            if (data != null && data.getOwningColony() > 0 && level.hasChunk(pos.x, pos.z)) {
                action.accept(pos, data.getOwningColony());
            }
        });
    }

    /** A chunk that missed a color change while unloaded catches up here (claims are read when the check runs). */
    @SubscribeEvent
    public static void onChunkLoad(ChunkEvent.Load event) {
        if (event.getLevel() instanceof ServerLevel level && !event.isNewChunk()) {
            DyeSweepQueue.check(level.dimension(), event.getChunk().getPos());
        }
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        applyConfigChanges(event.getServer());
        ticksSinceBackstop = 0;
    }

    /** Called when the server config is (re)loaded: a changed dye config re-sweeps every colony chunk. */
    public static void applyConfigChanges(MinecraftServer server) {
        if (server == null) {
            return;
        }
        int hash = Objects.hash(SWEEP_LOGIC_VERSION, AKConfig.TEAM_COLOR_DYES.get(), AKConfig.DYE_MACHINES.get(), AKConfig.DYE_DECOR.get(),
                AKConfig.OVERRIDE_CUSTOM_COLORS.get(), AKConfig.UNDYE_ON_LEAVE.get(), AKConfig.DYE_LIGHTS.get(), AKConfig.BUILDING_COLORS.get());
        for (ServerLevel level : server.getAllLevels()) {
            ArsKingdomSavedData state = ArsKingdomSavedData.get(level);
            if (state.updateConfigHash(hash)) {
                ArsKingdom.LOGGER.info("Colony dye config changed; re-sweeping loaded colony chunks in {}", level.dimension().location());
                forEachClaimedLoaded(level, (pos, owner) -> DyeSweepQueue.check(level.dimension(), pos));
            }
        }
    }

    /** Backstop: every N minutes, force-sweep all loaded colony chunks. */
    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        int minutes = AKConfig.BACKSTOP_SWEEP_MINUTES.get();
        if (minutes <= 0 || ++ticksSinceBackstop < minutes * 60L * 20L) {
            return;
        }
        ticksSinceBackstop = 0;
        for (ServerLevel level : event.getServer().getAllLevels()) {
            forEachClaimedLoaded(level, (pos, owner) -> DyeSweepQueue.force(level.dimension(), pos));
        }
    }
}
