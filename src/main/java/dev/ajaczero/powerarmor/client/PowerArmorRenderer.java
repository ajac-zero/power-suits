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
    /** Every tool's bone in the suit model (PowerTool.suitBone). Add new tools here. */
    private static final String[] TOOL_BONES = {"cannon", "em_projector", "drill", "chainsaw"};

    public PowerArmorRenderer(EntityRendererProvider.Context context) {
        super(context, new PowerArmorModel());
        this.shadowRadius = 0.9F;
        this.addRenderLayer(new HeldItemLayer(this)); // the pilot's selected item in the right hand
        this.addRenderLayer(new SuitGlowLayer(this)); // firebox + porthole visor lights
    }

    @Override
    public void preRender(PoseStack poseStack, PowerArmorEntity suit, BakedGeoModel model, @Nullable MultiBufferSource bufferSource,
                          @Nullable VertexConsumer buffer, boolean isReRender, float partialTick, int packedLight, int packedOverlay, int colour) {
        // In first person the camera sits inside the helmet. For our own suit only the legs are drawn (you see your
        // feet when looking down); the arms come from the cockpit viewmodel (CockpitHands), which lines up with the
        // view. The body bone holds everything above the hips: torso, arms, pads, head.
        boolean cockpit = isCockpitView(suit);
        model.getBone("body").ifPresent(body -> {
            body.setHidden(cockpit);
            body.setChildrenHidden(cockpit);
        });
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
