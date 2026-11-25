package fun.dungeon.debauchery.arskingdom.building;

import com.minecolonies.api.colony.IColony;
import com.minecolonies.core.colony.buildings.AbstractBuilding;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Tuple;
import net.minecraft.server.level.ServerLevel;

/**
 * Source Sanctum: the colony's source keeper (Chunk 6) and its Ars Kingdom rules tab
 * ({@link fun.dungeon.debauchery.arskingdom.building.modules.RulesSettingsModule}).
 */
public class BuildingSourceSanctum extends AbstractBuilding {
    /** Blueprint base name: {@code sourcesanctum1.blueprint}, ... */
    public static final String SCHEMATIC = "sourcesanctum";

    /** Fuel the keeper keeps in hand rather than dumping back into storage. */
    private static final int KEEP_FUEL = 64;

    public BuildingSourceSanctum(IColony colony, BlockPos pos) {
        super(colony, pos);
        keepX.put(SourceKeeping::isFuel, new Tuple<>(KEEP_FUEL, false));
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

    /** Whether any source jar stands inside the building's bounds (full or not). */
    public boolean hasAnyJar() {
        if (!(getColony().getWorld() instanceof ServerLevel level)) {
            return false;
        }
        Tuple<BlockPos, BlockPos> corners = getCorners();
        return !SourceKeeping.jars(level, corners.getA(), corners.getB(), false).isEmpty();
    }
}
