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
        // aimBlend fades the aiming out when the suit powers down and back in after it boots up.
        float aim = suit.getAimBlend();
        float yaw = Mth.clamp(data.netHeadYaw(), -90F, 90F) * aim;   // look yaw relative to the legs
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
}
