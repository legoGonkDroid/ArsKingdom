package fun.dungeon.debauchery.arskingdom.registry;

import com.minecolonies.api.equipment.registry.EquipmentTypeEntry;
import com.minecolonies.apiimp.CommonMinecoloniesAPIImpl;
import fun.dungeon.debauchery.arskingdom.ArsKingdom;
import fun.dungeon.debauchery.arskingdom.battlemage.BattlemageGear;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Battlemage equipment types. Levels run 1..3 and match the Mage Tower level that allows them, so a tower requests
 * {@code Tool(type, 1, towerLevel)}.
 */
public final class AKEquipmentTypes {
    public static final DeferredRegister<EquipmentTypeEntry> EQUIPMENT_TYPES =
            DeferredRegister.create(CommonMinecoloniesAPIImpl.EQUIPMENT_TYPES, ArsKingdom.MODID);

    public static final DeferredHolder<EquipmentTypeEntry, EquipmentTypeEntry> SPELLBOOK = register("spellbook",
            (stack, type) -> BattlemageGear.isSpellbook(stack),
            (stack, type) -> BattlemageGear.isSpellbook(stack) ? BattlemageGear.spellbookLevel(stack) : -1);
    public static final DeferredHolder<EquipmentTypeEntry, EquipmentTypeEntry> ARCANIST_HOOD = armor("arcanist_hood", BattlemageGear.HOOD);
    public static final DeferredHolder<EquipmentTypeEntry, EquipmentTypeEntry> ARCANIST_ROBES = armor("arcanist_robes", BattlemageGear.ROBES);
    public static final DeferredHolder<EquipmentTypeEntry, EquipmentTypeEntry> ARCANIST_LEGGINGS = armor("arcanist_leggings", BattlemageGear.LEGGINGS);
    public static final DeferredHolder<EquipmentTypeEntry, EquipmentTypeEntry> ARCANIST_BOOTS = armor("arcanist_boots", BattlemageGear.BOOTS);

    private AKEquipmentTypes() {
    }

    private static DeferredHolder<EquipmentTypeEntry, EquipmentTypeEntry> armor(String name, TagKey<Item> slot) {
        return register(name, (stack, type) -> BattlemageGear.isArmor(stack, slot),
                (stack, type) -> BattlemageGear.isArmor(stack, slot) ? BattlemageGear.armorLevel(stack) : -1);
    }

    private static DeferredHolder<EquipmentTypeEntry, EquipmentTypeEntry> register(String name,
            BiPredicate<ItemStack, EquipmentTypeEntry> isEquipment, BiFunction<ItemStack, EquipmentTypeEntry, Integer> level) {
        return EQUIPMENT_TYPES.register(name, () -> new EquipmentTypeEntry.Builder()
                .setRegistryName(ArsKingdom.id(name))
                .setDisplayName(Component.translatable("com.ars_kingdom.equipment." + name))
                .setIsEquipment(isEquipment)
                .setEquipmentLevel(level)
                .build());
    }
}
