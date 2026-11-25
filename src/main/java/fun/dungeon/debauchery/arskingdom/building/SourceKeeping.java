package fun.dungeon.debauchery.arskingdom.building;

import com.hollingsworth.arsnouveau.common.block.tile.SourceJarTile;
import fun.dungeon.debauchery.arskingdom.config.AKConfig;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;

/**
 * The source keeper's rules (Chunk 6), kept free of AI code so they can be gametested.
 *
 * <p>Fuel is any furnace fuel that leaves nothing behind (no lava buckets). Like Ars Nouveau's Volcanic
 * Sourcelink, one fuel item is worth {@code burnTime / 12} source (coal: 133); the keeper multiplies that by the
 * Sanctum's level and its Mana skill: {@code × level × (1 + mana / 50) × keeper_efficiency}.
 */
public final class SourceKeeping {
    /** Burn time per point of source, as Ars's Volcanic Sourcelink. */
    public static final int BURN_TICKS_PER_SOURCE = 12;

    private SourceKeeping() {
    }

    public static boolean isFuel(ItemStack stack) {
        return !stack.isEmpty() && stack.getBurnTime(RecipeType.SMELTING) > 0 && !stack.hasCraftingRemainingItem();
    }

    /** Source made from one item of {@code fuel}. */
    public static int sourceFor(ItemStack fuel, int buildingLevel, int manaSkill) {
        if (!isFuel(fuel)) {
            return 0;
        }
        double base = (double) fuel.getBurnTime(RecipeType.SMELTING) / BURN_TICKS_PER_SOURCE;
        double efficiency = AKConfig.SPEC.isLoaded() ? AKConfig.KEEPER_EFFICIENCY.get() : 1.0;
        return (int) Math.round(base * Math.max(1, buildingLevel) * (1.0 + Math.max(0, manaSkill) / 50.0) * efficiency);
    }

    /** Source jars inside the box with room left, emptiest (by fill fraction) first. */
    public static List<SourceJarTile> jarsWithRoom(ServerLevel level, BlockPos cornerA, BlockPos cornerB) {
        return jars(level, cornerA, cornerB, true);
    }

    /** Source jars inside the box (only those with room when {@code withRoomOnly}), emptiest first. */
    public static List<SourceJarTile> jars(ServerLevel level, BlockPos cornerA, BlockPos cornerB, boolean withRoomOnly) {
        AABB box = AABB.encapsulatingFullBlocks(cornerA, cornerB);
        List<SourceJarTile> jars = new ArrayList<>();
        int minCx = Math.min(cornerA.getX(), cornerB.getX()) >> 4, maxCx = Math.max(cornerA.getX(), cornerB.getX()) >> 4;
        int minCz = Math.min(cornerA.getZ(), cornerB.getZ()) >> 4, maxCz = Math.max(cornerA.getZ(), cornerB.getZ()) >> 4;
        for (int cx = minCx; cx <= maxCx; cx++) {
            for (int cz = minCz; cz <= maxCz; cz++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
                if (chunk == null) {
                    continue;
                }
                for (BlockEntity be : chunk.getBlockEntities().values()) {
                    if (be instanceof SourceJarTile jar && box.contains(be.getBlockPos().getCenter())
                            && (!withRoomOnly || jar.getSource() < jar.getMaxSource())) {
                        jars.add(jar);
                    }
                }
            }
        }
        jars.sort(Comparator.comparingDouble(j -> (double) j.getSource() / Math.max(1, j.getMaxSource())));
        return jars;
    }
}
