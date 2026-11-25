package fun.dungeon.debauchery.arskingdom.mixin;

import com.minecolonies.api.client.render.modeltype.CitizenModel;
import com.minecolonies.api.colony.ICitizenDataView;
import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import com.minecolonies.core.client.render.CitizenArmorLayer;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import software.bernie.geckolib.util.InternalUtil;

/**
 * GeckoLib armor (Ars Nouveau's Arcanist set) on citizens. GeckoLib renders its armor through a mixin on vanilla
 * {@code HumanoidArmorLayer.render}, but MineColonies' citizen armor layer overrides {@code render} and calls
 * {@code renderArmorPiece} itself, so GeckoLib never gets the piece and vanilla draws its plain armor cubes with the
 * GeckoLib texture (a garbled hood; the other pieces end up invisible). This hands GeckoLib armor to GeckoLib the same
 * way its own mixin does; any other armor goes on to MineColonies' normal path.
 */
@Mixin(CitizenArmorLayer.class)
public abstract class CitizenArmorLayerMixin {
    @Inject(method = "renderArmorPiece", at = @At("HEAD"), cancellable = true)
    private void ars_kingdom$renderGeckoLibArmor(PoseStack poseStack, MultiBufferSource bufferSource, AbstractEntityCitizen citizen,
            EquipmentSlot slot, int light, HumanoidModel<AbstractEntityCitizen> armorModel, ICitizenDataView view, CallbackInfo ci) {
        ItemStack display = view.getDisplayArmor(slot);
        ItemStack stack = display.isEmpty() ? citizen.getItemBySlot(slot) : display;
        if (stack.isEmpty()) {
            return;
        }
        @SuppressWarnings("unchecked")
        CitizenModel<AbstractEntityCitizen> parent =
                (CitizenModel<AbstractEntityCitizen>) ((CitizenArmorLayer<?, ?, ?>) (Object) this).getParentModel();
        float partialTick = Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(true);
        boolean rendered = InternalUtil.tryRenderGeoArmorPiece(poseStack, bufferSource, citizen, stack, slot, parent, armorModel,
                partialTick, light, citizen.walkAnimation.position(partialTick), citizen.walkAnimation.speed(partialTick),
                citizen.tickCount + partialTick, 0f, 0f, CitizenArmorLayerMixin::ars_kingdom$setPartVisibility);
        if (rendered) {
            ci.cancel();
        }
    }

    /** Vanilla {@code HumanoidArmorLayer.setPartVisibility}. */
    private static void ars_kingdom$setPartVisibility(HumanoidModel<AbstractEntityCitizen> model, EquipmentSlot slot) {
        model.setAllVisible(false);
        switch (slot) {
            case HEAD -> {
                model.head.visible = true;
                model.hat.visible = true;
            }
            case CHEST -> {
                model.body.visible = true;
                model.rightArm.visible = true;
                model.leftArm.visible = true;
            }
            case LEGS -> {
                model.body.visible = true;
                model.rightLeg.visible = true;
                model.leftLeg.visible = true;
            }
            case FEET -> {
                model.rightLeg.visible = true;
                model.leftLeg.visible = true;
            }
            default -> {
            }
        }
    }
}
