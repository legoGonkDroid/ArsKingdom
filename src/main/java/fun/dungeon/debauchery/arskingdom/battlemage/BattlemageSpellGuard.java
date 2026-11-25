package fun.dungeon.debauchery.arskingdom.battlemage;

import com.hollingsworth.arsnouveau.api.event.SpellResolveEvent;
import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import com.minecolonies.core.entity.ai.workers.guard.AbstractEntityAIGuard;
import fun.dungeon.debauchery.arskingdom.job.JobBattlemage;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.neoforged.bus.api.SubscribeEvent;

/**
 * Battlemage spells only land on enemies. A spell cast by a battlemage doesn't resolve on blocks (no fires from Ignite,
 * no walls crushed by Crush) or on citizens, players, villagers and pets that MineColonies doesn't count as attackers.
 */
public final class BattlemageSpellGuard {
    private BattlemageSpellGuard() {
    }

    @SubscribeEvent
    public static void onSpellResolve(SpellResolveEvent.Pre event) {
        if (!(event.shooter instanceof AbstractEntityCitizen citizen) || citizen.getCitizenData() == null
                || !(citizen.getCitizenData().getJob() instanceof JobBattlemage<?>)) {
            return;
        }
        if (event.rayTraceResult instanceof BlockHitResult) {
            event.setCanceled(true);
        } else if (event.rayTraceResult instanceof EntityHitResult hit && isFriendly(citizen, hit.getEntity())) {
            event.setCanceled(true);
        }
    }

    public static boolean isFriendly(AbstractEntityCitizen caster, Entity target) {
        boolean protectedKind = target instanceof AbstractEntityCitizen || target instanceof Player || target instanceof AbstractVillager
                || target instanceof OwnableEntity ownable && ownable.getOwnerUUID() != null;
        if (!protectedKind) {
            return false;
        }
        return !(target instanceof net.minecraft.world.entity.LivingEntity living) || !AbstractEntityAIGuard.isAttackableTarget(caster, living);
    }
}
