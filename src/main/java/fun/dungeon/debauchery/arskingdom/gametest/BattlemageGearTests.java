package fun.dungeon.debauchery.arskingdom.gametest;

import com.hollingsworth.arsnouveau.api.util.PerkUtil;
import com.hollingsworth.arsnouveau.setup.registry.DataComponentRegistry;
import com.minecolonies.api.equipment.registry.EquipmentTypeEntry;
import com.minecolonies.apiimp.CommonMinecoloniesAPIImpl;
import fun.dungeon.debauchery.arskingdom.ArsKingdom;
import fun.dungeon.debauchery.arskingdom.battlemage.BattlemageGear;
import fun.dungeon.debauchery.arskingdom.battlemage.MageSchool;
import fun.dungeon.debauchery.arskingdom.registry.AKEquipmentTypes;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Chunk 7: battlemage equipment types, gear rules and schools. */
@GameTestHolder(ArsKingdom.MODID)
@PrefixGameTestTemplate(false)
public final class BattlemageGearTests {
    private static final String EMPTY = "gametest/empty";
    private static final String[] ARMOR = {"hood", "robes", "leggings", "boots"};

    private BattlemageGearTests() {
    }

    private static ItemStack ars(String path) {
        var item = BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("ars_nouveau", path));
        return new ItemStack(item);
    }

    private static ItemStack armorAtTier(String slot, int tier) {
        ItemStack stack = ars("arcanist_" + slot);
        var holder = PerkUtil.getPerkHolder(stack);
        if (holder != null) {
            stack.set(DataComponentRegistry.ARMOR_PERKS, holder.setTier(tier));
        }
        return stack;
    }

    private static EquipmentTypeEntry armorType(String slot) {
        return switch (slot) {
            case "hood" -> AKEquipmentTypes.ARCANIST_HOOD.get();
            case "robes" -> AKEquipmentTypes.ARCANIST_ROBES.get();
            case "leggings" -> AKEquipmentTypes.ARCANIST_LEGGINGS.get();
            default -> AKEquipmentTypes.ARCANIST_BOOTS.get();
        };
    }

    @GameTest(template = EMPTY)
    public static void typesRegistered(GameTestHelper helper) {
        var registry = helper.getLevel().registryAccess().registryOrThrow(CommonMinecoloniesAPIImpl.EQUIPMENT_TYPES);
        for (String name : new String[] {"spellbook", "arcanist_hood", "arcanist_robes", "arcanist_leggings", "arcanist_boots"}) {
            helper.assertTrue(registry.get(ArsKingdom.id(name)) != null, "equipment type ars_kingdom:" + name + " not registered");
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void spellbookLevels(GameTestHelper helper) {
        EquipmentTypeEntry book = AKEquipmentTypes.SPELLBOOK.get();
        String[] books = {"novice_spell_book", "apprentice_spell_book", "archmage_spell_book"};
        for (int i = 0; i < books.length; i++) {
            ItemStack stack = ars(books[i]);
            helper.assertTrue(book.checkIsEquipment(stack), books[i] + " should be a battlemage spellbook");
            helper.assertTrue(book.getMiningLevel(stack) == i + 1, books[i] + " level " + book.getMiningLevel(stack) + ", expected " + (i + 1));
        }
        ItemStack creative = ars("creative_spell_book");
        helper.assertTrue(!book.checkIsEquipment(creative), "the creative book must be rejected");
        helper.assertTrue(!book.checkIsEquipment(new ItemStack(Items.BOOK)), "a plain book is not a spellbook");
        helper.assertTrue(!book.checkIsEquipment(ars("arcanist_hood")), "a hood is not a spellbook");
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void armorLevels(GameTestHelper helper) {
        for (String slot : ARMOR) {
            EquipmentTypeEntry type = armorType(slot);
            for (int tier = 0; tier <= 2; tier++) {
                ItemStack stack = armorAtTier(slot, tier);
                helper.assertTrue(type.checkIsEquipment(stack), "arcanist_" + slot + " should match its type");
                helper.assertTrue(type.getMiningLevel(stack) == tier + 1,
                        "arcanist_" + slot + " tier " + tier + " level " + type.getMiningLevel(stack) + ", expected " + (tier + 1));
            }
            for (String other : ARMOR) {
                if (!other.equals(slot)) {
                    helper.assertTrue(!type.checkIsEquipment(ars("arcanist_" + other)), "arcanist_" + other + " must not match " + slot);
                }
            }
        }
        helper.assertTrue(BattlemageGear.armorLevel(ars("arcanist_robes")) == 1, "fresh robes are level 1");
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void nonArcanistArmorRejected(GameTestHelper helper) {
        ItemStack[][] cases = {
                {new ItemStack(Items.DIAMOND_HELMET), ars("sorcerer_hood"), ars("battlemage_hood")},
                {new ItemStack(Items.DIAMOND_CHESTPLATE), ars("sorcerer_robes"), ars("battlemage_robes")},
                {new ItemStack(Items.DIAMOND_LEGGINGS), ars("sorcerer_leggings"), ars("battlemage_leggings")},
                {new ItemStack(Items.DIAMOND_BOOTS), ars("sorcerer_boots"), ars("battlemage_boots")},
        };
        for (int i = 0; i < ARMOR.length; i++) {
            for (ItemStack stack : cases[i]) {
                helper.assertTrue(!armorType(ARMOR[i]).checkIsEquipment(stack), stack + " must not count as Arcanist " + ARMOR[i]);
            }
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void schoolColorApplied(GameTestHelper helper) {
        for (MageSchool school : MageSchool.values()) {
            for (String path : new String[] {"novice_spell_book", "arcanist_hood", "arcanist_robes", "arcanist_leggings", "arcanist_boots"}) {
                ItemStack stack = ars(path);
                helper.assertTrue(BattlemageGear.applySchoolColor(stack, school), path + " should take a colour");
                helper.assertTrue(stack.get(DataComponents.BASE_COLOR) == school.dye(),
                        path + " BASE_COLOR " + stack.get(DataComponents.BASE_COLOR) + ", expected " + school.dye());
            }
        }
        ItemStack dirt = new ItemStack(Items.DIRT);
        helper.assertTrue(!BattlemageGear.applySchoolColor(dirt, MageSchool.RED) && dirt.get(DataComponents.BASE_COLOR) == null,
                "dirt takes no colour");
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void schoolDyeMapping(GameTestHelper helper) {
        helper.assertTrue(MageSchool.RED.dye() == DyeColor.RED, "red");
        helper.assertTrue(MageSchool.BLUE.dye() == DyeColor.BLUE, "blue");
        helper.assertTrue(MageSchool.GREEN.dye() == DyeColor.GREEN, "green");
        helper.assertTrue(MageSchool.PURPLE.dye() == DyeColor.PURPLE, "purple");
        Set<DyeColor> seen = new HashSet<>();
        for (MageSchool school : MageSchool.values()) {
            helper.assertTrue(seen.add(school.dye()), "two schools share " + school.dye());
            helper.assertTrue(MageSchool.byDye(school.dye()) == school, "byDye round trip for " + school);
        }
        helper.assertTrue(MageSchool.byDye(DyeColor.WHITE) == null, "white is no school");
        helper.assertTrue(MageSchool.RED.hutId().equals("magetowerred") && MageSchool.RED.guardTypeId().equals("battlemage_red")
                && MageSchool.RED.workModuleKey().equals("ars_kingdom_battlemage_red_work"), "red ids");
        helper.succeed();
    }
}
