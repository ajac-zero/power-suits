package dev.ajaczero.powerarmor.item;

import dev.ajaczero.powerarmor.PowerArmorConfig;
import dev.ajaczero.powerarmor.entity.PlasmaBolt;
import dev.ajaczero.powerarmor.entity.PowerArmorEntity;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
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
    // Fuel per shot, cooldown (default 0.6 s; the shot animation is 0.5 s) and damage: PowerArmorConfig.
    public static float damage() {
        return PowerArmorConfig.CANNON_DAMAGE.get().floatValue();
    }
    /** How far the crosshair is traced to find the aim point. */
    private static final double AIM_RANGE = 96;


    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    public PlasmaCannonItem(Properties properties) {
        super(properties);
    }

    @Override
    public int energyPerUse() {
        return PowerArmorConfig.CANNON_ENERGY.get();
    }

    @Override
    public int cooldownTicks() {
        return PowerArmorConfig.CANNON_COOLDOWN.get();
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
        Vec3 muzzle = SocketAim.muzzlePosition(suit, pilot);
        Vec3 look = pilot.getViewVector(1F);
        Vec3 direction = SocketAim.aimPoint(suit, pilot, AIM_RANGE).subtract(muzzle).normalize();
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
