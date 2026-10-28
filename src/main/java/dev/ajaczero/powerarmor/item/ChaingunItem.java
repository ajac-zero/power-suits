package dev.ajaczero.powerarmor.item;

import dev.ajaczero.powerarmor.PowerArmorConfig;
import dev.ajaczero.powerarmor.entity.Bullet;
import dev.ajaczero.powerarmor.entity.PowerArmorEntity;
import dev.ajaczero.powerarmor.entity.ai.GunAttack;
import dev.ajaczero.powerarmor.entity.ai.ToolUse;
import dev.ajaczero.powerarmor.registry.ModSounds;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * The suit's gun: a cluster of four barrels driven by the engine. Tap the trigger and it fires single heavy shots; hold
 * it and the barrels spin up, firing faster and faster until it's a barrage (10 bullets/s by default). Let go and they
 * coast down; grab the trigger again before they stop and it picks up where it was.
 *
 * <p>Each bullet costs one iron nugget from the pilot's inventory (off hand first, like a bow's arrows; free in
 * creative); the fuel goes with the spin: while the trigger is held it burns from walking's worth a tick with the barrels
 * just turning to three times that at full spin (user, 2026-10-27: the visor's burn embers then show the spin). Heavy single shots hit harder than the barrage's bullets. With
 * overheat on (config), too many bullets in a row overheat the barrels: the gun stops until they've cooled.
 *
 * <p>A continuous tool (the suit calls {@link #holdTick} every tick the trigger is held and {@link #serverTick} every
 * tick), so it times its own shots. The spin, heat and a shot counter are synced to every client through the suit:
 * they drive the barrel spin, the recoil, the muzzle burst, the heat glow and the motor whine.
 *
 * <p>Registered as power_suits:chaingun; the old ids power_suits:plasma_cannon and power_suits:rivet_gun are aliases, so
 * those items in old worlds load as chainguns. A flat item like a vanilla tool; only the suit wears it in 3D.
 */
public class ChaingunItem extends Item implements PowerTool, ModTooltip {
    /** How far the crosshair is traced to find the aim point. */
    private static final double AIM_RANGE = 96;
    /** Bullet spread at full spin, degrees (none for the first, slow shot). Small: the barrage should still hit what
     *  is in the crosshair (1.2 missed small mobs beyond ~20 blocks). */
    private static final float MAX_SPREAD = 0.4F;


    public ChaingunItem(Properties properties) {
        super(properties);
    }

    /** Per-suit state (PowerArmorEntity.toolData), server only. */
    static final class State {
        float spin;          // 0 = barrels at rest .. 1 = full speed
        float heat;          // 0 = cold .. 1 = overheated
        boolean overheated;  // locked until the barrels are cold (heat back to 0: the HUD's bar empty, ChaingunEffects)
        boolean held;        // the trigger was held last tick
        int timer;           // ticks until the next shot may fire
        int sinceShot = 100; // ticks since the last shot
        int noAmmoMessage;   // ticks until the "no bullets" message may show again
        float burnOwed;      // the spin's fuel not burnt yet (it's fractional a tick, the suit burns whole units)
    }

    private static State state(PowerArmorEntity suit) {
        return suit.socket().toolData(State.class, State::new);
    }

    /** None per shot: the fuel goes with the spin while the trigger is held ({@link #spinBurn}). */
    @Override
    public int energyPerUse() {
        return 0;
    }

    /** 1, 1.5: a per-tick figure for the tooltip without a needless ".0". */
    private static String perTick(double energy) {
        return energy == Math.rint(energy) ? String.format("%.0f", energy) : String.format("%.1f", energy);
    }

    @Override
    public int cooldownTicks() {
        return PowerArmorConfig.CHAINGUN_SLOW_INTERVAL.get();
    }

    @Override
    public String suitBone() {
        return "chaingun";
    }

    @Override
    public ToolEffects createEffects() {
        return new ChaingunEffects();
    }

    /** How the mind fights with it (GunAttack). */
    private static final GunAttack.Gun MIND_GUN =
            new GunAttack.Gun(28, false, suit -> true, suit -> !isOverheated(suit)); // the mind never runs out (SuitAmmo)

    @Override
    public ToolUse mindUse() {
        return ToolUse.GUN;
    }

    @Override
    public List<Goal> mindGoals(PowerArmorEntity suit) {
        // Fires from up to 28 blocks (bullets fly 80 blocks/s; past ~30 the spread misses small mobs); a friendly mind
        // needs bullets in the hidden store and waits out an overheat.
        return List.of(new GunAttack(suit, MIND_GUN));
    }

    @Override
    public boolean isContinuous() {
        return true;
    }

    @Override
    public boolean use(PowerArmorEntity suit, LivingEntity operator, ItemStack tool) {
        return false; // continuous: holdTick does the work
    }

    /** Server: the barrels are too hot to fire (overheat on): the mind waits instead of clicking. */
    public static boolean isOverheated(PowerArmorEntity suit) {
        return state(suit).overheated;
    }

    /** Ticks between shots at this spin. */
    static float interval(float spin) {
        return Mth.lerp(spin, PowerArmorConfig.CHAINGUN_SLOW_INTERVAL.get(), PowerArmorConfig.CHAINGUN_FAST_INTERVAL.get());
    }

    /** Damage of a bullet fired at this spin: heavy single shots, lighter barrage bullets. */
    public static float damage(float spin) {
        return (float) Mth.lerp(spin, PowerArmorConfig.CHAINGUN_DAMAGE_SLOW.get(), PowerArmorConfig.CHAINGUN_DAMAGE_FAST.get());
    }

    /** Every tick, held or not: the timer runs, the barrels coast down when let go, hot barrels cool. */
    @Override
    public void serverTick(PowerArmorEntity suit) {
        State s = state(suit);
        s.timer = Math.max(0, s.timer - 1);
        s.sinceShot++;
        s.noAmmoMessage = Math.max(0, s.noAmmoMessage - 1);
        if (!s.held) {
            s.spin = Math.max(0F, s.spin - 1F / 30F); // ~1.5 s to stop from full speed
        }
        s.held = false; // holdTick sets it again if the trigger is still down this tick
        // Barrels cool once you stop firing: a gap longer than the slow interval, so the spin-up's first slow shots
        // still count as firing in a row.
        if (s.sinceShot > PowerArmorConfig.CHAINGUN_SLOW_INTERVAL.get() + 3) {
            s.heat = Math.max(0F, s.heat - 1F / PowerArmorConfig.CHAINGUN_COOL_TICKS.get());
        }
        // Unlocks only when fully cold (was 0.2 until 2026-10-27; user: the HUD keeps OVERHEAT up until the bar is empty,
        // and the gun must not fire before that).
        if (s.overheated && s.heat <= 0F) {
            s.overheated = false;
        }
        if (!PowerArmorConfig.CHAINGUN_OVERHEAT.get()) {
            s.heat = 0F;
            s.overheated = false;
        }
        suit.socket().setSpin(s.spin, s.heat);
    }

    /** Every tick the trigger is held (and the suit can operate): spin up, and fire whenever the next shot is due. */
    @Override
    public void holdTick(PowerArmorEntity suit, LivingEntity operator, ItemStack tool) {
        State s = state(suit);
        s.held = true;
        if (s.overheated || !this.burnSpin(suit, s)) {
            return; // overheated (or no fuel to turn the barrels): it can only coast
        }
        s.spin = Math.min(1F, s.spin + 1F / PowerArmorConfig.CHAINGUN_SPIN_UP.get());
        if (s.timer > 0 || !(suit.level() instanceof ServerLevel level)) {
            return;
        }
        s.timer = Math.max(1, Math.round(interval(s.spin)));
        Vec3 muzzle = SocketAim.muzzlePosition(suit, operator);
        if (!takeBullet(suit, operator)) {
            level.playSound(null, muzzle.x, muzzle.y, muzzle.z, SoundEvents.DISPENSER_FAIL, SoundSource.PLAYERS, 0.6F, 1.6F);
            if (s.noAmmoMessage == 0) {
                if (operator instanceof Player pilot) {
                    pilot.sendOverlayMessage(Component.translatable("message.power_suits.no_bullets"));
                }
                s.noAmmoMessage = 40;
            }
            return;
        }
        this.fire(level, suit, operator, muzzle, s);
    }

    /** Fuel per tick the held barrels burn at this spin: slow_spin_energy_per_tick just turning, full_spin_energy_per_tick
     *  at full speed, in between along the spin. */
    public static float spinBurn(float spin) {
        return (float) Mth.lerp(spin, PowerArmorConfig.CHAINGUN_SLOW_SPIN_ENERGY.get(), PowerArmorConfig.CHAINGUN_FULL_SPIN_ENERGY.get());
    }

    /** This tick's spin fuel (whole units as they add up): false when there's none to burn (the barrels only coast). */
    private boolean burnSpin(PowerArmorEntity suit, State s) {
        s.burnOwed += spinBurn(s.spin);
        int whole = (int) s.burnOwed;
        if (!suit.power().hasEnergyFor(Math.max(1, whole))) {
            s.burnOwed = Math.min(s.burnOwed, 1F); // don't run up a debt while it's dry
            return false;
        }
        suit.power().consume(whole);
        s.burnOwed -= whole;
        return true;
    }

    private void fire(ServerLevel level, PowerArmorEntity suit, LivingEntity operator, Vec3 muzzle, State s) {
        // The bullet flies along the operator's line of sight: from the eye through the crosshair, so it hits what the
        // pilot (or the mind) sees there. Clients draw it coming out of the gun (Bullet.visualShift); sound and muzzle
        // burst are at the gun. The operator owns the bullet: kills are the pilot's, or the suit's own.
        Vec3 eye = operator.getEyePosition();
        Vec3 direction = SocketAim.aimPoint(suit, operator, AIM_RANGE).subtract(eye).normalize();
        if (direction.lengthSqr() < 0.5) {
            direction = operator.getViewVector(1F);
        }
        float spread = MAX_SPREAD * s.spin * Mth.DEG_TO_RAD;
        direction = direction.xRot((float) level.getRandom().nextGaussian() * spread).yRot((float) level.getRandom().nextGaussian() * spread);
        float spin = s.spin;
        level.addFreshEntity(new Bullet(level, operator, eye, direction.scale(Bullet.SPEED), damage(spin), 1F - spin));
        suit.socket().countUse();
        suit.socket().countShot(); // every client: recoil kick, muzzle burst (the pilot's own client draws it on screen)
        s.sinceShot = 0;
        if (PowerArmorConfig.CHAINGUN_OVERHEAT.get()) {
            s.heat = Math.min(1F, s.heat + 1F / PowerArmorConfig.CHAINGUN_OVERHEAT_SHOTS.get());
            if (s.heat >= 1F - 1e-4F) { // (n x 1/n can land a hair under 1 in floats)
                s.heat = 1F;
                s.overheated = true;
                level.playSound(null, muzzle.x, muzzle.y, muzzle.z, SoundEvents.LAVA_EXTINGUISH, SoundSource.PLAYERS, 0.9F, 0.8F);
                level.playSound(null, muzzle.x, muzzle.y, muzzle.z, SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.7F, 1.4F);
            }
        }
        // Heavy single shots: a deep thump and the piston-like clank; the barrage: lighter, higher cracks.
        level.playSound(null, muzzle.x, muzzle.y, muzzle.z, SoundEvents.FIREWORK_ROCKET_BLAST, SoundSource.PLAYERS,
                0.9F - 0.3F * spin, 0.65F + 0.55F * spin + level.getRandom().nextFloat() * 0.1F);
        if (spin < 0.4F) {
            level.playSound(null, muzzle.x, muzzle.y, muzzle.z, SoundEvents.IRON_GOLEM_DAMAGE, SoundSource.PLAYERS, 0.4F, 1.5F);
        }
        for (ServerPlayer viewer : level.players()) {
            if (viewer != operator && viewer.distanceToSqr(muzzle) < 64 * 64) {
                muzzleBurst((type, x, y, z, n, sp, v) -> level.sendParticles(viewer, type, false, false, x, y, z, n, sp, sp, sp, v), muzzle, spin, true);
            }
        }
    }

    /** The puff at the muzzle: sparks and smoke, and a burst of steam for the heavy slow shots (not in the pilot's
     *  own first-person view, where it covered the target). Light at full spin: ten of these a second. */
    public static void muzzleBurst(ToolEffects.Particles out, Vec3 at, float spin, boolean steam) {
        if (steam && spin < 0.4F) {
            out.add(ParticleTypes.POOF, at.x, at.y, at.z, 3, 0.06, 0.03);
        }
        out.add(ParticleTypes.SMALL_FLAME, at.x, at.y, at.z, spin < 0.4F ? 3 : 1, 0.04, 0.05);
        out.add(ParticleTypes.SMOKE, at.x, at.y, at.z, 1, 0.05, 0.02);
    }

    /** A bullet: an iron nugget. */
    public static boolean isBullet(ItemStack stack) {
        return stack.is(Items.IRON_NUGGET);
    }

    /** One bullet for this operator: an iron nugget from a pilot's inventory (off hand first, like a bow's arrows; free
     *  in creative); the machine mind's never run out (SuitAmmo). */
    public static boolean takeBullet(PowerArmorEntity suit, LivingEntity operator) {
        return SuitAmmo.take(operator, ChaingunItem::isBullet);
    }

    /** Both sides: how many bullets (iron nuggets) the pilot carries (HUD); SuitAmmo.ENDLESS in creative. */
    public static int countBullets(Player player) {
        return SuitAmmo.count(player, ChaingunItem::isBullet);
    }

    /** Keep the motor whine going while the barrels coast down. */
    @Override
    public boolean keepsRunning(PowerArmorEntity suit) {
        return state(suit).spin > 0.01F;
    }

    @Override
    @Nullable
    public SoundEvent runSound() {
        return ModSounds.CHAINGUN.get();
    }

    /** The motor whine rises with the spin. */
    @Override
    public float runSoundPitch(PowerArmorEntity suit) {
        return 0.5F + 1.3F * suit.socket().getSpin();
    }

    @Override
    public float runSoundVolume(PowerArmorEntity suit) {
        return 0.25F + 0.75F * suit.socket().getSpin();
    }

    /** On foot it's just a heavy piece of kit: say where it goes. */
    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (!level.isClientSide()) {
            player.sendOverlayMessage(Component.translatable("message.power_suits.tool_on_foot"));
        }
        return InteractionResult.PASS;
    }

    @Override
    public void addTooltip(ItemStack stack, TooltipContext context, Consumer<Component> tooltip, TooltipFlag flag) {
        tooltip.accept(Component.translatable("item.power_suits.chaingun.tooltip").withStyle(ChatFormatting.GRAY));
        tooltip.accept(Component.translatable("item.power_suits.chaingun.stats",
                String.format("%.1f", damage(0F) / 2), String.format("%.1f", damage(1F) / 2),
                String.format("%.0f", 20F / PowerArmorConfig.CHAINGUN_FAST_INTERVAL.get()),
                perTick(PowerArmorConfig.CHAINGUN_SLOW_SPIN_ENERGY.get()), perTick(PowerArmorConfig.CHAINGUN_FULL_SPIN_ENERGY.get()))
                .withStyle(ChatFormatting.DARK_AQUA));
        tooltip.accept(Component.translatable("item.power_suits.power_tool.install").withStyle(ChatFormatting.DARK_GRAY));
    }
}
