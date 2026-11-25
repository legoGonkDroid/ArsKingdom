package fun.dungeon.debauchery.arskingdom.colony.dye;

import com.minecolonies.api.colony.IColony;
import fun.dungeon.debauchery.arskingdom.colony.ColonyDye;
import fun.dungeon.debauchery.arskingdom.colony.ColonyLookup;
import fun.dungeon.debauchery.arskingdom.colony.ColonyRules;
import fun.dungeon.debauchery.arskingdom.colony.dye.DyeDecider.Action;
import fun.dungeon.debauchery.arskingdom.compat.ArsDecoBridge;
import fun.dungeon.debauchery.arskingdom.compat.ArsDecoBridge.Category;
import fun.dungeon.debauchery.arskingdom.compat.ArsLightBridge;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * Decides and applies the colony color on one position: gathers the inputs, asks {@link DyeDecider}, acts.
 * A position can carry two colors: the block's Ars Deco dye and, for lights (sconces, magelights, mage
 * lights), the native Ars light color (M1d). Each has its own ownership record.
 */
public final class ColonyDyer {
    private ColonyDyer() {
    }

    /** Brings the block at {@code pos} in line with the colony rules. Safe to call on any position. */
    public static DyeDecider.Decision process(ServerLevel level, BlockPos pos) {
        IColony colony = ColonyLookup.colony(level, pos);
        boolean override = ColonyRules.overrideCustomColors(colony);
        boolean undye = ColonyRules.undyeOnLeave(colony);
        Optional<DyeColor> zone = colony == null ? Optional.empty() : ColonyDye.zoneDye(level, pos);

        Optional<DyeColor> lightDesired = colony != null && ColonyRules.dyesLights(colony) ? zone : Optional.empty();
        processLightWith(level, pos, lightDesired, override, undye);

        Optional<Category> category = ArsDecoBridge.category(level.getBlockState(pos).getBlock());
        if (category.isEmpty()) {
            // Not a target (any more): just drop a leftover record.
            AutoDyedData data = AutoDyedData.get(level);
            if (data.get(pos).isPresent()) {
                data.remove(pos);
                return new DyeDecider.Decision(Action.FORGET, null);
            }
            return new DyeDecider.Decision(Action.NONE, null);
        }
        Optional<DyeColor> desired = colony != null && ColonyRules.dyes(colony, category.get()) ? zone : Optional.empty();
        return processWith(level, pos, desired, override, undye);
    }

    /** The block-dye half of {@link #process}, with the colony inputs given directly (used by gametests). */
    public static DyeDecider.Decision processWith(ServerLevel level, BlockPos pos, Optional<DyeColor> desired,
                                                  boolean overrideCustomColors, boolean undyeOnLeave) {
        AutoDyedData data = AutoDyedData.get(level);
        Optional<DyeColor> current = ArsDecoBridge.getColor(level, pos);
        DyeDecider.Decision decision = DyeDecider.decide(desired, current, data.get(pos), overrideCustomColors, undyeOnLeave);
        switch (decision.action()) {
            case APPLY -> {
                if (ArsDecoBridge.apply(level, pos, decision.dye())) {
                    data.put(pos, decision.dye());
                }
            }
            case CLEAR -> {
                ArsDecoBridge.clear(level, pos);
                data.remove(pos);
            }
            case FORGET -> data.remove(pos);
            case NONE -> {
            }
        }
        return decision;
    }

    /**
     * The light half of {@link #process}: "uncolored" is Ars's default purple, "ours" is the particle color of
     * the dye we recorded, anything else is a player's (e.g. lit with a colored spell).
     */
    public static Action processLightWith(ServerLevel level, BlockPos pos, Optional<DyeColor> desired,
                                          boolean overrideCustomColors, boolean undyeOnLeave) {
        AutoDyedData lights = AutoDyedData.lights(level);
        BlockEntity be = level.getBlockEntity(pos);
        Optional<DyeColor> recordedDye = lights.get(pos);
        if (be == null || !ArsLightBridge.isLight(be)) {
            if (recordedDye.isPresent()) {
                lights.remove(pos);
                return Action.FORGET;
            }
            return Action.NONE;
        }
        Action action = DyeDecider.action(
                desired.map(ArsLightBridge::rgbFor),
                ArsLightBridge.color(be),
                recordedDye.map(ArsLightBridge::rgbFor),
                overrideCustomColors, undyeOnLeave);
        switch (action) {
            case APPLY -> {
                ArsLightBridge.setDye(be, desired.get());
                lights.put(pos, desired.get());
            }
            case CLEAR -> {
                ArsLightBridge.reset(be);
                lights.remove(pos);
            }
            case FORGET -> lights.remove(pos);
            case NONE -> {
            }
        }
        return action;
    }
}
