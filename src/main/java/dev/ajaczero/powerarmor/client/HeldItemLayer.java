package dev.ajaczero.powerarmor.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.ajaczero.powerarmor.entity.PowerArmorEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.renderer.GeoRenderer;
import software.bernie.geckolib.renderer.layer.BlockAndItemGeoLayer;

/**
 * Draws the pilot's selected item in the suit's left hand (the "left_hand" grip bone), the way a player's
 * held item is drawn, only bigger. Skipped in the pilot's own cockpit view: the suit's upper body
 * isn't drawn there; the cockpit viewmodel's gauntlet holds the item instead (CockpitHands).
 */
public class HeldItemLayer extends BlockAndItemGeoLayer<PowerArmorEntity> {
    /** The suit's hand is about 1.5x a player's. */
    private static final float SCALE = 1.5F;

    public HeldItemLayer(GeoRenderer<PowerArmorEntity> renderer) {
        super(renderer);
    }

    @Override
    protected ItemStack getStackForBone(GeoBone bone, PowerArmorEntity suit) {
        if (!"left_hand".equals(bone.getName()) || !(suit.getControllingPassenger() instanceof Player pilot)
                || PowerArmorRenderer.isCockpitView(suit)) {
            return null;
        }
        ItemStack stack = pilot.getMainHandItem();
        return stack.isEmpty() ? null : stack;
    }

    @Override
    protected void renderStackForBone(PoseStack poseStack, GeoBone bone, ItemStack stack, PowerArmorEntity suit,
                                      MultiBufferSource buffers, float partialTick, int packedLight, int packedOverlay) {
        poseStack.pushPose();
        // Same grip as vanilla's ItemInHandLayer (the bone's -Y runs down the forearm to the fingers).
        poseStack.mulPose(Axis.XP.rotationDegrees(-90.0F));
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0F));
        poseStack.translate(-1F / 16F, 0.125F, 0.0F);
        poseStack.scale(SCALE, SCALE, SCALE);
        Minecraft.getInstance().getItemRenderer().renderStatic(suit, stack, ItemDisplayContext.THIRD_PERSON_LEFT_HAND, true,
                poseStack, buffers, suit.level(), packedLight, packedOverlay, suit.getId());
        poseStack.popPose();
    }
}
