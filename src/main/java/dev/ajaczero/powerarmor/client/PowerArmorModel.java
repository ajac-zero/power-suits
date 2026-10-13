package dev.ajaczero.powerarmor.client;

import dev.ajaczero.powerarmor.PowerArmor;
import dev.ajaczero.powerarmor.entity.PowerArmorEntity;
import dev.ajaczero.powerarmor.item.PowerTool;
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
        // The model (and its bones) is shared by every suit, so the cockpit pose is set for each render, first thing.
        boolean cockpitTool = suit.isVehicle() && !suit.isDeadOrDying() && PowerArmorRenderer.isCockpitView(suit)
                && suit.getSocketStack().getItem() instanceof PowerTool;
        cockpitToolPose(cockpitTool ? suit.getAimBlend() : 0F);
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

    /**
     * From the cockpit you look straight down the right arm, so the tool (the forearm) points away from you.
     * In first person only (third person keeps the animated pose), raise the arm a little, swing it inward and
     * bend the tool up at the elbow socket so its side faces the camera, the way vanilla shows a held item.
     * Tuned with a cockpit camera in Blockbench; SocketAim's muzzle point sits between this pose and the animated one.
     *
     * These go on two bones no animation keys (right_shoulder above right_arm, tool_mount above the tools) and are
     * SET, not added: GeckoLib only resets bones the playing animation keys, so adding to an unkeyed bone every
     * frame piles up and fights GeckoLib's slow ease back to rest (the arm flickered up and down).
     * Same signs as Blockbench (checked in game: +rotX raises the tool).
     */
    private void cockpitToolPose(float blend) {
        GeoBone shoulder = getAnimationProcessor().getBone("right_shoulder");
        GeoBone mount = getAnimationProcessor().getBone("tool_mount");
        if (shoulder != null) {
            shoulder.setRotX(COCKPIT_ARM_RAISE * blend * Mth.DEG_TO_RAD);
            shoulder.setRotY(COCKPIT_ARM_INWARD * blend * Mth.DEG_TO_RAD);
        }
        if (mount != null) {
            mount.setRotX(COCKPIT_TOOL_TILT * blend * Mth.DEG_TO_RAD);
        }
    }

    // degrees, as Blockbench +X / +Y
    private static final float COCKPIT_ARM_RAISE = 10F, COCKPIT_ARM_INWARD = 5F, COCKPIT_TOOL_TILT = 30F;
}
