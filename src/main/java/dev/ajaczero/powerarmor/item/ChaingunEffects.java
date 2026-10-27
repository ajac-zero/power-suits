package dev.ajaczero.powerarmor.item;

import dev.ajaczero.powerarmor.HudAlert;
import dev.ajaczero.powerarmor.entity.PowerArmorEntity;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;

/**
 * Client: the chaingun's looks on one suit. The barrel cluster turns by the synced spin (up to 60 degrees a tick, ten
 * turns a second), the barrels glow with the synced heat, and each shot puffs a muzzle burst for this client's own pilot
 * (the server sends the burst to everyone else, ChaingunItem.use).
 */
final class ChaingunEffects implements ToolEffects {
    /** The HUD warns (AMMO blinks inverted) from this many bullets down: about a second and a half of a full barrage. */
    static final int LOW_BULLETS = 16;

    private float barrelAngle, barrelAngleO;
    /** The barrels are locked by an overheat (the HUD keeps OVERHEAT up, blinking, while they cool). The server's lock
     *  (ChaingunItem.State.overheated) isn't synced, but it follows the synced heat exactly: it locks when the heat hits
     *  1 (and the heat stays there for the cooling delay, so every client sees it) and opens when it's back to 0; the
     *  heat can't rise in between, as a locked gun doesn't fire. (Only a client that joins mid-cooldown misses one.) */
    private boolean locked;

    @Override
    public void tick(PowerArmorEntity suit) {
        float heat = suit.socket().getHeat();
        if (heat >= 1F - 1e-4F) {
            this.locked = true;
        } else if (heat <= 0F) {
            this.locked = false;
        }
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

    /** HUD: bullets left (the iron nuggets the pilot carries), low at the last {@link #LOW_BULLETS}; the barrel
     *  heat, a warning from half, critical in the last 15% and all the way down while an overheat locks the barrels
     *  (until they're cold: the pilot sees they can't fire until the bar is empty). */
    @Override
    public HudReadout hud(PowerArmorEntity suit, Player pilot) {
        int bullets = pilot.hasInfiniteMaterials() ? HudReadout.ENDLESS : ChaingunItem.countBullets(pilot);
        float heat = suit.socket().getHeat();
        return new HudReadout(bullets, bullets >= 0 && bullets <= LOW_BULLETS, heat,
                this.locked ? HudAlert.CRITICAL : HudAlert.ofFill(heat), this.locked);
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
