package fun.dungeon.debauchery.arskingdom.compat;

import com.hollingsworth.arsnouveau.client.particle.ParticleColor;
import java.util.Optional;
import lambdamagus.ars_deco.dye.ArsDecoDyeableBlockEntity;
import lambdamagus.ars_deco.dye.ClientPlacedDyeColors;
import lambdamagus.ars_deco.dye.DyeBlockColorApplier;
import lambdamagus.ars_deco.dye.DyeParticleColors;
import lambdamagus.ars_deco.dye.DyeTarget;
import lambdamagus.ars_deco.dye.DyeableArsBlocks;
import lambdamagus.ars_deco.dye.PlacedDyeColors;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The only class that touches {@code lambdamagus.ars_deco.*}, which is not a public API.
 * If an Ars Deco update moves things around, only this file should need to change.
 */
public final class ArsDecoBridge {
    /** Which colony rule toggle governs a target block. */
    public enum Category {
        /** Functional Ars blocks: jars, relays, sourcelinks, braziers, turrets, apparatus, pedestals... ({@code dye_machines}). */
        MACHINE,
        /** Sourcestone, archwood, sconces, grates, lanterns... ({@code dye_decor}). */
        DECOR
    }

    private ArsDecoBridge() {
    }

    public static boolean isTarget(Block block) {
        return DyeableArsBlocks.target(block).isPresent();
    }

    public static Optional<DyeTarget> target(Block block) {
        return DyeableArsBlocks.target(block);
    }

    /**
     * Machines are target blocks with a block entity, except decorative ones (sconces, signs, lanterns),
     * which count as decor even though Ars gives them a block entity.
     */
    public static Optional<Category> category(Block block) {
        return target(block).map(t -> categoryOf(block, t.blockId().getPath()));
    }

    static Category categoryOf(Block block, String path) {
        boolean decorative = path.contains("sconce") || path.contains("sign") || path.contains("lantern");
        return block instanceof EntityBlock && !decorative ? Category.MACHINE : Category.DECOR;
    }

    /** The Ars Deco color on a placed block, if any. Works on both sides. */
    public static Optional<DyeColor> getColor(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        Optional<DyeTarget> target = target(state.getBlock());
        if (target.isEmpty()) {
            return Optional.empty();
        }
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof ArsDecoDyeableBlockEntity) {
            return DyeableArsBlocks.placedColor(level, pos);
        }
        if (level instanceof ServerLevel server) {
            return PlacedDyeColors.get(server).getColor(pos, target.get().blockId());
        }
        return ClientPlacedDyeColors.getColor(pos, state);
    }

    /** The Ars Deco color stored on a block entity (synced to clients with the block entity). */
    public static Optional<DyeColor> blockEntityColor(BlockEntity be) {
        return be instanceof ArsDecoDyeableBlockEntity dyeable ? dyeable.arsDeco$getColor() : Optional.empty();
    }

    /** Dyes the block at {@code pos} exactly as an Ars Deco dye would. Returns false if it isn't a target. */
    public static boolean apply(Level level, BlockPos pos, DyeColor dye) {
        Optional<DyeTarget> target = target(level.getBlockState(pos).getBlock());
        target.ifPresent(t -> DyeBlockColorApplier.apply(level, pos, t, dye));
        return target.isPresent();
    }

    /**
     * Removes the Ars Deco color from the block at {@code pos}. Returns false if it isn't a target.
     * Ars Deco's own {@code clear} only resets its SavedData store (blocks without a block entity), so
     * block-entity colors are reset here directly; the update packet carries the removal to clients
     * because Ars Deco's load mixin drops the color when {@code ars_deco_color} is absent.
     */
    public static boolean clear(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        Optional<DyeTarget> target = target(state.getBlock());
        if (target.isEmpty()) {
            return false;
        }
        if (level.getBlockEntity(pos) instanceof ArsDecoDyeableBlockEntity dyeable) {
            dyeable.arsDeco$setColor(null);
            ((BlockEntity) dyeable).setChanged();
            level.sendBlockUpdated(pos, state, state, Block.UPDATE_ALL);
        }
        DyeBlockColorApplier.clear(level, pos, target.get());
        return true;
    }

    /** The particle color Ars Deco gives blocks dyed with {@code dye}. */
    public static ParticleColor particleColor(DyeColor dye) {
        return DyeParticleColors.particleColor(dye);
    }
}
