package fun.dungeon.debauchery.arskingdom.job;

import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.core.colony.jobs.AbstractJob;
import fun.dungeon.debauchery.arskingdom.ai.EntityAIWorkSourceKeeper;

/** Source keeper (Source Sanctum): burns fuel into source for the jars inside the Sanctum. */
public class JobSourceKeeper extends AbstractJob<EntityAIWorkSourceKeeper, JobSourceKeeper> {
    public JobSourceKeeper(ICitizenData citizen) {
        super(citizen);
    }

    @Override
    public EntityAIWorkSourceKeeper generateAI() {
        return new EntityAIWorkSourceKeeper(this);
    }
}
