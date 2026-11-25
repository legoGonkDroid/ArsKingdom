package fun.dungeon.debauchery.arskingdom.registry;

import com.minecolonies.api.colony.guardtype.GuardType;
import com.minecolonies.apiimp.CommonMinecoloniesAPIImpl;
import fun.dungeon.debauchery.arskingdom.ArsKingdom;
import fun.dungeon.debauchery.arskingdom.battlemage.BattlemageSchools;
import fun.dungeon.debauchery.arskingdom.battlemage.MageSchool;
import java.util.EnumMap;
import java.util.Map;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Battlemage guard types, one per registered school: {@code ars_kingdom:battlemage_<school>}. */
public final class AKGuardTypes {
    public static final DeferredRegister<GuardType> GUARD_TYPES =
            DeferredRegister.create(CommonMinecoloniesAPIImpl.GUARD_TYPES, ArsKingdom.MODID);

    public static final Map<MageSchool, DeferredHolder<GuardType, GuardType>> BATTLEMAGES = new EnumMap<>(MageSchool.class);

    static {
        for (BattlemageSchools.Entry entry : BattlemageSchools.REGISTERED) {
            MageSchool school = entry.school();
            String id = school.guardTypeId();
            BATTLEMAGES.put(school, GUARD_TYPES.register(id, () -> new GuardType.Builder()
                    .setJobTranslationKey("com.ars_kingdom.job." + id)
                    .setButtonTranslationKey("com.ars_kingdom.gui.workerhuts." + id)
                    .setPrimarySkill(school.primarySkill())
                    .setSecondarySkill(school.secondarySkill())
                    .setWorkerSoundName("druid")
                    .setJobEntry(() -> AKJobs.BATTLEMAGES.get(school).get())
                    .setRegistryName(ArsKingdom.id(id))
                    .setClazz(entry.jobClass())
                    .createGuardType()));
        }
    }

    private AKGuardTypes() {
    }
}
