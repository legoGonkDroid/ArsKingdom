package fun.dungeon.debauchery.arskingdom.job;

import com.minecolonies.api.colony.ICitizenData;
import fun.dungeon.debauchery.arskingdom.battlemage.MageSchool;

/** Purple school battlemage (arcane). */
public class JobBattlemagePurple extends JobBattlemage<JobBattlemagePurple> {
    public JobBattlemagePurple(ICitizenData citizen) {
        super(citizen);
    }

    @Override
    public MageSchool getSchool() {
        return MageSchool.PURPLE;
    }
}
