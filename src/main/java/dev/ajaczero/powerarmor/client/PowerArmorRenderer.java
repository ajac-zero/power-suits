package dev.ajaczero.powerarmor.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.ajaczero.powerarmor.entity.PowerArmorEntity;
import dev.ajaczero.powerarmor.item.PowerTool;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

public class PowerArmorRenderer extends GeoEntityRenderer<PowerArmorEntity> {
    /** Bones whose own cubes surround the pilot's camera. */
    private static final String[] SHELL_BONES = {"body", "torso"};
    /** Every tool's bone in the suit model (PowerTool.suitBone). Add new tools here. */
    private static final String[] TOOL_BONES = {"cannon"};

    public PowerArmorRenderer(EntityRendererProvider.Context context) {
        super(context, new PowerArmorModel());
        this.shadowRadius = 0.9F;
        this.addRenderLayer(new HeldItemLayer(this)); // the pilot's selected item in the left hand
    }

    @Override
    public void preRender(PoseStack poseStack, PowerArmorEntity suit, BakedGeoModel model, @Nullable MultiBufferSource bufferSource,
                          @Nullable VertexConsumer buffer, boolean isReRender, float partialTick, int packedLight, int packedOverlay, int colour) {
        // In first person the camera sits inside the helmet. For our own suit, hide the shell around
        // the camera (hips, torso, head+helmet) but keep the arms, shoulder pads and legs, like a cockpit.
        boolean cockpit = isCockpitView(suit);
        for (String shell : SHELL_BONES) {
            model.getBone(shell).ifPresent(bone -> {
                bone.setHidden(cockpit);         // hides this bone's own cubes (and children by default)
                bone.setChildrenHidden(false);   // ...but keep drawing child bones (arms, legs)
            });
        }
        model.getBone("head").ifPresent(head -> head.setHidden(cockpit)); // head + helmet fully hidden
        // Socket: show the installed tool's bone and hide the others; the bare coupling only when empty.
        String installed = suit.getSocketStack().getItem() instanceof PowerTool tool ? tool.suitBone() : null;
        for (String toolBone : TOOL_BONES) {
            model.getBone(toolBone).ifPresent(bone -> bone.setHidden(!toolBone.equals(installed)));
        }
        model.getBone("socket").ifPresent(bone -> bone.setHidden(installed != null));
        if (!isReRender) {
            poseStack.translate(0, suit.getStepOffset(partialTick), 0); // glide over step-ups/downs
        }
        super.preRender(poseStack, suit, model, bufferSource, buffer, isReRender, partialTick, packedLight, packedOverlay, colour);
    }

    /** True when we're drawing the suit the local player sits in, from inside (first person). */
    static boolean isCockpitView(PowerArmorEntity suit) {
        Minecraft mc = Minecraft.getInstance();
        return suit.isPilotedBy(mc.player) && mc.options.getCameraType().isFirstPerson();
    }

    /** GeckoLib draws both sides of every face by default. From inside the suit that exposes the
     *  insides of overlapping cubes (which flicker as parts move), so cull back faces there. */
    @Override
    public RenderType getRenderType(PowerArmorEntity suit, ResourceLocation texture, @Nullable MultiBufferSource bufferSource, float partialTick) {
        if (isCockpitView(suit)) {
            return RenderType.entityCutout(texture);
        }
        return super.getRenderType(suit, texture, bufferSource, partialTick);
    }

    /** Vanilla tips dying mobs onto their side; our death animation handles the fall. */
    @Override
    protected float getDeathMaxRotation(PowerArmorEntity suit) {
        return 0.0F;
    }
}
