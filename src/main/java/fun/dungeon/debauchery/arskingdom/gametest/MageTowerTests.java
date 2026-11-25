package fun.dungeon.debauchery.arskingdom.gametest;

import com.hollingsworth.arsnouveau.api.util.PerkUtil;
import com.hollingsworth.arsnouveau.setup.registry.DataComponentRegistry;
import com.minecolonies.api.colony.buildings.registry.BuildingEntry;
import com.minecolonies.api.colony.guardtype.GuardType;
import com.minecolonies.api.colony.jobs.registry.JobEntry;
import com.minecolonies.api.inventory.InventoryCitizen;
import com.minecolonies.api.items.ItemBlockHut;
import com.minecolonies.api.sounds.ModSoundEvents;
import com.minecolonies.apiimp.CommonMinecoloniesAPIImpl;
import com.minecolonies.core.colony.buildings.modules.BuildingModules;
import com.minecolonies.core.tileentities.TileEntityColonyBuilding;
import fun.dungeon.debauchery.arskingdom.ArsKingdom;
import com.hollingsworth.arsnouveau.setup.config.Config;
import com.hollingsworth.arsnouveau.setup.config.ServerConfig;
import fun.dungeon.debauchery.arskingdom.battlemage.SchoolSpells;
import fun.dungeon.debauchery.arskingdom.battlemage.BattlemageEquipment;
import fun.dungeon.debauchery.arskingdom.battlemage.BattlemageUpkeep;
import fun.dungeon.debauchery.arskingdom.battlemage.MageSchool;
import fun.dungeon.debauchery.arskingdom.job.JobBattlemageRed;
import fun.dungeon.debauchery.arskingdom.registry.AKBlockEntities;
import fun.dungeon.debauchery.arskingdom.registry.AKBlocks;
import fun.dungeon.debauchery.arskingdom.registry.AKBuildings;
import fun.dungeon.debauchery.arskingdom.registry.AKItems;
import com.ldtteam.structurize.blueprints.v1.Blueprint;
import com.ldtteam.structurize.storage.StructurePacks;
import java.util.Arrays;
import fun.dungeon.debauchery.arskingdom.building.BuildingMageTower;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Chunk 8: the Red Mage Tower, its battlemage registration and the "not allowed to work" gear gate. */
@GameTestHolder(ArsKingdom.MODID)
@PrefixGameTestTemplate(false)
public final class MageTowerTests {
    private static final String EMPTY = "gametest/empty";
    private static final EquipmentSlot[] SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
    private static final String[] PIECES = {"hood", "robes", "leggings", "boots"};

    private MageTowerTests() {
    }

