package dev.ajaczero.powerarmor.item;

import dev.ajaczero.powerarmor.PowerArmorConfig;
import dev.ajaczero.powerarmor.entity.PowerArmorEntity;
import dev.ajaczero.powerarmor.entity.TntShell;
import dev.ajaczero.powerarmor.registry.ModDataComponents;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * The TNT launcher (Phase 11, 2026-10-25; the plan's "grenade launcher"): a revolver drum of TNT under a fat barrel,
 * Team Fortress 2's grenade launcher in the suit's steel. Each shot lobs a primed TNT block (TntShell) that blows up the
 * moment it hits something, as real TNT.
 *
 * <p>Decided with the user: the TNT comes from the suit's ammo slot (a creative pilot loads for free), the shot costs no
 * fuel unless the config says so, and the machine mind never fires it (no suit of yours lobs TNT round your base on its
 * own; rogues never carry one).
 *
 * <p>The drum (user, 2026-10-27): it holds {@link #DRUM} rounds. While there are rounds in it, a tap fires one and
 * holding fires one every half second ({@link #cooldownTicks}: shot_interval_ticks), so a full drum empties in under two
 * seconds. Reloading is filling the chambers back up, one at a time (a second each, reload_ticks), from the ammo slot,
 * starting half a second after the last shot (reload_delay_ticks); a shot interrupts the chamber being loaded, so you
 * can fire whatever is loaded at any time. The rounds in the drum stay on the launcher item ({@link
 * ModDataComponents#LOADED_ROUNDS}): take it out loaded and it goes back in loaded. The chamber being loaded is synced
 * as the socket's spin (0..1), for the HUD's drum.
 *
 * <p>A repeating tool: while the trigger is held, the suit calls {@link #use} whenever {@link #cooldownTicks} have
 * passed; the loading runs in {@link #serverTick}. After each shot the drum turns a quarter turn to bring the next round
 * under the barrel (client side, from the synced shot counter: TntLauncherEffects, drawn by ToolRotors).
 */
public class TntLauncherItem extends Item implements PowerTool, ModTooltip {
    /** How far ahead the operator's aim is worked out (blocks): the round flies at what the crosshair is on. */
    private static final double AIM_RANGE = 64;

    public TntLauncherItem(Properties properties) {
        super(properties);
    }

    /** Fuel per shot (none by default: the TNT is the cost). */
    @Override
    public int energyPerUse() {
        return PowerArmorConfig.TNT_LAUNCHER_ENERGY.get();
    }

    @Override
    public ToolEffects createEffects() {
        return new TntLauncherEffects();
    }

    /** Rounds the drum holds (the model's four chambers). */
    public static final int DRUM = 4;

    /** Ticks between shots while the trigger is held (and between dry clicks on an empty drum). */
    @Override
    public int cooldownTicks() {
        return PowerArmorConfig.TNT_LAUNCHER_SHOT_INTERVAL.get();
    }

    /** Both sides: rounds loaded in this launcher's drum. */
    public static int loaded(ItemStack launcher) {
        return Math.min(DRUM, launcher.getOrDefault(ModDataComponents.LOADED_ROUNDS.get(), 0));
    }

    /** Server: set the rounds in the drum of the launcher in this suit's socket (stored on the item, synced with it). */
    public static void setLoaded(PowerArmorEntity suit, ItemStack launcher, int rounds) {
        if (rounds <= 0) {
            launcher.remove(ModDataComponents.LOADED_ROUNDS.get());
        } else {
            launcher.set(ModDataComponents.LOADED_ROUNDS.get(), Math.min(DRUM, rounds));
        }
        suit.getInventory().setChanged(); // a component changed in place: sync the socket's copy (the HUD's drum)
    }

    /** Server: one suit's loading state (not saved: a chamber half loaded when the world closes starts over). */
    private static final class Drum {
        int sinceShot = Integer.MAX_VALUE / 2;
        int progress;
    }

    /** Server, every tick while it's in the socket: load the next chamber once the trigger has rested, from the ammo
     *  slot (free for a creative pilot), while the suit has power; the chamber's progress goes to the clients as spin. */
    @Override
    public void serverTick(PowerArmorEntity suit) {
        ItemStack launcher = suit.getInventory().getItem(PowerArmorEntity.SOCKET_SLOT);
        Drum drum = suit.socket().toolData(Drum.class, Drum::new);
        drum.sinceShot = Math.min(drum.sinceShot + 1, Integer.MAX_VALUE / 2);
        int loaded = loaded(launcher);
        boolean free = suit.getControllingPassenger() instanceof Player pilot && pilot.hasInfiniteMaterials();
        int reload = Math.max(1, PowerArmorConfig.TNT_LAUNCHER_RELOAD.get());
        boolean loading = loaded < DRUM && suit.isPowered() && drum.sinceShot >= PowerArmorConfig.TNT_LAUNCHER_RELOAD_DELAY.get()
                && (free || isRound(suit.getAmmoStack()));
        if (!loading) {
            drum.progress = 0;
        } else if (++drum.progress >= reload) {
            if (free || suit.takeAmmo(TntLauncherItem::isRound)) {
                setLoaded(suit, launcher, loaded + 1);
                // a round dropping into its chamber and the drum clicking round
                suit.playSound(SoundEvents.CROSSBOW_LOADING_END.value(), 0.6F, 0.7F);
            }
            drum.progress = 0;
        }
        suit.socket().setSpin(drum.progress / (float) reload, 0F);
    }

    @Override
    public String suitBone() {
        return "tnt_launcher";
    }

    /** Its muzzle is 9 px below the socket base, 1 px short of the chaingun's. */
    @Override
    public float muzzleInsetPixels() {
        return 1F;
    }

    /** The barrel sits in front of the drum's axis (the socket's): its centre is 1.25 px toward the suit's front. */
    @Override
    public float barrelForwardPixels() {
        return 1.25F;
    }

    /** Does this go in the launcher? TNT, from the ammo slot. */
    public static boolean isRound(ItemStack stack) {
        return stack.is(Items.TNT);
    }

    @Override
    public boolean use(PowerArmorEntity suit, LivingEntity operator, ItemStack tool) {
        // The machine mind never fires it (user decision): only a pilot does.
        if (!(operator instanceof Player pilot) || !(suit.level() instanceof ServerLevel level)) {
            return false;
        }
        Vec3 muzzle = SocketAim.muzzlePosition(suit, operator);
        int loaded = loaded(tool);
        if (loaded == 0) {
            // An empty drum: the hammer clicks on nothing (once per shot interval while the trigger is held). With TNT in
            // the ammo slot it's only reloading; with none, say so.
            level.playSound(null, muzzle.x, muzzle.y, muzzle.z, SoundEvents.DISPENSER_FAIL, SoundSource.PLAYERS, 0.6F, 1.2F);
            if (!pilot.hasInfiniteMaterials() && !isRound(suit.getAmmoStack())) {
                pilot.sendOverlayMessage(Component.translatable("message.powerarmor.no_tnt"));
            }
            return false;
        }
        setLoaded(suit, tool, loaded - 1);
        Drum drum = suit.socket().toolData(Drum.class, Drum::new);
        drum.sinceShot = 0; // the reload waits, and the chamber being loaded starts over
        drum.progress = 0;
        suit.socket().setSpin(0F, 0F); // (serverTick ran first this tick and may have counted it on)
        Launch launch = launch(suit, operator, 1F);
        level.addFreshEntity(new TntShell(level, operator, launch.from(), launch.velocity(),
                PowerArmorConfig.TNT_LAUNCHER_POWER.get().floatValue(), PowerArmorConfig.TNT_LAUNCHER_BREAKS_BLOCKS.get()));
        // The launch: a deep thunk, the whoosh of the round leaving and the hiss of its fuse.
        level.playSound(null, muzzle.x, muzzle.y, muzzle.z, SoundEvents.DISPENSER_LAUNCH, SoundSource.PLAYERS, 1.0F, 0.5F);
        level.playSound(null, muzzle.x, muzzle.y, muzzle.z, SoundEvents.FIREWORK_ROCKET_LAUNCH, SoundSource.PLAYERS, 0.8F, 0.6F);
        level.playSound(null, muzzle.x, muzzle.y, muzzle.z, SoundEvents.TNT_PRIMED, SoundSource.PLAYERS, 0.35F, 1.3F);
        suit.socket().countShot(); // every client: the recoil kick and the drum's quarter turn; the pilot's own client puffs smoke
        for (ServerPlayer viewer : level.players()) {
            if (viewer != operator && viewer.distanceToSqr(muzzle) < 64 * 64) {
                muzzlePuff((type, x, y, z, n, sp, v) -> level.sendParticles(viewer, type, false, false, x, y, z, n, sp, sp, sp, v), muzzle, true);
            }
        }
        return true;
    }

    /** Where a round starts and how fast it goes. */
    public record Launch(Vec3 from, Vec3 velocity) {}

    /**
     * Both sides: the round this operator would fire now. Like the chaingun's bullets it leaves from the eye toward the
     * crosshair (TntShell draws it from the muzzle), and it carries the suit's own motion so running forward doesn't
     * overtake it. The server fires it ({@code partial} 1); the pilot's HUD draws its arc every frame from the same
     * numbers ({@code partial} the frame's, client/hud/TntLauncherHud), so the arc is the flight.
     */
    public static Launch launch(PowerArmorEntity suit, LivingEntity operator, float partial) {
        Vec3 eye = operator.getEyePosition(partial);
        Vec3 direction = SocketAim.aimPoint(suit, operator, AIM_RANGE, partial).subtract(eye).normalize();
        if (direction.lengthSqr() < 0.5) {
            direction = operator.getViewVector(partial);
        }
        return new Launch(eye, direction.scale(TntShell.SPEED).add(suit.getDeltaMovement().multiply(1, 0, 1)));
    }

    /** The puff at the muzzle after a shot: smoke and a few sparks (no big flash: the charge only lobs the round). The
     *  big white puffs only where they're seen from a distance ({@code puffs}): in the pilot's own cockpit view they drift
     *  right in front of the camera as big blobs. */
    public static void muzzlePuff(ToolEffects.Particles out, Vec3 at, boolean puffs) {
        if (puffs) {
            out.add(ParticleTypes.POOF, at.x, at.y, at.z, 4, 0.08, 0.03);
        }
        out.add(ParticleTypes.SMOKE, at.x, at.y, at.z, 3, 0.06, 0.02);
        out.add(ParticleTypes.SMALL_FLAME, at.x, at.y, at.z, 2, 0.04, 0.04);
    }

    /** Both sides: rounds in reserve for this pilot's launcher (the ammo slot's TNT, not the drum), or -1 for endless
     *  (creative). */
    public static int countRounds(PowerArmorEntity suit, Player pilot) {
        if (pilot.hasInfiniteMaterials()) {
            return -1;
        }
        ItemStack ammo = suit.getAmmoStack();
        return isRound(ammo) ? ammo.getCount() : 0;
    }

    /** On foot it's just a heavy piece of kit: say where it goes. */
    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (!level.isClientSide()) {
            player.sendOverlayMessage(Component.translatable("message.powerarmor.tool_on_foot"));
        }
        return InteractionResult.PASS;
    }

    @Override
    public void addTooltip(ItemStack stack, TooltipContext context, Consumer<Component> tooltip, TooltipFlag flag) {
        tooltip.accept(Component.translatable("item.powerarmor.tnt_launcher.tooltip").withStyle(ChatFormatting.GRAY));
        tooltip.accept(Component.translatable("item.powerarmor.tnt_launcher.stats", loaded(stack),
                String.format("%.1f", this.cooldownTicks() / 20F),
                String.format("%.1f", PowerArmorConfig.TNT_LAUNCHER_RELOAD.get() / 20F)).withStyle(ChatFormatting.DARK_AQUA));
        tooltip.accept(Component.translatable("item.powerarmor.power_tool.install").withStyle(ChatFormatting.DARK_GRAY));
    }
}
