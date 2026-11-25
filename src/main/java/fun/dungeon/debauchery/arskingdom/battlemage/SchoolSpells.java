package fun.dungeon.debauchery.arskingdom.battlemage;

import com.hollingsworth.arsnouveau.api.particle.timelines.ProjectileTimeline;
import com.hollingsworth.arsnouveau.api.particle.timelines.TimelineEntryData;
import com.hollingsworth.arsnouveau.api.particle.timelines.TimelineMap;
import com.hollingsworth.arsnouveau.api.registry.ParticleTimelineRegistry;
import com.hollingsworth.arsnouveau.api.spell.AbstractAugment;
import com.hollingsworth.arsnouveau.api.spell.AbstractCastMethod;
import com.hollingsworth.arsnouveau.api.spell.AbstractSpellPart;
import com.hollingsworth.arsnouveau.api.spell.Spell;
import com.hollingsworth.arsnouveau.client.particle.ParticleColor;
import com.hollingsworth.arsnouveau.common.spell.augment.AugmentAmplify;
import com.hollingsworth.arsnouveau.common.spell.augment.AugmentExtendTime;
import com.hollingsworth.arsnouveau.common.spell.effect.EffectCrush;
import com.hollingsworth.arsnouveau.common.spell.effect.EffectHarm;
import com.hollingsworth.arsnouveau.common.spell.effect.EffectIgnite;
import com.hollingsworth.arsnouveau.common.spell.effect.EffectFreeze;
import com.hollingsworth.arsnouveau.common.spell.effect.EffectKnockback;
import com.hollingsworth.arsnouveau.common.spell.method.MethodProjectile;
import fun.dungeon.debauchery.arskingdom.compat.ArsDecoBridge;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

/**
 * The battlemage spell of each school and tier (tier = the book tier the tower allows, 1..3). Each is a projectile
 * spell built only from glyphs that are enabled and at or below that tier, coloured in the school's dye: the spell's
 * default color and every stage of its projectile particle timeline use the dye's Ars Deco particle color, so spells
 * match the school's dyed blocks. No AOE: a battlemage never splashes citizens.
 *
 * <ul>
 *   <li>Red: Ignite + Harm (fire damage over time). Tier 2+: Extend Time on Ignite, Amplify on Harm.</li>
 *   <li>Blue: Freeze + Harm (slows enemies). Tier 2+: Extend Time on Freeze, Amplify on Harm. (Not Snare: it costs
 *   100 mana on its own.)</li>
 *   <li>Green: Knockback + Harm (crowd control). Tier 2+: Crush (a tier-2 glyph) first, Amplify on Harm; tier 3 also
 *   Amplify on Crush.</li>
 *   <li>Purple: Harm + Amplify (highest raw arcane damage), one more Amplify per tier.</li>
 * </ul>
 * Glyph configs are read when a spell is first built, so the cache is cleared on server start.
 */
public final class SchoolSpells {
    public static final int MAX_TIER = 3;

    private static final Map<MageSchool, Spell[]> CACHE = new EnumMap<>(MageSchool.class);

    private SchoolSpells() {
    }

    /** The school's spell for {@code tier} (clamped to 1..3); empty if Projectile itself isn't allowed. */
    public static synchronized Spell get(MageSchool school, int tier) {
        int t = Math.max(1, Math.min(MAX_TIER, tier));
        Spell[] spells = CACHE.computeIfAbsent(school, s -> new Spell[MAX_TIER]);
        if (spells[t - 1] == null) {
            spells[t - 1] = build(school, t);
        }
        return spells[t - 1];
    }

    /** Mana an emulated bolt costs (the fallback when no Ars spell can be cast). */
    public static final int BOLT_MANA = 10;

    /** Mana one battlemage attack needs at this tier: the spell's cost, or the emulated bolt's. */
    public static int attackCost(MageSchool school, int tier) {
        Spell spell = get(school, tier);
        return spell.isEmpty() ? BOLT_MANA : spell.getCost();
    }

