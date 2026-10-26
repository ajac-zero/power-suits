package dev.ajaczero.powerarmor.entity;

import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Client-side smoke from the two exhaust pipes on the suit's back (the firebox), bubbles from the tidal engine's tank
 * valves and a faint aura round the star reactor's canisters. Particles are purely visual, so they are spawned on each client from what it already knows (powered, speed,
 * fuel left, engine under water); the server never hears about them.
 */
final class ExhaustSmoke {
    /** Pipe tops in the model, in blocks: 3.04 up (48.6 px), 0.53 behind the centre, 0.31 to each side. */
    private static final double PIPE_UP = 3.04, PIPE_BACK = 0.53, PIPE_SIDE = 0.31;
    /** The tidal engine's tank valves (pack_tidal): 2.79 up (44.6 px), over the same spots as the pipes. */
    private static final double VALVE_UP = 2.79;
    /** The star reactor's canisters (pack_reactor): the glass strips' middle 2.45 up, their outer sides 0.43 from the centre. */
    private static final double GLASS_UP = 2.45, GLASS_SIDE = 0.43;

    /** Called every client tick. Smoke only while someone is driving: a parked suit's engine is off. */
    static void tick(PowerArmorEntity suit) {
        if (suit.getEngine() == SuitEngine.TIDAL) {
            tidalBubbles(suit);
            return;
        }
        if (suit.getEngine() == SuitEngine.REACTOR) {
            reactorMotes(suit);
            return;
        }
        if (!suit.isAlive() || !suit.isVehicle() || !suit.canOperate() || suit.getEngine() != SuitEngine.FIREBOX) {
            return; // only the firebox has exhausts (design sec. 4b)
        }
        RandomSource random = suit.getRandom();
        boolean sprinting = suit.getControllingPassenger() instanceof Player pilot && pilot.isSprinting() && suit.animator().getVisualSpeed() > 0.3;
        boolean moving = suit.animator().getVisualSpeed() > 0.3;
        int interval = sprinting ? 1 : moving ? 2 : 7;
        if (suit.tickCount % interval != 0) {
            return;
        }
        // Sprinting: a big dark puff every other tick, small ones in between (a thick but not opaque trail).
        ParticleOptions type = sprinting && suit.tickCount % 2 == 0 ? ParticleTypes.LARGE_SMOKE : ParticleTypes.SMOKE;
        double lift = sprinting ? 0.12 : 0.07;
        if (suit.power().isLowOnFuel()) {
            // Running on fumes: the engine coughs. Long gaps, then a dark puff.
            if (random.nextFloat() < 0.55F) {
                return;
            }
            type = ParticleTypes.LARGE_SMOKE;
            lift = 0.03;
        }
        puff(suit, type, 1, lift, PIPE_UP);
    }

    /**
     * The tidal engine breathes out through its tank valves: a stream of bubbles while it runs with its backpack under
     * water, thicker under load (walking, sprinting, a tool running). Not in rain (user): it runs on rain too, but there's
     * nothing to bubble through. Vanilla's bubble particle dies by itself once it leaves the water.
     */
    private static void tidalBubbles(PowerArmorEntity suit) {
        if (!suit.isAlive() || !suit.isPowered() || !suit.power().isEngineUnderWater()) {
            return;
        }
        RandomSource random = suit.getRandom();
        float load = Math.min(1F, suit.animator().engineLoad());
        if (random.nextFloat() < 0.35F + 0.5F * load) {
            puff(suit, ParticleTypes.BUBBLE, 1, 0.06 + 0.06 * load, VALVE_UP);
        }
        if (load > 0.5F && random.nextFloat() < 0.3F) {
            puff(suit, ParticleTypes.BUBBLE_COLUMN_UP, 1, 0.1, VALVE_UP); // the odd fast stream when it works hard
        }
    }

    /** A thick black cough when the engine starts. */
    static void startupBurst(PowerArmorEntity suit) {
        if (suit.getEngine() == SuitEngine.TIDAL && suit.power().isEngineUnderWater()) {
            puff(suit, ParticleTypes.BUBBLE, 8, 0.15, VALVE_UP); // the pump kicks in
            return;
        }
        if (suit.getEngine() != SuitEngine.FIREBOX) {
            return;
        }
        puff(suit, ParticleTypes.LARGE_SMOKE, 6, 0.15, PIPE_UP);
        puff(suit, ParticleTypes.SMOKE, 6, 0.2, PIPE_UP);
    }

