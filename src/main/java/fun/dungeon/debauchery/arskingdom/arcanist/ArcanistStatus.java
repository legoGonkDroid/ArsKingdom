package fun.dungeon.debauchery.arskingdom.arcanist;

import com.minecolonies.api.entity.citizen.VisibleCitizenStatus;
import net.minecraft.resources.ResourceLocation;

/**
 * The Arcanist's "needs source" status icon. MineColonies numbers statuses in creation order, so this is created during
 * mod construction on both sides, never lazily.
 */
public final class ArcanistStatus {
    public static final VisibleCitizenStatus NEEDS_SOURCE = new VisibleCitizenStatus(
            ResourceLocation.fromNamespaceAndPath("ars_nouveau", "textures/item/source_gem.png"),
            "com.ars_kingdom.gui.visiblestatus.needs_source", true);

    private ArcanistStatus() {
    }

    /** Forces class loading (and so status numbering) at a fixed point. */
    public static void init() {
    }
}
