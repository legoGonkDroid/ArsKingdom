package fun.dungeon.debauchery.arskingdom.colony.dye;

import java.util.Optional;
import net.minecraft.world.item.DyeColor;

/**
 * Pure decision: what to do with one Ars Deco target block. No world access, so every case is gametested.
 *
 * <p>Ownership: a block is "ours" when we have a record for it and its current color still equals
 * that record. Anything else that is dyed was dyed by a player (or by a blueprint) and is left alone
 * unless {@code overrideCustomColors} is on.
 */
public final class DyeDecider {
    public enum Action {
        /** Nothing to do. */
        NONE,
        /** Dye the block with {@link Decision#dye()} and record it as ours. */
        APPLY,
        /** Remove our dye from the block and drop the record. */
        CLEAR,
        /** Leave the block as it is, but drop our (stale or abandoned) record. */
        FORGET
    }

    public record Decision(Action action, DyeColor dye) {
        static final Decision NONE = new Decision(Action.NONE, null);
        static final Decision CLEAR = new Decision(Action.CLEAR, null);
        static final Decision FORGET = new Decision(Action.FORGET, null);

        static Decision apply(DyeColor dye) {
            return new Decision(Action.APPLY, dye);
        }
    }

    /**
     * @param desired              the zone dye here, or empty when the block is outside a colony, the colony has no
     *                             color, or the rule for this block's category is off
     * @param current              the block's current Ars Deco color
     * @param recorded             the color we recorded applying here, if any
     * @param overrideCustomColors repaint player-dyed blocks too
     * @param undyeOnLeave         remove our dye when the block no longer should have one
     */
    public static Decision decide(Optional<DyeColor> desired, Optional<DyeColor> current, Optional<DyeColor> recorded,
                                  boolean overrideCustomColors, boolean undyeOnLeave) {
        Action action = action(desired, current, recorded, overrideCustomColors, undyeOnLeave);
        return action == Action.APPLY ? Decision.apply(desired.get()) : new Decision(action, null);
    }

    /**
     * The ownership rules for any kind of color (dyes for blocks, RGB values for lights). {@code current} is
     * empty when the block shows its native, uncolored look; any other value counts as a color someone chose.
     */
    public static <T> Action action(Optional<T> desired, Optional<T> current, Optional<T> recorded,
                                    boolean overrideCustomColors, boolean undyeOnLeave) {
        boolean ours = recorded.isPresent() && recorded.equals(current);
        boolean staleRecord = recorded.isPresent() && !ours;

        if (desired.isEmpty()) {
            if (ours) {
                return undyeOnLeave ? Action.CLEAR : Action.FORGET;
            }
            return staleRecord ? Action.FORGET : Action.NONE;
        }

        if (current.isEmpty()) {
            return Action.APPLY;
        }
        if (ours) {
            return current.equals(desired) ? Action.NONE : Action.APPLY;
        }
        // Colored by a player (or a stale record whose color the player changed).
        if (overrideCustomColors) {
            // Take the block over (even when it already has our color) so later color changes follow the colony.
            return Action.APPLY;
        }
        return staleRecord ? Action.FORGET : Action.NONE;
    }

    private DyeDecider() {
    }
}
