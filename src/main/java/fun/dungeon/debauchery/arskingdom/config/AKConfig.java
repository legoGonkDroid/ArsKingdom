package fun.dungeon.debauchery.arskingdom.config;

import fun.dungeon.debauchery.arskingdom.colony.BuildingColors;
import fun.dungeon.debauchery.arskingdom.colony.ColonyDye;
import java.util.List;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Server config. The rule values are the defaults used until a colony has a Source Sanctum,
 * whose rules tab then overrides them per colony. Later chunks add the
 * raid chance and the other options listed in PLAN.md.
 */
public final class AKConfig {
    public static final ModConfigSpec SPEC;

    public static final ModConfigSpec.BooleanValue DYE_MACHINES;
    public static final ModConfigSpec.BooleanValue DYE_DECOR;
    public static final ModConfigSpec.BooleanValue OVERRIDE_CUSTOM_COLORS;
    public static final ModConfigSpec.BooleanValue UNDYE_ON_LEAVE;
    public static final ModConfigSpec.BooleanValue RECOLOR_TRANSFER_PARTICLES;
    public static final ModConfigSpec.BooleanValue RECOLOR_RITUALS;
    public static final ModConfigSpec.BooleanValue DYE_LIGHTS;
    public static final ModConfigSpec.BooleanValue WILDEN_RAIDS;
    public static final ModConfigSpec.BooleanValue SKIP_ARCHWOOD_DUPLICATES;
    public static final ModConfigSpec.DoubleValue KEEPER_EFFICIENCY;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> TEAM_COLOR_DYES;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> BUILDING_COLORS;
    public static final ModConfigSpec.IntValue SWEEP_CHUNKS_PER_TICK;
    public static final ModConfigSpec.IntValue BACKSTOP_SWEEP_MINUTES;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();

        b.comment("Default colony rules, used until a colony builds a Source Sanctum.").push("rules");
        DYE_MACHINES = b.comment("Dye functional Ars blocks (jars, relays, sourcelinks, braziers, turrets, apparatus) in the colony color.")
                .define("dye_machines", true);
        DYE_DECOR = b.comment("Dye Ars decor blocks (sourcestone, archwood, sconces, grates) in the colony color.")
                .define("dye_decor", true);
        OVERRIDE_CUSTOM_COLORS = b.comment("Also repaint blocks that a player dyed by hand.")
                .define("override_custom_colors", false);
        UNDYE_ON_LEAVE = b.comment("Remove the colony dye when a block leaves the colony or the colony is deleted.")
                .define("undye_on_leave", true);
        RECOLOR_TRANSFER_PARTICLES = b.comment("Source transfer particles from a dyed jar use the jar's dye color.")
                .define("recolor_transfer_particles", true);
        RECOLOR_RITUALS = b.comment("Ritual particles on a dyed brazier use the brazier's dye color.")
                .define("recolor_rituals", true);
        DYE_LIGHTS = b.comment("Give magelight torches, sconce flames and mage lights the colony color (Ars's own light color, not a tint).")
                .define("dye_lights", true);
        WILDEN_RAIDS = b.comment("Allow Wilden raids on colonies.")
                .define("wilden_raids", true);
        BUILDING_COLORS = b.comment("Building types that keep their own color inside their bounds instead of the colony color",
                        "(e.g. Mage Towers in their school's color). Each type has its own toggle in the Source Sanctum.",
                        "Types: ars_kingdom:mage_towers")
                .defineListAllowEmpty("building_colors", List.of("ars_kingdom:mage_towers"), () -> "ars_kingdom:mage_towers",
                        o -> o instanceof String str && str.contains(":"));
        b.pop();

        b.comment("Colony color: each MineColonies team color maps to one of the 16 dye colors.").push("colors");
        TEAM_COLOR_DYES = b.comment("Entries are TEAM_COLOR=dye. Use 'none' (or leave a color out) for no colony dye: Ars blocks keep their",
                        "native purple look. By default DARK_PURPLE is 'none', so WHITE dyes blocks white.",
                        "Team colors: BLACK, DARK_BLUE, DARK_GREEN, DARK_AQUA, DARK_RED, DARK_PURPLE, GOLD, GRAY,",
                        "DARK_GRAY, BLUE, GREEN, AQUA, RED, LIGHT_PURPLE, YELLOW, WHITE (MineColonies' default team color).")
                .defineListAllowEmpty("team_color_dyes", ColonyDye.DEFAULT_MAP, () -> "DARK_PURPLE=none",
                        o -> o instanceof String str && str.contains("="));
        b.pop();

        b.comment("Colony dye sweeps: re-checking every Ars block in a colony's chunks.",
                "Sweeps run after team color changes, finished builds, new claims, colony deletion and config changes,",
                "and when an out-of-date chunk loads.").push("sweeps");
        SWEEP_CHUNKS_PER_TICK = b.comment("How many chunks a sweep processes per server tick.")
                .defineInRange("sweep_chunks_per_tick", 2, 1, 64);
        BACKSTOP_SWEEP_MINUTES = b.comment("Every this many minutes, re-sweep all loaded colony chunks to catch anything missed",
                        "(e.g. decorations placed by builders, blocks changed by other mods). 0 disables it.")
                .defineInRange("backstop_sweep_minutes", 30, 0, 24 * 60);
        b.pop();

        b.comment("Source Sanctum keeper.").push("keeper");
        KEEPER_EFFICIENCY = b.comment("Multiplier on the source a keeper makes per fuel item.",
                        "Base: burn time / 12 (as Ars's Volcanic Sourcelink; coal = 133), x Sanctum level x (1 + Mana / 50).")
                .defineInRange("keeper_efficiency", 1.0, 0.0, 100.0);
        b.pop();

        b.push("crafting");
        SKIP_ARCHWOOD_DUPLICATES = b.comment("Don't import Ars machine recipes for items an Archwood Colonies crafter recipe already makes.")
                .define("skip_archwood_duplicates", true);
        b.pop();

        SPEC = b.build();
    }

    /** Whether a colored building type is on by default (no Sanctum, or a new Sanctum's starting value). */
    public static boolean buildingColorDefault(BuildingColors.Entry entry) {
        List<? extends String> ids = SPEC.isLoaded() ? BUILDING_COLORS.get() : BUILDING_COLORS.getDefault();
        return ids.contains(entry.id().toString());
    }

    private AKConfig() {
    }
}
