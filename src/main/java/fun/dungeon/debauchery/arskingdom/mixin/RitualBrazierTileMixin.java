package fun.dungeon.debauchery.arskingdom.mixin;

import com.hollingsworth.arsnouveau.client.particle.ParticleColor;
import com.hollingsworth.arsnouveau.common.block.tile.RitualBrazierTile;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import fun.dungeon.debauchery.arskingdom.config.AKConfig;
import fun.dungeon.debauchery.arskingdom.compat.ArsDecoBridge;
import java.util.Optional;
import net.minecraft.world.item.DyeColor;
import org.spongepowered.asm.mixin.Mixin;

/**
 * M1c: ritual particles follow the brazier's Ars Deco dye. {@code makeParticle} draws both the brazier's
 * ritual particles and its decorative flame, and is also how a brazier makes its linked brazier relays
 * (subclasses) mimic it, so each relay ends up in its own dye. Runs client side; the dye arrives with the
 * block entity's update tag. A decorative brazier keeps a color the player gave it explicitly.
 */
@Mixin(value = RitualBrazierTile.class, remap = false)
public abstract class RitualBrazierTileMixin {
    @WrapMethod(method = "makeParticle")
    private void ars_kingdom$dyedParticles(ParticleColor center, ParticleColor outer, int intensity, Operation<Void> original) {
        RitualBrazierTile self = (RitualBrazierTile) (Object) this;
        if (AKConfig.SPEC.isLoaded() && AKConfig.RECOLOR_RITUALS.get()) {
            Optional<DyeColor> dye = ArsDecoBridge.blockEntityColor(self);
            boolean customDecorative = self.isDecorative && self.color != null
                    && self.color.getColor() != ParticleColor.defaultParticleColor().getColor();
            if (dye.isPresent() && !customDecorative) {
                ParticleColor color = ArsDecoBridge.particleColor(dye.get());
                original.call(color, color, intensity);
                return;
            }
        }
        original.call(center, outer, intensity);
    }
}
