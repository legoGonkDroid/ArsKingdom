package fun.dungeon.debauchery.arskingdom.colony;

import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.colony.buildings.modules.settings.ISettingKey;
import com.minecolonies.core.colony.buildings.modules.settings.BoolSetting;
import fun.dungeon.debauchery.arskingdom.building.BuildingSourceSanctum;
import fun.dungeon.debauchery.arskingdom.building.modules.RulesSettingsModule;
import fun.dungeon.debauchery.arskingdom.compat.ArsDecoBridge.Category;
import fun.dungeon.debauchery.arskingdom.config.AKConfig;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import java.util.Optional;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Colony rule toggles: a colony with a built Source Sanctum uses that Sanctum's rules tab; any other
 * colony (or no colony) uses the server config. Lookups are memoized per colony for one server tick.
 */
public final class ColonyRules {
    private static final Int2ObjectOpenHashMap<Optional<RulesSettingsModule>> MEMO = new Int2ObjectOpenHashMap<>();

    private ColonyRules() {
    }

    /** The rules module of the colony's built Source Sanctum, if it has one. */
    public static Optional<RulesSettingsModule> rulesModule(IColony colony) {
        if (colony == null) {
            return Optional.empty();
        }
        return MEMO.computeIfAbsent(colony.getID(), id -> {
            IBuilding sanctum = colony.getServerBuildingManager().getFirstBuildingMatching(
                    b -> b instanceof BuildingSourceSanctum && b.getBuildingLevel() > 0);
            return sanctum == null ? Optional.empty() : Optional.ofNullable(sanctum.getModule(RulesSettingsModule.class));
        });
    }

    private static boolean rule(IColony colony, ISettingKey<BoolSetting> key, ModConfigSpec.BooleanValue config) {
        boolean fallback = config.get();
        return rulesModule(colony).map(m -> m.get(key, fallback)).orElse(fallback);
    }

    public static boolean dyes(IColony colony, Category category) {
        return switch (category) {
            case MACHINE -> rule(colony, RulesSettingsModule.DYE_MACHINES, AKConfig.DYE_MACHINES);
            case DECOR -> rule(colony, RulesSettingsModule.DYE_DECOR, AKConfig.DYE_DECOR);
        };
    }

    public static boolean dyesLights(IColony colony) {
        return rule(colony, RulesSettingsModule.DYE_LIGHTS, AKConfig.DYE_LIGHTS);
    }

    public static boolean recolorTransferParticles(IColony colony) {
        return rule(colony, RulesSettingsModule.RECOLOR_TRANSFER_PARTICLES, AKConfig.RECOLOR_TRANSFER_PARTICLES);
    }

    /**
     * Ritual particles are drawn client side, where colonies aren't available, so this one only follows the
     * server config (synced to clients) for now.
     */
    public static boolean recolorRituals(IColony colony) {
        return AKConfig.RECOLOR_RITUALS.get();
    }

    public static boolean overrideCustomColors(IColony colony) {
        return rule(colony, RulesSettingsModule.OVERRIDE_CUSTOM_COLORS, AKConfig.OVERRIDE_CUSTOM_COLORS);
    }

    public static boolean undyeOnLeave(IColony colony) {
        return rule(colony, RulesSettingsModule.UNDYE_ON_LEAVE, AKConfig.UNDYE_ON_LEAVE);
    }

    /** Whether buildings of this colored type keep their own color in this colony. */
    public static boolean buildingColor(IColony colony, BuildingColors.Entry entry) {
        boolean fallback = AKConfig.buildingColorDefault(entry);
        return rulesModule(colony).map(m -> m.get(entry.settingKey(), fallback)).orElse(fallback);
    }

    public static boolean wildenRaids(IColony colony) {
        return rule(colony, RulesSettingsModule.WILDEN_RAIDS, AKConfig.WILDEN_RAIDS);
    }

    /** Forget cached Sanctum lookups (each tick, and whenever a Sanctum or its rules change). */
    public static void invalidate() {
        MEMO.clear();
        BuildingColors.invalidate();
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Pre event) {
        invalidate();
    }
}
