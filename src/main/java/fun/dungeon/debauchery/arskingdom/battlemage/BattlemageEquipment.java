package fun.dungeon.debauchery.arskingdom.battlemage;

import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.equipment.registry.EquipmentTypeEntry;
import com.minecolonies.api.inventory.InventoryCitizen;
import com.minecolonies.api.util.InventoryUtils;
import com.minecolonies.api.util.ItemStackUtils;
import fun.dungeon.debauchery.arskingdom.job.JobBattlemage;
import fun.dungeon.debauchery.arskingdom.registry.AKEquipmentTypes;
import java.util.List;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

/**
 * Whether a battlemage carries everything it needs to work: a spellbook in its inventory and all four Arcanist pieces
 * worn, each at or below the tower's equipment level.
 */
public final class BattlemageEquipment {
    /** The four armor types and the slot each is worn in. */
    public record ArmorPiece(EquipmentTypeEntry type, EquipmentSlot slot) {
    }

    private BattlemageEquipment() {
    }

    public static List<ArmorPiece> armorPieces() {
        return List.of(
                new ArmorPiece(AKEquipmentTypes.ARCANIST_HOOD.get(), EquipmentSlot.HEAD),
                new ArmorPiece(AKEquipmentTypes.ARCANIST_ROBES.get(), EquipmentSlot.CHEST),
                new ArmorPiece(AKEquipmentTypes.ARCANIST_LEGGINGS.get(), EquipmentSlot.LEGS),
                new ArmorPiece(AKEquipmentTypes.ARCANIST_BOOTS.get(), EquipmentSlot.FEET));
    }

    public static boolean hasSpellbook(InventoryCitizen inventory, int maxLevel) {
        return InventoryUtils.isEquipmentInItemHandler(inventory, AKEquipmentTypes.SPELLBOOK.get(), 1, maxLevel);
    }

    public static boolean isWorn(InventoryCitizen inventory, ArmorPiece piece, int maxLevel) {
        ItemStack worn = inventory.getArmorInSlot(piece.slot());
        return ItemStackUtils.hasEquipmentLevel(worn, piece.type(), 1, maxLevel);
    }

    public static boolean isFullyEquipped(InventoryCitizen inventory, int maxLevel) {
        if (!hasSpellbook(inventory, maxLevel)) {
            return false;
        }
        for (ArmorPiece piece : armorPieces()) {
            if (!isWorn(inventory, piece, maxLevel)) {
                return false;
            }
        }
        return true;
    }

    /** For the "not allowed to work" interaction's validator: true while a battlemage still lacks gear. */
    public static boolean isUnequippedBattlemage(ICitizenData citizen) {
        if (!(citizen.getJob() instanceof JobBattlemage<?>)) {
            return false;
        }
        IBuilding building = citizen.getWorkBuilding();
        if (building == null) {
            return false;
        }
        return !isFullyEquipped(citizen.getInventory(), building.getMaxEquipmentLevel());
    }
}
