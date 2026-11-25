package fun.dungeon.debauchery.arskingdom.hut.blocks;

import com.minecolonies.api.colony.buildings.registry.BuildingEntry;
import fun.dungeon.debauchery.arskingdom.hut.AKBlockHut;
import fun.dungeon.debauchery.arskingdom.registry.AKBuildings;

/** Source Sanctum hut: the colony's source keeper (Chunk 6) and its Ars Kingdom rules tab. */
public class BlockHutSourceSanctum extends AKBlockHut<BlockHutSourceSanctum> {
    public static final String HUT_NAME = "blockhutsourcesanctum";

    @Override
    public String getHutName() {
        return HUT_NAME;
    }

    @Override
    public BuildingEntry getBuildingEntry() {
        return AKBuildings.SOURCE_SANCTUM.get();
    }
}
