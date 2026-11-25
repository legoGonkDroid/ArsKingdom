package fun.dungeon.debauchery.arskingdom.hut.blocks;

import com.minecolonies.api.colony.buildings.registry.BuildingEntry;
import fun.dungeon.debauchery.arskingdom.hut.AKBlockHut;
import fun.dungeon.debauchery.arskingdom.registry.AKBuildings;

/** Arcanist Workshop hut. */
public class BlockHutArcanistWorkshop extends AKBlockHut<BlockHutArcanistWorkshop> {
    public static final String HUT_NAME = "blockhutarcanistworkshop";

    @Override
    public String getHutName() {
        return HUT_NAME;
    }

    @Override
    public BuildingEntry getBuildingEntry() {
        return AKBuildings.ARCANIST_WORKSHOP.get();
    }
}
