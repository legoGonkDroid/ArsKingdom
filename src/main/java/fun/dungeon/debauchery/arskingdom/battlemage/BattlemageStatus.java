package fun.dungeon.debauchery.arskingdom.battlemage;

import com.minecolonies.api.entity.citizen.VisibleCitizenStatus;
import net.minecraft.resources.ResourceLocation;

/**
 * Citizen status icons. MineColonies numbers statuses in creation order, so they are created during mod construction
 * on both sides, never lazily.
 */
public final class BattlemageStatus {
    public static final VisibleCitizenStatus UNEQUIPPED = new VisibleCitizenStatus(
            ResourceLocation.fromNamespaceAndPath("ars_nouveau", "textures/item/spellbook_purple.png"),
            "com.ars_kingdom.gui.visiblestatus.unequipped", true);

    private BattlemageStatus() {
    }

    /** Forces class loading (and so status numbering) at a fixed point. */
    public static void init() {
    }
}
