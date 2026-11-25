package fun.dungeon.debauchery.arskingdom.job;

import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.core.colony.jobs.AbstractJobCrafter;
import fun.dungeon.debauchery.arskingdom.ai.EntityAIWorkArcanist;

/** Arcanist (Arcanist Workshop): a crafter for Ars items no Archwood Colonies crafter makes, paid partly in source. */
public class JobArcanist extends AbstractJobCrafter<EntityAIWorkArcanist, JobArcanist> {
    public JobArcanist(ICitizenData citizen) {
        super(citizen);
    }

    @Override
    public EntityAIWorkArcanist generateAI() {
        return new EntityAIWorkArcanist(this);
    }
}