    private static ItemStack ars(String path) {
        return new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("ars_nouveau", path)));
    }

    private static ItemStack armor(String piece, int tier) {
        ItemStack stack = ars("arcanist_" + piece);
        var holder = PerkUtil.getPerkHolder(stack);
        if (holder != null) {
            stack.set(DataComponentRegistry.ARMOR_PERKS, holder.setTier(tier));
        }
        return stack;
    }

    /** A detached citizen inventory: a book in slot 0 and the four pieces worn at the given tier. */
    private static InventoryCitizen kitted(String book, int armorTier) {
        InventoryCitizen inv = new InventoryCitizen("test", false);
        if (book != null) {
            inv.setStackInSlot(0, ars(book));
        }
        for (int i = 0; i < SLOTS.length; i++) {
            inv.forceArmorStackToSlot(SLOTS[i], armor(PIECES[i], armorTier));
        }
        return inv;
    }

    @GameTest(template = EMPTY)
    public static void allTowersRegistered(GameTestHelper helper) {
        var access = helper.getLevel().registryAccess();
        for (MageSchool school : MageSchool.values()) {
            String id = school.id();
            BuildingEntry building = access.registryOrThrow(CommonMinecoloniesAPIImpl.BUILDINGS).get(ArsKingdom.id(school.hutId()));
            helper.assertTrue(building != null, school.hutId() + " missing from the building registry");
            helper.assertTrue(building.getBuildingBlock() == AKBlocks.MAGE_TOWERS.get(school).get(), id + ": building should use its own hut");
            var modules = building.getModuleProducers();
            helper.assertTrue(modules.contains(AKBuildings.BATTLEMAGE_WORK.get(school)), id + ": battlemage work module");
            helper.assertTrue(modules.contains(BuildingModules.GUARD_SETTINGS) && modules.contains(BuildingModules.GUARD_ENTITY_LIST)
                    && modules.contains(BuildingModules.BED), id + ": guard settings, hostiles list and beds");
            helper.assertTrue(!modules.contains(BuildingModules.GUARD_TOOL), id + ": no vanilla guard tool module");

            GuardType guard = access.registryOrThrow(CommonMinecoloniesAPIImpl.GUARD_TYPES).get(ArsKingdom.id(school.guardTypeId()));
            helper.assertTrue(guard != null, school.guardTypeId() + " guard type missing");
            JobEntry job = access.registryOrThrow(CommonMinecoloniesAPIImpl.JOBS).get(ArsKingdom.id(school.guardTypeId()));
            helper.assertTrue(job != null && guard.getJobEntry().get() == job, id + ": guard type should point at its own job");
            helper.assertTrue(ModSoundEvents.CITIZEN_SOUND_EVENTS.get(school.guardTypeId()) != null, id + ": needs citizen voice lines");
            helper.assertTrue(job.produceJob(null) instanceof fun.dungeon.debauchery.arskingdom.job.JobBattlemage<?> mage && mage.getSchool() == school,
                    id + ": the job belongs to its school");

            var hut = AKBlocks.MAGE_TOWERS.get(school).get();
            helper.assertTrue(hut.getRegistryName().equals(ArsKingdom.id("blockhut" + school.hutId())), "hut name " + hut.getRegistryName());
            helper.assertTrue(hut.getBlueprintName().equals(school.hutId()), "blueprint name " + hut.getBlueprintName());
            helper.assertTrue(AKItems.MAGE_TOWERS.get(school).get() instanceof ItemBlockHut, id + ": hut item");
            helper.assertTrue(AKBlockEntities.BUILDING.get().isValid(hut.defaultBlockState()), id + ": block entity type must accept the hut");
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void redHutBlockEntity(GameTestHelper helper) {
        BlockPos rel = new BlockPos(2, 1, 2);
        helper.setBlock(rel, AKBlocks.MAGE_TOWERS.get(MageSchool.RED).get());
        var be = helper.getBlockEntity(rel);
        helper.assertTrue(be instanceof TileEntityColonyBuilding building && ArsKingdom.id("magetowerred").equals(building.registryName),
                "red hut block entity should carry ars_kingdom:magetowerred");
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void gearGate(GameTestHelper helper) {
        helper.assertTrue(!BattlemageEquipment.isFullyEquipped(new InventoryCitizen("test", false), 1), "nothing: not allowed");
        helper.assertTrue(BattlemageEquipment.isFullyEquipped(kitted("novice_spell_book", 0), 1), "novice book + tier 1 set at level 1");
        helper.assertTrue(!BattlemageEquipment.isFullyEquipped(kitted(null, 0), 1), "no book: not allowed");
        helper.assertTrue(!BattlemageEquipment.isFullyEquipped(kitted("creative_spell_book", 0), 3), "creative book never counts");
        helper.assertTrue(!BattlemageEquipment.isFullyEquipped(kitted("apprentice_spell_book", 0), 1), "apprentice book is above level 1");
        helper.assertTrue(BattlemageEquipment.isFullyEquipped(kitted("apprentice_spell_book", 0), 2), "apprentice book is fine at level 2");
        helper.assertTrue(!BattlemageEquipment.isFullyEquipped(kitted("novice_spell_book", 1), 1), "tier 2 armor is above level 1");
        helper.assertTrue(BattlemageEquipment.isFullyEquipped(kitted("archmage_spell_book", 2), 3), "archmage + tier 3 at level 3");

        for (EquipmentSlot slot : SLOTS) {
            InventoryCitizen inv = kitted("novice_spell_book", 0);
            inv.forceClearArmorInSlot(slot, inv.getArmorInSlot(slot));
            helper.assertTrue(!BattlemageEquipment.isFullyEquipped(inv, 1), "missing " + slot + ": not allowed");
        }
        InventoryCitizen wrongSlot = kitted("novice_spell_book", 0);
        wrongSlot.forceArmorStackToSlot(EquipmentSlot.HEAD, new ItemStack(Items.DIAMOND_HELMET));
        helper.assertTrue(!BattlemageEquipment.isFullyEquipped(wrongSlot, 1), "a diamond helmet is not an Arcanist hood");
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void redJobSchool(GameTestHelper helper) {
        helper.assertTrue(new JobBattlemageRed(null).getSchool() == MageSchool.RED, "red job belongs to the red school");
        helper.succeed();
    }

    /** Dye names, longest first so "light_blue" is matched before "blue". */
    private static final List<String> DYES = java.util.Arrays.stream(net.minecraft.world.item.DyeColor.values())
            .map(net.minecraft.world.item.DyeColor::getName).sorted((x, y) -> y.length() - x.length()).toList();

    /**
     * Every tower blueprint ({@code military/magetower<school>1..3}) loads from our pack, is anchored on its school's
     * hut, has the 13x13 plot, no unresolved or stripped blocks, lit lights and standing pedestals, one bed per
     * battlemage (= the level), and every color-named block in it is the school's color.
     */
    @GameTest(template = EMPTY, timeoutTicks = 400)
    public static void towerBlueprintsLoad(GameTestHelper helper) {
        StructurePacks.waitUntilFinishedLoading();
        String pack = "Ars Kingdom (Archwood Collective)";
        // The towers are being redesigned: only the Red level 1 blueprint exists so far.
        for (MageSchool school : List.of(MageSchool.RED)) {
            for (int level = 1; level <= BuildingMageTower.BLUEPRINT_LEVELS; level++) {
                String file = "military/" + school.hutId() + level + ".blueprint";
                Blueprint bp = StructurePacks.getBlueprint(pack, file, helper.getLevel().registryAccess());
                helper.assertTrue(bp != null, file + " not found in " + pack);
                BlockState anchor = bp.getBlockState(bp.getPrimaryBlockOffset());
                helper.assertTrue(anchor.is(AKBlocks.MAGE_TOWERS.get(school).get()), file + ": anchor should be its hut, got " + anchor);
                long airInPalette = Arrays.stream(bp.getPalette()).filter(st -> st.is(Blocks.AIR)).count();
                helper.assertTrue(airInPalette == 1, file + ": an unknown block id loaded as air (" + airInPalette + ")");
                helper.assertTrue(bp.getSizeX() == 13 && bp.getSizeZ() == 13 && bp.getSizeY() == 20, file + ": plot should be 13x20x13");
                boolean schoolLog = false;
                for (BlockState st : bp.getPalette()) {
                    String path = BuiltInRegistries.BLOCK.getKey(st.getBlock()).getPath();
                    helper.assertTrue(!path.contains("stripped"), file + ": no stripped logs (" + path + ")");
                    schoolLog |= path.equals(school.id() + "_archwood_log");
                    DYES.stream().filter(d -> path.startsWith(d + "_")).findFirst().ifPresent(d ->
                            helper.assertTrue(d.equals(school.id()), file + ": colored block " + path + " should be " + school.id()));
                    if (path.endsWith("sconce") || path.equals("magelight_torch")) {
                        var lvl = st.getBlock().getStateDefinition().getProperty("level");
                        helper.assertTrue(lvl != null && st.getValue(lvl).toString().equals("15"), file + ": " + path + " must be placed lit");
                    }
                    if (path.equals("arcane_pedestal")) {
                        var facing = st.getBlock().getStateDefinition().getProperty("facing");
                        helper.assertTrue(facing == null || st.getValue(facing).toString().equals("up"), file + ": pedestals must stand up");
                    }
                }
                helper.assertTrue(schoolLog, file + ": accent should be " + school.id() + " archwood logs");
                int beds = 0;
                for (int x = 0; x < bp.getSizeX(); x++) {
                    for (int y = 0; y < bp.getSizeY(); y++) {
                        for (int z = 0; z < bp.getSizeZ(); z++) {
                            BlockState st = bp.getBlockState(new BlockPos(x, y, z));
                            if (st.getBlock() instanceof net.minecraft.world.level.block.BedBlock
                                    && st.getValue(net.minecraft.world.level.block.BedBlock.PART) == net.minecraft.world.level.block.state.properties.BedPart.HEAD) {
                                beds++;
                            }
                        }
                    }
                }
                helper.assertTrue(beds == level, file + ": one bed per battlemage, expected " + level + ", got " + beds);
            }
        }
        helper.succeed();
    }

    /** Ars's player armor repair, for a battlemage: base rate per damaged piece, 20 mana each, nothing without mana. */
    @GameTest(template = EMPTY)
    public static void armorRepairCostsMana(GameTestHelper helper) {
        int rate = Config.BASE_ARMOR_REPAIR_RATE.get();
        helper.assertTrue(rate > 0, "Ars's base armor repair rate should be positive, got " + rate);
        InventoryCitizen inv = kitted("novice_spell_book", 0);
        for (EquipmentSlot slot : SLOTS) {
            inv.getArmorInSlot(slot).setDamageValue(50);
        }
        JobBattlemageRed job = new JobBattlemageRed(null);
        job.setStoredMana(50);
        BattlemageUpkeep.repairArmor(inv, job, 0);
        int repaired = 0;
        for (EquipmentSlot slot : SLOTS) {
            int damage = inv.getArmorInSlot(slot).getDamageValue();
            helper.assertTrue(damage == 50 || damage == 50 - rate, slot + " damage " + damage);
            repaired += damage < 50 ? 1 : 0;
        }
        helper.assertTrue(repaired == 2, "50 mana pays for two repairs at 20 each, repaired " + repaired);
        helper.assertTrue(job.getStoredMana() == 10, "mana left " + job.getStoredMana());

        BattlemageUpkeep.repairArmor(inv, job, 0);
        int still = 0;
        for (EquipmentSlot slot : SLOTS) {
            still += inv.getArmorInSlot(slot).getDamageValue() == 50 ? 1 : 0;
        }
        helper.assertTrue(still == 2 && job.getStoredMana() == 10, "no repairs below 20 mana");

        InventoryCitizen fresh = kitted("novice_spell_book", 0);
        job.setStoredMana(100);
        BattlemageUpkeep.repairArmor(fresh, job, 0);
        helper.assertTrue(job.getStoredMana() == 100, "undamaged armor costs nothing");
        helper.assertTrue(BattlemageUpkeep.repairingPerkLevel(fresh) == 0, "fresh Arcanist pieces have no Repairing perk");
        helper.succeed();
    }

    /** A player's pool and regen for the same gear: base + per book tier, plus the worn Arcanist pieces' bonuses. */
    @GameTest(template = EMPTY)
    public static void manaLikeAPlayer(GameTestHelper helper) {
        int baseMax = ServerConfig.INIT_MAX_MANA.get();
        int tierMax = ServerConfig.TIER_MAX_BONUS.get();
        int baseRegen = ServerConfig.INIT_MANA_REGEN.get();
        int tierRegen = ServerConfig.TIER_REGEN_BONUS.get();
        helper.assertTrue(BattlemageUpkeep.maxMana(new InventoryCitizen("test", false), 3) == 0, "no book, no mana");
        helper.assertTrue(BattlemageUpkeep.regenPerSecond(new InventoryCitizen("test", false), 3) == 0, "no book, no regen");

        InventoryCitizen bookOnly = new InventoryCitizen("test", false);
        bookOnly.setStackInSlot(0, ars("novice_spell_book"));
        helper.assertTrue(BattlemageUpkeep.maxMana(bookOnly, 1) == baseMax + tierMax, "novice, no armor: " + BattlemageUpkeep.maxMana(bookOnly, 1));
        helper.assertTrue(BattlemageUpkeep.regenPerSecond(bookOnly, 1) == baseRegen + tierRegen, "novice regen");

        // Each worn Arcanist piece adds 30 x (tier + 1) max mana and tier + 1 regen, as on a player.
        InventoryCitizen tier1 = kitted("novice_spell_book", 0);
        helper.assertTrue(BattlemageUpkeep.maxMana(tier1, 1) == baseMax + tierMax + 4 * 30, "novice + tier 1 set: " + BattlemageUpkeep.maxMana(tier1, 1));
        helper.assertTrue(BattlemageUpkeep.regenPerSecond(tier1, 1) == baseRegen + tierRegen + 4, "novice + tier 1 set regen");
        InventoryCitizen tier3 = kitted("archmage_spell_book", 2);
        helper.assertTrue(BattlemageUpkeep.maxMana(tier3, 3) == baseMax + 3 * tierMax + 4 * 90, "archmage + tier 3 set: " + BattlemageUpkeep.maxMana(tier3, 3));
        helper.assertTrue(BattlemageUpkeep.maxMana(kitted("archmage_spell_book", 0), 1) == 0, "a book above the tower level doesn't count");
        helper.assertTrue(BattlemageUpkeep.maxMana(kitted("creative_spell_book", 0), 3) == 0, "creative book doesn't count");
        helper.succeed();
    }

    /** Repairs never take the battlemage below its two-attack combat reserve. */
    @GameTest(template = EMPTY)
    public static void repairKeepsCombatReserve(GameTestHelper helper) {
        InventoryCitizen inv = kitted("novice_spell_book", 0);
        for (EquipmentSlot slot : SLOTS) {
            inv.getArmorInSlot(slot).setDamageValue(50);
        }
        JobBattlemageRed job = new JobBattlemageRed(null);
        double reserve = BattlemageUpkeep.reserve(job, inv, 1);
        helper.assertTrue(reserve == 2 * SchoolSpells.attackCost(MageSchool.RED, 1), "reserve is two red tier-1 attacks: " + reserve);
        job.setStoredMana(reserve + 30);
        BattlemageUpkeep.repairArmor(inv, job, reserve);
        int repaired = 0;
        for (EquipmentSlot slot : SLOTS) {
            repaired += inv.getArmorInSlot(slot).getDamageValue() < 50 ? 1 : 0;
        }
        helper.assertTrue(repaired == 1, "30 mana above the reserve pays for one repair, repaired " + repaired);
        helper.assertTrue(job.getStoredMana() >= reserve, "the reserve is untouched: " + job.getStoredMana());
        helper.succeed();
    }
}
