package dev.ajaczero.powerarmor.client;

import dev.ajaczero.powerarmor.PowerArmor;
import dev.ajaczero.powerarmor.entity.PowerArmorEntity;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.constant.DataTickets;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;
import software.bernie.geckolib.model.data.EntityModelData;

/**
 * Points GeckoLib at our three asset files (by naming convention):
 *   geo/entity/power_armor.geo.json, animations/entity/power_armor.animation.json,
 *   textures/entity/power_armor.png
 */
public class PowerArmorModel extends DefaultedEntityGeoModel<PowerArmorEntity> {
    public PowerArmorModel() {
        super(ResourceLocation.fromNamespaceAndPath(PowerArmor.MODID, "power_armor"));
    }

    /**
     * Runs every frame after the keyframe animations. The legs face yBodyRot; here the torso (and with
     * it the arms, pads and cannon) twists and leans to face where the pilot looks, and the head adds
     * a little extra pitch. Values are added on top of the animation, which sets these bones every frame.
     */
    @Override
    public void setCustomAnimations(PowerArmorEntity suit, long instanceId, AnimationState<PowerArmorEntity> state) {
        if (!suit.isVehicle() || suit.isDeadOrDying()) {
            return;
        }
        EntityModelData data = state.getData(DataTickets.ENTITY_MODEL_DATA);
        if (data == null) {
            return;
        }
        GeoBone root = getAnimationProcessor().getBone("marine");
        if (root != null) {
            suit.setHipHeight(root.getPosY()); // the gait's hip bob, used for the cockpit camera bob
        }
        raiseTool(suit, state.getPartialTick());
        // aimBlend fades the aiming out when the suit powers down and back in after it boots up.
        float aim = suit.getAimBlend();
        // Look yaw relative to the legs. Wrapped to -180..180 first: yaw angles are unbounded (a pilot who has turned
        // round twice is at 720), so a raw difference can be +-360 off and would twist the torso the wrong way.
        float yaw = Mth.clamp(Mth.wrapDegrees(data.netHeadYaw()), -90F, 90F) * aim;
        float pitch = data.headPitch() * aim;                         // + = looking down
        GeoBone torso = getAnimationProcessor().getBone("torso");
        GeoBone head = getAnimationProcessor().getBone("head");
        if (torso != null) {
            torso.setRotY(torso.getRotY() + yaw * Mth.DEG_TO_RAD);
            torso.setRotX(torso.getRotX() + Mth.clamp(pitch * 0.55F, -25F, 25F) * Mth.DEG_TO_RAD);
        }
        if (head != null) {
            head.setRotX(head.getRotX() + Mth.clamp(pitch * 0.35F, -20F, 20F) * Mth.DEG_TO_RAD);
        }
    }

    // The tool arm's rest pose is a hip carry (Blockbench: left_arm [18, 0, -18], left_forearm [62, -4, 0]). Using the
    // tool raises it to the aiming pose [46, 0, -22] / [38, -4, 0] (the muzzle SocketAim fires from), like raising a
    // shield. These are the differences, in Blockbench degrees.
    private static final float RAISE_ARM_X = 28F, RAISE_ARM_Z = -4F, RAISE_FOREARM_X = -24F;

    /**
     * Raise the tool arm by the suit's toolRaise blend. Added on top of the animation, so the gait's sway and the
     * shot recoil still show. Safe to add every frame: every animation that plays while piloted keys both bones
     * (an unkeyed bone would keep the previous frame's value and the offset would pile up). GeckoLib flips the
     * sign of X and Y rotations relative to Blockbench (Bedrock -> Java model space); Z keeps it.
     */
    private void raiseTool(PowerArmorEntity suit, float partial) {
        float w = suit.getToolRaise(partial);
        if (w <= 0F) {
            return;
        }
        GeoBone arm = getAnimationProcessor().getBone("left_arm");
        GeoBone forearm = getAnimationProcessor().getBone("left_forearm");
        if (arm != null) {
            arm.setRotX(arm.getRotX() - RAISE_ARM_X * w * Mth.DEG_TO_RAD);
            arm.setRotZ(arm.getRotZ() + RAISE_ARM_Z * w * Mth.DEG_TO_RAD);
        }
        if (forearm != null) {
            forearm.setRotX(forearm.getRotX() - RAISE_FOREARM_X * w * Mth.DEG_TO_RAD);
        }
    }
}
