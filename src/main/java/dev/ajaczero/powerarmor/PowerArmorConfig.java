package dev.ajaczero.powerarmor;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Balance knobs, saved in {@code config/powerarmor-common.toml} (created on first launch, comments included).
 * Edit the file or use Mods > Power Armor > Config in game; changes apply immediately, no restart.
 *
 * Fuel is counted in "energy": walking burns 1 per tick, so 20 energy = 1 second of walking and a piece of
 * coal (1600 by default) = 80 s. The comments express costs in those terms so they're easy to compare.
 *
 * A "COMMON" config is one file for the whole game (not per world). Read values with {@code .get()} at the
 * moment you use them; never copy them into static fields, or a change wouldn't take effect.
 */
public final class PowerArmorConfig {
    private static final ModConfigSpec.Builder B = new ModConfigSpec.Builder();

    // ---- fuel ----------------------------------------------------------------------------------
    public static final ModConfigSpec.IntValue COAL_ENERGY = B.comment("What the suit burns and how fast. 1 energy per tick = walking.").translation(key("fuel")).push("fuel")
            .comment("Energy per coal or charcoal (1600 = 1:20 of walking).").translation(key("coal_energy"))
            .defineInRange("coal_energy", 1600, 1, 1_000_000);
    public static final ModConfigSpec.IntValue LAVA_ENERGY = B
            .comment("Energy per lava bucket (the empty bucket stays in the fuel slot).").translation(key("lava_energy"))
            .defineInRange("lava_energy", 3200, 1, 1_000_000);
    public static final ModConfigSpec.IntValue BLAZE_ENERGY = B
            .comment("Energy per blaze rod.").translation(key("blaze_energy"))
            .defineInRange("blaze_energy", 4800, 1, 1_000_000);
    public static final ModConfigSpec.DoubleValue DRAIN_IDLE = B
            .comment("Energy per tick while piloted and standing still.").translation(key("drain_idle"))
            .defineInRange("drain_idle", 0.25, 0, 100);
    public static final ModConfigSpec.DoubleValue DRAIN_WALK = B
            .comment("Energy per tick while walking (the unit everything else is measured in).").translation(key("drain_walk"))
            .defineInRange("drain_walk", 1.0, 0, 100);
    public static final ModConfigSpec.DoubleValue DRAIN_SPRINT = B
            .comment("Energy per tick while sprinting.").translation(key("drain_sprint"))
            .defineInRange("drain_sprint", 2.0, 0, 100);
    public static final ModConfigSpec.IntValue JUMP_ENERGY = B
            .comment("Extra energy per jump.").translation(key("jump_energy"))
            .defineInRange("jump_energy", 20, 0, 10_000);
    public static final ModConfigSpec.IntValue LOW_FUEL = B
            .comment("Below this much energy (fuel slot included) the suit warns: flashing gauge, beeps, dark smoke.").translation(key("low_fuel"))
            .defineInRange("low_fuel", 600, 0, 1_000_000);

