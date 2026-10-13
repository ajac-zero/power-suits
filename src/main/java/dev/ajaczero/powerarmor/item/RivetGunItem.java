package dev.ajaczero.powerarmor.item;

import dev.ajaczero.powerarmor.PowerArmorConfig;
import dev.ajaczero.powerarmor.entity.PowerArmorEntity;
import dev.ajaczero.powerarmor.entity.Rivet;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * The suit's gun: steam pressure from the engine drives a piston that punches a red-hot rivet out of the barrel.
 * Each shot costs fuel (the pressure) and one iron nugget from the pilot's inventory (the rivet), taken like a bow
 * takes arrows: off hand first, then the rest; free in creative. Without nuggets it just clicks.
 *
 * <p>The rivet leaves the gun's muzzle and lands on whatever is under the crosshair: it drops a little in flight, so
 * the gun aims the arc (launches a bit high), and you hit what you look at even though the gun sits off to the left
 * of your eyes. It's a GeckoLib item (GeoItem) so it can use the same 3D model in your hand as on the suit's arm.
 * Registered as powerarmor:rivet_gun; the old powerarmor:plasma_cannon id is an alias, so cannons in old worlds
 * load as rivet guns.
 */
public class RivetGunItem extends Item implements PowerTool, GeoItem {
    // Fuel per shot, cooldown (default 0.6 s; the shot animation is 0.5 s) and damage: PowerArmorConfig.
    public static float damage() {
        return PowerArmorConfig.RIVET_DAMAGE.get().floatValue();
    }
    /** How far the crosshair is traced to find the aim point. */
    private static final double AIM_RANGE = 96;

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    public RivetGunItem(Properties properties) {
        super(properties);
    }

    @Override
    public int energyPerUse() {
        return PowerArmorConfig.RIVET_ENERGY.get();
    }

    @Override
    public int cooldownTicks() {
        return PowerArmorConfig.RIVET_COOLDOWN.get();
    }

    @Override
    public String suitBone() {
        return "rivet_gun";
    }

    @Override
    public boolean use(PowerArmorEntity suit, Player pilot, ItemStack tool) {
        if (!(suit.level() instanceof ServerLevel level)) {
            return false;
        }
        Vec3 muzzle = SocketAim.muzzlePosition(suit, pilot);
        if (!takeRivet(pilot)) {
            level.playSound(null, muzzle.x, muzzle.y, muzzle.z, SoundEvents.DISPENSER_FAIL, SoundSource.PLAYERS, 0.7F, 1.4F);
            pilot.displayClientMessage(Component.translatable("message.powerarmor.no_rivets"), true);
            return false; // no fuel charged; the suit still waits a cooldown before trying again
        }
        Vec3 look = pilot.getViewVector(1F);
        Vec3 velocity = launchVelocity(muzzle, SocketAim.aimPoint(suit, pilot, AIM_RANGE));
        if (velocity.normalize().dot(look) < 0.5) {
            velocity = look.scale(Rivet.SPEED); // the aim point is beside/behind the muzzle (something very close): fire straight
        }
        level.addFreshEntity(new Rivet(level, pilot, muzzle, velocity, !pilot.hasInfiniteMaterials()));

        suit.triggerAnim("action", "rivet_shot"); // recoil, piston slam, drum turn on every client
        level.playSound(null, muzzle.x, muzzle.y, muzzle.z, SoundEvents.PISTON_EXTEND, SoundSource.PLAYERS, 1.0F, 0.55F);
        level.playSound(null, muzzle.x, muzzle.y, muzzle.z, SoundEvents.IRON_GOLEM_DAMAGE, SoundSource.PLAYERS, 0.5F, 1.6F);
        level.playSound(null, muzzle.x, muzzle.y, muzzle.z, SoundEvents.GENERIC_EXTINGUISH_FIRE, SoundSource.PLAYERS, 0.35F, 1.8F);
        // Everyone else sees the muzzle burst here; the pilot's own client draws it at the muzzle on their screen
        // (PowerArmorEntity.tickShotEffects), which in first person is the cockpit viewmodel's.
        for (ServerPlayer viewer : level.players()) {
            if (viewer != pilot && viewer.distanceToSqr(muzzle) < 64 * 64) {
                muzzleBurst((type, x, y, z, n, s, v) -> level.sendParticles(viewer, type, false, x, y, z, n, s, s, s, v), muzzle, true);
            }
        }
        return true;
    }

    /** A particle sink: (type, x, y, z, count, spread, speed). Lets the server and the client share muzzleBurst. */
    public interface Particles {
        void add(ParticleOptions type, double x, double y, double z, int count, double spread, double speed);
    }

    /** The puff at the muzzle: steam (not for the pilot's own first-person view, where it covers the target),
     *  a few sparks, a wisp of smoke. */
    public static void muzzleBurst(Particles out, Vec3 at, boolean steam) {
        if (steam) {
            out.add(ParticleTypes.POOF, at.x, at.y, at.z, 4, 0.06, 0.03);
            out.add(ParticleTypes.CLOUD, at.x, at.y, at.z, 2, 0.05, 0.02);
        }
        out.add(ParticleTypes.SMALL_FLAME, at.x, at.y, at.z, 3, 0.04, 0.06);
        out.add(ParticleTypes.SMOKE, at.x, at.y, at.z, 2, 0.05, 0.02);
    }

    /**
     * Launch velocity that makes the rivet pass through {@code target}: the rivet moves by its velocity every tick and
     * then falls by GRAVITY, so after n ticks it has dropped g*n*(n-1)/2; aim that much higher (n = flight ticks at
     * full speed). Long shots visibly arc; short ones fly flat.
     */
    static Vec3 launchVelocity(Vec3 from, Vec3 target) {
        Vec3 d = target.subtract(from);
        double n = Math.max(1, d.length() / Rivet.SPEED);
        return new Vec3(d.x / n, d.y / n + Rivet.GRAVITY * (n - 1) / 2, d.z / n);
    }

    /** Server: use up one iron nugget (off hand first, like a bow's arrows). False if there is none. Creative: free. */
    public static boolean takeRivet(Player player) {
        if (player.hasInfiniteMaterials()) {
            return true;
        }
        ItemStack offhand = player.getOffhandItem();
        if (offhand.is(Items.IRON_NUGGET)) {
            offhand.shrink(1);
            return true;
        }
        Inventory inventory = player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack.is(Items.IRON_NUGGET)) {
                stack.shrink(1);
                return true;
            }
        }
        return false;
    }

    /** Both sides: how many rivets the player carries (for the HUD). */
    public static int countRivets(Player player) {
        return player.getInventory().countItem(Items.IRON_NUGGET);
    }

    /** On foot it's just a heavy piece of kit: say where it goes. */
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (!level.isClientSide()) {
            player.displayClientMessage(Component.translatable("message.powerarmor.tool_on_foot"), true);
        }
        return InteractionResultHolder.pass(player.getItemInHand(hand));
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.powerarmor.rivet_gun.tooltip").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.powerarmor.power_tool.stats", String.format("%.1f", damage() / 2), this.energyPerUse(),
                String.format("%.1f", this.cooldownTicks() / 20.0)).withStyle(ChatFormatting.DARK_AQUA));
        tooltip.add(Component.translatable("item.powerarmor.power_tool.install").withStyle(ChatFormatting.DARK_GRAY));
    }

    // GeckoLib: no animations of its own in the hand.
    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.cache;
    }
}
