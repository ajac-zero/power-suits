package dev.ajaczero.powerarmor.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.neoforged.neoforge.client.event.RenderHandEvent;

/**
 * In the cockpit the suit's left arm hangs below the view, so the selected item is drawn the way vanilla
 * draws a held item in first person, but on the left (the right side is the cannon): same position,
 * swing and eating motion as vanilla's ItemInHandRenderer, mirrored to the left hand.
 */
final class FirstPersonLeftHand {
    private static final int SIDE = -1; // left

    static void render(RenderHandEvent event, LocalPlayer player) {
        ItemStack stack = event.getItemStack();
        if (stack.isEmpty()) {
            return;
        }
        PoseStack pose = event.getPoseStack();
        float swing = event.getSwingProgress();
        float equip = event.getEquipProgress();
        pose.pushPose();
        boolean eating = player.isUsingItem() && player.getUsedItemHand() == event.getHand() && player.getUseItemRemainingTicks() > 0
                && (stack.getUseAnimation() == UseAnim.EAT || stack.getUseAnimation() == UseAnim.DRINK);
        if (eating) {
            applyEat(pose, event.getPartialTick(), stack, player);
            armTransform(pose, equip);
        } else {
            float sq = Mth.sqrt(swing);
            pose.translate(SIDE * -0.4F * Mth.sin(sq * Mth.PI), 0.2F * Mth.sin(sq * Mth.TWO_PI), -0.2F * Mth.sin(swing * Mth.PI));
            armTransform(pose, equip);
            float f = Mth.sin(swing * swing * Mth.PI);
            float f1 = Mth.sin(sq * Mth.PI);
            pose.mulPose(Axis.YP.rotationDegrees(SIDE * (45.0F + f * -20.0F)));
            pose.mulPose(Axis.ZP.rotationDegrees(SIDE * f1 * -20.0F));
            pose.mulPose(Axis.XP.rotationDegrees(f1 * -80.0F));
            pose.mulPose(Axis.YP.rotationDegrees(SIDE * -45.0F));
        }
        Minecraft.getInstance().getEntityRenderDispatcher().getItemInHandRenderer().renderItem(player, stack,
                ItemDisplayContext.FIRST_PERSON_LEFT_HAND, true, pose, event.getMultiBufferSource(), event.getPackedLight());
        pose.popPose();
    }

    private static void armTransform(PoseStack pose, float equip) {
        pose.translate(SIDE * 0.56F, -0.52F + equip * -0.6F, -0.72F);
    }

    /** Vanilla's eat/drink bob: the item comes to the middle of the view and bobs. */
    private static void applyEat(PoseStack pose, float partialTick, ItemStack stack, LocalPlayer player) {
        float f = player.getUseItemRemainingTicks() - partialTick + 1.0F;
        float f1 = f / stack.getUseDuration(player);
        if (f1 < 0.8F) {
            pose.translate(0.0F, Mth.abs(Mth.cos(f / 4.0F * Mth.PI) * 0.1F), 0.0F);
        }
        float f3 = 1.0F - (float) Math.pow(f1, 27.0);
        pose.translate(f3 * 0.6F * SIDE, f3 * -0.5F, 0.0F);
        pose.mulPose(Axis.YP.rotationDegrees(SIDE * f3 * 90.0F));
        pose.mulPose(Axis.XP.rotationDegrees(f3 * 10.0F));
        pose.mulPose(Axis.ZP.rotationDegrees(SIDE * f3 * 30.0F));
    }

    private FirstPersonLeftHand() {}
}
