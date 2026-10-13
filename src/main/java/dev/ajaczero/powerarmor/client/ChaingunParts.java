package dev.ajaczero.powerarmor.client;

import dev.ajaczero.powerarmor.entity.PowerArmorEntity;
import net.minecraft.util.Mth;
import software.bernie.geckolib.animation.AnimationProcessor;
import software.bernie.geckolib.cache.object.GeoBone;

/**
 * The chaingun's barrel cluster turns with the suit's synced spin (PowerArmorEntity.getBarrelAngle), not with an
 * animation: GeckoLib scales an animation's whole elapsed time by its speed, so speeding a spin animation up mid-loop
 * makes it jump (we hit that with the walk). Shared by the suit (PowerArmorModel) and the cockpit viewmodel
 * (CockpitViewRenderer). No animation keys this bone, so the value is SET every frame from its rest pose (adding
 * would pile up, see AGENTS.md).
 */
final class ChaingunParts {
    static void pose(AnimationProcessor<?> bones, PowerArmorEntity suit, float partial) {
        GeoBone barrels = bones.getBone("chaingun_barrels");
        if (barrels != null) {
            barrels.setRotY(barrels.getInitialSnapshot().getRotY() + suit.getBarrelAngle(partial) * Mth.DEG_TO_RAD);
        }
    }

    private ChaingunParts() {}
}
