package fun.dungeon.debauchery.arskingdom.job;

import com.minecolonies.api.colony.ICitizenData;
import fun.dungeon.debauchery.arskingdom.battlemage.MageSchool;

/** Green school battlemage (earth). */
public class JobBattlemageGreen extends JobBattlemage<JobBattlemageGreen> {
    public JobBattlemageGreen(ICitizenData citizen) {
        super(citizen);
    }

    @Override
    public MageSchool getSchool() {
        return MageSchool.GREEN;
    }
}
