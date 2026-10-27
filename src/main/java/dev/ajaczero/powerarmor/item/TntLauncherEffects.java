package dev.ajaczero.powerarmor.item;

import dev.ajaczero.powerarmor.PowerArmorConfig;
import dev.ajaczero.powerarmor.entity.PowerArmorEntity;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;

/**
 * Client: the TNT launcher's looks on one suit. After each shot the drum turns a quarter turn in 5 ticks and clicks
 * the next round into line; the shot puffs smoke at the muzzle for this client's own pilot (the server sends the puff to
 * everyone else, TntLauncherItem.use).
 */
final class TntLauncherEffects implements ToolEffects {
    /** Degrees per tick the drum turns after a shot (a quarter turn in 5 ticks). */
    private static final float DRUM_SPEED = 18F;

    private float drumAngle, drumAngleO;
    /** Degrees the drum still has to turn. */
    private float drumTurn;
    /** Ticks since this client saw the last shot (the HUD's reload bar); starts loaded. */
    private int sinceShot = Integer.MAX_VALUE / 2;

    @Override
    public void tick(PowerArmorEntity suit) {
        this.sinceShot = Math.min(this.sinceShot + 1, Integer.MAX_VALUE / 2);
        this.drumAngleO = this.drumAngle;
        if (this.drumTurn > 0F) {
            float step = Math.min(DRUM_SPEED, this.drumTurn);
            this.drumAngle += step;
            this.drumTurn -= step;
            if (this.drumTurn <= 0F) {
                // the next round drops into line with the barrel: a hard little click
                suit.level().playLocalSound(suit.getX(), suit.getY() + 1.5, suit.getZ(), SoundEvents.IRON_TRAPDOOR_CLOSE,
                        SoundSource.PLAYERS, 0.35F, 1.7F, false);
            }
        }
        if (this.drumAngle > 3600F) { // keep it small; a multiple of 90 so the drum doesn't jump
            this.drumAngle -= 3600F;
            this.drumAngleO -= 3600F;
        }
    }

    @Override
    public void shot(PowerArmorEntity suit) {
        this.sinceShot = 0;
        this.drumTurn += 90F;
        LocalMuzzle muzzle = ToolEffects.localMuzzle(suit);
        if (muzzle != null) {
            TntLauncherItem.muzzlePuff(ToolEffects.clientParticles(suit), muzzle.at(), !muzzle.firstPerson());
        }
    }

    /** HUD: rounds left (TNT in the ammo slot) and the reload since the last shot (full = ready); critical on the last
     *  round. */
    @Override
    public HudReadout hud(PowerArmorEntity suit, Player pilot) {
        int rounds = TntLauncherItem.countRounds(suit, pilot);
        float reload = Math.min(1F, this.sinceShot / (float) Math.max(1, PowerArmorConfig.TNT_LAUNCHER_RELOAD.get()));
        return new HudReadout(rounds, "hud.powerarmor.reload", reload, rounds >= 0 && rounds <= 1);
    }

    @Override
    public float rotorAngle(float partial) {
        return Mth.lerp(partial, this.drumAngleO, this.drumAngle);
    }
}
