package dev.ajaczero.powerarmor.client;

import com.geckolib.renderer.base.BoneSnapshots;
import net.minecraft.util.Mth;

/**
 * The socket tools' turning parts, driven by an angle each client accumulates (PowerArmorEntity.getBarrelAngle), not by
 * an animation: the chaingun's barrel cluster spins with the synced spin, the TNT launcher's drum turns a quarter turn
 * after each shot. A part whose speed changes, or that steps, is easier to drive from an angle. Shared by the suit
 * (PowerArmorRenderer) and the cockpit viewmodel (CockpitViewRenderer). No animation keys these bones, and GeckoLib 5
 * starts every bone from its rest pose each frame, so the angle is simply set. Only one tool is installed at a time,
 * so both share the angle.
 */
final class ToolRotors {
    static void pose(BoneSnapshots bones, float angle) {
        bones.ifPresent("chaingun_barrels", barrels -> barrels.setRotY(angle * Mth.DEG_TO_RAD));
        bones.ifPresent("tnt_launcher_drum", drum -> drum.setRotY(angle * Mth.DEG_TO_RAD));
    }

    private ToolRotors() {}
}
