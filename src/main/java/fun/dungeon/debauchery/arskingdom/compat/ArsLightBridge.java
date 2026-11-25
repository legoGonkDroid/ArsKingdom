package fun.dungeon.debauchery.arskingdom.compat;

import com.hollingsworth.arsnouveau.api.particle.configurations.properties.ColorProperty;
import com.hollingsworth.arsnouveau.api.particle.timelines.LightTimeline;
import com.hollingsworth.arsnouveau.client.particle.ParticleColor;
import com.hollingsworth.arsnouveau.common.block.tile.LightTile;
import com.hollingsworth.arsnouveau.common.block.tile.SconceTile;
import com.hollingsworth.arsnouveau.common.block.tile.TempLightTile;
import java.util.Optional;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * Native Ars Nouveau light colors (M1d): magelight torches and sconces ({@link SconceTile}) and spell-placed
 * mage lights ({@link LightTile}). Their flame is drawn from a saved particle timeline, so a color is set the
 * way Ars itself builds one on load: write {@code color}, drop {@code timeline}, reload, and Ars rebuilds the
 * timeline from the color. Then {@code setColor} syncs it to clients. No tinting: this is Ars's own color.
 */
public final class ArsLightBridge {
    private ArsLightBridge() {
    }

    /** Lights we color. Temporary lights are skipped; they expire on their own. */
    public static boolean isLight(BlockEntity be) {
        return be instanceof SconceTile || (be instanceof LightTile && !(be instanceof TempLightTile));
    }

    /**
     * The color the light actually draws, as RGB, or empty while that is still Ars's default (uncolored) purple.
     * This reads the flame's particle color (the timeline's particle-type color), not the tile's {@code color}
     * field: blueprints such as Archwood Collective's save {@code color} as white while the flame is the default.
     */
    public static Optional<Integer> color(BlockEntity be) {
        ParticleColor color = displayedColor(be);
        if (color == null || color.getColor() == defaultRgb()) {
            return Optional.empty();
        }
        return Optional.of(color.getColor());
    }

    /** The RGB a light gets for a colony dye: the particle color Ars Deco gives blocks of that dye. */
    public static int rgbFor(DyeColor dye) {
        return ArsDecoBridge.particleColor(dye).getColor();
    }

    public static int defaultRgb() {
        return ParticleColor.defaultParticleColor().getColor();
    }

    /** Sets the light's color, rebuilding its flame timeline and syncing to clients. */
    public static void setColor(BlockEntity be, ParticleColor color) {
        if (be.getLevel() == null) {
            return;
        }
        var registries = be.getLevel().registryAccess();
        CompoundTag tag = be.saveWithoutMetadata(registries);
        tag.put("color", color.serialize());
        tag.remove("timeline");
        be.loadWithComponents(tag, registries);
        // Ars's rebuild only sets the timeline's top-level color; the particle type carries its own color
        // (what colorProp() returns and the flame uses), so set that one too.
        LightTimeline timeline = timeline(be);
        if (timeline != null && timeline.onTickEffect != null) {
            ColorProperty typeColor = timeline.onTickEffect.particleOptions().colorProp();
            if (typeColor != null) {
                typeColor.particleColor = color;
            }
        }
        if (be instanceof SconceTile sconce) {
            sconce.setColor(color);
        } else if (be instanceof LightTile light) {
            light.setColor(color);
        }
        be.setChanged();
    }

    public static void setDye(BlockEntity be, DyeColor dye) {
        setColor(be, ArsDecoBridge.particleColor(dye));
    }

    /** Back to Ars's default purple. */
    public static void reset(BlockEntity be) {
        setColor(be, ParticleColor.defaultParticleColor());
    }

    private static ParticleColor displayedColor(BlockEntity be) {
        LightTimeline timeline = timeline(be);
        if (timeline != null && timeline.onTickEffect != null) {
            ColorProperty typeColor = timeline.onTickEffect.particleOptions().colorProp();
            if (typeColor != null && typeColor.particleColor != null) {
                return typeColor.particleColor;
            }
        }
        return rawColor(be);
    }

    private static LightTimeline timeline(BlockEntity be) {
        return be instanceof SconceTile sconce ? sconce.getTimeline()
                : be instanceof LightTile light ? light.getTimeline() : null;
    }

    private static ParticleColor rawColor(BlockEntity be) {
        if (be instanceof SconceTile sconce) {
            return sconce.getColor();
        }
        if (be instanceof LightTile light) {
            return light.getColor();
        }
        return null;
    }
}
