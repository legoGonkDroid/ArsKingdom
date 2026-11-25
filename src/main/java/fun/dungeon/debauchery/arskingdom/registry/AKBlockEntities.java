package fun.dungeon.debauchery.arskingdom.registry;

import fun.dungeon.debauchery.arskingdom.ArsKingdom;
import fun.dungeon.debauchery.arskingdom.hut.AKTileEntityColonyBuilding;
import java.util.ArrayList;
import net.minecraft.core.Direction;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import java.util.List;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class AKBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, ArsKingdom.MODID);

    /** One type for every Ars Kingdom hut. Add each new hut block to the valid blocks here. */
    @SuppressWarnings("DataFlowIssue")
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<AKTileEntityColonyBuilding>> BUILDING =
            BLOCK_ENTITIES.register("colony_building", () -> BlockEntityType.Builder
                    .of(AKTileEntityColonyBuilding::new, hutBlocks())
                    .build(null));

    private static Block[] hutBlocks() {
        List<Block> blocks = new ArrayList<>();
        blocks.add(AKBlocks.SOURCE_SANCTUM.get());
        blocks.add(AKBlocks.ARCANIST_WORKSHOP.get());
        AKBlocks.MAGE_TOWERS.values().forEach(block -> blocks.add(block.get()));
        return blocks.toArray(Block[]::new);
    }

    /**
     * MineColonies registers the hut inventory capability only for its own block entity types. Without this, anything
     * that reads a hut through the capability (a worker taking materials from its hut, couriers delivering into it)
     * sees our huts as empty.
     */
    public static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, BUILDING.get(),
                (be, side) -> be.getItemHandlerCap((Direction) side));
    }

    private AKBlockEntities() {
    }
}
