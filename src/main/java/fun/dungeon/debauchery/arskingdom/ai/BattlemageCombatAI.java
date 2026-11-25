package fun.dungeon.debauchery.arskingdom.ai;

import com.hollingsworth.arsnouveau.api.spell.Spell;
import com.hollingsworth.arsnouveau.api.spell.SpellContext;
import com.hollingsworth.arsnouveau.api.spell.SpellResolver;
import com.hollingsworth.arsnouveau.api.particle.PropertyParticleOptions;
import com.minecolonies.api.entity.ai.combat.CombatAIStates;
import com.minecolonies.api.entity.ai.combat.threat.IThreatTableEntity;
import com.minecolonies.api.entity.ai.statemachine.tickratestatemachine.ITickRateStateMachine;
import com.minecolonies.api.entity.ai.statemachine.tickratestatemachine.TickingTransition;
import com.minecolonies.api.entity.citizen.Skill;
import com.minecolonies.api.util.BlockPosUtil;
import com.minecolonies.core.colony.buildings.AbstractBuildingGuards;
import com.minecolonies.core.colony.jobs.AbstractJobGuard;
import com.minecolonies.core.entity.ai.BehaviourStateGroup;
import com.minecolonies.core.entity.ai.combat.AttackMoveAI;
import com.minecolonies.core.entity.ai.workers.guard.AbstractEntityAIGuard;
import com.minecolonies.core.entity.citizen.EntityCitizen;
import com.minecolonies.core.entity.pathfinding.PathfindingUtils;
import com.minecolonies.core.entity.pathfinding.PathingOptions;
import com.minecolonies.core.entity.pathfinding.navigation.EntityNavigationUtils;
import com.minecolonies.core.entity.pathfinding.navigation.MinecoloniesAdvancedPathNavigate;
import com.minecolonies.core.entity.pathfinding.pathjobs.PathJobCanSee;
import com.minecolonies.core.entity.pathfinding.pathjobs.PathJobMoveAwayFromLocation;
import com.minecolonies.core.entity.pathfinding.pathjobs.PathJobMoveToLocation;
import com.minecolonies.core.entity.pathfinding.pathresults.PathResult;
import fun.dungeon.debauchery.arskingdom.battlemage.BattlemageCaster;
import fun.dungeon.debauchery.arskingdom.battlemage.BattlemageEquipment;
import fun.dungeon.debauchery.arskingdom.battlemage.BattlemageGear;
import fun.dungeon.debauchery.arskingdom.battlemage.MageSchool;
import fun.dungeon.debauchery.arskingdom.battlemage.SchoolSpells;
import fun.dungeon.debauchery.arskingdom.job.JobBattlemage;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * Battlemage combat: a ranged guard that casts its school's spell ({@link SchoolSpells}) at enemies, through Ars's own
 * cast pipeline ({@link SpellResolver#onCast}) with the battlemage as caster and its own mana pool. Targeting, movement
 * and persecution follow MineColonies' druid and ranger AIs. If the spell can't be cast (e.g. its glyphs are disabled
 * in Ars's config), an emulated bolt in the school's color is fired instead.
 */
public class BattlemageCombatAI extends AttackMoveAI<EntityCitizen> {
    private static final double XP_PER_CAST = 0.05;

    private final EntityAIBattlemage<?> parentAI;
    private final JobBattlemage<?> job;
    private final PathingOptions combatPathingOptions = new PathingOptions();

    public BattlemageCombatAI(EntityCitizen owner, ITickRateStateMachine stateMachine, EntityAIBattlemage<?> parentAI, JobBattlemage<?> job) {
        super(owner, stateMachine);
        stateMachine.addTransitionGroup(BehaviourStateGroup.GUARD_ABORT_AND_FIGHT,
                new TickingTransition<>(this::checkForTarget, () -> CombatAIStates.ATTACKING, 5).withName("busy_checkTarget"));
        stateMachine.addTransitionGroup(BehaviourStateGroup.GUARD_ABORT_AND_FIGHT,
                new TickingTransition<>(this::searchNearbyTarget, () -> CombatAIStates.ATTACKING, 80).withName("busy_searchTarget"));
        this.parentAI = parentAI;
        this.job = job;
        combatPathingOptions.setEnterDoors(true);
        combatPathingOptions.setEnterGates(true);
        combatPathingOptions.setCanOpenDoors(true);
        combatPathingOptions.setCanSwim(true);
        combatPathingOptions.withOnPathCost(0.8);
        combatPathingOptions.withJumpCost(0.01);
        combatPathingOptions.withDropCost(1.5);
    }

    private MageSchool school() {
        return job.getSchool();
    }

    /** The spell tier: the held book's tier, capped by the tower. */
    public int tier() {
        int book = BattlemageGear.spellbookLevel(user.getMainHandItem());
        int max = user.getCitizenData().getWorkBuilding() == null ? 1 : user.getCitizenData().getWorkBuilding().getMaxEquipmentLevel();
        return Math.max(1, Math.min(book, max));
    }

    /** Mana one attack needs: the spell's cost, or the emulated bolt's. */
    public int attackCost() {
        return SchoolSpells.attackCost(school(), tier());
    }

    /** Fully equipped and with enough mana for one attack. */
    @Override
    public boolean canAttack() {
        var building = user.getCitizenData().getWorkBuilding();
        return building != null && BattlemageEquipment.isFullyEquipped(user.getInventoryCitizen(), building.getMaxEquipmentLevel())
                && job.getStoredMana() >= attackCost();
    }

    @Override
    protected void doAttack(LivingEntity target) {
        if (user.distanceToSqr(target) < 7.0) {
            if (user.getRandom().nextInt(3) == 0 && !guardBuilding().getTask().equals("com.minecolonies.core.guard.setting.guard")) {
                EntityNavigationUtils.walkAwayFrom(user, target.blockPosition(), (int) (getAttackDistance() / 2.0), getCombatMovementSpeed());
            }
        } else {
            user.getNavigation().stop();
        }
        faceTarget(target);
        user.swing(InteractionHand.MAIN_HAND);
        Spell spell = SchoolSpells.get(school(), tier());
        boolean cast = false;
        if (!spell.isEmpty()) {
            SpellContext context = new SpellContext(user.level(), spell, user, new BattlemageCaster(user, job), user.getMainHandItem());
            cast = new SpellResolver(context).withSilent(true).onCast(user.getMainHandItem(), user.level());
        }
        if (!cast) {
            emulatedBolt(target);
        }
        target.setLastHurtByMob(user);
        user.getCitizenExperienceHandler().addExperience(XP_PER_CAST);
    }

    /** Points the battlemage straight at the target's middle: Ars launches the projectile along its look. */
    private void faceTarget(LivingEntity target) {
        Vec3 from = user.getEyePosition();
        Vec3 to = target.position().add(0, target.getBbHeight() * 0.5, 0);
        Vec3 d = to.subtract(from);
        float yRot = (float) (Mth.atan2(d.z, d.x) * Mth.RAD_TO_DEG) - 90f;
        float xRot = (float) -(Mth.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)) * Mth.RAD_TO_DEG);
        user.setYRot(yRot);
        user.setXRot(xRot);
        user.setYHeadRot(yRot);
        user.setYBodyRot(yRot);
        user.yRotO = yRot;
        user.xRotO = xRot;
    }

    /** Fallback: school-coloured Ars glow particles to the target, magic damage and the school's effect. */
    private void emulatedBolt(LivingEntity target) {
        if (job.getStoredMana() < SchoolSpells.BOLT_MANA || !(user.level() instanceof ServerLevel level)) {
            return;
        }
        job.setStoredMana(job.getStoredMana() - SchoolSpells.BOLT_MANA);
        Vec3 from = user.getEyePosition();
        Vec3 to = target.position().add(0, target.getBbHeight() * 0.5, 0);
        Vec3 step = to.subtract(from);
        int points = Math.max(2, (int) (step.length() * 3));
        PropertyParticleOptions particle = new PropertyParticleOptions();
        particle.colorProp().particleColor = SchoolSpells.color(school());
        for (int i = 0; i <= points; i++) {
            Vec3 p = from.add(step.scale(i / (double) points));
            level.sendParticles(particle, p.x, p.y, p.z, 1, 0, 0, 0, 0);
        }
        int tier = tier();
        float damage = 4.0f + 2.0f * tier + (school() == MageSchool.PURPLE ? 2.0f : 0f);
        target.hurt(level.damageSources().indirectMagic(user, user), damage);
        switch (school()) {
            case RED -> target.igniteForSeconds(2 + tier);
            case BLUE -> target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40 + 20 * tier, 1), user);
            case GREEN -> target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 40 + 20 * tier, 0), user);
            case PURPLE -> {
            }
        }
    }

    private AbstractBuildingGuards guardBuilding() {
        return (AbstractBuildingGuards) user.getCitizenData().getWorkBuilding();
    }

    /** Focus shortens the pause between casts (60 → 30 ticks). */
    @Override
    protected int getAttackDelay() {
        int focus = user.getCitizenData().getCitizenSkillHandler().getLevel(school().secondarySkill());
        return Math.max(30, 60 - focus / 2);
    }

    /** Like the ranger: 10 + tower level, more with Mana, more when holding a position. */
    @Override
    protected double getAttackDistance() {
        int dist = 10;
        if (user.getCitizenData().getWorkBuilding() != null) {
            dist += user.getCitizenData().getWorkBuilding().getBuildingLevelEquivalent();
        }
        dist += (int) (user.getCitizenData().getCitizenSkillHandler().getLevel(Skill.Mana) / 50.0f * 10);
        dist = Math.min(dist, 22);
        if (target != null) {
            dist += (int) (user.getY() - target.getY());
        }
        if (guardBuilding().getTask().equals("com.minecolonies.core.guard.setting.guard")) {
            dist += 6;
        }
        return dist;
    }

    @Override
    protected PathResult moveInAttackPosition(LivingEntity target) {
        PathResult result;
        if (BlockPosUtil.getDistanceSquared(target.blockPosition(), user.blockPosition()) <= 4.0) {
            PathJobMoveAwayFromLocation job = new PathJobMoveAwayFromLocation(user.level(), PathfindingUtils.prepareStart(target),
                    target.blockPosition(), 7, (int) user.getAttribute(Attributes.FOLLOW_RANGE).getValue(), user);
            result = ((MinecoloniesAdvancedPathNavigate) user.getNavigation()).setPathJob(job, null, getCombatMovementSpeed(), true);
            job.setPathingOptions(combatPathingOptions);
        } else if (BlockPosUtil.getDistance2D(target.blockPosition(), user.blockPosition()) >= 20L) {
            PathJobMoveToLocation job = new PathJobMoveToLocation(user.level(), PathfindingUtils.prepareStart(user), target.blockPosition(), 200, user);
            result = ((MinecoloniesAdvancedPathNavigate) user.getNavigation()).setPathJob(job, null, getCombatMovementSpeed(), true);
            job.setPathingOptions(combatPathingOptions);
        } else {
            PathJobCanSee job = new PathJobCanSee(user, target, user.level(), guardBuilding().getGuardPos(user), 40);
            result = ((MinecoloniesAdvancedPathNavigate) user.getNavigation()).setPathJob(job, null, getCombatMovementSpeed(), true);
            job.setPathingOptions(combatPathingOptions);
        }
        return result;
    }

    protected double getCombatMovementSpeed() {
        double adjust = user.getCitizenData().getCitizenSkillHandler().getLevel(Skill.Mana) * 0.01;
        adjust += (user.getCitizenData().getWorkBuilding().getBuildingLevelEquivalent() - 1) * 0.01;
        return 1.0 + Math.min(adjust, 0.3);
    }

    @Override
    protected boolean isAttackableTarget(LivingEntity entity) {
        return AbstractEntityAIGuard.isAttackableTarget(user, entity)
                || entity instanceof IThreatTableEntity threat && threat.getThreatTable().getTarget() != null
                || entity instanceof Player && entity.getLastHurtByMobTimestamp() != 0 && entity.tickCount > entity.getLastHurtByMobTimestamp()
                        && entity.tickCount - entity.getLastHurtByMobTimestamp() < 600;
    }

    @Override
    protected boolean isWithinPersecutionDistance(LivingEntity target) {
        return parentAI.isWithinPersecutionDistance(target.blockPosition(), getAttackDistance());
    }

    @Override
    protected boolean skipSearch(LivingEntity entity) {
        if (entity instanceof EntityCitizen citizen && user.getRandom().nextInt(10) < 1
                && citizen.getCitizenJobHandler().getColonyJob() instanceof AbstractJobGuard<?> guard && guard.isAsleep()
                && user.getSensing().hasLineOfSight(citizen)) {
            parentAI.setWakeCitizen(citizen);
            return true;
        }
        return false;
    }

    @Override
    protected int getYSearchRange() {
        return guardBuilding().getTask().equals("com.minecolonies.core.guard.setting.guard") ? 28 : 3;
    }

    @Override
    protected void onTargetDied(LivingEntity entity) {
        parentAI.incrementActionsDone();
        user.getCitizenExperienceHandler().addExperience(15.0);
        user.decreaseSaturationForContinuousAction();
    }
}
