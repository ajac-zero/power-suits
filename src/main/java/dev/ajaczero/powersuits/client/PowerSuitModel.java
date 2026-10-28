package dev.ajaczero.powersuits.client;

import com.geckolib.model.DefaultedEntityGeoModel;
import com.geckolib.renderer.base.GeoRenderState;
import dev.ajaczero.powersuits.PowerSuits;
import dev.ajaczero.powersuits.entity.PowerSuitEntity;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.DyeColor;

/**
 * Points GeckoLib at our three asset files (by naming convention):
 *   geo/entity/power_suit.geo.json, animations/entity/power_suit.animation.json,
 *   textures/entity/power_suit.png
 * The code-driven bone offsets (torso aim, tool raise, barrel spin) live in PowerSuitRenderer.adjustModelBonesForRender.
 */
public class PowerSuitModel extends DefaultedEntityGeoModel<PowerSuitEntity> {
    /** A painted suit's texture per dye (by DyeColor id): the steel texture with its plates in the dye's colours, built
     *  by tools/make-paint-textures.py. The paint is only a texture swap: same model, same glowmasks. */
    private static final Identifier[] PAINTED = new Identifier[DyeColor.values().length];

    static {
        for (DyeColor dye : DyeColor.values()) {
            PAINTED[dye.getId()] = Identifier.fromNamespaceAndPath(PowerSuits.MODID, "textures/entity/paint/power_suit_" + dye.getName() + ".png");
        }
    }

    public PowerSuitModel() {
        super(Identifier.fromNamespaceAndPath(PowerSuits.MODID, "power_suit"));
    }

    @Override
    public Identifier getTextureResource(GeoRenderState state) {
        int paint = state.getOrDefaultGeckolibData(SuitRenderData.PAINT, -1);
        return paint >= 0 && paint < PAINTED.length ? PAINTED[paint] : super.getTextureResource(state);
    }
}
