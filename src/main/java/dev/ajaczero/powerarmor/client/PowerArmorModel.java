package dev.ajaczero.powerarmor.client;

import com.geckolib.model.DefaultedEntityGeoModel;
import dev.ajaczero.powerarmor.PowerArmor;
import dev.ajaczero.powerarmor.entity.PowerArmorEntity;
import net.minecraft.resources.Identifier;

/**
 * Points GeckoLib at our three asset files (by naming convention):
 *   geo/entity/power_armor.geo.json, animations/entity/power_armor.animation.json,
 *   textures/entity/power_armor.png
 * The code-driven bone offsets (torso aim, tool raise, barrel spin) live in PowerArmorRenderer.adjustModelBonesForRender.
 */
public class PowerArmorModel extends DefaultedEntityGeoModel<PowerArmorEntity> {
    public PowerArmorModel() {
        super(Identifier.fromNamespaceAndPath(PowerArmor.MODID, "power_armor"));
    }
}
