package fun.dungeon.debauchery.arskingdom.building.modules;

import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.colony.guardtype.GuardType;
import com.minecolonies.core.colony.buildings.modules.GuardBuildingModule;
import com.minecolonies.core.util.BuildingUtils;
import java.util.function.Function;
import org.jetbrains.annotations.NotNull;

/**
 * A guard work module for a tower that has only one guard type. MineColonies' {@code GuardBuildingModule} only
 * auto-hires when {@code random.nextInt(<guard modules>) != 0}, which is never true with one module, so the plain
 * worker-module hiring is done here instead.
 */
public class BattlemageBuildingModule extends GuardBuildingModule {
    public BattlemageBuildingModule(GuardType type, boolean canWorkingDuringRain, Function<IBuilding, Integer> sizeLimit) {
        super(type, canWorkingDuringRain, sizeLimit);
    }

    @Override
    public void onColonyTick(@NotNull IColony colony) {
        if (!isFull() && BuildingUtils.canAutoHire(building, getHiringMode(), getJobEntry())) {
            ICitizenData jobless = colony.getCitizenManager().getJoblessCitizen();
            if (jobless != null) {
                assignCitizen(jobless);
            }
        }
    }
}
