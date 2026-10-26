package dev.ajaczero.powerarmor.item;

import dev.ajaczero.powerarmor.PowerArmor;
import dev.ajaczero.powerarmor.PowerArmorConfig;
import dev.ajaczero.powerarmor.entity.PowerArmorEntity;
import dev.ajaczero.powerarmor.entity.ai.SuitTargets;
import dev.ajaczero.powerarmor.entity.ai.ToolUse;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Second power tool: an electromagnetic field projector (a redstone electromagnet: copper-wound prongs, a redstone
 * lens), a gravity gun for items, mobs and players (no blocks).
 *  - Hold use (right click) on a dropped item, a mob or a player: the beam pulls it in front of the suit and holds it
 *    there. Items that arrive go straight into the pilot's inventory.
 *  - Release: whatever is held is launched along the view. A quick tap shoves a target away.
 *  - Thrown mobs and players that hit a wall take damage.
 *  - Heavy things fight the field (decided 2026-10-13): other power armor suits and the entity tag powerarmor:em_heavy
 *    (bosses, iron golems, ravagers, wardens; datapacks can add more) aren't lifted. The beam holds them in place (they slow to a crawl) and drags them a little; letting go only
 *    shoves them.
 * Players (and a mount or suit with a player on it) only where the server allows PvP; a rogue mind ignores that. Never
 * spectators, creative players or no-AI mobs (map statues); a rider is caught by their mount.
 * Like all tools it only works in a powered suit's socket.
 */
public class EmProjectorItem extends Item implements PowerTool {
    // Fuel per tick while holding, per throw, grab range and slam damage: PowerArmorConfig.
    /** Held things get away beyond this many times the grab range. */
    private static final double KEEP_RANGE_FACTOR = 1.5;
    /** Held things float this far in front of the projector's tip. */
    private static final double HOLD_DISTANCE = 3.5;
    /** Launch speeds, blocks per tick (x20 = blocks per second). */
    public static final double MOB_PUSH = 1.6, ITEM_PUSH = 1.2;
    /** "powerarmor:em_heavy" (data/powerarmor/tags/entity_type/em_heavy.json): too heavy to lift. A tag, not a size
     *  rule: hitbox size was a poor guess at weight (a warden is thinner than an iron golem), and a tag lets datapacks
     *  and other mods add their own heavyweights. */
    public static final TagKey<EntityType<?>> HEAVY = TagKey.create(Registries.ENTITY_TYPE,
            Identifier.fromNamespaceAndPath(PowerArmor.MODID, "em_heavy"));
    /** Wall impacts faster than this (blocks per tick) hurt: damage = (speed - this) * config slam damage. */
    private static final double SLAM_SPEED = 0.5;
    private static final int COOLDOWN_TICKS = 8;
    /** Heavy things are only shoved: this fraction of a throw. */
    private static final double HEAVY_SHOVE = 0.25;
    /** "powerarmor:em_slam" (data/powerarmor/damage_type/em_slam.json): "slammed into a wall". */
    public static final ResourceKey<DamageType> EM_SLAM = ResourceKey.create(Registries.DAMAGE_TYPE,
            Identifier.fromNamespaceAndPath(PowerArmor.MODID, "em_slam"));


    public EmProjectorItem(Properties properties) {
        super(properties);
    }

    /** What one suit's projector is doing. Items are shared by every stack, so state lives on the suit. */
    public static final class Beam {
        @Nullable
        Entity target;
        boolean targetHadNoGravity;
        int heldTicks;
        final List<Thrown> thrown = new ArrayList<>();

        @Nullable
        public Entity target() {
            return this.target;
        }
    }

    /** A mob we launched, followed for 2 s to see if it slams into something. */
    private static final class Thrown {
        final LivingEntity entity;
        @Nullable
        final LivingEntity thrower;
        int ticks;
        double lastSpeed;

        Thrown(LivingEntity entity, @Nullable LivingEntity thrower) {
            this.entity = entity;
            this.thrower = thrower;
        }
    }

    /** What the field can catch at all: dropped items, players and mobs. A rider is caught by their mount (it's attached:
     *  grab the horse, or the suit, not the person on it). */
    public static boolean canGrab(Entity e) {
        if (!e.isAlive() || e.isPassenger()) {
            return false;
        }
        if (e instanceof ItemEntity) {
            return true;
        }
        if (e instanceof Player player) {
            return !player.isSpectator() && !player.isCreative();
        }
        // No-AI mobs are statues / shopkeepers placed by map makers (and Minecraft doesn't move them by velocity anyway).
        return e instanceof Mob mob && !mob.isNoAi();
    }

    /** Too heavy to lift: other power armor suits (always) and anything in the em_heavy tag. They fight the field. */
    public static boolean isHeavy(Entity e) {
        return e instanceof PowerArmorEntity || e.is(HEAVY);
    }

