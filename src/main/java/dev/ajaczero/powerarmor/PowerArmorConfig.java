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

    // ---- rivet gun ------------------------------------------------------------------------------------
    public static final ModConfigSpec.IntValue RIVET_ENERGY = B.pop().translation(key("rivet_gun")).push("rivet_gun")
            .comment("Energy per shot, the steam pressure (60 = 3 s of walking: 26 shots per coal). Each shot also takes an iron nugget.").translation(key("rivet_energy"))
            .defineInRange("energy_per_shot", 60, 0, 100_000);
    public static final ModConfigSpec.IntValue RIVET_COOLDOWN = B
            .comment("Ticks between shots while the tool button is held (20 = 1 s). At least 11: the shot animation lasts 10.").translation(key("rivet_cooldown"))
            .defineInRange("cooldown_ticks", 12, 11, 200);
    public static final ModConfigSpec.DoubleValue RIVET_DAMAGE = B
            .comment("Damage per rivet (2 = one heart).").translation(key("rivet_damage"))
            .defineInRange("damage", 10.0, 0, 1000);

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
