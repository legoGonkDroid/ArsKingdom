package fun.dungeon.debauchery.arskingdom.ai;

import com.minecolonies.api.colony.interactionhandling.ChatPriority;
import com.minecolonies.api.entity.ai.combat.CombatAIStates;
import com.minecolonies.api.entity.ai.statemachine.AITarget;
import com.minecolonies.api.entity.ai.statemachine.states.AIWorkerState;
import com.minecolonies.api.entity.ai.statemachine.states.IAIState;
import com.minecolonies.api.entity.ai.workers.util.GuardGear;
import com.minecolonies.api.entity.citizen.VisibleCitizenStatus;
import com.minecolonies.api.equipment.registry.EquipmentTypeEntry;
import com.minecolonies.api.inventory.InventoryCitizen;
import com.minecolonies.api.util.InventoryUtils;
import com.minecolonies.api.util.ItemStackUtils;
import net.minecraft.util.Tuple;
import com.minecolonies.core.colony.buildings.AbstractBuildingGuards;
import com.minecolonies.core.colony.interactionhandling.StandardInteraction;
import com.minecolonies.core.entity.ai.workers.guard.AbstractEntityAIGuard;
import com.minecolonies.core.entity.citizen.EntityCitizen;
import com.minecolonies.core.util.citizenutils.CitizenItemUtils;
import fun.dungeon.debauchery.arskingdom.battlemage.BattlemageEquipment;
import fun.dungeon.debauchery.arskingdom.battlemage.BattlemageGear;
import fun.dungeon.debauchery.arskingdom.battlemage.BattlemageStatus;
import fun.dungeon.debauchery.arskingdom.battlemage.MageSchool;
import fun.dungeon.debauchery.arskingdom.building.BuildingMageTower;
import fun.dungeon.debauchery.arskingdom.job.JobBattlemage;
import fun.dungeon.debauchery.arskingdom.registry.AKEquipmentTypes;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

/**
 * Battlemage AI. It guards and patrols like any MineColonies guard, but only with a spellbook and a full set of
 * Arcanist armor at or below the tower's level; without them it stays at the tower, asks for them and shows a blocking
 * "not allowed to work" message. Combat comes in Chunk 9.
 *
 * <p>The armor goes through MineColonies' own guard gear list ({@code itemsNeeded}), so it is taken from the tower,
 * requested, equipped and upgraded the same way as vanilla guard armor. The spellbook isn't a weapon to MineColonies,
 * so it is fetched, requested and held here.
 */
public class EntityAIBattlemage<J extends JobBattlemage<J>> extends AbstractEntityAIGuard<J, AbstractBuildingGuards> {
    /** Validator key of the blocking interaction; the shown text carries the tier as an argument. */
    public static final String NOT_ALLOWED_KEY = "com.ars_kingdom.interaction.battlemage.unequipped";
    private static final Tuple<Integer, Integer> ANY_CITIZEN_LEVEL = new Tuple<>(0, 99);

    private final MageSchool school;
    private final BattlemageCombatAI combat;

    public EntityAIBattlemage(@NotNull J job) {
        super(job);
        this.school = job.getSchool();
        // No vanilla tools or leather-to-diamond gear: our five types replace them.
        toolsNeeded.clear();
        itemsNeeded.clear();
        for (int level = 1; level <= BuildingMageTower.MAX_LEVEL; level++) {
            List<GuardGear> gear = new ArrayList<>();
            for (BattlemageEquipment.ArmorPiece piece : BattlemageEquipment.armorPieces()) {
                gear.add(new GuardGear(piece.type(), piece.slot(), 1, level, ANY_CITIZEN_LEVEL, new Tuple<>(level, level)));
            }
            itemsNeeded.add(gear);
        }
        this.combat = new BattlemageCombatAI((EntityCitizen) worker, getStateAI(), this, job);
    }

    private int maxLevel() {
        return building.getMaxEquipmentLevel();
    }

    private InventoryCitizen inventory() {
        return worker.getInventoryCitizen();
    }

    private boolean equipped() {
        return BattlemageEquipment.isFullyEquipped(inventory(), maxLevel());
    }

    /** Guards drop out of combat and patrol into preparing when this is false. */
    @Override
    public boolean hasTool() {
        return equipped();
    }

