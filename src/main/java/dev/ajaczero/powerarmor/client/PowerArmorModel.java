package dev.ajaczero.powerarmor.client;

import com.geckolib.model.DefaultedEntityGeoModel;
import com.geckolib.renderer.base.GeoRenderState;
import dev.ajaczero.powerarmor.PowerArmor;
import dev.ajaczero.powerarmor.entity.PowerArmorEntity;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.DyeColor;

/**
 * Points GeckoLib at our three asset files (by naming convention):
 *   geo/entity/power_suit.geo.json, animations/entity/power_suit.animation.json,
 *   textures/entity/power_suit.png
 * The code-driven bone offsets (torso aim, tool raise, barrel spin) live in PowerArmorRenderer.adjustModelBonesForRender.
 */
public class PowerArmorModel extends DefaultedEntityGeoModel<PowerArmorEntity> {
    /** A painted suit's texture per dye (by DyeColor id): the steel texture with its plates in the dye's colours, built
     *  by tools/make-paint-textures.py. The paint is only a texture swap: same model, same glowmasks. */
    private static final Identifier[] PAINTED = new Identifier[DyeColor.values().length];

    static {
        for (DyeColor dye : DyeColor.values()) {
            PAINTED[dye.getId()] = Identifier.fromNamespaceAndPath(PowerArmor.MODID, "textures/entity/paint/power_suit_" + dye.getName() + ".png");
        }
    }

    public PowerArmorModel() {
        super(Identifier.fromNamespaceAndPath(PowerArmor.MODID, "power_suit"));
    }

    @Override
    public Identifier getTextureResource(GeoRenderState state) {
        int paint = state.getOrDefaultGeckolibData(SuitRenderData.PAINT, -1);
        return paint >= 0 && paint < PAINTED.length ? PAINTED[paint] : super.getTextureResource(state);
    }
}
