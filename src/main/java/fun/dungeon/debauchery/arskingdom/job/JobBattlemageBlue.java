package fun.dungeon.debauchery.arskingdom.job;

import com.minecolonies.api.colony.ICitizenData;
import fun.dungeon.debauchery.arskingdom.battlemage.MageSchool;

/** Blue school battlemage (frost). */
public class JobBattlemageBlue extends JobBattlemage<JobBattlemageBlue> {
    public JobBattlemageBlue(ICitizenData citizen) {
        super(citizen);
    }

    @Override
    public MageSchool getSchool() {
        return MageSchool.BLUE;
    }
}
