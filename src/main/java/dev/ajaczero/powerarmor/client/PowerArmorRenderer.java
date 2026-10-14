package dev.ajaczero.powerarmor.client;

import com.geckolib.renderer.GeoEntityRenderer;
import com.geckolib.renderer.base.BoneSnapshots;
import com.geckolib.renderer.base.GeoRenderState;
import com.geckolib.renderer.base.RenderPassInfo;
import dev.ajaczero.powerarmor.entity.PowerArmorEntity;
import dev.ajaczero.powerarmor.item.PowerTool;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import org.jspecify.annotations.Nullable;

/**
 * Draws the suit with GeckoLib. Rendering happens in two steps since Minecraft 1.21.2: first the frame is "extracted"
 * (addRenderData copies what we need from the entity into the render state, see SuitRenderData), then the model is
 * posed and drawn from that snapshot alone (adjustModelBonesForRender, the layers).
 */
public class PowerArmorRenderer extends GeoEntityRenderer<PowerArmorEntity, LivingEntityRenderState> {
    /** Every tool's bone in the suit model (PowerTool.suitBone). Add new tools here. */
    private static final String[] TOOL_BONES = {"chaingun", "em_projector", "drill", "chainsaw"};

    public PowerArmorRenderer(EntityRendererProvider.Context context) {
        super(context, new PowerArmorModel());
        this.shadowRadius = 0.9F;
        this.withRenderLayer(new HeldItemLayer(context, this)); // the pilot's selected item in the right hand
        this.withRenderLayer(new SuitGlowLayer(this));         // firebox + visor lights
    }

    // ---- extract: entity -> render state ------------------------------------------------------------------------

    @Override
    public void addRenderData(PowerArmorEntity suit, @Nullable Void ignored, LivingEntityRenderState state, float partialTick) {
        state.addGeckolibData(SuitRenderData.COCKPIT, isCockpitView(suit));
        state.addGeckolibData(SuitRenderData.TOOL_BONE, suit.getSocketStack().getItem() instanceof PowerTool tool ? tool.suitBone() : "");
        state.addGeckolibData(SuitRenderData.STEP_OFFSET, (float) suit.getStepOffset(partialTick));
        state.addGeckolibData(SuitRenderData.AIMING, suit.isDriven() && !suit.isDeadOrDying());
        state.addGeckolibData(SuitRenderData.AIM_BLEND, suit.getAimBlend());
        state.addGeckolibData(SuitRenderData.TOOL_RAISE, suit.getToolRaise(partialTick));
        state.addGeckolibData(SuitRenderData.RECOIL, suit.getRecoil(partialTick));
        state.addGeckolibData(SuitRenderData.BARREL_ANGLE, suit.getBarrelAngle(partialTick));
        state.addGeckolibData(SuitRenderData.HIP_SINK, suit::setHipHeight);
    }

    /** True when we're drawing the suit the local player sits in, from inside (first person). */
    static boolean isCockpitView(PowerArmorEntity suit) {
        Minecraft mc = Minecraft.getInstance();
        // The camera must also be the pilot: a scripted camera (another entity, e.g. the autopilot's close-ups) sees
        // the whole suit even while the view is set to first person.
        return suit.isPilotedBy(mc.player) && mc.options.getCameraType().isFirstPerson() && mc.getCameraEntity() == mc.player;
    }

    // ---- render: render state -> model ---------------------------------------------------------------------------

    @Override
    public void adjustRenderPose(RenderPassInfo<LivingEntityRenderState> info) {
        info.poseStack().translate(0, info.getOrDefaultGeckolibData(SuitRenderData.STEP_OFFSET, 0F), 0); // glide over steps
        super.adjustRenderPose(info);
    }

    /**
     * Runs every frame after the keyframe animations have posed the bones. GeckoLib 5 hands us a fresh snapshot per bone
     * each frame holding the animation's offset from the bone's rest pose, so we add to it (nothing piles up: the next
     * frame starts from the animation again).
     */
    @Override
    public void adjustModelBonesForRender(RenderPassInfo<LivingEntityRenderState> info, BoneSnapshots bones) {
        // In first person the camera sits inside the helmet. For our own suit only the legs are drawn (you see your
        // feet when looking down); the arms come from the cockpit viewmodel (CockpitHands), which lines up with the
        // view. The body bone holds everything above the hips: torso, arms, pads, head.
        boolean cockpit = info.getOrDefaultGeckolibData(SuitRenderData.COCKPIT, false);
        bones.ifPresent("body", body -> body.skipRender(cockpit).skipChildrenRender(cockpit));
        // Socket: show the installed tool's bone and hide the others; the bare coupling only when empty.
        String installed = info.getOrDefaultGeckolibData(SuitRenderData.TOOL_BONE, "");
        for (String toolBone : TOOL_BONES) {
            boolean hide = !toolBone.equals(installed);
            bones.ifPresent(toolBone, bone -> bone.skipRender(hide).skipChildrenRender(hide));
        }
        boolean socketHidden = !installed.isEmpty();
        bones.ifPresent("socket", bone -> bone.skipRender(socketHidden));
        ChaingunParts.pose(bones, info.getOrDefaultGeckolibData(SuitRenderData.BARREL_ANGLE, 0F)); // turn (and coast) always
        aim(info, bones);
    }