    // ---- chaingun ---------------------------------------------------------------------------------------
    public static final ModConfigSpec.IntValue CHAINGUN_ENERGY = B.pop().translation(key("chaingun")).push("chaingun")
            .comment("Energy per bullet (each bullet also takes an iron nugget).").translation(key("chaingun_energy"))
            .defineInRange("energy_per_shot", 15, 0, 100_000);
    public static final ModConfigSpec.IntValue CHAINGUN_SPIN_ENERGY = B
            .comment("Energy per tick while the trigger is held and the barrels spin (walking burns 1).").translation(key("chaingun_spin_energy"))
            .defineInRange("spin_energy_per_tick", 1, 0, 1000);
    public static final ModConfigSpec.IntValue CHAINGUN_SLOW_INTERVAL = B
            .comment("Ticks between shots with the barrels at rest (the first shot of a burst, or tapping).").translation(key("chaingun_slow_interval"))
            .defineInRange("slow_interval_ticks", 12, 1, 200);
    public static final ModConfigSpec.IntValue CHAINGUN_FAST_INTERVAL = B
            .comment("Ticks between shots at full spin (2 = 10 bullets per second).").translation(key("chaingun_fast_interval"))
            .defineInRange("fast_interval_ticks", 2, 1, 200);
    public static final ModConfigSpec.IntValue CHAINGUN_SPIN_UP = B
            .comment("Ticks of holding the trigger to reach full spin.").translation(key("chaingun_spin_up"))
            .defineInRange("spin_up_ticks", 50, 1, 1200);
    public static final ModConfigSpec.DoubleValue CHAINGUN_DAMAGE_SLOW = B
            .comment("Damage per bullet with the barrels at rest (2 = one heart).").translation(key("chaingun_damage_slow"))
            .defineInRange("damage_slow", 8.0, 0, 1000);
    public static final ModConfigSpec.DoubleValue CHAINGUN_DAMAGE_FAST = B
            .comment("Damage per bullet at full spin. Bullets ignore the half-second hit immunity, so every one counts.").translation(key("chaingun_damage_fast"))
            .defineInRange("damage_fast", 3.0, 0, 1000);
    public static final ModConfigSpec.BooleanValue CHAINGUN_OVERHEAT = B
            .comment("Barrels heat up: after overheat_shots bullets without a pause the gun must cool down before it fires again.").translation(key("chaingun_overheat"))
            .define("overheat", true);
    public static final ModConfigSpec.IntValue CHAINGUN_OVERHEAT_SHOTS = B
            .comment("Bullets in a row (without letting go) until the barrels overheat.").translation(key("chaingun_overheat_shots"))
            .defineInRange("overheat_shots", 80, 1, 10_000);
    public static final ModConfigSpec.IntValue CHAINGUN_COOL_TICKS = B
            .comment("Ticks for fully hot barrels to cool down (starts once the gun stops firing).").translation(key("chaingun_cool_ticks"))
            .defineInRange("cool_ticks", 60, 1, 2400);

    // ---- EM projector --------------------------------------------------------------------------------
    public static final ModConfigSpec.IntValue EM_HOLD_ENERGY = B.pop().translation(key("em_projector")).push("em_projector")
            .comment("Energy per tick while pulling or holding something (1 = same as walking).").translation(key("em_hold_energy"))
            .defineInRange("energy_per_tick", 1, 0, 1000);
    public static final ModConfigSpec.IntValue EM_THROW_ENERGY = B
            .comment("Energy per throw or shove.").translation(key("em_throw_energy"))
            .defineInRange("energy_per_throw", 80, 0, 100_000);
    public static final ModConfigSpec.DoubleValue EM_RANGE = B
            .comment("How far away (blocks) the beam can grab something.").translation(key("em_range"))
            .defineInRange("range", 16.0, 2, 64);
    public static final ModConfigSpec.DoubleValue EM_SLAM_DAMAGE = B
            .comment("Wall-slam damage per block/tick of speed above 0.5 (a full-speed throw: about 13).").translation(key("em_slam_damage"))
            .defineInRange("slam_damage", 12.0, 0, 1000);

    // ---- mining drill --------------------------------------------------------------------------------
    public static final ModConfigSpec.IntValue DRILL_ENERGY = B.pop().translation(key("drill")).push("drill")
            .comment("Energy per block dug (20: a 3x3 face costs 180, about 9 faces per coal).").translation(key("drill_energy"))
            .defineInRange("energy_per_block", 20, 0, 100_000);
    public static final ModConfigSpec.IntValue DRILL_PAUSE = B
            .comment("Ticks of pause after each 3x3 before the next one starts.").translation(key("drill_pause"))
            .defineInRange("pause_ticks", 4, 0, 100);

    // ---- chainsaw ------------------------------------------------------------------------------------
    public static final ModConfigSpec.IntValue CHAINSAW_ENERGY = B.pop().translation(key("chainsaw")).push("chainsaw")
            .comment("Energy per block cut, felled logs included (15: a 5-log oak costs 75, about 20 oaks per coal).").translation(key("chainsaw_energy"))
            .defineInRange("energy_per_log", 15, 0, 100_000);
    public static final ModConfigSpec.IntValue CHAINSAW_MAX_LOGS = B
            .comment("Biggest tree it fells in one go (logs).").translation(key("chainsaw_max_logs"))
            .defineInRange("max_logs", 256, 1, 4096);
    public static final ModConfigSpec.IntValue CHAINSAW_LOGS_PER_TICK = B
            .comment("How fast a tree comes down (logs per tick).").translation(key("chainsaw_logs_per_tick"))
            .defineInRange("logs_per_tick", 3, 1, 64);

    public static final ModConfigSpec SPEC = B.pop().build();

    private static String key(String name) {
        return PowerArmor.MODID + ".configuration." + name;
    }

    private PowerArmorConfig() {}
}
