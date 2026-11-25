package fun.dungeon.debauchery.arskingdom.hut;

import com.minecolonies.core.tileentities.TileEntityColonyBuilding;
import fun.dungeon.debauchery.arskingdom.registry.AKBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/** MineColonies' colony-building block entity, on our own {@code BlockEntityType} (valid for our hut blocks). */
public class AKTileEntityColonyBuilding extends TileEntityColonyBuilding {
    public AKTileEntityColonyBuilding(BlockPos pos, BlockState state) {
        super(AKBlockEntities.BUILDING.get(), pos, state);
    }
}
