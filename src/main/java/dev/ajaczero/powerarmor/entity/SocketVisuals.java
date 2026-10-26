package dev.ajaczero.powerarmor.entity;

import dev.ajaczero.powerarmor.item.PowerTool;
import dev.ajaczero.powerarmor.item.ToolEffects;
import dev.ajaczero.powerarmor.item.ToolLight;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;

/**
 * Client: how this client draws the suit's socket tool. What every tool shares lives here: the arm raising to aim while
 * the tool is used, and the recoil kick of each shot. What one tool does (barrels, drum, lights, beams, muzzle bursts)
 * is the installed tool's own {@link ToolEffects}, made fresh when a tool goes into the socket.
 *
 * One of the suit's parts (see PowerArmorEntity): {@code suit.socketVisuals()}, ticked on the client only. It reads the
 * synced socket state (SuitSocket) and never sends anything back.
 */
public final class SocketVisuals {
    private static final int LIGHTS = ToolLight.values().length;

    private final PowerArmorEntity suit;
    /** The tool the effects were made for, and its effects. */
    @Nullable
    private PowerTool tool;
    private ToolEffects effects = ToolEffects.NONE;
    /** The shot counter last seen (-1: not yet, so a suit coming into view doesn't replay its last shot). */
    private int seenShots = -1;
    /** The recoil kick, 1 right after a shot, decaying (this and last tick). */
    private float recoil, recoilO;
    /** 0 = tool arm in its hip carry, 1 = raised to aim (follows the synced "active", like raising a shield). */
    private float raise, raiseO;

    SocketVisuals(PowerArmorEntity suit) {
        this.suit = suit;
    }

    /** Client, every tick. */
    void tick() {
        PowerTool now = this.suit.socket().tool();
        if (now != this.tool) {
            this.tool = now;
            this.effects = now == null ? ToolEffects.NONE : now.createEffects();
        }
        // Raise the tool arm in 3 ticks while the tool is used, lower it in 5 (heavier on the way down).
        this.raiseO = this.raise;
        this.raise = this.suit.socket().isActive() && this.suit.isDriven() ? Math.min(1F, this.raise + 1F / 3F)
                : Math.max(0F, this.raise - 1F / 5F);
        this.effects.tick(this.suit);
        this.recoilO = this.recoil;
        this.recoil *= 0.55F;
        int shots = this.suit.socket().getShots();
        if (this.seenShots < 0) {
            this.seenShots = shots;
        } else if (shots != this.seenShots) {
            this.seenShots = shots;
            // each new shot kicks the arm: harder for the heavy slow shots than at full spin
            this.recoil = Math.max(this.recoil, 1F - 0.65F * this.suit.socket().getSpin());
            this.effects.shot(this.suit);
        }
    }

    /** Client: the installed tool's effects (NONE without a tool). */
    public ToolEffects effects() {
        return this.effects;
    }

    /** Client: how far the tool arm is raised this frame, 0 (hip carry) .. 1 (aiming), eased. */
    public float raise(float partial) {
        float t = Mth.lerp(partial, this.raiseO, this.raise);
        return t * t * (3 - 2 * t);
    }

    /** Client: the recoil kick this frame, 0..1. */
    public float recoil(float partial) {
        return Mth.lerp(partial, this.recoilO, this.recoil);
    }

    /** Client: the angle of the tool's turning part this frame (degrees). */
    public float rotorAngle(float partial) {
        return this.effects.rotorAngle(partial);
    }

    /** Client: every tool light's brightness this frame, indexed by ToolLight.ordinal() (0 = off). */
    public float[] lights(float time, float partial) {
        float[] out = new float[LIGHTS];
        this.effects.lights(this.suit, time, partial, out);
        return out;
    }

    /** Client/dev: one light's brightness this frame (the autopilot logs). */
    public float light(ToolLight light, float time) {
        return this.lights(time, 0F)[light.ordinal()];
    }
}
