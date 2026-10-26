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

    /**
     * The rail cannon fighting its charge (user, 2026-10-26: "vibrations like it's hard to hold together"): from a fifth
     * of the charge the whole cannon trembles (up to a degree and a half and a sixth of a pixel, growing with the square of the charge) and
     * its two prongs strain apart and buzz against each other; at full charge it shakes hardest. Several fast sines that
     * never line up stand in for noise (this runs every frame, and the time includes the partial tick). The prong bones
     * (rail_fork_out / rail_fork_in) carry no animation, the cannon bone only the arm's; offsets are added.
     */
    static void railShake(BoneSnapshots bones, float charge, float time) {
        float s = Math.max(0F, (charge - 0.2F) / 0.8F);
        if (s <= 0F) {
            return;
        }
        s = s * s * (charge >= 0.999F ? 1.3F : 1F);
        float a = jitter(time, 0F), b = jitter(time, 1.7F), c = jitter(time, 3.1F), d = jitter(time, 4.6F);
        float deg = 1.6F * s * Mth.DEG_TO_RAD, shift = 0.15F * s;
        bones.ifPresent("rail_cannon", g -> g.setRotX(g.getRotX() + deg * a).setRotZ(g.getRotZ() + deg * b)
                .setTranslateX(g.getTranslateX() + shift * c).setTranslateZ(g.getTranslateZ() + shift * d));
        // apart: the outer prong toward -x, the inner one toward +x (GeckoLib 5 offsets have Blockbench's signs), plus a buzz
        float apart = 0.2F * s, buzz = 0.15F * s;
        bones.ifPresent("rail_fork_out", g -> g.setTranslateX(g.getTranslateX() - apart - buzz * c).setRotZ(g.getRotZ() - deg * 0.8F * (1F + d)));
        bones.ifPresent("rail_fork_in", g -> g.setTranslateX(g.getTranslateX() + apart + buzz * d).setRotZ(g.getRotZ() + deg * 0.8F * (1F + c)));
    }

    /** A fast irregular wobble in -1..1 (three sines at unrelated rates). */
    private static float jitter(float time, float seed) {
        return 0.5F * Mth.sin(time * 9.7F + seed * 5.3F) + 0.3F * Mth.sin(time * 23.3F + seed) + 0.2F * Mth.sin(time * 41.9F + seed * 2.1F);
    }

    private ToolRotors() {}
}
