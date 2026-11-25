package fun.dungeon.debauchery.arskingdom.colony;

import com.minecolonies.api.colony.IColony;
import fun.dungeon.debauchery.arskingdom.ArsKingdom;
import fun.dungeon.debauchery.arskingdom.config.AKConfig;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.Level;

/** Colony color: the MineColonies team color mapped to one of the 16 dye colors. */
public final class ColonyDye {
    /** Value in the config map meaning "this team color doesn't dye anything". */
    public static final String UNSET = "none";

    /**
     * Default team color → dye table (see PLAN.md, M1). RED→PINK keeps RED and DARK_RED apart.
     * DARK_PURPLE means "undyed": Ars blocks keep their native purple look, so every other team color,
     * WHITE included, gets a real dye.
     */
    public static final List<String> DEFAULT_MAP = List.of(
            "BLACK=BLACK", "DARK_BLUE=BLUE", "DARK_GREEN=GREEN", "DARK_AQUA=CYAN",
            "DARK_RED=RED", "DARK_PURPLE=" + UNSET, "GOLD=ORANGE", "GRAY=LIGHT_GRAY",
            "DARK_GRAY=GRAY", "BLUE=LIGHT_BLUE", "GREEN=LIME", "AQUA=LIGHT_BLUE",
            "RED=PINK", "LIGHT_PURPLE=MAGENTA", "YELLOW=YELLOW", "WHITE=WHITE");

    /** The table shipped before DARK_PURPLE became "undyed"; configs still holding it are migrated. */
    static final List<String> OLD_DEFAULT_MAP = List.of(
            "BLACK=BLACK", "DARK_BLUE=BLUE", "DARK_GREEN=GREEN", "DARK_AQUA=CYAN",
            "DARK_RED=RED", "DARK_PURPLE=PURPLE", "GOLD=ORANGE", "GRAY=LIGHT_GRAY",
            "DARK_GRAY=GRAY", "BLUE=LIGHT_BLUE", "GREEN=LIME", "AQUA=LIGHT_BLUE",
            "RED=PINK", "LIGHT_PURPLE=MAGENTA", "YELLOW=YELLOW", "WHITE=" + UNSET);

    private static volatile Map<ChatFormatting, DyeColor> map = parse(DEFAULT_MAP);

    private ColonyDye() {
    }

    /** Re-reads the map from the config. Called on config load and reload. */
    public static void reload() {
        if (OLD_DEFAULT_MAP.equals(AKConfig.TEAM_COLOR_DYES.get())) {
            ArsKingdom.LOGGER.info("Migrating team_color_dyes to the new default (DARK_PURPLE = undyed, WHITE = white)");
            AKConfig.TEAM_COLOR_DYES.set(DEFAULT_MAP);
            AKConfig.SPEC.save();
        }
        map = parse(AKConfig.TEAM_COLOR_DYES.get());
    }

    /**
     * Parses {@code TEAM_COLOR=DYE} entries. Unknown names are logged and skipped; {@code none} (or a
     * missing entry) leaves that team color unset.
     */
    public static Map<ChatFormatting, DyeColor> parse(List<? extends String> entries) {
        Map<ChatFormatting, DyeColor> result = new EnumMap<>(ChatFormatting.class);
        for (String entry : entries) {
            int eq = entry.indexOf('=');
            if (eq < 0) {
                ArsKingdom.LOGGER.warn("Ignoring team color entry without '=': {}", entry);
                continue;
            }
            String team = entry.substring(0, eq).trim().toUpperCase(Locale.ROOT);
            String dye = entry.substring(eq + 1).trim().toLowerCase(Locale.ROOT);
            ChatFormatting color = ChatFormatting.getByName(team);
            if (color == null || !color.isColor()) {
                ArsKingdom.LOGGER.warn("Ignoring unknown team color '{}' in {}", team, entry);
                continue;
            }
            if (dye.equals(UNSET)) {
                result.remove(color);
                continue;
            }
            DyeColor dyeColor = DyeColor.byName(dye, null);
            if (dyeColor == null) {
                ArsKingdom.LOGGER.warn("Ignoring unknown dye '{}' in {}", dye, entry);
                continue;
            }
            result.put(color, dyeColor);
        }
        return Collections.unmodifiableMap(result);
    }

    public static Optional<DyeColor> dyeFor(ChatFormatting teamColor) {
        return Optional.ofNullable(teamColor == null ? null : map.get(teamColor));
    }

    /** The colony's dye, or empty when its team color means "undyed" (DARK_PURPLE by default). */
    public static Optional<DyeColor> colonyDye(IColony colony) {
        return colony == null ? Optional.empty() : dyeFor(colony.getTeamColonyColor());
    }

    /**
     * The dye a target block at {@code pos} should get: inside a colored building whose type is switched on (e.g. a
     * Mage Tower, see {@link BuildingColors}) that building's color, else the owning colony's dye.
     */
    public static Optional<DyeColor> zoneDye(Level level, BlockPos pos) {
        IColony colony = ColonyLookup.colony(level, pos);
        Optional<BuildingColors.Zone> zone = BuildingColors.zoneAt(colony, pos);
        return zone.isPresent() ? Optional.of(zone.get().dye()) : colonyDye(colony);
    }
}
