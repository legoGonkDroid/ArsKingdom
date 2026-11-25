package fun.dungeon.debauchery.arskingdom.building;

import com.minecolonies.api.colony.IColony;
import com.minecolonies.core.colony.buildings.AbstractBuilding;
import net.minecraft.core.BlockPos;

/** Arcanist Workshop: crafts spellbooks (and other Ars items no Archwood crafter makes), paying source from its jars. */
public class BuildingArcanistWorkshop extends AbstractBuilding {
    /** Blueprint base name: {@code arcanistworkshop1.blueprint}. */
    public static final String SCHEMATIC = "arcanistworkshop";

    public BuildingArcanistWorkshop(IColony colony, BlockPos pos) {
        super(colony, pos);
    }

    @Override
    public String getSchematicName() {
        return SCHEMATIC;
    }

    /** Raised once the level 2 and 3 blueprints exist. */
    @Override
    public int getMaxBuildingLevel() {
        return 1;
    }
}
