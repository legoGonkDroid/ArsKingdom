package fun.dungeon.debauchery.arskingdom.registry;

import fun.dungeon.debauchery.arskingdom.ArsKingdom;
import fun.dungeon.debauchery.arskingdom.battlemage.BattlemageSchools;
import fun.dungeon.debauchery.arskingdom.battlemage.MageSchool;
import fun.dungeon.debauchery.arskingdom.hut.blocks.BlockHutMageTower;
import fun.dungeon.debauchery.arskingdom.hut.blocks.BlockHutSourceSanctum;
import java.util.EnumMap;
import java.util.Map;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class AKBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(ArsKingdom.MODID);

    public static final DeferredBlock<BlockHutSourceSanctum> SOURCE_SANCTUM =
            BLOCKS.register(BlockHutSourceSanctum.HUT_NAME, BlockHutSourceSanctum::new);

    /** Mage Tower huts, one per registered school: {@code blockhutmagetower<school>}. */
    public static final Map<MageSchool, DeferredBlock<BlockHutMageTower>> MAGE_TOWERS = new EnumMap<>(MageSchool.class);

    static {
        for (BattlemageSchools.Entry entry : BattlemageSchools.REGISTERED) {
            MageSchool school = entry.school();
            MAGE_TOWERS.put(school, BLOCKS.register(BlockHutMageTower.hutName(school), () -> BlockHutMageTower.create(school)));
        }
    }

    private AKBlocks() {
    }
}
