package fun.dungeon.debauchery.arskingdom.battlemage;

import com.hollingsworth.arsnouveau.api.spell.SpellTier;
import com.hollingsworth.arsnouveau.api.util.PerkUtil;
import com.hollingsworth.arsnouveau.common.armor.AnimatedMagicArmor;
import com.hollingsworth.arsnouveau.common.items.SpellBook;
import com.hollingsworth.arsnouveau.common.items.data.ArmorPerkHolder;
import fun.dungeon.debauchery.arskingdom.ArsKingdom;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Battlemage gear rules: which items count as a battlemage's spellbook and Arcanist pieces, their equipment level
 * (1..3, matching the tower level that allows them), and the school colour applied when they are equipped.
 */
public final class BattlemageGear {
    public static final TagKey<Item> SPELLBOOKS = tag("spellbooks");
    public static final TagKey<Item> HOOD = tag("hood");
    public static final TagKey<Item> ROBES = tag("robes");
    public static final TagKey<Item> LEGGINGS = tag("leggings");
    public static final TagKey<Item> BOOTS = tag("boots");

    private BattlemageGear() {
    }

    private static TagKey<Item> tag(String slot) {
        return TagKey.create(Registries.ITEM, ArsKingdom.id("battlemage/" + slot));
    }

    /** A spellbook in the tag, never the creative book. */
    public static boolean isSpellbook(ItemStack stack) {
        return !stack.isEmpty() && stack.is(SPELLBOOKS) && stack.getItem() instanceof SpellBook book
                && book.getTier() != SpellTier.CREATIVE;
    }

    /** Book tier: novice 1, apprentice 2, archmage 3. */
    public static int spellbookLevel(ItemStack stack) {
        return stack.getItem() instanceof SpellBook book ? book.getTier().value : 0;
    }

    /** An armor piece in the given slot tag. */
    public static boolean isArmor(ItemStack stack, TagKey<Item> slot) {
        return !stack.isEmpty() && stack.is(slot);
    }

    /** Armor tier + 1: a fresh piece is 1, an upgraded one 2 or 3. Pieces without perk data count as 1. */
    public static int armorLevel(ItemStack stack) {
        ArmorPerkHolder holder = PerkUtil.getPerkHolder(stack);
        return holder == null ? 1 : holder.getTier() + 1;
    }

    /**
     * Sets the item's {@code BASE_COLOR} to the school dye, so Ars renders its own coloured texture
     * ({@code arcanists_armor_<color>}, {@code spellbook_<color>}). Only Ars armor and spellbooks take a colour.
     */
    public static boolean applySchoolColor(ItemStack stack, MageSchool school) {
        if (!(stack.getItem() instanceof AnimatedMagicArmor) && !(stack.getItem() instanceof SpellBook)) {
            return false;
        }
        stack.set(DataComponents.BASE_COLOR, school.dye());
        return true;
    }
}
