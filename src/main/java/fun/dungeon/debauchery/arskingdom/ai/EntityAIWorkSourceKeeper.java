package fun.dungeon.debauchery.arskingdom.ai;

import static com.minecolonies.api.entity.ai.statemachine.states.AIWorkerState.DECIDE;
import static com.minecolonies.api.entity.ai.statemachine.states.AIWorkerState.IDLE;
import static com.minecolonies.api.entity.ai.statemachine.states.AIWorkerState.START_WORKING;

import com.google.common.reflect.TypeToken;
import com.hollingsworth.arsnouveau.client.particle.ParticleColor;
import com.hollingsworth.arsnouveau.common.block.tile.SourceJarTile;
import com.hollingsworth.arsnouveau.common.entity.EntityFollowProjectile;
import com.minecolonies.api.colony.interactionhandling.ChatPriority;
import com.minecolonies.api.colony.requestsystem.requestable.Burnable;
import com.minecolonies.api.entity.ai.statemachine.AITarget;
import com.minecolonies.api.entity.ai.statemachine.states.IAIState;
import com.minecolonies.api.util.InventoryUtils;
import com.minecolonies.core.colony.interactionhandling.StandardInteraction;
import com.minecolonies.core.entity.ai.workers.AbstractEntityAIInteract;
import fun.dungeon.debauchery.arskingdom.building.BuildingSourceSanctum;
import fun.dungeon.debauchery.arskingdom.building.SourceKeeping;
import fun.dungeon.debauchery.arskingdom.colony.ColonyRules;
import fun.dungeon.debauchery.arskingdom.compat.ArsDecoBridge;
import fun.dungeon.debauchery.arskingdom.job.JobSourceKeeper;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Tuple;
import net.minecraft.world.item.ItemStack;

/**
 * The source keeper's work loop:
 * <ol>
 *   <li>DECIDE: any jar in the Sanctum with room? Fuel in hand? Fuel in the Sanctum? Otherwise request fuel.</li>
 *   <li>GET_FUEL: walk to the hut and take a stack of fuel from the Sanctum's storage.</li>
 *   <li>FILL_JAR: walk to the target jar and burn one fuel item into it (particles in the jar's dye).</li>
 * </ol>
 * The keeper fills one jar completely before moving on: the target is kept until it is full (or gone), and
 * only then is the next one chosen (the emptiest jar with room).
 */
public class EntityAIWorkSourceKeeper extends AbstractEntityAIInteract<JobSourceKeeper, BuildingSourceSanctum> {
    private enum KeeperState implements IAIState {
        GET_FUEL, FILL_JAR;

        @Override
        public boolean isOkayToEat() {
            return true;
        }
    }

    /** Fuel to ask couriers for at once. */
    private static final int FUEL_REQUEST = 16;
    private static final int IDLE_DELAY = 100;

    private BlockPos targetJar;

    @SuppressWarnings("unchecked")
    public EntityAIWorkSourceKeeper(JobSourceKeeper job) {
        super(job);
        super.registerTargets(
                new AITarget<IAIState>(IDLE, START_WORKING, 20),
                new AITarget<IAIState>(START_WORKING, DECIDE, 20),
                new AITarget<IAIState>(DECIDE, this::decide, 20),
                new AITarget<IAIState>(KeeperState.GET_FUEL, this::getFuel, 20),
                new AITarget<IAIState>(KeeperState.FILL_JAR, this::fillJar, 20));
        worker.setCanPickUpLoot(true);
    }

    @Override
    public Class<BuildingSourceSanctum> getExpectedBuildingClass() {
        return BuildingSourceSanctum.class;
    }

    private List<SourceJarTile> jarsWithRoom() {
        Tuple<BlockPos, BlockPos> corners = building.getCorners();
        return SourceKeeping.jarsWithRoom((ServerLevel) world, corners.getA(), corners.getB());
    }

    private boolean hasFuelInHand() {
        return InventoryUtils.getItemCountInItemHandler(getInventory(), SourceKeeping::isFuel) > 0;
    }

