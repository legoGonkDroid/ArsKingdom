package fun.dungeon.debauchery.arskingdom.battlemage;

import com.hollingsworth.arsnouveau.api.perk.PerkAttributes;
import com.hollingsworth.arsnouveau.api.perk.PerkInstance;
import com.hollingsworth.arsnouveau.api.source.ISpecialSourceProvider;
import com.hollingsworth.arsnouveau.api.util.PerkUtil;
import com.hollingsworth.arsnouveau.api.util.SourceUtil;
import com.hollingsworth.arsnouveau.common.items.data.ArmorPerkHolder;
import com.hollingsworth.arsnouveau.common.perk.RepairingPerk;
import com.hollingsworth.arsnouveau.setup.config.Config;
import com.hollingsworth.arsnouveau.setup.config.ServerConfig;
import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import com.minecolonies.api.inventory.InventoryCitizen;
import fun.dungeon.debauchery.arskingdom.job.JobBattlemage;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

/**
 * A battlemage's mana and armor upkeep, done the way Ars Nouveau does it for players.
 *
 * <ul>
 *   <li><b>Mana pool</b> ({@link JobBattlemage#getStoredMana()}): a player's pool for the same gear. Max = Ars's base max
 *   mana + its per-book-tier bonus + the worn armor's max-mana bonuses; it regenerates every second by Ars's base regen
 *   + its per-book-tier bonus + the armor's regen bonuses. Citizens have no Ars mana capability or attributes, so these
 *   are computed from Ars's server config and the worn stacks. No book, no mana.</li>
 *   <li><b>Jar top-up</b>: within 10 blocks of its tower, the battlemage also draws source from the tower's jars.</li>
 *   <li><b>Armor repair</b>: Ars's {@code RepairingPerk.attemptRepair} every 200 ticks per damaged worn piece (base rate +
 *   Repairing perk level, 20 mana), but never below a reserve of two attacks, so combat comes first.</li>
 * </ul>
 */
public final class BattlemageUpkeep {
    public static final int REPAIR_INTERVAL = 200;
    public static final double REPAIR_MANA_COST = 20.0;
    /** Repairs leave mana for this many attacks. */
    public static final int RESERVE_ATTACKS = 2;
    private static final int REGEN_INTERVAL = 20;
    private static final int REFILL_INTERVAL = 100;
    private static final double REFILL_DISTANCE_SQ = 10 * 10;
    private static final int JAR_RANGE = 6;
    private static final EquipmentSlot[] ARMOR = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

    private BattlemageUpkeep() {
    }

