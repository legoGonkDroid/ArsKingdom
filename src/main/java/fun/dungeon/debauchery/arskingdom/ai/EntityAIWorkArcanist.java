package fun.dungeon.debauchery.arskingdom.ai;

import com.minecolonies.api.entity.ai.statemachine.states.IAIState;
import com.minecolonies.api.entity.citizen.VisibleCitizenStatus;
import com.minecolonies.core.entity.ai.workers.crafting.AbstractEntityAICrafting;
import fun.dungeon.debauchery.arskingdom.arcanist.ArcanistSource;
import fun.dungeon.debauchery.arskingdom.arcanist.ArcanistSourceCosts;
import fun.dungeon.debauchery.arskingdom.arcanist.ArcanistStatus;
import fun.dungeon.debauchery.arskingdom.building.BuildingArcanistWorkshop;
import fun.dungeon.debauchery.arskingdom.job.JobArcanist;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Tuple;

/**
 * MineColonies' crafter AI, plus source: before each craft the Arcanist pays the output's source cost
 * ({@link ArcanistSourceCosts}) from the jars inside the workshop. If they hold too little it waits, showing
 * "needs source", and tries again.
 */
public class EntityAIWorkArcanist extends AbstractEntityAICrafting<JobArcanist, BuildingArcanistWorkshop> {
    /** Ticks between source checks while waiting. */
    private static final int SOURCE_RETRY_DELAY = 100;

    /** Whether the craft in progress is paid for. */
    private boolean paid;

    public EntityAIWorkArcanist(JobArcanist job) {
        super(job);
    }

    @Override
    public Class<BuildingArcanistWorkshop> getExpectedBuildingClass() {
        return BuildingArcanistWorkshop.class;
    }

    @Override
    protected IAIState craft() {
        if (currentRecipeStorage != null && job.getProgress() == 0 && !paid) {
            int cost = ArcanistSourceCosts.cost(currentRecipeStorage.getPrimaryOutput());
            if (cost > 0) {
                Tuple<BlockPos, BlockPos> corners = building.getCorners();
                if (!ArcanistSource.take(world, corners.getA(), corners.getB(), cost)) {
                    worker.getCitizenData().setVisibleStatus(ArcanistStatus.NEEDS_SOURCE);
                    setDelay(SOURCE_RETRY_DELAY);
                    return getState();
                }
            }
            paid = true;
            worker.getCitizenData().setVisibleStatus(VisibleCitizenStatus.WORKING);
        }
        int before = job.getProgress();
        IAIState next = super.craft();
        if (before > 0 && job.getProgress() == 0) {
            paid = false;                       // that craft finished; the next one pays again
        }
        return next;
    }

    @Override
    public void resetValues() {
        super.resetValues();
        paid = false;
    }
}
