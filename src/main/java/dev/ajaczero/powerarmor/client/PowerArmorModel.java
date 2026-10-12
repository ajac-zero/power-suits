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

    /** Runs every frame after the keyframe animations: tilt the head with the pilot's view. */
    @Override
    public void setCustomAnimations(PowerArmorEntity suit, long instanceId, AnimationState<PowerArmorEntity> state) {
        if (!suit.isVehicle() || suit.isDeadOrDying()) {
            return;
        }
        GeoBone head = getAnimationProcessor().getBone("head");
        EntityModelData data = state.getData(DataTickets.ENTITY_MODEL_DATA);
        if (head != null && data != null) {
            // Added on top of the animation (which sets the head every frame) instead of replacing it.
            head.setRotX(head.getRotX() + Mth.clamp(data.headPitch(), -30F, 30F) * Mth.DEG_TO_RAD);
        }
    }
}
