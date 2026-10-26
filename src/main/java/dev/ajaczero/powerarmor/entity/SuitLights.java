package dev.ajaczero.powerarmor.entity;

import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;

/**
 * Client: the suit's own lights, drawn by client/SuitGlowLayer: the firebox's fire (flares up quickly, dies down slowly),
 * the visor (who is in control: amber = a pilot, green = the friendly machine mind, red = a rogue; dark = powered off;
 * it stutters while the engine starts or dies and drops out now and then when the fuel runs low), the tidal engine's
 * and the star reactor's glass; and the sparks that crawl over the suit when lightning knocks it out. The socket tool's
 * lights belong to the tool (item/ToolEffects).
 *
 * One of the suit's parts (see PowerArmorEntity): {@code suit.lights()}, ticked on the client only.
 */
public final class SuitLights {
    private static final float[] VISOR_GREEN = {0.35F, 1F, 0.45F}, VISOR_AMBER = {1F, 0.68F, 0.22F}, VISOR_RED = {1F, 0.12F, 0.08F};

    private final PowerArmorEntity suit;
    /** Client: the lights (SuitGlowLayer). Fire 0..1; visor brightness 0..1 and colour; ticks the visor stays red after a hit. */
    private float fireGlow, visorLevel;
    /** Client: the tidal engine's / star reactor's glass, 0 (dark) .. 1 (running), eased like the fire. */
    private float engineGlow;
    private final float[] visorColor = {1F, 0.68F, 0.22F};
    /** Client: ticks since the lightning knock-out began (sparks fly for the first second). */
    private int sparkTicks = -1;

    SuitLights(PowerArmorEntity suit) {
        this.suit = suit;
    }

    /** Client, every tick. */
    void tick() {
        this.tickLights();
        this.tickSparks();
    }

    /** Client, every tick: ease the lights toward what the suit is doing. The fire flares up quickly and dies down
     *  slowly (embers); the visor shows who is in control: amber = a pilot (whatever they do), green = the friendly
     *  machine mind, red = a rogue mind (only rogues: so a red visor always means danger), dark = powered off. */
    private void tickLights() {
        boolean powered = this.suit.isPowered();
        float fireTarget = powered ? (0.6F + 0.4F * Math.min(1F, this.suit.animator().engineLoad())) * (this.suit.power().isLowOnFuel() ? 0.7F : 1F) : 0F;
        this.fireGlow += (fireTarget - this.fireGlow) * (fireTarget > this.fireGlow ? 0.2F : 0.03F);
        float[] target = visorColorFor(this.suit.isVehicle(), this.suit.getMind());
        for (int i = 0; i < 3; i++) {
            this.visorColor[i] += (target[i] - this.visorColor[i]) * 0.25F;
        }
        float levelTarget = powered ? 1F : 0F;
        this.visorLevel += (levelTarget - this.visorLevel) * (powered ? 0.3F : 0.08F);
        // The tidal engine's and the reactor's glass light up while they run (the firebox has its fire above).
        float engineTarget = powered && this.suit.getEngine() != SuitEngine.FIREBOX ? 1F : 0F;
        this.engineGlow += (engineTarget - this.engineGlow) * (engineTarget > this.engineGlow ? 0.15F : 0.05F);
    }
    /** Client: sparks crawl over the suit for the first second of a lightning knock-out (while it convulses). Only on the
     *  legs and hips: particles near the cockpit camera turn into big blobs. */
    private void tickSparks() {
        if (!this.suit.isStunned()) {
            this.sparkTicks = -1;
            return;
        }
        this.sparkTicks++;
        if (this.sparkTicks > 24) {
            return;
        }
        float yaw = this.suit.yBodyRot * Mth.DEG_TO_RAD;
        for (int i = 0; i < 3; i++) {
            double side = (this.suit.getRandom().nextDouble() - 0.5) * 1.4, front = (this.suit.getRandom().nextDouble() - 0.5) * 0.8;
            double x = this.suit.getX() + side * Mth.cos(yaw) - front * Mth.sin(yaw);
            double z = this.suit.getZ() + side * Mth.sin(yaw) + front * Mth.cos(yaw);
            double y = this.suit.getY() + 0.2 + this.suit.getRandom().nextDouble() * 1.1;
            this.suit.level().addParticle(net.minecraft.core.particles.ParticleTypes.ELECTRIC_SPARK, x, y, z,
                    this.suit.getRandom().nextGaussian() * 0.15, this.suit.getRandom().nextGaussian() * 0.15, this.suit.getRandom().nextGaussian() * 0.15);
        }
        if (this.sparkTicks % 6 == 0) {
            this.suit.level().playLocalSound(this.suit.getX(), this.suit.getY() + 1, this.suit.getZ(), SoundEvents.COPPER_BULB_TURN_OFF,
                    net.minecraft.sounds.SoundSource.NEUTRAL, 1.0F, 0.6F + this.suit.getRandom().nextFloat() * 0.6F, false);
        }
    }
    /** Client: the Heart of the Sea in the tidal engine's porthole: a slow shimmer, like light through water. */
    public float getTidalGlow(float time) {
        if (this.suit.getEngine() != SuitEngine.TIDAL) {
            return 0F;
        }
        return this.engineGlow * (0.82F + 0.1F * Mth.sin(time * 0.21F + this.suit.getId()) + 0.08F * Mth.sin(time * 0.57F));
    }
    /** Client: the star reactor's energy: a slow, deep pulse that never quite goes out. */
    public float getReactorGlow(float time) {
        if (this.suit.getEngine() != SuitEngine.REACTOR) {
            return 0F;
        }
        float pulse = Mth.sin(time * 0.12F + this.suit.getId());
        return this.engineGlow * (0.78F + 0.22F * pulse * pulse);
    }
    /** Client: firebox glow, 0 (cold) .. 1 (roaring). */
    public float getFireGlow() {
        return this.fireGlow;
    }
    /** The visor colour each state eases toward: the pilot's amber beats the mind's colour. */
    public static float[] visorColorFor(boolean piloted, SuitMind mind) {
        return piloted ? VISOR_AMBER : mind == SuitMind.ROGUE ? VISOR_RED : VISOR_GREEN;
    }
    /** Client: the visor's colour (r, g, b, 0..1). */
    public float[] getVisorColor() {
        return this.visorColor;
    }
    /** Client: the visor's brightness at this moment: stutters while the engine starts or dies, and drops out now
     *  and then when the fuel runs low (time in ticks, with the partial tick). */
    public float getVisorFlicker(float time) {
        float level = this.visorLevel;
        boolean unsteady = this.suit.startupTicks() > 0 || (!this.suit.isPowered() && level > 0.02F);
        if (unsteady && Mth.sin(time * 2.1F + this.suit.getId()) + Mth.sin(time * 3.7F) < -0.4F) {
            return level * 0.2F;
        }
        if (this.suit.isPowered() && this.suit.power().isLowOnFuel() && Mth.sin(time * 0.9F) * Mth.sin(time * 2.3F) > 0.6F) {
            return level * 0.3F;
        }
        return level;
    }
}
