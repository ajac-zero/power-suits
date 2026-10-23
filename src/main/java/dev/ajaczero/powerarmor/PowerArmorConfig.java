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
    // The firebox has no table here: it burns what a furnace burns, for as long as a furnace does (SuitFuel).
    public static final ModConfigSpec.IntValue WATER_BUCKET_ENERGY = B.comment("What the suit burns and how fast. 1 energy per tick = walking.").translation(key("fuel")).push("fuel")
            .comment("Tidal engine: energy per water bucket, used only out of the water (300 = 15 s of walking; the bucket stays).").translation(key("water_bucket_energy"))
            .defineInRange("water_bucket_energy", 300, 1, 1_000_000);
    public static final ModConfigSpec.IntValue WATER_BOTTLE_ENERGY = B
            .comment("Tidal engine: energy per water bottle (the glass bottle stays).").translation(key("water_bottle_energy"))
            .defineInRange("water_bottle_energy", 100, 1, 1_000_000);
    public static final ModConfigSpec.IntValue TIDAL_DRY_GRACE = B
            .comment("Tidal engine: ticks it keeps running free after leaving the water (a shoreline mustn't flicker it).").translation(key("tidal_dry_grace"))
            .defineInRange("tidal_dry_grace", 20, 0, 1200);
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

    // ---- the suit itself ---------------------------------------------------------------------------
    public static final ModConfigSpec.DoubleValue SEAL_ENERGY_PER_DAMAGE = B.pop().translation(key("suit")).push("suit")
            .comment("Sealed suit: fire, frost, magma, cactus and effect damage cost this much energy per point of damage instead of",
                    "hurting the suit (only while powered; lava still hurts the hull). 20 = a second of walking per point.").translation(key("seal_energy_per_damage"))
            .defineInRange("seal_energy_per_damage", 20.0, 0, 10_000);
    public static final ModConfigSpec.IntValue SEAL_EFFECT_ENERGY = B
            .comment("Energy to keep one harmful effect (poison, wither, slowness...) away from the pilot. Good effects still get through.").translation(key("seal_effect_energy"))
            .defineInRange("seal_effect_energy", 100, 0, 100_000);
    public static final ModConfigSpec.DoubleValue AIR_ENERGY_PER_TICK = B
            .comment("Extra energy per tick while the pilot's head is under water: the suit feeds them air.").translation(key("air_energy_per_tick"))
            .defineInRange("air_energy_per_tick", 0.5, 0, 100);
    public static final ModConfigSpec.DoubleValue MELEE_BONUS = B
            .comment("Extra melee damage for the pilot's hand, with or without a weapon (2 = one heart).").translation(key("melee_bonus"))
            .defineInRange("melee_bonus", 4.0, 0, 1000);
    public static final ModConfigSpec.DoubleValue STOMP_MIN_FALL = B
            .comment("Landings from at least this high (blocks) hurt everything around (a jump on flat ground is ~2.3).").translation(key("stomp_min_fall"))
            .defineInRange("stomp_min_fall", 3.5, 0, 256);
    public static final ModConfigSpec.DoubleValue STOMP_DAMAGE_PER_BLOCK = B
            .comment("Stomp damage per block fallen, at the centre (half of it at the edge of the shock wave).").translation(key("stomp_damage_per_block"))
            .defineInRange("stomp_damage_per_block", 2.0, 0, 1000);
    public static final ModConfigSpec.DoubleValue STOMP_MAX_DAMAGE = B
            .comment("Most damage a stomp can do.").translation(key("stomp_max_damage"))
            .defineInRange("stomp_max_damage", 30.0, 0, 10_000);
    public static final ModConfigSpec.IntValue LIGHTNING_STUN_TICKS = B
            .comment("A lightning strike knocks the suit out for this many ticks (sparks, slump), then it restarts.").translation(key("lightning_stun_ticks"))
            .defineInRange("lightning_stun_ticks", 100, 0, 6000);
    public static final ModConfigSpec.BooleanValue SMASH_BLOCKS = B
            .comment("Walking breaks leaves, panes, cobwebs, snow layers (block tag powerarmor:suit_smashes_walking); sprinting also",
                    "breaks glass and ice (powerarmor:suit_smashes_sprinting). Nothing drops.").translation(key("smash_blocks"))
            .define("smash_blocks", true);

    // ---- The machine mind (design sec. 8): what a powered suit does with nobody inside
    public static final ModConfigSpec.IntValue NUGGET_STORE_MAX = B.pop().translation(key("mind")).push("mind")
            .comment("Most iron nuggets a suit's hidden ammo store holds (right-click the suit with nuggets to load it). The",
                    "chaingun uses it: the machine mind always, a pilot after the nuggets in their own inventory.").translation(key("nugget_store_max"))
            .defineInRange("nugget_store_max", 640, 0, 100_000);
    public static final ModConfigSpec.DoubleValue GUARD_RANGE = B
            .comment("A friendly suit with a chaingun shoots monsters within this many blocks of its owner (or of itself).").translation(key("guard_range"))
            .defineInRange("guard_range", 16.0, 0, 64);
    public static final ModConfigSpec.DoubleValue WORK_RADIUS = B
            .comment("How far from home a friendly suit's drill (an open hall at home level, 3 blocks high) and chainsaw",
                    "(natural trees) work, in blocks. Keep it under 32: allays only see dropped items within 32 blocks.").translation(key("work_radius"))
            .defineInRange("work_radius", 12.0, 1, 32);
    public static final ModConfigSpec.DoubleValue ROGUE_SPAWN_CHANCE = B
            .comment("Rogue suits: once per in-game day (like the wandering trader) this is the chance that one turns up 48-64",
                    "blocks from a random player in the overworld (only one at a time, not on peaceful). 0.02 = about once in",
                    "50 days. 0 = never.").translation(key("rogue_spawn_chance"))
            .defineInRange("rogue_spawn_chance", 0.02, 0, 1);
    public static final ModConfigSpec.IntValue ROGUE_LIFETIME = B
            .comment("A rogue suit leaves after this many ticks (24000 = an in-game day), as soon as no player is within 64",
                    "blocks to watch it go.").translation(key("rogue_lifetime"))
            .defineInRange("rogue_lifetime", 24000, 20, 10_000_000);
    public static final ModConfigSpec.DoubleValue EM_GUARD_RADIUS = B
            .comment("A friendly suit with an EM projector guards this many blocks around its home: monsters inside get flung.").translation(key("em_guard_radius"))
            .defineInRange("em_guard_radius", 12.0, 0, 32);
    public static final ModConfigSpec.DoubleValue EM_GUARD_LAUNCH = B
            .comment("How hard the guarding mind flings a monster, blocks per second (straight up: 44 = ~24 blocks high, enough",
                    "for the fall to kill a zombie; a pilot's throws are 32). Creepers are thrown away from home instead.").translation(key("em_guard_launch"))
            .defineInRange("em_guard_launch", 44.0, 0, 200);

    public static final ModConfigSpec SPEC = B.pop().build();

    private static String key(String name) {
        return PowerArmor.MODID + ".configuration." + name;
    }

    private PowerArmorConfig() {}
}
