package fun.dungeon.debauchery.arskingdom.job;

import com.minecolonies.api.colony.ICitizenData;
import fun.dungeon.debauchery.arskingdom.battlemage.MageSchool;

/** Red school battlemage (fire). */
public class JobBattlemageRed extends JobBattlemage<JobBattlemageRed> {
    public JobBattlemageRed(ICitizenData citizen) {
        super(citizen);
    }

    @Override
    public MageSchool getSchool() {
        return MageSchool.RED;
    }
}
