package fun.dungeon.debauchery.arskingdom.building.modules;

import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.buildings.modules.settings.ISetting;
import com.minecolonies.api.colony.buildings.modules.settings.ISettingKey;
import com.minecolonies.core.colony.buildings.modules.SettingsModule;
import com.minecolonies.core.colony.buildings.modules.settings.BoolSetting;
import com.minecolonies.core.colony.buildings.modules.settings.SettingKey;
import fun.dungeon.debauchery.arskingdom.ArsKingdom;
import fun.dungeon.debauchery.arskingdom.colony.BuildingColors;
import fun.dungeon.debauchery.arskingdom.colony.dye.ColonyDyeSweeps;
import fun.dungeon.debauchery.arskingdom.config.AKConfig;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * The colony's Ars Kingdom rules, shown as a settings tab on the Source Sanctum. New Sanctums start from the
 * server config's defaults. Changing a rule re-sweeps the colony so it takes effect right away. Besides the fixed
 * rules it has one toggle per {@link BuildingColors} type.
 */
public class RulesSettingsModule extends SettingsModule {
    public static final ISettingKey<BoolSetting> DYE_MACHINES = key("dye_machines");
    public static final ISettingKey<BoolSetting> DYE_DECOR = key("dye_decor");
    public static final ISettingKey<BoolSetting> DYE_LIGHTS = key("dye_lights");
    public static final ISettingKey<BoolSetting> OVERRIDE_CUSTOM_COLORS = key("override_custom_colors");
    public static final ISettingKey<BoolSetting> UNDYE_ON_LEAVE = key("undye_on_leave");
    public static final ISettingKey<BoolSetting> RECOLOR_TRANSFER_PARTICLES = key("recolor_transfer_particles");
    public static final ISettingKey<BoolSetting> RECOLOR_RITUALS = key("recolor_rituals");
    public static final ISettingKey<BoolSetting> WILDEN_RAIDS = key("wilden_raids");

    /** Every rule with the config value that seeds it, in tab order. */
    private static final List<Rule> RULES = List.of(
            new Rule(DYE_MACHINES, () -> AKConfig.DYE_MACHINES),
            new Rule(DYE_DECOR, () -> AKConfig.DYE_DECOR),
            new Rule(DYE_LIGHTS, () -> AKConfig.DYE_LIGHTS),
            new Rule(OVERRIDE_CUSTOM_COLORS, () -> AKConfig.OVERRIDE_CUSTOM_COLORS),
            new Rule(UNDYE_ON_LEAVE, () -> AKConfig.UNDYE_ON_LEAVE),
            new Rule(RECOLOR_TRANSFER_PARTICLES, () -> AKConfig.RECOLOR_TRANSFER_PARTICLES),
            new Rule(RECOLOR_RITUALS, () -> AKConfig.RECOLOR_RITUALS),
            new Rule(WILDEN_RAIDS, () -> AKConfig.WILDEN_RAIDS));

    private record Rule(ISettingKey<BoolSetting> key, Supplier<ModConfigSpec.BooleanValue> config) {
    }

    private static ISettingKey<BoolSetting> key(String path) {
        return new SettingKey<>(BoolSetting.class, ArsKingdom.id(path));
    }

    /**
     * Server side only: seeds each rule from the config (MineColonies needs all keys added up front).
     * The client's {@code SettingsModuleView} receives keys and values from the server.
     */
    public RulesSettingsModule() {
        for (Rule rule : RULES) {
            ModConfigSpec.BooleanValue value = rule.config().get();
            with(rule.key(), new BoolSetting(AKConfig.SPEC.isLoaded() ? value.get() : value.getDefault()));
        }
        // One toggle per colored building type ("Mage Towers keep their school color", ...).
        for (BuildingColors.Entry entry : BuildingColors.all()) {
            with(entry.settingKey(), new BoolSetting(AKConfig.buildingColorDefault(entry)));
        }
    }

    public boolean get(ISettingKey<BoolSetting> key, boolean fallback) {
        return getSettingValueOrDefault(key, fallback);
    }

    @Override
    public void updateSetting(ISettingKey<?> key, ISetting<?> setting, ServerPlayer player) {
        super.updateSetting(key, setting, player);
        if (building != null) {
            IColony colony = building.getColony();
            ColonyDyeSweeps.rulesChanged(colony);
        }
    }
}