    /** What this suit's projector may hold: anything it can catch, but players (or a mount or suit carrying one) only
     *  where the server allows PvP. A rogue mind doesn't ask. */
    public static boolean canHold(PowerArmorEntity suit, Entity e) {
        if (!canGrab(e)) {
            return false;
        }
        boolean player = e instanceof Player || e.hasPassenger(p -> p instanceof Player);
        return !player || suit.isRogue() || SuitTargets.pvpAllowed(suit);
    }

    // ---- PowerTool -------------------------------------------------------------------------------

    @Override
    public ToolEffects createEffects() {
        return new EmProjectorEffects();
    }

    @Override
    public ToolUse mindUse() {
        return ToolUse.EM;
    }

    @Override
    public boolean isContinuous() {
        return true;
    }

    @Override
    public int energyPerUse() {
        return PowerArmorConfig.EM_THROW_ENERGY.get();
    }

    @Override
    public int cooldownTicks() {
        return COOLDOWN_TICKS;
    }

    @Override
    public String suitBone() {
        return "em_projector";
    }

    /** The prong tips end 13 px below the socket, the chaingun's barrels 15 (the projector is the shortest tool). */
    @Override
    public float muzzleInsetPixels() {
        return 2F;
    }

    @Override
    public boolean use(PowerArmorEntity suit, LivingEntity operator, ItemStack tool) {
        return false; // continuous: see holdTick / release
    }

    @Override
    public void holdTick(PowerArmorEntity suit, LivingEntity operator, ItemStack tool) {
        Beam beam = suit.socket().toolData(Beam.class, Beam::new);
        if (beam.target == null) {
            if (!suit.socket().isReady()) {
                return;
            }
            // A pilot grabs whatever they point at; the machine mind only the monster it picked (never an item, a cow or
            // a villager that happens to be in the line).
            Predicate<Entity> grabbable = operator instanceof Player ? e -> canHold(suit, e)
                    : e -> e == suit.getTarget() && canHold(suit, e);
            Entity found = SocketAim.entityInView(suit, operator, PowerArmorConfig.EM_RANGE.get(), grabbable, 0.6F);
            if (found != null && suit.power().tryUseEnergy(PowerArmorConfig.EM_HOLD_ENERGY.get())) {
                grab(suit, beam, found);
            }
            return;
        }
        Entity target = beam.target;
        if (!canHold(suit, target) || target.distanceTo(suit) > PowerArmorConfig.EM_RANGE.get() * KEEP_RANGE_FACTOR
                || !suit.power().tryUseEnergy(PowerArmorConfig.EM_HOLD_ENERGY.get())) {
            drop(suit, beam); // died, got away, or out of fuel
            return;
        }
        beam.heldTicks++;
        Vec3 hold = SocketAim.muzzlePosition(suit, operator).add(operator.getViewVector(1F).scale(HOLD_DISTANCE));
        Vec3 toHold = hold.subtract(target.position().add(0, target.getBbHeight() / 2, 0));
        if (target instanceof ItemEntity item && toHold.length() < 1.2 && operator instanceof Player pilot) {
            // Arrived: straight into the pilot's pockets (vanilla pickup, so events, stats and sounds all work).
            item.setNoGravity(beam.targetHadNoGravity);
            item.setNoPickUpDelay();
            item.playerTouch(pilot);
            if (item.isRemoved()) {
                beam.target = null;
                suit.socket().setBeamTarget(null);
                return;
            }
            item.setNoGravity(true); // inventory full: keep it floating
        }
        if (isHeavy(target)) {
            // Fights the field: gravity still holds it down, its own motion is damped to a crawl, and the beam drags it
            // toward the hold point only slowly (at most 1.2 blocks/s).
            Vec3 v = target.getDeltaMovement();
            Vec3 drag = toHold.multiply(1, 0, 1);
            drag = drag.length() > 0.06 ? drag.normalize().scale(0.06) : drag;
            target.setDeltaMovement(v.x * 0.2 + drag.x, v.y, v.z * 0.2 + drag.z);
            target.syncVelocity = true;
            if (target instanceof PowerArmorEntity held) {
                held.holdInField(); // a piloted suit is moved by its pilot's client: tell it to crawl
            }
            if (beam.heldTicks % 20 == 0) {
                suit.playSound(SoundEvents.BEACON_AMBIENT, 0.8F, 1.2F); // the field straining
            }
            return;
        }
        // Steer toward the hold point: fast when far, settling gently (no overshoot) when close.
        Vec3 pull = toHold.scale(0.3);
        if (pull.length() > 1.0) {
            pull = pull.normalize();
        }
        target.setDeltaMovement(target.getDeltaMovement().scale(0.3).add(pull.scale(0.7)));
        target.syncVelocity = true; // send the new velocity to clients
        target.fallDistance = 0;
        if (beam.heldTicks % 20 == 0) {
            suit.playSound(SoundEvents.BEACON_AMBIENT, 0.6F, 1.8F);
        }
    }

