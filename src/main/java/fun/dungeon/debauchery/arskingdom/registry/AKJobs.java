package fun.dungeon.debauchery.arskingdom.registry;

import com.minecolonies.api.colony.jobs.registry.JobEntry;
import com.minecolonies.api.sounds.ModSoundEvents;
import com.minecolonies.apiimp.CommonMinecoloniesAPIImpl;
import com.minecolonies.core.colony.jobs.views.DefaultJobView;
import fun.dungeon.debauchery.arskingdom.ArsKingdom;
import fun.dungeon.debauchery.arskingdom.battlemage.BattlemageSchools;
import fun.dungeon.debauchery.arskingdom.battlemage.MageSchool;
import fun.dungeon.debauchery.arskingdom.job.JobSourceKeeper;
import java.util.EnumMap;
import java.util.Map;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * MineColonies jobs. Job paths must be unique across all mods: MineColonies' crafter keys drop the namespace.
 */
public final class AKJobs {
    public static final DeferredRegister<JobEntry> JOBS = DeferredRegister.create(CommonMinecoloniesAPIImpl.JOBS, ArsKingdom.MODID);

    public static final DeferredHolder<JobEntry, JobEntry> SOURCE_KEEPER = JOBS.register("sourcekeeper", () -> new JobEntry.Builder()
            .setJobProducer(JobSourceKeeper::new)
            .setJobViewProducer(() -> DefaultJobView::new)
            .setRegistryName(ArsKingdom.id("sourcekeeper"))
            .createJobEntry());

    /** One battlemage job per registered school: {@code ars_kingdom:battlemage_<school>}. */
    public static final Map<MageSchool, DeferredHolder<JobEntry, JobEntry>> BATTLEMAGES = new EnumMap<>(MageSchool.class);

    static {
        for (BattlemageSchools.Entry entry : BattlemageSchools.REGISTERED) {
            String id = entry.school().guardTypeId();
            BATTLEMAGES.put(entry.school(), JOBS.register(id, () -> new JobEntry.Builder()
                    .setJobProducer(entry.jobFactory()::apply)
                    .setJobViewProducer(() -> DefaultJobView::new)
                    .setRegistryName(ArsKingdom.id(id))
                    .createJobEntry()));
        }
    }

    /**
     * MineColonies keys citizen voice lines by job path in a map built only from its own jobs, and NPEs
     * (e.g. on a skill level-up) for any job missing from it. Give our jobs the Enchanter's voice set.
     */
    public static void registerCitizenSounds() {
        var sounds = ModSoundEvents.CITIZEN_SOUND_EVENTS;
        var voice = sounds.get("enchanter");
        if (voice == null) {
            return;
        }
        for (var job : JOBS.getEntries()) {
            sounds.putIfAbsent(job.getId().getPath(), voice);
        }
    }

    private AKJobs() {
    }
}
