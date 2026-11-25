package fun.dungeon.debauchery.arskingdom.building;

import com.ldtteam.structurize.blueprints.v1.Blueprint;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.core.colony.buildings.AbstractBuildingGuards;
import fun.dungeon.debauchery.arskingdom.battlemage.MageSchool;
import net.minecraft.core.BlockPos;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A Mage Tower of one school: houses that school's battlemages. Levels 1-3, and the tower level is also the highest
 * spellbook and Arcanist armor tier it allows.
 */
public class BuildingMageTower extends AbstractBuildingGuards {
    public static final int MAX_LEVEL = 3;
    /** Levels with a blueprint so far (the tower is being redesigned level by level); raise as blueprints land. */
    public static final int BLUEPRINT_LEVELS = 3;

    private final MageSchool school;

    public BuildingMageTower(@NotNull IColony colony, BlockPos pos, MageSchool school) {
        super(colony, pos);
        this.school = school;
    }

    public MageSchool getSchool() {
        return school;
    }

    @NotNull
    @Override
    public String getSchematicName() {
        return school.hutId();
    }

    @Override
    public int getMaxBuildingLevel() {
        return BLUEPRINT_LEVELS;
    }

    /** Exactly the tower level (the default allows anything at the top level). */
    @Override
    public int getMaxEquipmentLevel() {
        return Math.max(1, getBuildingLevel());
    }

    @Override
    public int getClaimRadius(int newLevel) {
        return switch (newLevel) {
            case 1 -> 2;
            case 2 -> 3;
            case 3 -> 4;
            default -> 0;
        };
    }

    @Override
    public void onDestroyed() {
        super.onDestroyed();
        colony.getServerBuildingManager().guardBuildingChangedAt(this, 0);
    }

    @Override
    public void onUpgradeComplete(@Nullable Blueprint blueprint, int newLevel) {
        super.onUpgradeComplete(blueprint, newLevel);
        colony.getServerBuildingManager().guardBuildingChangedAt(this, newLevel);
    }

    @Override
    public boolean requiresManualTarget() {
        return (patrolTargets == null || patrolTargets.isEmpty() || tempNextPatrolPoint != null || !shallPatrolManually())
                && tempNextPatrolPoint == null;
    }
}
