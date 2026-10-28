package dev.ajaczero.powersuits.item;

import dev.ajaczero.powersuits.PowerSuitsConfig;
import dev.ajaczero.powersuits.entity.FlameJet;
import dev.ajaczero.powersuits.entity.PowerSuitEntity;
import dev.ajaczero.powersuits.entity.ai.GunAttack;
import dev.ajaczero.powersuits.entity.ai.ToolUse;
import dev.ajaczero.powersuits.registry.ModSounds;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForgeMod;
import org.jetbrains.annotations.Nullable;

/**
 * The flamethrower (Phase 11, 2026-10-25): hold the trigger and it throws a stream of burning fuel about 7 blocks out
 * (FlameJet: one gob per tick). It sets mobs and blocks alight, melts ice and snow, lights campfires and candles, sets off
 * TNT and cooks dropped food. It runs on the suit's fuel alone (user: no ammo) and burns a lot of it; a star reactor
 * makes it free like everything else. Under water it only bubbles: the pilot light can't burn there.
 *
 * <p>A continuous tool: the suit calls {@link #holdTick} every tick the trigger is held (pilot's right click, or the
 * machine mind's trigger). Its slate pilot light glows blue whenever the suit is powered (SuitGlowLayer).
 */
public class FlamethrowerItem extends Item implements PowerTool, ModTooltip {
    /** Spread of the stream, degrees (each gob gets its own small deviation: the flame flickers and fans out a little). */
    private static final float SPREAD = 2.5F;

    public FlamethrowerItem(Properties properties) {
        super(properties);
    }

    /** Per-suit state (PowerSuitEntity.toolData), server only. */
    static final class State {
        int held;         // ticks the trigger has been held (0 = just pressed)
        boolean wasHeld;  // last tick
        int message;      // ticks until the "under water" message may show again
    }

    private static State state(PowerSuitEntity suit) {
        return suit.socket().toolData(State.class, State::new);
    }

    /** Energy per tick of spraying. */
    @Override
    public int energyPerUse() {
        return PowerSuitsConfig.FLAMETHROWER_ENERGY.get();
    }

    /** The 3x3 barrel and the tank behind it are centred under the 7x7 base together, so the barrel's centre is 1 px in
     *  front of the socket's axis. */
    @Override
    public float barrelForwardPixels() {
        return 1F;
    }

    @Override
    public int cooldownTicks() {
        return 1;
    }

    @Override
    public String suitBone() {
        return "flamethrower";
    }

    /** Its barrel ends where the chaingun's muzzle does, 10 px below the socket base (the tip is painted on its end). */
    @Override
    public float muzzleInsetPixels() {
        return 0F;
    }

    @Override
    public ToolEffects createEffects() {
        return new FlamethrowerEffects();
    }

    /** How the mind fights with it (GunAttack): the chaingun's job up close. The stream reaches about 7 blocks, so it fires
     *  from 5.5 (the target well inside), keeps a wide berth round friends (the flame fans out and sets things alight)
     *  and runs on fuel alone. */
    private static final GunAttack.Gun MIND_GUN = new GunAttack.Gun(5.5, true, suit -> true, suit -> true);

    @Override
    public ToolUse mindUse() {
        return ToolUse.GUN;
    }

    @Override
    public List<Goal> mindGoals(PowerSuitEntity suit) {
        return List.of(new GunAttack(suit, MIND_GUN));
    }

    @Override
    public boolean isContinuous() {
        return true;
    }

    @Override
    public boolean use(PowerSuitEntity suit, LivingEntity operator, ItemStack tool) {
        return false; // continuous: holdTick does the work
    }

    @Override
    public void serverTick(PowerSuitEntity suit) {
        State s = state(suit);
        if (!s.wasHeld) {
            s.held = 0;
        }
        s.wasHeld = false; // holdTick sets it again while the trigger is down
        s.message = Math.max(0, s.message - 1);
    }

