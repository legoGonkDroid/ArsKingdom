package fun.dungeon.debauchery.arskingdom.gametest;

import com.hollingsworth.arsnouveau.api.particle.timelines.ProjectileTimeline;
import com.hollingsworth.arsnouveau.api.particle.timelines.TimelineEntryData;
import com.hollingsworth.arsnouveau.api.registry.ParticleTimelineRegistry;
import com.hollingsworth.arsnouveau.api.spell.AbstractAugment;
import com.hollingsworth.arsnouveau.api.spell.AbstractSpellPart;
import com.hollingsworth.arsnouveau.api.spell.Spell;
import com.hollingsworth.arsnouveau.api.spell.SpellContext;
import com.hollingsworth.arsnouveau.api.spell.SpellResolver;
import com.hollingsworth.arsnouveau.common.entity.EntityProjectileSpell;
import com.hollingsworth.arsnouveau.common.spell.augment.AugmentAmplify;
import com.hollingsworth.arsnouveau.common.spell.effect.EffectHarm;
import com.hollingsworth.arsnouveau.common.spell.method.MethodProjectile;
import fun.dungeon.debauchery.arskingdom.ArsKingdom;
import fun.dungeon.debauchery.arskingdom.battlemage.BattlemageCaster;
import fun.dungeon.debauchery.arskingdom.battlemage.MageSchool;
import fun.dungeon.debauchery.arskingdom.battlemage.SchoolSpells;
import fun.dungeon.debauchery.arskingdom.job.JobBattlemageRed;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Chunk 9: battlemage spells per school and tier, and casting from the battlemage's own mana. */
@GameTestHolder(ArsKingdom.MODID)
@PrefixGameTestTemplate(false)
public final class SchoolSpellTests {
    private static final String EMPTY = "gametest/empty";

    private SchoolSpellTests() {
    }

    /** Each school/tier: a projectile spell of allowed glyphs only, every timeline color the school's. */
    @GameTest(template = EMPTY)
    public static void spellsPerSchoolAndTier(GameTestHelper helper) {
        for (MageSchool school : MageSchool.values()) {
            int expected = SchoolSpells.color(school).getColor();
            for (int tier = 1; tier <= SchoolSpells.MAX_TIER; tier++) {
                Spell spell = SchoolSpells.get(school, tier);
                String what = school.id() + " tier " + tier;
                helper.assertTrue(!spell.isEmpty(), what + ": spell should not be empty (Projectile and Harm are tier 1)");
                helper.assertTrue(spell.get(0) == MethodProjectile.INSTANCE, what + ": cast by projectile");
                helper.assertTrue(spell.getInstanceCount(EffectHarm.INSTANCE) == 1, what + ": deals Harm");
                for (AbstractSpellPart part : spell.recipe()) {
                    helper.assertTrue(SchoolSpells.allowed(part, tier), what + ": " + part.getRegistryName() + " not allowed at this tier");
                }
                helper.assertTrue(spell.isValid(), what + ": Ars should accept the spell");
                ProjectileTimeline timeline = spell.particleTimeline().get(ParticleTimelineRegistry.PROJECTILE_TIMELINE.get());
                helper.assertTrue(timeline != null, what + ": has a projectile timeline");
                for (TimelineEntryData entry : List.of(timeline.onSpawnEffect, timeline.trailEffect, timeline.onResolvingEffect, timeline.flairEffect)) {
                    helper.assertTrue(entry.particleOptions().colorProp().particleColor.getColor() == expected, what + ": timeline color");
                }
                helper.assertTrue(SchoolSpells.get(school, tier) == spell, what + ": cached");
            }
            int amp1 = SchoolSpells.get(school, 1).getInstanceCount(AugmentAmplify.INSTANCE);
            int amp3 = SchoolSpells.get(school, 3).getInstanceCount(AugmentAmplify.INSTANCE);
            helper.assertTrue(amp3 > amp1, school.id() + ": tier 3 should be stronger than tier 1 (" + amp1 + " → " + amp3 + " amplify)");
        }
        helper.succeed();
    }

    /** Filtering: augments above the tier go, a cast method above the tier empties the spell. */
    @GameTest(template = EMPTY)
    public static void glyphFilter(GameTestHelper helper) {
        List<AbstractSpellPart> recipe = SchoolSpells.recipe(MageSchool.PURPLE, 3);
        List<AbstractSpellPart> tier1 = SchoolSpells.filter(recipe, 1);
        helper.assertTrue(tier1.get(0) == MethodProjectile.INSTANCE && tier1.contains(EffectHarm.INSTANCE), "tier 1 keeps projectile + harm");
        for (AbstractSpellPart part : tier1) {
            helper.assertTrue(SchoolSpells.allowed(part, 1), part.getRegistryName() + " not allowed at tier 1");
        }
        helper.assertTrue(SchoolSpells.filter(List.of(AugmentAmplify.INSTANCE, EffectHarm.INSTANCE), 3).isEmpty() ||
                !(SchoolSpells.filter(List.of(AugmentAmplify.INSTANCE, EffectHarm.INSTANCE), 3).get(0) instanceof AbstractAugment),
                "a leading augment has nothing to augment");
        helper.assertTrue(SchoolSpells.filter(List.of(MethodProjectile.INSTANCE), 3).isEmpty(), "a method alone is no spell");
        helper.succeed();
    }

    /** A cast through BattlemageCaster spends the battlemage's mana and launches an Ars projectile. */
    @GameTest(template = EMPTY, timeoutTicks = 100)
    public static void castSpendsBattlemageMana(GameTestHelper helper) {
        Zombie caster = helper.spawn(EntityType.ZOMBIE, new BlockPos(2, 2, 2));
        caster.setNoAi(true);
        JobBattlemageRed job = new JobBattlemageRed(null);
        Spell spell = SchoolSpells.get(MageSchool.RED, 1);

        job.setStoredMana(0);
        SpellContext broke = new SpellContext(caster.level(), spell, caster, new BattlemageCaster(caster, job), ItemStack.EMPTY);
        helper.assertTrue(!new SpellResolver(broke).withSilent(true).onCast(ItemStack.EMPTY, caster.level()), "no mana, no cast");

        job.setStoredMana(1000);
        SpellContext context = new SpellContext(caster.level(), spell, caster, new BattlemageCaster(caster, job), ItemStack.EMPTY);
        SpellResolver resolver = new SpellResolver(context).withSilent(true);
        boolean cast = resolver.onCast(ItemStack.EMPTY, caster.level());
        helper.assertTrue(cast, "with mana the spell casts");
        helper.assertTrue(job.getStoredMana() < 1000, "the cast spent the battlemage's mana: " + job.getStoredMana());
        AABB around = new AABB(helper.absolutePos(BlockPos.ZERO)).inflate(16);
        helper.assertTrue(!helper.getLevel().getEntitiesOfClass(EntityProjectileSpell.class, around).isEmpty(), "an Ars projectile was launched");
        helper.getLevel().getEntitiesOfClass(EntityProjectileSpell.class, around).forEach(e -> e.discard());
        helper.succeed();
    }
}
