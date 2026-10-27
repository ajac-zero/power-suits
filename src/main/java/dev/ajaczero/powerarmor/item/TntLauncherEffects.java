package dev.ajaczero.powerarmor.item;

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

    @Override
    public void tick(PowerArmorEntity suit) {
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
        this.drumTurn += 90F;
        LocalMuzzle muzzle = ToolEffects.localMuzzle(suit);
        if (muzzle != null) {
            TntLauncherItem.muzzlePuff(ToolEffects.clientParticles(suit), muzzle.at(), !muzzle.firstPerson());
        }
    }

    /** HUD: the rounds in reserve (TNT in the ammo slot; the drum's own rounds are on the launcher: TntLauncherItem.loaded),
     *  low on the last round anywhere; the chamber being loaded (synced as the socket's spin, 0..1). */
    @Override
    public HudReadout hud(PowerArmorEntity suit, Player pilot) {
        int reserve = TntLauncherItem.countRounds(suit, pilot);
        int all = TntLauncherItem.loaded(suit.getSocketStack()) + Math.max(0, reserve);
        return new HudReadout(reserve, reserve != HudReadout.ENDLESS && all <= 1, suit.socket().getSpin(), false);
    }

    @Override
    public float rotorAngle(float partial) {
        return Mth.lerp(partial, this.drumAngleO, this.drumAngle);
    }
}