    /** Server: is the nozzle under water? Then there's no flame. */
    public static boolean nozzleUnderWater(PowerSuitEntity suit, LivingEntity operator) {
        Vec3 muzzle = SocketAim.muzzlePosition(suit, operator);
        return suit.level().getFluidState(BlockPos.containing(muzzle)).is(FluidTags.WATER);
    }

    @Override
    public void holdTick(PowerSuitEntity suit, LivingEntity operator, ItemStack tool) {
        State s = state(suit);
        s.wasHeld = true;
        int held = s.held++;
        if (!(suit.level() instanceof ServerLevel level)) {
            return;
        }
        Vec3 muzzle = SocketAim.muzzlePosition(suit, operator);
        if (nozzleUnderWater(suit, operator)) {
            // The pilot light can't burn under water: bubbles and a hiss, no fuel used.
            if (held % 4 == 0) {
                level.sendParticles(ParticleTypes.BUBBLE, muzzle.x, muzzle.y, muzzle.z, 4, 0.1, 0.1, 0.1, 0.05);
            }
            if (held == 0) {
                level.playSound(null, muzzle.x, muzzle.y, muzzle.z, SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.5F, 1.4F);
            }
            if (s.message == 0 && operator instanceof Player pilot) {
                pilot.sendOverlayMessage(Component.translatable("message.power_suits.flamethrower_wet"));
                s.message = 60;
            }
            return;
        }
        if (!suit.power().tryUseEnergy(this.energyPerUse())) {
            if (held % 10 == 0) {
                level.playSound(null, muzzle.x, muzzle.y, muzzle.z, SoundEvents.DISPENSER_FAIL, SoundSource.PLAYERS, 0.6F, 0.6F);
            }
            return;
        }
        if (held == 0) {
            // Lighting up: the whoomp of the fuel catching.
            level.playSound(null, muzzle.x, muzzle.y, muzzle.z, SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 0.9F, 0.7F);
        }
        // Like the chaingun's bullets: the stream starts at the operator's eye and goes where the crosshair is, so it
        // hits what the pilot sees there; each client draws it coming out of the nozzle (FlameJet.drawn). It carries the
        // suit's own motion, so walking forward doesn't run into your own flame.
        Vec3 eye = operator.getEyePosition();
        Vec3 direction = SocketAim.aimPoint(suit, operator, 16).subtract(eye).normalize();
        if (direction.lengthSqr() < 0.5) {
            direction = operator.getViewVector(1F);
        }
        float spread = SPREAD * Mth.DEG_TO_RAD;
        direction = direction.xRot((float) level.getRandom().nextGaussian() * spread)
                .yRot((float) level.getRandom().nextGaussian() * spread);
        Vec3 velocity = direction.scale(FlameJet.SPEED).add(suit.getDeltaMovement().multiply(1, 0, 1));
        level.addFreshEntity(new FlameJet(level, operator, eye, velocity));
        suit.socket().countUse();
    }

    @Override
    @Nullable
    public SoundEvent runSound() {
        return ModSounds.FLAMETHROWER.get();
    }

    /** Client: silent while its nozzle is under water (it only bubbles there). */
    @Override
    public float runSoundVolume(PowerSuitEntity suit) {
        return suit.isEyeInFluidType(NeoForgeMod.WATER_TYPE.value()) ? 0F : 1F;
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
        tooltip.accept(Component.translatable("item.power_suits.flamethrower.tooltip").withStyle(ChatFormatting.GRAY));
        tooltip.accept(Component.translatable("item.power_suits.flamethrower.stats",
                String.format("%.1f", PowerSuitsConfig.FLAMETHROWER_DAMAGE.get() / 2),
                String.format("%.0f", PowerSuitsConfig.FLAMETHROWER_BURN_SECONDS.get()),
                this.energyPerUse() * 20).withStyle(ChatFormatting.DARK_AQUA));
        tooltip.accept(Component.translatable("item.power_suits.power_tool.install").withStyle(ChatFormatting.DARK_GRAY));
    }
}
