package dev.ajaczero.powerarmor.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.ajaczero.powerarmor.entity.PowerArmorEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

public class PowerArmorRenderer extends GeoEntityRenderer<PowerArmorEntity> {
    public PowerArmorRenderer(EntityRendererProvider.Context context) {
        super(context, new PowerArmorModel());
        this.shadowRadius = 0.9F;
    }

    @Override
    public void preRender(PoseStack poseStack, PowerArmorEntity suit, BakedGeoModel model, @Nullable MultiBufferSource bufferSource,
                          @Nullable VertexConsumer buffer, boolean isReRender, float partialTick, int packedLight, int packedOverlay, int colour) {
        // In first person the camera sits inside the helmet, so hide the head for our own suit.
        Minecraft mc = Minecraft.getInstance();
        boolean hideHead = suit.isPilotedBy(mc.player) && mc.options.getCameraType().isFirstPerson();
        model.getBone("head").ifPresent(head -> head.setHidden(hideHead));
        super.preRender(poseStack, suit, model, bufferSource, buffer, isReRender, partialTick, packedLight, packedOverlay, colour);
    }

    /** Vanilla tips dying mobs onto their side; our death animation handles the fall. */
    @Override
    protected float getDeathMaxRotation(PowerArmorEntity suit) {
        return 0.0F;
    }
}