    @Override
    protected IAIState decide() {
        applySchoolColors();
        if (!equipped()) {
            worker.getCitizenData().triggerInteraction(new StandardInteraction(
                    Component.translatable(NOT_ALLOWED_KEY + ".tier", maxLevel()), Component.translatable(NOT_ALLOWED_KEY),
                    ChatPriority.BLOCKING));
            worker.getCitizenData().setVisibleStatus(BattlemageStatus.UNEQUIPPED);
            requestMissingGear();
            // A guard's START_WORKING goes straight back to DECIDE without walking, so walk to the tower here;
            // preparing there picks up and equips whatever has arrived.
            return walkToBuilding() ? AIWorkerState.PREPARING : getState();
        }
        if (worker.getCitizenData().getStatus() == BattlemageStatus.UNEQUIPPED) {
            worker.getCitizenData().setVisibleStatus(VisibleCitizenStatus.WORKING);
        }
        return super.decide();
    }

    /**
     * Posts a {@code Tool(type, 1, towerLevel)} request for the book and each armor piece the battlemage neither has
     * nor can take from the tower, so they show in its request tab. Open requests aren't duplicated.
     */
    private void requestMissingGear() {
        int max = maxLevel();
        if (!BattlemageEquipment.hasSpellbook(inventory(), max) && !inTower(AKEquipmentTypes.SPELLBOOK.get(), max)) {
            checkForToolOrWeaponAsync(AKEquipmentTypes.SPELLBOOK.get(), 1, max);
        }
        for (BattlemageEquipment.ArmorPiece piece : BattlemageEquipment.armorPieces()) {
            if (!BattlemageEquipment.isWorn(inventory(), piece, max)
                    && !InventoryUtils.isEquipmentInItemHandler(inventory(), piece.type(), 1, max)
                    && !inTower(piece.type(), max)) {
                checkForToolOrWeaponAsync(piece.type(), 1, max);
            }
        }
    }

    private boolean inTower(EquipmentTypeEntry type, int max) {
        return InventoryUtils.hasItemInProvider(building, stack -> ItemStackUtils.hasEquipmentLevel(stack, type, 1, max));
    }

    /** Runs while preparing at the tower: armor via the guard gear list, then the spellbook. */
    @Override
    protected void atBuildingActions() {
        super.atBuildingActions();
        int max = maxLevel();
        Predicate<ItemStack> validBook = stack -> ItemStackUtils.hasEquipmentLevel(stack, AKEquipmentTypes.SPELLBOOK.get(), 1, max);
        if (!BattlemageEquipment.hasSpellbook(inventory(), max)) {
            InventoryUtils.transferXOfFirstSlotInProviderWithIntoNextFreeSlotInItemHandler(building, validBook, 1, inventory());
        }
        requestMissingGear();
        applySchoolColors();
    }

    /** Holds the best allowed book and gives the book and worn armor the school colour. */
    private void applySchoolColors() {
        int max = maxLevel();
        int bestSlot = -1;
        int bestLevel = 0;
        for (int slot = 0; slot < inventory().getSlots(); slot++) {
            ItemStack stack = inventory().getStackInSlot(slot);
            if (ItemStackUtils.hasEquipmentLevel(stack, AKEquipmentTypes.SPELLBOOK.get(), 1, max)
                    && BattlemageGear.spellbookLevel(stack) > bestLevel) {
                bestLevel = BattlemageGear.spellbookLevel(stack);
                bestSlot = slot;
            }
        }
        if (bestSlot >= 0) {
            color(inventory().getStackInSlot(bestSlot));
            if (worker.getMainHandItem() != inventory().getStackInSlot(bestSlot)) {
                CitizenItemUtils.setMainHeldItem(worker, bestSlot);
            }
        }
        for (BattlemageEquipment.ArmorPiece piece : BattlemageEquipment.armorPieces()) {
            ItemStack worn = inventory().getArmorInSlot(piece.slot());
            if (!worn.isEmpty()) {
                color(worn);
            }
        }
    }

    private void color(ItemStack stack) {
        if (stack.get(DataComponents.BASE_COLOR) != school.dye() && BattlemageGear.applySchoolColor(stack, school)) {
            inventory().markDirty();
        }
    }
}
