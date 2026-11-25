package fun.dungeon.debauchery.arskingdom.battlemage;

import com.hollingsworth.arsnouveau.api.item.inv.FilterableItemHandler;
import com.hollingsworth.arsnouveau.api.spell.SpellContext;
import com.hollingsworth.arsnouveau.api.spell.wrapped_caster.IWrappedCaster;
import com.hollingsworth.arsnouveau.api.spell.wrapped_caster.LivingCaster;
import fun.dungeon.debauchery.arskingdom.job.JobBattlemage;
import java.util.List;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * A battlemage as an Ars caster: Ars's {@link LivingCaster} for position, facing and inventory, but mana comes from
 * the battlemage's own pool ({@link JobBattlemage#getStoredMana()}); {@code LivingCaster} reads the entity's mana
 * capability, which citizens don't have.
 */
public class BattlemageCaster implements IWrappedCaster {
    private final LivingCaster living;
    private final JobBattlemage<?> job;

    public BattlemageCaster(LivingEntity citizen, JobBattlemage<?> job) {
        this.living = LivingCaster.from(citizen);
        this.job = job;
    }

    @Override
    public SpellContext.CasterType getCasterType() {
        return living.getCasterType();
    }

    @Override
    public List<FilterableItemHandler> getInventory() {
        return living.getInventory();
    }

    @Override
    public Direction getFacingDirection() {
        return living.getFacingDirection();
    }

    @Override
    public Vec3 getPosition() {
        return living.getPosition();
    }

    @Override
    public boolean enoughMana(int totalCost) {
        return job.getStoredMana() >= totalCost;
    }

    @Override
    public void expendMana(int totalCost) {
        job.setStoredMana(job.getStoredMana() - totalCost);
    }
}