    /** The full recipe before filtering by tier and enabled glyphs. */
    public static List<AbstractSpellPart> recipe(MageSchool school, int tier) {
        List<AbstractSpellPart> parts = new ArrayList<>();
        parts.add(MethodProjectile.INSTANCE);
        switch (school) {
            case RED -> {
                parts.add(EffectIgnite.INSTANCE);
                if (tier >= 2) {
                    parts.add(AugmentExtendTime.INSTANCE);
                }
                parts.add(EffectHarm.INSTANCE);
                amplify(parts, tier - 1);
            }
            case BLUE -> {
                parts.add(EffectFreeze.INSTANCE);
                if (tier >= 2) {
                    parts.add(AugmentExtendTime.INSTANCE);
                }
                parts.add(EffectHarm.INSTANCE);
                amplify(parts, tier - 1);
            }
            case GREEN -> {
                if (tier >= 2) {
                    parts.add(EffectCrush.INSTANCE);
                    amplify(parts, tier >= 3 ? 1 : 0);
                }
                parts.add(EffectKnockback.INSTANCE);
                parts.add(EffectHarm.INSTANCE);
                amplify(parts, tier - 1);
            }
            case PURPLE -> {
                parts.add(EffectHarm.INSTANCE);
                amplify(parts, tier);
            }
        }
        return parts;
    }

    private static void amplify(List<AbstractSpellPart> parts, int count) {
        for (int i = 0; i < count; i++) {
            parts.add(AugmentAmplify.INSTANCE);
        }
    }

    /** Whether a glyph may be used at this tier: enabled in its config and at or below the tier. */
    public static boolean allowed(AbstractSpellPart part, int tier) {
        return part.isEnabled() && part.getConfigTier().value <= tier;
    }

    /**
     * Drops glyphs that aren't allowed. An effect that is dropped takes its augments with it; without an allowed cast
     * method (Projectile) the spell is empty and the battlemage falls back to an emulated bolt.
     */
    public static List<AbstractSpellPart> filter(List<AbstractSpellPart> recipe, int tier) {
        List<AbstractSpellPart> out = new ArrayList<>();
        boolean skipping = false;
        for (AbstractSpellPart part : recipe) {
            if (part instanceof AbstractAugment) {
                if (!skipping && !out.isEmpty() && allowed(part, tier)) {
                    out.add(part);
                }
                continue;
            }
            skipping = !allowed(part, tier);
            if (part instanceof AbstractCastMethod && skipping) {
                return List.of();
            }
            if (!skipping) {
                out.add(part);
            }
        }
        return out.size() > 1 ? out : List.of();
    }

    public static ParticleColor color(MageSchool school) {
        return ArsDecoBridge.particleColor(school.dye());
    }

    private static Spell build(MageSchool school, int tier) {
        List<AbstractSpellPart> parts = filter(recipe(school, tier), tier);
        if (parts.isEmpty()) {
            return new Spell();
        }
        ParticleColor color = color(school);
        ProjectileTimeline timeline = new ProjectileTimeline();
        for (TimelineEntryData entry : List.of(timeline.onSpawnEffect, timeline.trailEffect, timeline.onResolvingEffect, timeline.flairEffect)) {
            if (entry != null && entry.particleOptions() != null) {
                entry.particleOptions().colorProp().particleColor = color;
            }
        }
        TimelineMap timelines = new TimelineMap().put(ParticleTimelineRegistry.PROJECTILE_TIMELINE.get(), timeline);
        return new Spell(parts).withName("Battlemage (" + school.id() + ", tier " + tier + ")").withColor(color).withTimeline(timelines);
    }

    /** Glyph configs may have changed: rebuild spells on next use. */
    public static synchronized void invalidate() {
        CACHE.clear();
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        invalidate();
    }
}