    @Override
    public void release(PowerArmorEntity suit, @Nullable LivingEntity operator, boolean interrupted) {
        Beam beam = suit.socket().toolData(Beam.class, Beam::new);
        Entity target = beam.target;
        if (target == null) {
            return;
        }
        if (interrupted || operator == null || !suit.power().tryUseEnergy(PowerArmorConfig.EM_THROW_ENERGY.get())) {
            drop(suit, beam);
            return;
        }
        // Launch along the view, a touch upward so things fly in a shallow arc.
        Vec3 view = operator.getViewVector(1F);
        // The guarding mind flings harder than a pilot throws: straight up, the fall has to kill (EM_GUARD_LAUNCH).
        double speed = target instanceof ItemEntity ? ITEM_PUSH
                : operator instanceof Player || suit.isRogue() ? MOB_PUSH : PowerArmorConfig.EM_GUARD_LAUNCH.get() / 20.0;
        if (isHeavy(target)) {
            speed *= HEAVY_SHOVE; // too heavy to throw: a shove
        }
        target.setNoGravity(beam.targetHadNoGravity);
        target.setDeltaMovement(view.scale(speed).add(0, 0.1, 0));
        target.syncVelocity = true;
        if (target instanceof LivingEntity living) {
            beam.thrown.add(new Thrown(living, operator));
        }
        beam.target = null;
        suit.socket().setBeamTarget(null);
        suit.socket().setCooldown(COOLDOWN_TICKS);
        suit.socket().countUse();
        suit.triggerAnim("action", "tool_recoil");
        suit.playSound(SoundEvents.WIND_CHARGE_BURST.value(), 1.0F, 0.8F);
        if (suit.level() instanceof ServerLevel level) {
            Vec3 at = target.position().add(0, target.getBbHeight() / 2, 0);
            // A puff of redstone dust where the field lets go (vanilla's redstone particle).
            level.sendParticles(DustParticleOptions.REDSTONE, at.x, at.y, at.z, 24, 0.35, 0.35, 0.35, 0.0);
        }
    }

    @Override
    public void serverTick(PowerArmorEntity suit) {
        Beam beam = suit.socket().toolData(Beam.class, Beam::new);
        beam.thrown.removeIf(t -> {
            t.ticks++;
            if (!t.entity.isAlive() || t.ticks > 40) {
                return true;
            }
            double speed = t.entity.getDeltaMovement().length();
            if (t.ticks > 1 && t.entity.horizontalCollision && t.lastSpeed > SLAM_SPEED) {
                float damage = (float) ((t.lastSpeed - SLAM_SPEED) * PowerArmorConfig.EM_SLAM_DAMAGE.get());
                t.entity.hurt(suit.damageSources().source(EM_SLAM, suit, t.thrower), damage); // (direct, causing)
                return true;
            }
            t.lastSpeed = speed;
            return false;
        });
    }

    private static void grab(PowerArmorEntity suit, Beam beam, Entity target) {
        beam.target = target;
        beam.targetHadNoGravity = target.isNoGravity();
        beam.heldTicks = 0;
        if (!isHeavy(target)) {
            target.setNoGravity(true); // lifted; heavy things stay on the ground
        }
        suit.socket().setBeamTarget(target);
        suit.playSound(SoundEvents.BEACON_POWER_SELECT, 0.8F, 1.6F);
    }

    /** Let go without throwing (power lost, pilot left, target escaped): it just falls. */
    private static void drop(PowerArmorEntity suit, Beam beam) {
        if (beam.target != null) {
            beam.target.setNoGravity(beam.targetHadNoGravity);
            beam.target = null;
            suit.socket().setBeamTarget(null);
            suit.playSound(SoundEvents.BEACON_DEACTIVATE, 0.6F, 2.0F);
        }
    }

    // ---- item --------------------------------------------------------------------------------------

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (!level.isClientSide()) {
            player.sendOverlayMessage(Component.translatable("message.powerarmor.tool_on_foot"));
        }
        return InteractionResult.PASS;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
            Consumer<Component> tooltip, TooltipFlag flag) {
        tooltip.accept(Component.translatable("item.powerarmor.em_projector.tooltip").withStyle(ChatFormatting.GRAY));
        tooltip.accept(Component.translatable("item.powerarmor.em_projector.stats", PowerArmorConfig.EM_HOLD_ENERGY.get() * 20, PowerArmorConfig.EM_THROW_ENERGY.get())
                .withStyle(ChatFormatting.DARK_AQUA));
        tooltip.accept(Component.translatable("item.powerarmor.power_tool.install").withStyle(ChatFormatting.DARK_GRAY));
    }
}
