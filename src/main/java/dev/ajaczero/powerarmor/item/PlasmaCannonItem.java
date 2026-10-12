package dev.ajaczero.powerarmor.item;

import dev.ajaczero.powerarmor.entity.PlasmaBolt;
import dev.ajaczero.powerarmor.entity.PowerArmorEntity;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * The first power tool: fires a plasma bolt where the pilot aims. The bolt leaves the cannon's muzzle
 * and flies toward whatever is under the crosshair, so it hits what you look at even though the
 * cannon sits off to the right of your eyes.
 *
 * It's a GeckoLib item (GeoItem) so it can use the same 3D model in your hand as on the suit's arm.
 */
public class PlasmaCannonItem extends Item implements PowerTool, GeoItem {
    public static final int ENERGY_PER_SHOT = 300;
    public static final int COOLDOWN_TICKS = 12;   // 0.6 s; the shot animation is 0.5 s
    public static final float DAMAGE = 10.0F;       // 5 hearts
    /** How far the crosshair is traced to find the aim point. */
    private static final double AIM_RANGE = 96;

    // Muzzle in the torso's frame, in blocks (measured in Blockbench from the muzzle_flash bone):
    // right of centre, up from the feet, forward. The torso pivot is 1.54 up; the torso pitches with the view.
    private static final double MUZZLE_RIGHT = 0.95, MUZZLE_UP = 1.73, MUZZLE_FORWARD = 1.71, TORSO_PIVOT_UP = 1.54;

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    public PlasmaCannonItem(Properties properties) {
        super(properties);
    }

    @Override
    public int energyPerUse() {
        return ENERGY_PER_SHOT;
    }

    @Override
    public int cooldownTicks() {
        return COOLDOWN_TICKS;
    }

    @Override
    public String suitBone() {
        return "cannon";
    }

    @Override
    public boolean use(PowerArmorEntity suit, Player pilot, ItemStack tool) {
        if (!(suit.level() instanceof ServerLevel level)) {
            return false;
        }
        Vec3 muzzle = muzzlePosition(suit, pilot);
        Vec3 look = pilot.getViewVector(1F);
        Vec3 direction = aimPoint(suit, pilot).subtract(muzzle).normalize();
        if (direction.dot(look) < 0.5) {
            direction = look; // the aim point is beside/behind the muzzle (something very close): fire straight
        }
        PlasmaBolt bolt = new PlasmaBolt(level, pilot, muzzle, direction);
        level.addFreshEntity(bolt);

        suit.triggerAnim("action", "plasma_shot"); // recoil + muzzle flash on every client
        level.playSound(null, muzzle.x, muzzle.y, muzzle.z, SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 1.0F, 1.6F);
        level.playSound(null, muzzle.x, muzzle.y, muzzle.z, SoundEvents.SHULKER_SHOOT, SoundSource.PLAYERS, 0.8F, 0.5F);
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, muzzle.x, muzzle.y, muzzle.z, 8, 0.1, 0.1, 0.1, 0.25);
        return true;
    }

    /** Where the muzzle is: the torso faces the pilot's view (yaw) and leans with it (55% of the pitch). */
    public static Vec3 muzzlePosition(PowerArmorEntity suit, Player pilot) {
        float yaw = pilot.getYRot() * Mth.DEG_TO_RAD;
        float lean = Mth.clamp(pilot.getXRot() * 0.55F, -25F, 25F) * Mth.DEG_TO_RAD; // + = looking down
        double up = MUZZLE_UP - TORSO_PIVOT_UP;
        double leanedForward = MUZZLE_FORWARD * Mth.cos(lean) + up * Mth.sin(lean);
        double leanedUp = up * Mth.cos(lean) - MUZZLE_FORWARD * Mth.sin(lean);
        Vec3 forward = new Vec3(-Mth.sin(yaw), 0, Mth.cos(yaw));
        Vec3 right = new Vec3(-Mth.cos(yaw), 0, -Mth.sin(yaw));
        return suit.position().add(0, TORSO_PIVOT_UP + leanedUp, 0).add(forward.scale(leanedForward)).add(right.scale(MUZZLE_RIGHT));
    }

    /** What's under the pilot's crosshair: the first entity or block along the view, or a far point. */
    public static Vec3 aimPoint(PowerArmorEntity suit, Player pilot) {
        Vec3 eye = pilot.getEyePosition();
        Vec3 end = eye.add(pilot.getViewVector(1F).scale(AIM_RANGE));
        var blockHit = suit.level().clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, pilot));
        Vec3 blockEnd = blockHit.getLocation();
        EntityHitResult entityHit = ProjectileUtil.getEntityHitResult(suit.level(), pilot, eye, blockEnd,
                new AABB(eye, blockEnd).inflate(1), (Entity e) -> e != suit && e != pilot && e.isPickable() && !e.isSpectator());
        return entityHit != null ? entityHit.getLocation() : blockEnd;
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
        tooltip.add(Component.translatable("item.powerarmor.plasma_cannon.tooltip").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.powerarmor.power_tool.stats", (int) DAMAGE / 2, ENERGY_PER_SHOT,
                String.format("%.1f", COOLDOWN_TICKS / 20.0)).withStyle(ChatFormatting.DARK_AQUA));
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