    @SubscribeEvent
    public static void onEntityTick(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof AbstractEntityCitizen citizen) || citizen.level().isClientSide()) {
            return;
        }
        long time = citizen.level().getGameTime();
        if (time % REGEN_INTERVAL != 0) {
            return;
        }
        ICitizenData data = citizen.getCitizenData();
        if (data == null || !(data.getJob() instanceof JobBattlemage<?> job)) {
            return;
        }
        IBuilding tower = data.getWorkBuilding();
        if (tower == null) {
            return;
        }
        InventoryCitizen inventory = data.getInventory();
        int maxLevel = tower.getMaxEquipmentLevel();
        double max = maxMana(inventory, maxLevel);
        job.setStoredMana(Math.min(max, job.getStoredMana() + regenPerSecond(inventory, maxLevel)));
        if (time % REFILL_INTERVAL == 0) {
            refill(citizen, job, tower, max);
        }
        if (time % REPAIR_INTERVAL == 0) {
            repairArmor(inventory, job, reserve(job, inventory, maxLevel));
        }
    }

    /** The tier of the best spellbook the battlemage carries that the tower allows (0 without one). */
    public static int bookTier(InventoryCitizen inventory, int maxLevel) {
        int best = 0;
        for (int slot = 0; slot < inventory.getSlots(); slot++) {
            ItemStack stack = inventory.getStackInSlot(slot);
            if (BattlemageGear.isSpellbook(stack) && BattlemageGear.spellbookLevel(stack) <= maxLevel) {
                best = Math.max(best, BattlemageGear.spellbookLevel(stack));
            }
        }
        return best;
    }

    /** A player's max mana for this book and worn armor (glyph-knowledge bonuses aside). */
    public static double maxMana(InventoryCitizen inventory, int maxLevel) {
        int tier = bookTier(inventory, maxLevel);
        if (tier == 0) {
            return 0;
        }
        return ServerConfig.INIT_MAX_MANA.get() + (double) ServerConfig.TIER_MAX_BONUS.get() * tier
                + armorBonus(inventory, PerkAttributes.MAX_MANA::get);
    }

    /** A player's mana regen per second for this book and worn armor. */
    public static double regenPerSecond(InventoryCitizen inventory, int maxLevel) {
        int tier = bookTier(inventory, maxLevel);
        if (tier == 0) {
            return 0;
        }
        return ServerConfig.INIT_MANA_REGEN.get() + (double) ServerConfig.TIER_REGEN_BONUS.get() * tier
                + armorBonus(inventory, PerkAttributes.MANA_REGEN_BONUS::get);
    }

    /** Mana kept back from repairs: two attacks at the battlemage's tier. */
    public static double reserve(JobBattlemage<?> job, InventoryCitizen inventory, int maxLevel) {
        return RESERVE_ATTACKS * SchoolSpells.attackCost(job.getSchool(), Math.max(1, bookTier(inventory, maxLevel)));
    }

    /** Sum of the worn pieces' additive modifiers for an Ars attribute (Arcanist armor: +30/+1 per tier, plus perks). */
    private static double armorBonus(InventoryCitizen inventory, Supplier<Attribute> attribute) {
        double[] total = {0};
        for (EquipmentSlot slot : ARMOR) {
            ItemStack stack = inventory.getArmorInSlot(slot);
            if (!stack.isEmpty()) {
                stack.forEachModifier(slot, (holder, modifier) -> {
                    if (holder.value() == attribute.get() && modifier.operation() == AttributeModifier.Operation.ADD_VALUE) {
                        total[0] += modifier.amount();
                    }
                });
            }
        }
        return total[0];
    }

    /** Tops the battlemage's mana up from the tower's jars while it is at the tower. */
    private static void refill(AbstractEntityCitizen citizen, JobBattlemage<?> job, IBuilding tower, double cap) {
        BlockPos hut = tower.getPosition();
        if (citizen.blockPosition().distSqr(hut) > REFILL_DISTANCE_SQ) {
            return;
        }
        int missing = (int) Math.floor(cap - job.getStoredMana());
        if (missing <= 0) {
            return;
        }
        Level level = citizen.level();
        int available = 0;
        for (ISpecialSourceProvider provider : SourceUtil.canTakeSource(hut, level, JAR_RANGE)) {
            available += provider.getSource().getSource();
        }
        int take = Math.min(missing, available);
        if (take > 0 && SourceUtil.takeSourceMultipleWithParticles(hut, citizen.blockPosition(), level, JAR_RANGE, take) != null) {
            job.setStoredMana(job.getStoredMana() + take);
        }
    }

    /** Ars's {@code RepairingPerk.attemptRepair}, per worn piece, paid from mana above {@code reserve}. */
    public static void repairArmor(InventoryCitizen inventory, JobBattlemage<?> job, double reserve) {
        int perkLevel = repairingPerkLevel(inventory);
        for (EquipmentSlot slot : ARMOR) {
            ItemStack stack = inventory.getArmorInSlot(slot);
            int damage = stack.getDamageValue();
            if (stack.isEmpty() || damage <= 0 || PerkUtil.getPerkHolder(stack) == null) {
                continue;
            }
            int repair = Math.min(damage, perkLevel + Config.BASE_ARMOR_REPAIR_RATE.get());
            if (repair > 0 && job.getStoredMana() - REPAIR_MANA_COST >= reserve) {
                job.setStoredMana(job.getStoredMana() - REPAIR_MANA_COST);
                stack.setDamageValue(damage - repair);
                inventory.markDirty();
            }
        }
    }

    /**
     * Ars's {@code PerkUtil.countForPerk(RepairingPerk)}: the highest Repairing perk slot among the worn pieces. Read
     * from the citizen's armor inventory, because a citizen's vanilla armor list isn't kept up to date.
     */
    public static int repairingPerkLevel(InventoryCitizen inventory) {
        int level = 0;
        for (EquipmentSlot slot : ARMOR) {
            ItemStack stack = inventory.getArmorInSlot(slot);
            ArmorPerkHolder holder = PerkUtil.getPerkHolder(stack);
            if (holder == null) {
                continue;
            }
            for (PerkInstance instance : holder.getPerkInstances(stack)) {
                if (instance.getPerk() == RepairingPerk.INSTANCE) {
                    level = Math.max(level, instance.getSlot().value());
                }
            }
        }
        return level;
    }
}
