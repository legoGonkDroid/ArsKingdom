package fun.dungeon.debauchery.arskingdom.registry;

import com.minecolonies.api.colony.buildings.registry.BuildingEntry;
import com.minecolonies.apiimp.CommonMinecoloniesAPIImpl;
import com.minecolonies.api.entity.citizen.Skill;
import com.minecolonies.core.colony.buildings.modules.BuildingModules;
import com.minecolonies.core.colony.buildings.modules.WorkerBuildingModule;
import com.minecolonies.core.colony.buildings.moduleviews.SettingsModuleView;
import com.minecolonies.core.colony.buildings.moduleviews.WorkerBuildingModuleView;
import com.minecolonies.core.colony.buildings.views.EmptyView;
import fun.dungeon.debauchery.arskingdom.ArsKingdom;
import fun.dungeon.debauchery.arskingdom.building.BuildingSourceSanctum;
import fun.dungeon.debauchery.arskingdom.building.modules.RulesSettingsModule;
import com.minecolonies.core.colony.buildings.AbstractBuildingGuards;
import com.minecolonies.core.colony.buildings.modules.GuardBuildingModule;
import com.minecolonies.core.colony.buildings.moduleviews.CombinedHiringLimitModuleView;
import fun.dungeon.debauchery.arskingdom.battlemage.BattlemageSchools;
import fun.dungeon.debauchery.arskingdom.battlemage.MageSchool;
import fun.dungeon.debauchery.arskingdom.building.BuildingMageTower;
import fun.dungeon.debauchery.arskingdom.building.modules.BattlemageBuildingModule;
import com.minecolonies.api.colony.buildings.IBuilding;
import java.util.EnumMap;
import java.util.Map;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** MineColonies building entries and module producers. Module keys are unique across all mods: prefix them. */
public final class AKBuildings {
    public static final DeferredRegister<BuildingEntry> BUILDINGS =
            DeferredRegister.create(CommonMinecoloniesAPIImpl.BUILDINGS, ArsKingdom.MODID);

    /** The Ars Kingdom rules tab (Source Sanctum). */
    public static final BuildingEntry.ModuleProducer<RulesSettingsModule, SettingsModuleView> RULES =
            new BuildingEntry.ModuleProducer<>("ars_kingdom_rules", RulesSettingsModule::new, () -> SettingsModuleView::new);

    /** The source keeper: Mana (primary: source per fuel), Focus (secondary: speed); one per Sanctum. */
    public static final BuildingEntry.ModuleProducer<WorkerBuildingModule, WorkerBuildingModuleView> SOURCE_KEEPER_WORK =
            new BuildingEntry.ModuleProducer<>("ars_kingdom_sourcekeeper_work",
                    () -> new WorkerBuildingModule(AKJobs.SOURCE_KEEPER.get(), Skill.Mana, Skill.Focus, false, b -> 1),
                    () -> WorkerBuildingModuleView::new);

    public static final DeferredHolder<BuildingEntry, BuildingEntry> SOURCE_SANCTUM = BUILDINGS.register(BuildingSourceSanctum.SCHEMATIC,
            () -> new BuildingEntry.Builder()
                    .setBuildingBlock(AKBlocks.SOURCE_SANCTUM.get())
                    .setBuildingProducer(BuildingSourceSanctum::new)
                    .setBuildingViewProducer(() -> EmptyView::new)
                    .setRegistryName(ArsKingdom.id(BuildingSourceSanctum.SCHEMATIC))
                    .addBuildingModuleProducer(SOURCE_KEEPER_WORK)
                    .addBuildingModuleProducer(RULES)
                    .addBuildingModuleProducer(BuildingModules.MIN_STOCK)
                    .addBuildingModuleProducer(BuildingModules.STATS_MODULE)
                    .createBuildingEntry());

    /** Battlemage work modules: one battlemage per tower level. */
    public static final Map<MageSchool, BuildingEntry.ModuleProducer<GuardBuildingModule, CombinedHiringLimitModuleView>> BATTLEMAGE_WORK =
            new EnumMap<>(MageSchool.class);

    /**
     * Mage Towers, one per registered school. No {@code GUARD_TOOL} module: our equipment types replace the vanilla
     * guard tools.
     */
    public static final Map<MageSchool, DeferredHolder<BuildingEntry, BuildingEntry>> MAGE_TOWERS = new EnumMap<>(MageSchool.class);

    static {
        for (BattlemageSchools.Entry entry : BattlemageSchools.REGISTERED) {
            MageSchool school = entry.school();
            BuildingEntry.ModuleProducer<GuardBuildingModule, CombinedHiringLimitModuleView> work = new BuildingEntry.ModuleProducer<>(
                    school.workModuleKey(),
                    () -> new BattlemageBuildingModule(AKGuardTypes.BATTLEMAGES.get(school).get(), true, IBuilding::getBuildingLevel),
                    () -> CombinedHiringLimitModuleView::new);
            BATTLEMAGE_WORK.put(school, work);
            MAGE_TOWERS.put(school, BUILDINGS.register(school.hutId(), () -> new BuildingEntry.Builder()
                    .setBuildingBlock(AKBlocks.MAGE_TOWERS.get(school).get())
                    .setBuildingProducer((colony, pos) -> new BuildingMageTower(colony, pos, school))
                    .setBuildingViewProducer(() -> AbstractBuildingGuards.View::new)
                    .setRegistryName(ArsKingdom.id(school.hutId()))
                    .addBuildingModuleProducer(work)
                    .addBuildingModuleProducer(BuildingModules.GUARD_ENTITY_LIST)
                    .addBuildingModuleProducer(BuildingModules.GUARD_SETTINGS)
                    .addBuildingModuleProducer(BuildingModules.MIN_STOCK)
                    .addBuildingModuleProducer(BuildingModules.BED)
                    .addBuildingModuleProducer(BuildingModules.STATS_MODULE)
                    .createBuildingEntry()));
        }
    }

    private AKBuildings() {
    }
}
