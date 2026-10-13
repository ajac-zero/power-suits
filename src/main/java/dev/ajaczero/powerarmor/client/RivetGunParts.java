package dev.ajaczero.powerarmor.client;

import net.minecraft.util.Mth;
import software.bernie.geckolib.animation.AnimationProcessor;
import software.bernie.geckolib.cache.object.GeoBone;

/**
 * The rivet gun's moving parts that follow the tool arm's raise (PowerArmorEntity.getToolRaise) rather than an
 * animation: the louvred vent flap on its outer side swings open as the arm comes up, ready to blow off steam.
 * Shared by the suit (PowerArmorModel) and the cockpit viewmodel (CockpitViewRenderer). No animation keys the vent,
 * so the value is SET every frame from its rest pose (adding would pile up, see AGENTS.md).
 */
final class RivetGunParts {
    /** Blockbench degrees around Z: negative swings the flap's free end out (-X, away from the body). GeckoLib keeps
     *  Blockbench's sign for Z rotations. */
    private static final float VENT_OPEN_Z = -40F;

    static void raise(AnimationProcessor<?> bones, float raise) {
        GeoBone vent = bones.getBone("rivet_vent");
        if (vent != null) {
            vent.setRotZ(vent.getInitialSnapshot().getRotZ() + raise * VENT_OPEN_Z * Mth.DEG_TO_RAD);
        }
    }

    private RivetGunParts() {}
}
