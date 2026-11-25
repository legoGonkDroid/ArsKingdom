package fun.dungeon.debauchery.arskingdom.hut.blocks;

import com.minecolonies.api.colony.buildings.registry.BuildingEntry;
import fun.dungeon.debauchery.arskingdom.battlemage.MageSchool;
import fun.dungeon.debauchery.arskingdom.hut.AKBlockHut;
import fun.dungeon.debauchery.arskingdom.registry.AKBuildings;

/** Mage Tower hut of one school; the school is fixed by the block. Create with {@link #create}. */
public class BlockHutMageTower extends AKBlockHut<BlockHutMageTower> {
    /**
     * MineColonies' block constructor calls {@link #getHutName()} before a subclass field can be set, so the school
     * being built is passed through here (blocks are registered on one thread).
     */
    private static MageSchool constructing;

    private final MageSchool school;

    private BlockHutMageTower() {
        super();
        this.school = constructing;
    }

    public static BlockHutMageTower create(MageSchool school) {
        constructing = school;
        try {
            return new BlockHutMageTower();
        } finally {
            constructing = null;
        }
    }

    public static String hutName(MageSchool school) {
        return "blockhut" + school.hutId();
    }

    public MageSchool getSchool() {
        return school != null ? school : constructing;
    }

    @Override
    public String getHutName() {
        return hutName(getSchool());
    }

    @Override
    public BuildingEntry getBuildingEntry() {
        return AKBuildings.MAGE_TOWERS.get(getSchool()).get();
    }
}
