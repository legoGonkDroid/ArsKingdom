package fun.dungeon.debauchery.arskingdom.colony;

import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.colony.buildings.modules.settings.ISettingKey;
import com.minecolonies.core.colony.buildings.modules.settings.BoolSetting;
import com.minecolonies.core.colony.buildings.modules.settings.SettingKey;
import fun.dungeon.debauchery.arskingdom.ArsKingdom;
import fun.dungeon.debauchery.arskingdom.building.BuildingMageTower;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Tuple;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Building types with a color of their own. Inside such a building's bounds the colony dye sweep uses the building's
 * color instead of the colony color, as long as that type is switched on (one toggle per type in the Source Sanctum's
 * rules tab; the server config's {@code building_colors} lists the types that start on).
 *
 * <p>To add one: {@link #register} an entry whose function gives the building's color (empty for other buildings) and
 * add lang for its setting label and tooltip ({@code com.minecolonies.coremod.setting[.tooltip].<setting key>}).
 * Entries must be registered during mod construction, before any Sanctum's rules module is built.
 */
public final class BuildingColors {
    /** A colored building type and its Sanctum toggle. */
    public record Entry(ResourceLocation id, Function<IBuilding, Optional<DyeColor>> color, ISettingKey<BoolSetting> settingKey) {
    }

    /** The bounds of one colored building and its color. */
    public record Zone(BoundingBox box, DyeColor dye, Entry entry) {
    }

    private static final List<Entry> ENTRIES = new ArrayList<>();
    private static final Int2ObjectOpenHashMap<List<Zone>> MEMO = new Int2ObjectOpenHashMap<>();

    /** Mage Towers: each school's tower in its school's color. */
    public static final Entry MAGE_TOWERS = register(ArsKingdom.id("mage_towers"),
            b -> b instanceof BuildingMageTower tower ? Optional.of(tower.getSchool().dye()) : Optional.empty());

    private BuildingColors() {
    }

    /** Adds a colored building type; its setting key is {@code ars_kingdom:building_color/<path>}. */
    public static synchronized Entry register(ResourceLocation id, Function<IBuilding, Optional<DyeColor>> color) {
        for (Entry existing : ENTRIES) {
            if (existing.id().equals(id)) {
                throw new IllegalStateException("Building color " + id + " registered twice");
            }
        }
        String path = id.getNamespace().equals(ArsKingdom.MODID) ? id.getPath() : id.getNamespace() + "/" + id.getPath();
        Entry entry = new Entry(id, color, new SettingKey<>(BoolSetting.class, ArsKingdom.id("building_color/" + path)));
        ENTRIES.add(entry);
        return entry;
    }

    /** Every type, in registration order (the order of the toggles in the rules tab). */
    public static List<Entry> all() {
        return Collections.unmodifiableList(ENTRIES);
    }

    /** Forces class loading (and so {@link #MAGE_TOWERS}' registration) at a fixed point. */
    public static void init() {
    }

    /** Whether any registered type gives this building a color (switched on or not). */
    public static boolean isColored(IBuilding building) {
        for (Entry entry : ENTRIES) {
            if (entry.color().apply(building).isPresent()) {
                return true;
            }
        }
        return false;
    }

    /** The colored zone at {@code pos} in this colony, if any of its switched-on building types covers it. */
    public static Optional<Zone> zoneAt(IColony colony, BlockPos pos) {
        if (colony == null) {
            return Optional.empty();
        }
        return pick(MEMO.computeIfAbsent(colony.getID(), id -> zones(colony)), pos);
    }

    /** The zone containing {@code pos}; when zones nest, the smallest one wins. */
    public static Optional<Zone> pick(List<Zone> zones, BlockPos pos) {
        Zone best = null;
        long bestVolume = Long.MAX_VALUE;
        for (Zone zone : zones) {
            if (zone.box().isInside(pos)) {
                BoundingBox b = zone.box();
                long volume = (long) b.getXSpan() * b.getYSpan() * b.getZSpan();
                if (volume < bestVolume) {
                    best = zone;
                    bestVolume = volume;
                }
            }
        }
        return Optional.ofNullable(best);
    }

    private static List<Zone> zones(IColony colony) {
        List<Entry> enabled = new ArrayList<>();
        for (Entry entry : ENTRIES) {
            if (ColonyRules.buildingColor(colony, entry)) {
                enabled.add(entry);
            }
        }
        if (enabled.isEmpty()) {
            return List.of();
        }
        List<Zone> zones = new ArrayList<>();
        for (IBuilding building : colony.getServerBuildingManager().getBuildings().values()) {
            if (building.getBuildingLevel() <= 0) {
                continue;
            }
            for (Entry entry : enabled) {
                Optional<DyeColor> dye = entry.color().apply(building);
                if (dye.isPresent()) {
                    Tuple<BlockPos, BlockPos> corners = building.getCorners();
                    zones.add(new Zone(BoundingBox.fromCorners(corners.getA(), corners.getB()), dye.get(), entry));
                    break;
                }
            }
        }
        return zones;
    }

    /** Forget cached zones (each tick, and whenever rules or colored buildings change). */
    public static void invalidate() {
        MEMO.clear();
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Pre event) {
        invalidate();
    }
}
