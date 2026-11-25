package fun.dungeon.debauchery.arskingdom.hut;

import com.minecolonies.api.blocks.AbstractBlockHut;
import com.minecolonies.core.tileentities.TileEntityColonyBuilding;
import fun.dungeon.debauchery.arskingdom.ArsKingdom;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Base for every Ars Kingdom hut block. MineColonies' defaults assume its own namespace and its own
 * building block-entity type (whose valid blocks are only MineColonies huts), so both are replaced here.
 */
public abstract class AKBlockHut<B extends AKBlockHut<B>> extends AbstractBlockHut<B> {
    /** {@code ars_kingdom:<hutName>}, e.g. {@code ars_kingdom:blockhutsourcesanctum}. */
    @Override
    public ResourceLocation getRegistryName() {
        return ArsKingdom.id(getHutName());
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        TileEntityColonyBuilding building = new AKTileEntityColonyBuilding(pos, state);
        building.registryName = getBuildingEntry().getRegistryName();
        return building;
    }
}
