package dev.ajaczero.powerarmor.item;

import dev.ajaczero.powerarmor.PowerArmor;
import dev.ajaczero.powerarmor.PowerArmorConfig;
import dev.ajaczero.powerarmor.entity.PowerArmorEntity;
import dev.ajaczero.powerarmor.entity.SuitSmash;
import dev.ajaczero.powerarmor.registry.ModSounds;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * The rail cannon (Phase 11, 2026-10-26): the chaingun's opposite. Where the chaingun is damage per second, this is the
 * single big hit: hold the trigger and the core between its two prongs charges (it hums and glows brighter); let go at
 * full charge and it fires an instant beam along the crosshair that goes through every mob on the line, hurting each for
 * the same critical damage (40 = an enderman in one shot). Let go early and it just winds down: nothing is spent.
 * DOOM (2016)'s gauss cannon was the inspiration.
 *
 * <p>Decided with the user: hold to charge, fires only at full charge (no weaker partial shots), an instant beam that
 * pierces, breaking the blocks a sprinting suit smashes through on its way (glass, panes, leaves, ice: the
 * {@code powerarmor:suit_smashes_sprinting} tag) and stopping at anything solid; each shot costs an iron ingot from the
 * suit's ammo slot plus fuel; the machine mind uses it as a long-range guard (SuitGoals.RailGuard); rogues never carry it.
 *
 * <p>A continuous tool: the suit calls {@link #holdTick} every tick the trigger is held (the charge builds), and
 * {@link #release} when it goes up (a full charge fires). The charge (0..1) is synced to every client through the suit's
 * tool spin: it drives the core's glow, the hum's pitch and the HUD. A shot sets the synced beam end and bumps the shot
 * counter; each client then draws the beam from the muzzle it sees (RailCannonEffects).
 */
public class RailCannonItem extends Item implements PowerTool {
    /** "powerarmor:rail" (data/powerarmor/damage_type/rail.json): "skewered by a rail beam". It's in
     *  minecraft:bypasses_cooldown, so a mob that was just hurt by something else still takes the whole hit. */
    public static final ResourceKey<DamageType> RAIL = ResourceKey.create(Registries.DAMAGE_TYPE,
            Identifier.fromNamespaceAndPath(PowerArmor.MODID, "rail"));
    /** A shot breaks at most this many blocks (a long greenhouse wall of glass still ends somewhere). */
    private static final int MAX_BROKEN = 24;
    /** Mobs are a little easier to hit than their boxes: the beam is a hand wide. */
    private static final double BEAM_RADIUS = 0.2;

    public RailCannonItem(Properties properties) {
        super(properties);
    }

    /** Per-suit state (PowerArmorEntity.toolData), server only. */
    static final class State {
        int charge;         // ticks charged, 0 .. RAIL_CHARGE
        boolean held;       // the trigger was held last tick
        int cooldown;       // ticks until it may charge again
        int noAmmoMessage;  // ticks until the "no ingots" message may show again
        @Nullable
        Shot lastShot;
    }

    /** What a shot did (server; tests and the autopilot log read it). */
    public record Shot(Vec3 from, Vec3 to, List<LivingEntity> hit, int blocksBroken) {}

    private static State state(PowerArmorEntity suit) {
        return suit.socket().toolData(State.class, State::new);
    }

    /** Fuel per shot (paid when it fires). */
    @Override
    public int energyPerUse() {
        return PowerArmorConfig.RAIL_ENERGY.get();
    }

    @Override
    public int cooldownTicks() {
        return PowerArmorConfig.RAIL_COOLDOWN.get();
    }

    @Override
    public String suitBone() {
        return "rail_cannon";
    }

    /** The prongs end 12 px below the socket base, 3 px past the chaingun's muzzle. */
    @Override
    public float muzzleInsetPixels() {
        return -3F;
    }

    @Override
    public ToolEffects createEffects() {
        return new RailCannonEffects();
    }

    @Override
    public boolean isContinuous() {
        return true;
    }

    @Override
    public boolean use(PowerArmorEntity suit, LivingEntity operator, ItemStack tool) {
        return false; // continuous: holdTick and release do the work
    }

    /** Does this go in the cannon? An iron ingot, from the ammo slot. */
    public static boolean isRound(ItemStack stack) {
        return stack.is(Items.IRON_INGOT);
    }

    /** Server: can this operator fire a shot now (ammo and fuel)? A creative pilot needs no ingot. */
    public boolean canPay(PowerArmorEntity suit, LivingEntity operator) {
        boolean ammo = operator instanceof Player p && p.hasInfiniteMaterials() || isRound(suit.getInventory().getItem(PowerArmorEntity.AMMO_SLOT));
        return ammo && suit.power().hasEnergyFor(this.energyPerUse());
    }

    /** Server: fully charged (the mind lets go then). */
    public static boolean isCharged(PowerArmorEntity suit) {
        return state(suit).charge >= PowerArmorConfig.RAIL_CHARGE.get();
    }

    /** Server: drop the charge without firing (the mind gives up on a shot it can't take: letting go would fire). */
    public static void disarm(PowerArmorEntity suit) {
        state(suit).charge = 0;
    }

    /** Server: cooling down after a shot. */
    public static boolean isCoolingDown(PowerArmorEntity suit) {
        return state(suit).cooldown > 0;
    }

    /** Server: the last shot this suit fired (null: none yet). */
    @Nullable
    public static Shot lastShot(PowerArmorEntity suit) {
        return suit.getSocketStack().getItem() instanceof RailCannonItem ? state(suit).lastShot : null;
    }

    /** Every tick, held or not: the cooldown runs out, and a charge that isn't held winds down (about half a second). */
    @Override
    public void serverTick(PowerArmorEntity suit) {
        State s = state(suit);
        s.cooldown = Math.max(0, s.cooldown - 1);
        s.noAmmoMessage = Math.max(0, s.noAmmoMessage - 1);
        if (!s.held) {
            s.charge = Math.max(0, s.charge - 3);
        }
        s.held = false; // holdTick sets it again if the trigger is still down this tick
        suit.socket().setSpin(s.charge / (float) PowerArmorConfig.RAIL_CHARGE.get(), 0F);
    }

    /** Every tick the trigger is held: charge up. It won't start charging without an ingot and the fuel for a shot. */
    @Override
    public void holdTick(PowerArmorEntity suit, LivingEntity operator, ItemStack tool) {
        State s = state(suit);
        s.held = true;
        if (s.cooldown > 0 || !(suit.level() instanceof ServerLevel level)) {
            return;
        }
        int full = PowerArmorConfig.RAIL_CHARGE.get();
        if (s.charge == 0 && !this.canPay(suit, operator)) {
            if (s.noAmmoMessage == 0) {
                Vec3 muzzle = SocketAim.muzzlePosition(suit, operator);
                level.playSound(null, muzzle.x, muzzle.y, muzzle.z, SoundEvents.DISPENSER_FAIL, SoundSource.PLAYERS, 0.6F, 1.4F);
                if (operator instanceof Player pilot) {
                    pilot.sendOverlayMessage(Component.translatable(suit.power().hasEnergyFor(this.energyPerUse())
                            ? "message.powerarmor.no_rail_ammo" : "message.powerarmor.rail_no_fuel"));
                }
                s.noAmmoMessage = 40;
            }
            return;
        }
        if (s.charge < full && ++s.charge == full) {
            // Charged: a bright little chime over the hum, so the pilot knows it's ready to let go.
            Vec3 muzzle = SocketAim.muzzlePosition(suit, operator);
            level.playSound(null, muzzle.x, muzzle.y, muzzle.z, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.2F, 1.6F);
        }
    }

    /** The trigger went up: a full charge fires (an interruption, an early release: nothing, it winds down). */
    @Override
    public void release(PowerArmorEntity suit, @Nullable LivingEntity operator, boolean interrupted) {
        State s = state(suit);
        if (interrupted || operator == null || s.charge < PowerArmorConfig.RAIL_CHARGE.get() || !(suit.level() instanceof ServerLevel level)) {
            return;
        }
        s.charge = 0;
        Vec3 muzzle = SocketAim.muzzlePosition(suit, operator);
        boolean free = operator instanceof Player p && p.hasInfiniteMaterials();
        if (!suit.power().hasEnergyFor(this.energyPerUse()) || !free && !suit.takeAmmo(RailCannonItem::isRound)) {
            level.playSound(null, muzzle.x, muzzle.y, muzzle.z, SoundEvents.DISPENSER_FAIL, SoundSource.PLAYERS, 0.6F, 1.4F);
            return; // (the ingot or the fuel went while it charged)
        }
        suit.power().tryUseEnergy(this.energyPerUse());
        s.lastShot = fire(level, suit, operator);
        s.cooldown = this.cooldownTicks();
        suit.socket().countUse();
        suit.socket().setRailBeamEnd(s.lastShot.to());
        suit.socket().countShot(); // every client: the beam (from the muzzle it sees), the recoil, the core's flash
        // A crack like the warden's sonic boom, pitched up, over a deep blast.
        level.playSound(null, muzzle.x, muzzle.y, muzzle.z, SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 1.5F, 1.5F);
        level.playSound(null, muzzle.x, muzzle.y, muzzle.z, SoundEvents.FIREWORK_ROCKET_LARGE_BLAST, SoundSource.PLAYERS, 1.2F, 0.5F);
    }

    /**
     * The beam: from the operator's eye along their view (like the chaingun's bullets: it hits what the crosshair is on,
     * and clients draw it from the gun), up to the range. Blocks first: it goes through what a sprinting suit smashes
     * (breaking them, if it may) and through things nothing collides with (grass, flowers, torches), and stops at the
     * first other block. Then everything living along that stretch takes the damage.
     */
    static Shot fire(ServerLevel level, PowerArmorEntity suit, LivingEntity operator) {
        Vec3 eye = operator.getEyePosition();
        Vec3 dir = operator.getViewVector(1F);
        Vec3 far = eye.add(dir.scale(PowerArmorConfig.RAIL_RANGE.get()));
        // Only a pilot breaks blocks (the mind's beam never changes the world, like its flamethrower).
        boolean breaks = operator instanceof Player && PowerArmorConfig.RAIL_BREAKS_BLOCKS.get();
        List<BlockPos> smash = new ArrayList<>();
        ClipContext context = new ClipContext(eye, far, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, operator);
        BlockHitResult stop = BlockGetter.traverseBlocks(eye, far, context, (ctx, pos) -> {
            BlockState state = level.getBlockState(pos);
            BlockHitResult hit = level.clipWithInteractionOverride(eye, far, pos, ctx.getBlockShape(state, level, pos), state);
            if (hit == null) {
                return null; // the line passes this block's air
            }
            if (breaks && state.is(SuitSmash.SPRINTING) && smash.size() < MAX_BROKEN) {
                smash.add(pos.immutable());
                return null;
            }
            if (state.getCollisionShape(level, pos).isEmpty() && !state.is(SuitSmash.SPRINTING)) {
                return null; // grass, flowers, torches, cobwebs: nothing to stop a beam
            }
            return hit;
        }, ctx -> null);
        Vec3 end = stop == null ? far : stop.getLocation();
        int broken = 0;
        Player breaker = suit.blockBreaker(operator);
        for (BlockPos pos : smash) { // (all in front of the stop: the traversal went in order and ended there)
            if (ToolMining.breakBlock(level, pos, breaker, ItemStack.EMPTY, false)) { // claims may veto; nothing drops
                broken++;
            }
        }
        // Everything living the beam passes through (the suit, its pilot and its passengers excepted).
        DamageSource source = level.damageSources().source(RAIL, operator);
        float damage = PowerArmorConfig.RAIL_DAMAGE.get().floatValue();
        List<LivingEntity> hit = new ArrayList<>();
        for (Entity e : level.getEntities(suit, new AABB(eye, end).inflate(2.0), e -> e instanceof LivingEntity && e.isAlive()
                && e != operator && !e.isSpectator() && !suit.hasPassenger(e) && e.isPickable())) {
            AABB box = e.getBoundingBox().inflate(BEAM_RADIUS);
            if (box.contains(eye) || box.clip(eye, end).isPresent()) {
                hit.add((LivingEntity) e);
            }
        }
        hit.sort(java.util.Comparator.comparingDouble(e -> e.distanceToSqr(eye)));
        for (LivingEntity target : hit) {
            if (target.hurtServer(level, source, damage) && operator instanceof Player pilot) {
                pilot.setLastHurtMob(target);
            }
        }
        return new Shot(eye, end, hit, broken);
    }

    /** Keep the hum going while the charge winds down. */
    @Override
    public boolean keepsRunning(PowerArmorEntity suit) {
        return suit.socket().getSpin() > 0.01F;
    }

    @Override
    @Nullable
    public SoundEvent runSound() {
        return ModSounds.RAIL_CANNON.get();
    }

    /** The charging hum rises with the charge (like a guardian's beam locking on). */
    @Override
    public float runSoundPitch(PowerArmorEntity suit) {
        return 0.6F + 1.0F * suit.socket().getSpin();
    }

    @Override
    public float runSoundVolume(PowerArmorEntity suit) {
        return 0.3F + 0.7F * suit.socket().getSpin();
    }

    /** Both sides: shots left for this pilot (the ammo slot's iron ingots), or -1 for endless (creative). */
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
        tooltip.accept(Component.translatable("item.powerarmor.rail_cannon.tooltip").withStyle(ChatFormatting.GRAY));
        tooltip.accept(Component.translatable("item.powerarmor.rail_cannon.stats",
                String.format("%.0f", PowerArmorConfig.RAIL_DAMAGE.get() / 2), String.format("%.1f", PowerArmorConfig.RAIL_CHARGE.get() / 20F),
                this.energyPerUse()).withStyle(ChatFormatting.DARK_AQUA));
        tooltip.accept(Component.translatable("item.powerarmor.power_tool.install").withStyle(ChatFormatting.DARK_GRAY));
    }
}
