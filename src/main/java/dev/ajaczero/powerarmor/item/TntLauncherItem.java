package dev.ajaczero.powerarmor.item;

import dev.ajaczero.powerarmor.PowerArmorConfig;
import dev.ajaczero.powerarmor.entity.PowerArmorEntity;
import dev.ajaczero.powerarmor.entity.TntShell;
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
 * <p>Decided with the user: the TNT comes from the suit's ammo slot (one block per shot; a creative pilot fires for
 * free), a tap fires one round and holding fires again each time it has reloaded (a second by default), the shot costs
 * no fuel unless the config says so, and the machine mind never fires it (no suit of yours lobs TNT round your base on
 * its own; rogues never carry one).
 *
 * <p>A repeating tool: while the trigger is held, the suit calls {@link #use} whenever the reload ({@link
 * #cooldownTicks}) has run out. After each shot the drum turns a quarter turn to bring the next round under the barrel
 * (client side, from the synced shot counter: PowerArmorEntity.tickShotEffects, drawn by ToolRotors).
 */
public class TntLauncherItem extends Item implements PowerTool {
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

    /** The reload: ticks between shots while the trigger is held. */
    @Override
    public int cooldownTicks() {
        return PowerArmorConfig.TNT_LAUNCHER_RELOAD.get();
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
        if (!pilot.hasInfiniteMaterials() && !suit.takeAmmo(TntLauncherItem::isRound)) {
            // An empty drum: the hammer clicks on nothing (once per reload while the trigger is held).
            level.playSound(null, muzzle.x, muzzle.y, muzzle.z, SoundEvents.DISPENSER_FAIL, SoundSource.PLAYERS, 0.6F, 1.2F);
            pilot.sendOverlayMessage(Component.translatable("message.powerarmor.no_tnt"));
            return false;
        }
        // Like the chaingun's bullets the round leaves from the eye toward the crosshair (TntShell draws it from the
        // muzzle), and it carries the suit's own motion so running forward doesn't overtake it.
        Vec3 eye = operator.getEyePosition();
        Vec3 direction = SocketAim.aimPoint(suit, operator, AIM_RANGE).subtract(eye).normalize();
        if (direction.lengthSqr() < 0.5) {
            direction = operator.getViewVector(1F);
        }
        Vec3 velocity = direction.scale(TntShell.SPEED).add(suit.getDeltaMovement().multiply(1, 0, 1));
        level.addFreshEntity(new TntShell(level, operator, eye, velocity,
                PowerArmorConfig.TNT_LAUNCHER_POWER.get().floatValue(), PowerArmorConfig.TNT_LAUNCHER_BREAKS_BLOCKS.get()));
        // The launch: a deep thunk, the whoosh of the round leaving and the hiss of its fuse.
        level.playSound(null, muzzle.x, muzzle.y, muzzle.z, SoundEvents.DISPENSER_LAUNCH, SoundSource.PLAYERS, 1.0F, 0.5F);
        level.playSound(null, muzzle.x, muzzle.y, muzzle.z, SoundEvents.FIREWORK_ROCKET_LAUNCH, SoundSource.PLAYERS, 0.8F, 0.6F);
        level.playSound(null, muzzle.x, muzzle.y, muzzle.z, SoundEvents.TNT_PRIMED, SoundSource.PLAYERS, 0.35F, 1.3F);
        suit.countShot(); // every client: the recoil kick and the drum's quarter turn; the pilot's own client puffs smoke
        for (ServerPlayer viewer : level.players()) {
            if (viewer != operator && viewer.distanceToSqr(muzzle) < 64 * 64) {
                muzzlePuff((type, x, y, z, n, sp, v) -> level.sendParticles(viewer, type, false, false, x, y, z, n, sp, sp, sp, v), muzzle, true);
            }
        }
        return true;
    }

    /** The puff at the muzzle after a shot: smoke and a few sparks (no big flash: the charge only lobs the round). The
     *  big white puffs only where they're seen from a distance ({@code puffs}): in the pilot's own cockpit view they drift
     *  right in front of the camera as big blobs. */
    public static void muzzlePuff(ChaingunItem.Particles out, Vec3 at, boolean puffs) {
        if (puffs) {
            out.add(ParticleTypes.POOF, at.x, at.y, at.z, 4, 0.08, 0.03);
        }
        out.add(ParticleTypes.SMOKE, at.x, at.y, at.z, 3, 0.06, 0.02);
        out.add(ParticleTypes.SMALL_FLAME, at.x, at.y, at.z, 2, 0.04, 0.04);
    }

    /** Both sides: rounds left for this pilot's launcher (the ammo slot's TNT), or -1 for endless (creative). */
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
    public void appendHoverText(ItemStack stack, TooltipContext context, net.minecraft.world.item.component.TooltipDisplay display,
            java.util.function.Consumer<Component> tooltip, TooltipFlag flag) {
        tooltip.accept(Component.translatable("item.powerarmor.tnt_launcher.tooltip").withStyle(ChatFormatting.GRAY));
        tooltip.accept(Component.translatable("item.powerarmor.tnt_launcher.stats",
                String.format("%.1f", this.cooldownTicks() / 20F)).withStyle(ChatFormatting.DARK_AQUA));
        tooltip.accept(Component.translatable("item.powerarmor.power_tool.install").withStyle(ChatFormatting.DARK_GRAY));
    }
}