    private IAIState decide() {
        List<SourceJarTile> jars = jarsWithRoom();
        if (jars.isEmpty()) {
            if (!building.hasAnyJar()) {
                worker.getCitizenData().triggerInteraction(new StandardInteraction(
                        Component.translatable("com.ars_kingdom.interaction.sourcekeeper.nojars"), ChatPriority.BLOCKING));
            }
            setDelay(IDLE_DELAY);
            return IDLE;
        }
        if (hasFuelInHand()) {
            if (!hasRoom(targetJar)) {
                targetJar = jars.get(0).getBlockPos();
            }
            return KeeperState.FILL_JAR;
        }
        if (InventoryUtils.hasItemInProvider(building, SourceKeeping::isFuel)) {
            return KeeperState.GET_FUEL;
        }
        requestFuel();
        setDelay(IDLE_DELAY);
        return IDLE;
    }

    /** Whether the current target is still a jar with room (so the keeper keeps filling it). */
    private boolean hasRoom(BlockPos pos) {
        return pos != null && world.getBlockEntity(pos) instanceof SourceJarTile jar && jar.getSource() < jar.getMaxSource()
                && isInsideBuilding(pos);
    }

    private boolean isInsideBuilding(BlockPos pos) {
        Tuple<BlockPos, BlockPos> c = building.getCorners();
        return pos.getX() >= Math.min(c.getA().getX(), c.getB().getX()) && pos.getX() <= Math.max(c.getA().getX(), c.getB().getX())
                && pos.getY() >= Math.min(c.getA().getY(), c.getB().getY()) && pos.getY() <= Math.max(c.getA().getY(), c.getB().getY())
                && pos.getZ() >= Math.min(c.getA().getZ(), c.getB().getZ()) && pos.getZ() <= Math.max(c.getA().getZ(), c.getB().getZ());
    }

    private void requestFuel() {
        boolean open = !building.getOpenRequestsOfType(worker.getCitizenData().getId(), TypeToken.of(Burnable.class)).isEmpty();
        if (!open) {
            worker.getCitizenData().createRequestAsync(new Burnable(FUEL_REQUEST));
        }
    }

    private IAIState getFuel() {
        if (!walkToBuilding()) {
            return KeeperState.GET_FUEL;
        }
        InventoryUtils.transferXOfFirstSlotInProviderWithIntoNextFreeSlotInItemHandler(building, SourceKeeping::isFuel, 64, getInventory());
        return DECIDE;
    }

    private IAIState fillJar() {
        if (targetJar == null || !(world.getBlockEntity(targetJar) instanceof SourceJarTile jar) || jar.getSource() >= jar.getMaxSource()) {
            return DECIDE;
        }
        if (!walkToWorkPos(targetJar)) {
            return KeeperState.FILL_JAR;
        }
        int slot = InventoryUtils.findFirstSlotInItemHandlerWith(getInventory(), SourceKeeping::isFuel);
        if (slot < 0) {
            return DECIDE;
        }
        ItemStack fuel = getInventory().getStackInSlot(slot);
        int source = SourceKeeping.sourceFor(fuel, building.getBuildingLevel(), getPrimarySkillLevel());
        getInventory().extractItem(slot, 1, false);
        jar.addSource(source);
        spawnParticles(jar);

        worker.getCitizenExperienceHandler().addExperience(0.5);
        incrementActionsDoneAndDecSaturation();
        // Focus (the secondary skill) makes the keeper quicker between fuel items.
        setDelay(Math.max(20, 60 - getSecondarySkillLevel() / 2));
        return DECIDE;
    }

    /** Source flies from the keeper into the jar, in the jar's dye colour when the colony's rule allows it. */
    private void spawnParticles(SourceJarTile jar) {
        ServerLevel level = (ServerLevel) world;
        BlockPos from = worker.blockPosition().above();
        ParticleColor color = ArsDecoBridge.getColor(level, jar.getBlockPos())
                .filter(d -> ColonyRules.recolorTransferParticles(building.getColony()))
                .map(ArsDecoBridge::particleColor)
                .orElse(new ParticleColor(255, 25, 180));
        EntityFollowProjectile.spawn(level, from, jar.getBlockPos(), color.getRedInt(), color.getGreenInt(), color.getBlueInt());
    }
}
