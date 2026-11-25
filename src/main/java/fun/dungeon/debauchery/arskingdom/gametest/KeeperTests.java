package fun.dungeon.debauchery.arskingdom.gametest;

import com.hollingsworth.arsnouveau.common.block.tile.SourceJarTile;
import com.minecolonies.api.colony.jobs.registry.JobEntry;
import com.minecolonies.api.sounds.EventType;
import com.minecolonies.api.sounds.ModSoundEvents;
import com.minecolonies.apiimp.CommonMinecoloniesAPIImpl;
import fun.dungeon.debauchery.arskingdom.ArsKingdom;
import fun.dungeon.debauchery.arskingdom.building.SourceKeeping;
import fun.dungeon.debauchery.arskingdom.registry.AKBuildings;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Chunk 6: the Source Sanctum keeper's rules and registration. */
@GameTestHolder(ArsKingdom.MODID)
@PrefixGameTestTemplate(false)
public final class KeeperTests {
    private static final String EMPTY = "gametest/empty";

    private KeeperTests() {
    }

    @GameTest(template = EMPTY)
    public static void fuelRules(GameTestHelper helper) {
        helper.assertTrue(SourceKeeping.isFuel(new ItemStack(Items.COAL)), "coal is fuel");
        helper.assertTrue(SourceKeeping.isFuel(new ItemStack(Items.OAK_LOG)), "logs are fuel");
        helper.assertTrue(!SourceKeeping.isFuel(new ItemStack(Items.LAVA_BUCKET)), "lava buckets leave a bucket behind: not fuel");
        helper.assertTrue(!SourceKeeping.isFuel(new ItemStack(Items.DIRT)), "dirt is not fuel");

        ItemStack coal = new ItemStack(Items.COAL);
        // As Ars's Volcanic Sourcelink: burn time / 12 (coal 1600 -> 133).
        helper.assertTrue(SourceKeeping.sourceFor(coal, 1, 0) == 133, "coal, level 1, Mana 0: " + SourceKeeping.sourceFor(coal, 1, 0));
        helper.assertTrue(SourceKeeping.sourceFor(coal, 1, 50) == 267, "Mana 50 doubles it: " + SourceKeeping.sourceFor(coal, 1, 50));
        helper.assertTrue(SourceKeeping.sourceFor(coal, 2, 0) == 267, "level 2 doubles it: " + SourceKeeping.sourceFor(coal, 2, 0));
        helper.assertTrue(SourceKeeping.sourceFor(new ItemStack(Items.DIRT), 3, 99) == 0, "non-fuel makes nothing");
        helper.succeed();
    }

    /** Only jars inside the box with room are returned, emptiest first. */
    @GameTest(template = EMPTY)
    public static void jarsWithRoom(GameTestHelper helper) {
        var jarBlock = BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath("ars_nouveau", "source_jar"));
        BlockPos full = new BlockPos(1, 1, 1), half = new BlockPos(3, 1, 1), empty = new BlockPos(5, 1, 1), outside = new BlockPos(7, 1, 1);
        for (BlockPos p : List.of(full, half, empty, outside)) {
            helper.setBlock(p, jarBlock);
        }
        SourceJarTile fullJar = helper.getBlockEntity(full);
        fullJar.setSource(fullJar.getMaxSource());
        SourceJarTile halfJar = helper.getBlockEntity(half);
        halfJar.setSource(halfJar.getMaxSource() / 2);

        List<SourceJarTile> jars = SourceKeeping.jarsWithRoom(helper.getLevel(), helper.absolutePos(new BlockPos(0, 0, 0)),
                helper.absolutePos(new BlockPos(6, 2, 2)));
        helper.assertTrue(jars.size() == 2, "expected the half and empty jars, got " + jars.size());
        helper.assertTrue(jars.get(0).getBlockPos().equals(helper.absolutePos(empty)), "emptiest jar first");
        helper.assertTrue(jars.get(1).getBlockPos().equals(helper.absolutePos(half)), "then the half-full jar");
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void keeperRegistered(GameTestHelper helper) {
        var jobs = helper.getLevel().registryAccess().registryOrThrow(CommonMinecoloniesAPIImpl.JOBS);
        JobEntry job = jobs.get(ArsKingdom.id("sourcekeeper"));
        helper.assertTrue(job != null, "ars_kingdom:sourcekeeper missing from MineColonies' job registry");
        var sanctum = AKBuildings.SOURCE_SANCTUM.get();
        helper.assertTrue(sanctum.getModuleProducers().contains(AKBuildings.SOURCE_KEEPER_WORK), "Sanctum should have the keeper's work module");
        helper.succeed();
    }

    /** MineColonies NPEs on a level-up voice line for any job missing from its sound map. */
    @GameTest(template = EMPTY)
    public static void keeperHasVoice(GameTestHelper helper) {
        var voice = ModSoundEvents.CITIZEN_SOUND_EVENTS.get("sourcekeeper");
        helper.assertTrue(voice != null, "sourcekeeper missing from MineColonies' citizen sound map");
        helper.assertTrue(voice.get(EventType.SUCCESS) != null && !voice.get(EventType.SUCCESS).isEmpty(), "no level-up voice lines");
        helper.succeed();
    }
}
