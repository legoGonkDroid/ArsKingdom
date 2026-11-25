package fun.dungeon.debauchery.arskingdom.gametest;

import fun.dungeon.debauchery.arskingdom.ArsKingdom;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Chunk 0 smoke test: every required mod is loaded, and the classes later chunks build on resolve.
 * Run with {@code ./gradlew runGameTestServer}.
 */
@GameTestHolder(ArsKingdom.MODID)
@PrefixGameTestTemplate(false)
public final class SkeletonTests {
    private static final String EMPTY = "gametest/empty";

    private static final String[] REQUIRED_MODS = {
            ArsKingdom.MODID, "ars_nouveau", "ars_deco", "minecolonies", "structurize", "archwoodcolonies"
    };

    private static final String[] REQUIRED_CLASSES = {
            "com.minecolonies.api.IMinecoloniesAPI",
            "com.minecolonies.core.colony.buildings.AbstractBuilding",
            "com.ldtteam.structurize.storage.StructurePacks",
            "com.hollingsworth.arsnouveau.common.block.tile.SourceJarTile",
            "lambdamagus.ars_deco.dye.DyeableArsBlocks",
            // Mixin targets: loading them applies our mixins, which fail loudly (require = 1) if they don't fit.
            "com.hollingsworth.arsnouveau.common.entity.EntityFollowProjectile",
            "com.hollingsworth.arsnouveau.common.block.tile.RitualBrazierTile",
    };

    private SkeletonTests() {
    }

    @GameTest(template = EMPTY)
    public static void requiredModsLoaded(GameTestHelper helper) {
        for (String id : REQUIRED_MODS) {
            helper.assertTrue(ModList.get().isLoaded(id), "required mod not loaded: " + id);
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void dependencyClassesResolve(GameTestHelper helper) {
        for (String name : REQUIRED_CLASSES) {
            try {
                Class.forName(name, false, SkeletonTests.class.getClassLoader());
            } catch (ClassNotFoundException e) {
                helper.fail("class not found: " + name);
                return;
            }
        }
        helper.succeed();
    }
}
