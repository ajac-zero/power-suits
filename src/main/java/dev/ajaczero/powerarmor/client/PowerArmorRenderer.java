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
    /** Bones whose own cubes surround the pilot's camera. */
    private static final String[] SHELL_BONES = {"body", "torso"};

    public PowerArmorRenderer(EntityRendererProvider.Context context) {
        super(context, new PowerArmorModel());
        this.shadowRadius = 0.9F;
    }

    @Override
    public void preRender(PoseStack poseStack, PowerArmorEntity suit, BakedGeoModel model, @Nullable MultiBufferSource bufferSource,
                          @Nullable VertexConsumer buffer, boolean isReRender, float partialTick, int packedLight, int packedOverlay, int colour) {
        // In first person the camera sits inside the helmet. For our own suit, hide the shell around
        // the camera (hips, torso, head+helmet) but keep the arms, shoulder pads and legs, like a cockpit.
        Minecraft mc = Minecraft.getInstance();
        boolean cockpit = suit.isPilotedBy(mc.player) && mc.options.getCameraType().isFirstPerson();
        for (String shell : SHELL_BONES) {
            model.getBone(shell).ifPresent(bone -> {
                bone.setHidden(cockpit);         // hides this bone's own cubes (and children by default)
                bone.setChildrenHidden(false);   // ...but keep drawing child bones (arms, legs)
            });
        }
        model.getBone("head").ifPresent(head -> head.setHidden(cockpit)); // head + helmet fully hidden
        super.preRender(poseStack, suit, model, bufferSource, buffer, isReRender, partialTick, packedLight, packedOverlay, colour);
    }

    /** Vanilla tips dying mobs onto their side; our death animation handles the fall. */
    @Override
    protected float getDeathMaxRotation(PowerArmorEntity suit) {
        return 0.0F;
    }
}
