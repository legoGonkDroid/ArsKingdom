package fun.dungeon.debauchery.arskingdom.job;

import com.minecolonies.api.client.render.modeltype.ModModelTypes;
import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import com.minecolonies.api.util.constant.CitizenConstants;
import com.minecolonies.core.colony.jobs.AbstractJobGuard;
import com.minecolonies.core.util.AttributeModifierUtils;
import fun.dungeon.debauchery.arskingdom.ai.EntityAIBattlemage;
import fun.dungeon.debauchery.arskingdom.battlemage.MageSchool;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;

/**
 * A battlemage of one school. Each school has its own one-line subclass because MineColonies' {@code GuardType}
 * identifies guards by job class.
 */
public abstract class JobBattlemage<J extends JobBattlemage<J>> extends AbstractJobGuard<J> {
    private static final String TAG_MANA = "ars_kingdom_mana";

    /** The battlemage's own mana (the player's Ars mana pool, for a citizen). Refilled from the tower's jars. */
    private double storedMana;

    protected JobBattlemage(ICitizenData citizen) {
        super(citizen);
    }

    public double getStoredMana() {
        return storedMana;
    }

    public void setStoredMana(double mana) {
        this.storedMana = Math.max(0, mana);
    }

    @Override
    public CompoundTag serializeNBT(@NotNull HolderLookup.Provider provider) {
        CompoundTag tag = super.serializeNBT(provider);
        tag.putDouble(TAG_MANA, storedMana);
        return tag;
    }

    @Override
    public void deserializeNBT(@NotNull HolderLookup.Provider provider, CompoundTag compound) {
        super.deserializeNBT(provider, compound);
        storedMana = compound.getDouble(TAG_MANA);
    }

    public abstract MageSchool getSchool();

    @SuppressWarnings("unchecked")
    @Override
    protected EntityAIBattlemage<J> generateGuardAI() {
        return new EntityAIBattlemage<>((J) this);
    }

    /** Health grows with Mana, as for the druid. */
    @Override
    public void onLevelUp() {
        getCitizen().getEntity().ifPresent(citizen -> {
            AttributeModifier health = new AttributeModifier(CitizenConstants.GUARD_HEALTH_MOD_LEVEL_NAME,
                    getCitizen().getCitizenSkillHandler().getLevel(getSchool().primarySkill()) / 2.0 + 12.0,
                    AttributeModifier.Operation.ADD_VALUE);
            AttributeModifierUtils.addHealthModifier(citizen, health);
        });
    }

    /** The druid's robed model: the Arcanist armor renders over it. */
    @Override
    public ResourceLocation getModel() {
        return ModModelTypes.DRUID_ID;
    }
}
