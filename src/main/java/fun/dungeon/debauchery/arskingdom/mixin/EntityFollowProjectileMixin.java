package fun.dungeon.debauchery.arskingdom.mixin;

import com.hollingsworth.arsnouveau.client.particle.ParticleColor;
import com.hollingsworth.arsnouveau.common.entity.EntityFollowProjectile;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import fun.dungeon.debauchery.arskingdom.colony.ColonyLookup;
import fun.dungeon.debauchery.arskingdom.colony.ColonyRules;
import fun.dungeon.debauchery.arskingdom.compat.ArsDecoBridge;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.DyeColor;
import org.spongepowered.asm.mixin.Mixin;

/**
 * M1b: source transfers from a jar (or other source provider) to a machine hardcode Ars's default
 * 255,25,180. When the block they leave is Ars Deco-dyed, use that dye's particle color instead, the same
 * color Ars Deco gives dyed relays. Server side; the color travels to clients with the projectile entity.
 */
@Mixin(value = EntityFollowProjectile.class, remap = false)
public abstract class EntityFollowProjectileMixin {
    @WrapMethod(method = "spawn(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/BlockPos;III)V")
    private static void ars_kingdom$dyedSource(ServerLevel level, BlockPos from, BlockPos to, int r, int g, int b,
                                               Operation<Void> original) {
        if (r == 255 && g == 25 && b == 180
                && ColonyRules.recolorTransferParticles(ColonyLookup.colony(level, from))) {
            Optional<DyeColor> dye = ArsDecoBridge.getColor(level, from);
            if (dye.isPresent()) {
                ParticleColor color = ArsDecoBridge.particleColor(dye.get());
                original.call(level, from, to, color.getRedInt(), color.getGreenInt(), color.getBlueInt());
                return;
            }
        }
        original.call(level, from, to, r, g, b);
    }
}
