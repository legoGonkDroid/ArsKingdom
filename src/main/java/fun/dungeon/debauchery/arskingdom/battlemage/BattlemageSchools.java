package fun.dungeon.debauchery.arskingdom.battlemage;

import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.api.colony.jobs.IJob;
import fun.dungeon.debauchery.arskingdom.job.JobBattlemage;
import fun.dungeon.debauchery.arskingdom.job.JobBattlemageBlue;
import fun.dungeon.debauchery.arskingdom.job.JobBattlemageGreen;
import fun.dungeon.debauchery.arskingdom.job.JobBattlemagePurple;
import fun.dungeon.debauchery.arskingdom.job.JobBattlemageRed;
import java.util.List;
import java.util.function.Function;

/**
 * The schools that are registered (towers, jobs and guard types), each with its job class: one entry per school.
 */
public final class BattlemageSchools {
    public record Entry(MageSchool school, Class<? extends JobBattlemage<?>> jobClass, Function<ICitizenData, IJob<?>> jobFactory) {
    }

    public static final List<Entry> REGISTERED = List.of(
            new Entry(MageSchool.RED, JobBattlemageRed.class, JobBattlemageRed::new),
            new Entry(MageSchool.BLUE, JobBattlemageBlue.class, JobBattlemageBlue::new),
            new Entry(MageSchool.GREEN, JobBattlemageGreen.class, JobBattlemageGreen::new),
            new Entry(MageSchool.PURPLE, JobBattlemagePurple.class, JobBattlemagePurple::new));

    private BattlemageSchools() {
    }
}