    /**
     * The legs face the body yaw; here the torso (and with it the arms, pads and socket tool) twists and leans to face
     * where the pilot (or the machine mind) looks, and the head adds a little extra pitch.
     */
    private void aim(RenderPassInfo<LivingEntityRenderState> info, BoneSnapshots bones) {
        if (!info.getOrDefaultGeckolibData(SuitRenderData.AIMING, false)) {
            return; // parked: rest pose
        }
        LivingEntityRenderState state = info.renderState();
        bones.ifPresent("marine", root -> {
            var sink = info.getGeckolibData(SuitRenderData.HIP_SINK);
            if (sink != null) {
                sink.accept(root.getTranslateY()); // the gait's hip bob, used for the cockpit camera bob
            }
        });
        raiseTool(info, bones);
        // aimBlend fades the aiming out when the suit powers down and back in after it boots up.
        float aim = info.getOrDefaultGeckolibData(SuitRenderData.AIM_BLEND, 1F);
        // Look yaw relative to the legs (Minecraft already wraps it to -180..180; unwrapped, a pilot who had turned round
        // twice twisted the torso the wrong way).
        float yaw = Mth.clamp(Mth.wrapDegrees(state.yRot), -90F, 90F) * aim;
        float pitch = state.xRot * aim;                                // + = looking down
        bones.ifPresent("torso", torso -> {
            torso.setRotY(torso.getRotY() + yaw * Mth.DEG_TO_RAD);
            torso.setRotX(torso.getRotX() + Mth.clamp(pitch * 0.55F, -25F, 25F) * Mth.DEG_TO_RAD);
        });
        bones.ifPresent("head", head -> head.setRotX(head.getRotX() + Mth.clamp(pitch * 0.35F, -20F, 20F) * Mth.DEG_TO_RAD));
    }

    // The tool arm's rest pose is a hip carry (Blockbench: left_arm [18, 0, -18], left_forearm [62, -4, 0]). Using the
    // tool raises it to the aiming pose [46, 0, -22] / [38, -4, 0] (the muzzle SocketAim fires from), like raising a
    // shield. These are the differences, in Blockbench degrees.
    private static final float RAISE_ARM_X = 28F, RAISE_ARM_Z = -4F, RAISE_FOREARM_X = -24F;

    /**
     * Raise the tool arm by the suit's toolRaise blend, added on top of the animation, so the gait's sway and the shot
     * recoil still show. GeckoLib flips the sign of X and Y rotations relative to Blockbench (Bedrock -> Java model
     * space); Z keeps it.
     */
    private static void raiseTool(RenderPassInfo<LivingEntityRenderState> info, BoneSnapshots bones) {
        float w = info.getOrDefaultGeckolibData(SuitRenderData.TOOL_RAISE, 0F);
        if (w <= 0F) {
            return;
        }
        // Recoil: each shot kicks the forearm up and the upper arm back a little (Blockbench +X forearm = flex, so
        // the muzzle climbs); at full spin ten small kicks a second become a shudder.
        float kick = info.getOrDefaultGeckolibData(SuitRenderData.RECOIL, 0F) * w;
        bones.ifPresent("left_arm", arm -> {
            arm.setRotX(arm.getRotX() - (RAISE_ARM_X * w - 3F * kick) * Mth.DEG_TO_RAD);
            arm.setRotZ(arm.getRotZ() + RAISE_ARM_Z * w * Mth.DEG_TO_RAD);
        });
        bones.ifPresent("left_forearm", forearm -> forearm.setRotX(forearm.getRotX() - (RAISE_FOREARM_X * w + 7F * kick) * Mth.DEG_TO_RAD));
    }

    /** GeckoLib draws both sides of every face by default. From inside the suit that exposes the
     *  insides of overlapping cubes (which flicker as parts move), so cull back faces there. */
    @Override
    public @Nullable RenderType getRenderType(LivingEntityRenderState state, Identifier texture) {
        if (state.getOrDefaultGeckolibData(SuitRenderData.COCKPIT, false)) {
            return RenderTypes.entityCutoutCull(texture);
        }
        return super.getRenderType(state, texture);
    }

    /** Vanilla tips dying mobs onto their side; our death animation handles the fall. */
    @Override
    protected float getDeathMaxRotation(GeoRenderState state) {
        return 0.0F;
    }
}
