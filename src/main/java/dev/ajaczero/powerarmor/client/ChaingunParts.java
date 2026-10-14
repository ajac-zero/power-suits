package dev.ajaczero.powerarmor.client;

import com.geckolib.renderer.base.BoneSnapshots;
import net.minecraft.util.Mth;

/**
 * The chaingun's barrel cluster turns with the suit's synced spin (PowerArmorEntity.getBarrelAngle), not with an
 * animation: a spinning part whose speed changes is easier to drive from an angle each client accumulates. Shared by
 * the suit (PowerArmorRenderer) and the cockpit viewmodel (CockpitViewRenderer). No animation keys this bone, and
 * GeckoLib 5 starts every bone from its rest pose each frame, so the angle is simply set.
 */
final class ChaingunParts {
    static void pose(BoneSnapshots bones, float barrelAngle) {
        bones.ifPresent("chaingun_barrels", barrels -> barrels.setRotY(barrelAngle * Mth.DEG_TO_RAD));
    }

    private ChaingunParts() {}
}
