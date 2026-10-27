package dev.ajaczero.powerarmor.item;

import dev.ajaczero.powerarmor.entity.PowerArmorEntity;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;

/**
 * Client: the chaingun's looks on one suit. The barrel cluster turns by the synced spin (up to 60 degrees a tick, ten
 * turns a second), the barrels glow with the synced heat, and each shot puffs a muzzle burst for this client's own pilot
 * (the server sends the burst to everyone else, ChaingunItem.use).
 */
final class ChaingunEffects implements ToolEffects {
    /** The HUD warns (turns red) from this many bullets down: about a second and a half of a full barrage. */
    static final int LOW_BULLETS = 16;

    private float barrelAngle, barrelAngleO;

    @Override
    public void tick(PowerArmorEntity suit) {
        this.barrelAngleO = this.barrelAngle;
        this.barrelAngle += 60F * suit.socket().getSpin();
        if (this.barrelAngle > 3600F) { // keep it small; a multiple of 90 so the square cluster doesn't jump
            this.barrelAngle -= 3600F;
            this.barrelAngleO -= 3600F;
        }
    }

    @Override
    public void shot(PowerArmorEntity suit) {
        LocalMuzzle muzzle = ToolEffects.localMuzzle(suit);
        if (muzzle != null) {
            // no steam in the cockpit view: it covered the target
            ChaingunItem.muzzleBurst(ToolEffects.clientParticles(suit), muzzle.at(), suit.socket().getSpin(), !muzzle.firstPerson());
        }
    }

    @Override
    public float rotorAngle(float partial) {
        return Mth.lerp(partial, this.barrelAngleO, this.barrelAngle);
    }

    @Override
    public void lights(PowerArmorEntity suit, float time, float partial, float[] out) {
        float heat = suit.socket().getHeat();
        out[ToolLight.HEAT.ordinal()] = heatGlow(heat);
        out[ToolLight.HEAT_BARREL.ordinal()] = barrelGlow(heat);
    }

    /** HUD: bullets left (iron nuggets carried + the suit's store) and the barrel heat; critical when overheated (the
     *  barrels lock until they cool) or down to the last bullets. */
    @Override
    public HudReadout hud(PowerArmorEntity suit, Player pilot) {
        int bullets = pilot.hasInfiniteMaterials() ? HudReadout.ENDLESS : ChaingunItem.countBullets(pilot);
        float heat = suit.socket().getHeat();
        return new HudReadout(bullets, "hud.powerarmor.heat", heat, heat >= 0.99F || (bullets >= 0 && bullets <= LOW_BULLETS));
    }

    /** Barrel tips: nothing while cool, a dull red glow from a third of the way, white-hot when overheated. */
    static float heatGlow(float heat) {
        float h = Math.max(0F, (heat - 0.3F) / 0.7F);
        return h * h * (3 - 2 * h);
    }

    /** The barrels behind the tips: only once they're really hot (from 55%), so the glow creeps back from the tips. */
    static float barrelGlow(float heat) {
        float h = Math.max(0F, (heat - 0.55F) / 0.45F);
        return h * h * (3 - 2 * h);
    }
}