    /**
     * The star reactor's aura: ender particles (vanilla's portal particle, the purple specks round endermen, ender chests
     * and end portal frames) drawn into a canister's glass (user, 2026-10-23; it was a faint violet potion swirl).
     * A portal particle flies from (its spawn point + its "velocity" + 1 block up) back to its spawn point over ~2 s,
     * so the spawn point is the glass, the velocity a short way out and half a block down: each speck starts at most
     * half a block off the canister and sinks into it (with the default 1 block up they fell in from overhead).
     * A little livelier under load. Not in the pilot's own cockpit view: the canisters sit about a block behind and
     * below the eye.
     */
    private static void reactorMotes(PowerArmorEntity suit) {
        if (!suit.isAlive() || !suit.isPowered()) {
            return;
        }
        if (ClientView.inCockpit.test(suit)) {
            return; // (shared code: asks the client through ClientView, never Minecraft.getInstance() directly)
        }
        RandomSource random = suit.getRandom();
        float load = Math.min(1F, suit.animator().engineLoad());
        if (random.nextFloat() >= 0.4F + 0.3F * load) {
            return;
        }
        float yaw = Mth.rotLerp(suit.animator().getAimBlend(), suit.yBodyRot, suit.getYHeadRot()) * Mth.DEG_TO_RAD;
        Vec3 forward = new Vec3(-Mth.sin(yaw), 0, Mth.cos(yaw));
        Vec3 side = new Vec3(forward.z, 0, -forward.x);
        int which = random.nextBoolean() ? 1 : -1;
        // a point on a canister's glass (its outer side or its back), somewhere up its height
        Vec3 glass = suit.position().add(0, GLASS_UP + suit.animator().getStepOffset(1F) + (random.nextDouble() - 0.5) * 0.3, 0)
                .subtract(forward.scale(PIPE_BACK + random.nextDouble() * 0.12)).add(side.scale(which * (GLASS_SIDE - random.nextDouble() * 0.12)));
        // where the speck starts, relative to the glass: out to the side or back, and up (cancelling most of the 1 block)
        Vec3 out = side.scale(which * (0.15 + random.nextDouble() * 0.35)).subtract(forward.scale(random.nextDouble() * 0.3));
        suit.level().addParticle(ParticleTypes.PORTAL, glass.x, glass.y, glass.z, out.x, -0.5 - random.nextDouble() * 0.3, out.z);
    }

    private static void puff(PowerArmorEntity suit, ParticleOptions type, int perPipe, double lift, double up) {
        puff(suit, type, perPipe, lift, up, PIPE_SIDE);
    }

    /** perPipe particles at each of the two spots (left and right, sideDist from the centre, up blocks high, behind the
     *  body), rising at lift and carried along with the suit. */
    private static void puff(PowerArmorEntity suit, ParticleOptions type, int perPipe, double lift, double up, double sideDist) {
        Level level = suit.level();
        RandomSource random = suit.getRandom();
        // The pipes are on the torso, which turns toward the pilot's view (yHeadRot) from the legs (yBodyRot).
        float yaw = Mth.rotLerp(suit.animator().getAimBlend(), suit.yBodyRot, suit.getYHeadRot()) * Mth.DEG_TO_RAD;
        Vec3 forward = new Vec3(-Mth.sin(yaw), 0, Mth.cos(yaw));
        Vec3 side = new Vec3(forward.z, 0, -forward.x);
        Vec3 base = suit.position().add(0, up + suit.animator().getStepOffset(1F), 0).subtract(forward.scale(PIPE_BACK));
        Vec3 motion = suit.getDeltaMovement();
        for (int pipe = -1; pipe <= 1; pipe += 2) {
            Vec3 top = base.add(side.scale(pipe * sideDist));
            for (int i = 0; i < perPipe; i++) {
                level.addParticle(type,
                        top.x + (random.nextDouble() - 0.5) * 0.1, top.y, top.z + (random.nextDouble() - 0.5) * 0.1,
                        motion.x * 0.3 - forward.x * 0.02, lift + random.nextDouble() * 0.04, motion.z * 0.3 - forward.z * 0.02);
            }
        }
    }

    private ExhaustSmoke() {}
}
