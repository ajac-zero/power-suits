package dev.ajaczero.powerarmor.item;

import dev.ajaczero.powerarmor.PowerArmor;
import dev.ajaczero.powerarmor.PowerArmorConfig;
import dev.ajaczero.powerarmor.entity.PowerArmorEntity;
import dev.ajaczero.powerarmor.entity.Trajectory;
import dev.ajaczero.powerarmor.entity.ai.EmGuard;
import dev.ajaczero.powerarmor.entity.ai.RogueEmAttack;
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
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Second power tool: an electromagnetic field projector (a redstone electromagnet: copper-wound prongs, a redstone
 * lens), a gravity gun for items, mobs and players (no blocks).
 *  - Hold use (right click) on a dropped item, a mob or a player: the beam pulls it in front of the suit and holds it
 *    there. Items that arrive go straight into the pilot's inventory.
 *  - Release: whatever is held is launched along the view. A quick tap shoves a target away.
 *  - Thrown mobs and players that hit a wall take damage, and bowl through any mob in their way (2026-10-27, user: a
 *    combat move): each one they hit takes the impact too, and so do they, and they fly on.
 *  - Heavy things fight the field (decided 2026-10-13): other power armor suits and the entity tag power_suits:em_heavy
 *    (bosses, iron golems, ravagers, wardens; datapacks can add more) aren't lifted. The beam holds them in place (they slow to a crawl) and drags them a little; letting go only
 *    shoves them.
 * Players (and a mount or suit with a player on it) only where the server allows PvP; a rogue mind ignores that. Never
 * spectators, creative players or no-AI mobs (map statues); a rider is caught by their mount.
 * Like all tools it only works in a powered suit's socket.
 */
public class EmProjectorItem extends Item implements PowerTool, ModTooltip {
    // Fuel per tick while holding, per throw, grab range and slam damage: PowerArmorConfig.
    /** Held things get away beyond this many times the grab range. */
    private static final double KEEP_RANGE_FACTOR = 1.5;
    /** Held things float this far in front of the projector's tip. */
    private static final double HOLD_DISTANCE = 3.5;
    /** Launch speeds, blocks per tick (x20 = blocks per second). */
    public static final double MOB_PUSH = 1.6, ITEM_PUSH = 1.2;
    /** "power_suits:em_heavy" (data/power_suits/tags/entity_type/em_heavy.json): too heavy to lift. A tag, not a size
     *  rule: hitbox size was a poor guess at weight (a warden is thinner than an iron golem), and a tag lets datapacks
     *  and other mods add their own heavyweights. */
    public static final TagKey<EntityType<?>> HEAVY = TagKey.create(Registries.ENTITY_TYPE,
            Identifier.fromNamespaceAndPath(PowerArmor.MODID, "em_heavy"));
    /** Impacts faster than this (blocks per tick) hurt: a full-speed throw's ({@link #MOB_PUSH}) does the config's
     *  slam damage, slower ones less in proportion to the speed above this. */
    private static final double SLAM_SPEED = 0.5;
    /** "power_suits:em_struck" (data/power_suits/damage_type/em_struck.json): "bowled over" by something thrown. */
    public static final ResourceKey<DamageType> EM_STRUCK = ResourceKey.create(Registries.DAMAGE_TYPE,
            Identifier.fromNamespaceAndPath(PowerArmor.MODID, "em_struck"));
    /** How hard a mob that's bowled over is knocked along the throw (vanilla's knockback strength; a sword's is 0.4). */
    private static final double STRUCK_KNOCKBACK = 0.8;
    private static final int COOLDOWN_TICKS = 8;
    /** Heavy things are only shoved: this fraction of a throw. */
    private static final double HEAVY_SHOVE = 0.25;
    /** "power_suits:em_slam" (data/power_suits/damage_type/em_slam.json): "slammed into a wall". */
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

    /** A mob we launched, followed for 2 s to see if it slams into something: a wall, or the mobs it bowls through. */
    private static final class Thrown {
        final LivingEntity entity;
        @Nullable
        final LivingEntity thrower;
        int ticks;
        double lastSpeed;
        /** The mobs it has already hit (each one once). */
        final java.util.Set<Entity> struck = new java.util.HashSet<>();

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
    public List<Goal> mindGoals(PowerArmorEntity suit) {
        return List.of(new EmGuard(suit), new RogueEmAttack(suit)); // a friendly mind guards home; a rogue throws you
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
                held.driving().holdInField(); // a piloted suit is moved by its pilot's client: tell it to crawl
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
        target.setNoGravity(beam.targetHadNoGravity);
        target.setDeltaMovement(throwVelocity(suit, target, operator, 1F));
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

    /**
     * Both sides: how fast {@code target} leaves the field when let go: along the view, a touch upward so things fly in a
     * shallow arc. The server throws with it ({@code partial} 1); the pilot's visor draws where it will land from the same
     * numbers every frame (client/hud/EmProjectorHud).
     */
    public static Vec3 throwVelocity(PowerArmorEntity suit, Entity target, LivingEntity operator, float partial) {
        Vec3 view = operator.getViewVector(partial);
        // The guarding mind flings harder than a pilot throws: straight up, the fall has to kill (EM_GUARD_LAUNCH).
        double speed = target instanceof ItemEntity ? ITEM_PUSH
                : operator instanceof Player || suit.isRogue() ? MOB_PUSH : PowerArmorConfig.EM_GUARD_LAUNCH.get() / 20.0;
        if (isHeavy(target)) {
            speed *= HEAVY_SHOVE; // too heavy to throw: a shove
        }
        return view.scale(speed).add(0, 0.1, 0);
    }

    /** Ticks a throw is followed ahead (3 s: far beyond where anything lands). */
    private static final int PREDICT_TICKS = 60;

    /**
     * Both sides: where {@code target}, let go at {@code velocity}, will fly and land: vanilla's own steps for it in the
     * air (a dropped item: gravity 0.04, then the move, then 0.98 drag; a mob: the move, then its gravity attribute and
     * 0.91 / 0.98 air drag, LivingEntity.travelInAir), and its whole hit box against the blocks (Entity.collideBoundingBox,
     * what Entity.move uses), until it touches something. A mob steering itself in the air drifts a little from this; a
     * wall it reaches fast enough is where it slams (serverTick).
     */
    public static Trajectory predictThrow(Level level, Entity target, Vec3 velocity) {
        boolean item = target instanceof ItemEntity;
        double gravity = target instanceof LivingEntity living ? living.getAttributeValue(Attributes.GRAVITY) : item ? 0.04 : 0.08;
        List<Vec3> path = new ArrayList<>();
        Vec3 pos = target.position();
        path.add(pos);
        AABB box = target.getBoundingBox();
        Vec3 v = velocity;
        for (int tick = 1; tick <= PREDICT_TICKS; tick++) {
            if (item) {
                v = v.add(0, -gravity, 0);
            }
            Vec3 move = Entity.collideBoundingBox(target, v, box, level, List.of());
            pos = pos.add(move);
            box = box.move(move);
            if (move.distanceToSqr(v) > 1.0E-7) {
                return new Trajectory(path, pos, tick, true, null); // blocked: it lands (or hits a wall) here
            }
            path.add(pos);
            v = item ? v.scale(0.98) : new Vec3(v.x * 0.91, (v.y - gravity) * 0.98, v.z * 0.91);
        }
        return new Trajectory(path, pos, PREDICT_TICKS, false, null);
    }

    @Override
    public void serverTick(PowerArmorEntity suit) {
        Beam beam = suit.socket().toolData(Beam.class, Beam::new);
        beam.thrown.removeIf(t -> {
            t.ticks++;
            // (a thrown mob killed by its first hit still flies on, and still bowls: only a removed one is done)
            if (t.entity.isRemoved() || t.ticks > 40 || !(t.entity.level() instanceof ServerLevel level)) {
                return true;
            }
            double speed = t.entity.getDeltaMovement().length();
            if (t.ticks > 1 && t.entity.horizontalCollision && t.lastSpeed > SLAM_SPEED) {
                t.entity.hurtServer(level, suit.damageSources().source(EM_SLAM, suit, t.thrower), impact(t.lastSpeed)); // (direct, causing)
                return true;
            }
            if (speed > SLAM_SPEED) {
                bowl(suit, t, level, speed);
            }
            t.lastSpeed = speed;
            return false;
        });
    }

    /** Damage of an impact at {@code speed} (blocks per tick): the config's slam damage at a full-speed throw. */
    private static float impact(double speed) {
        return (float) (PowerArmorConfig.EM_SLAM_DAMAGE.get() * Math.max(0, speed - SLAM_SPEED) / (MOB_PUSH - SLAM_SPEED));
    }

    /**
     * A thrown mob ploughing through others: every living thing in the space it swept through since last tick (its hit
     * box stretched back to where it was: at 1.6 blocks a tick it would jump clean over a mob between two ticks) takes
     * the impact and is knocked along the throw, and so does the thrown mob, which flies on. Not the suit, its pilot,
     * the thrower or anything it already hit.
     */
    private static void bowl(PowerArmorEntity suit, Thrown t, ServerLevel level, double speed) {
        LivingEntity flying = t.entity;
        Vec3 back = new Vec3(flying.xo - flying.getX(), flying.yo - flying.getY(), flying.zo - flying.getZ());
        AABB swept = flying.getBoundingBox().expandTowards(back);
        List<Entity> hit = level.getEntities(flying, swept, e -> e instanceof LivingEntity && e.isAlive() && !e.isSpectator()
                && e != suit && e != t.thrower && !suit.hasPassenger(e) && !t.struck.contains(e));
        Vec3 along = flying.getDeltaMovement().multiply(1, 0, 1).normalize();
        float damage = impact(speed);
        for (Entity e : hit) {
            t.struck.add(e);
            LivingEntity struck = (LivingEntity) e;
            DamageSource source = suit.damageSources().source(EM_STRUCK, suit, t.thrower); // (direct, causing)
            struck.hurtServer(level, source, damage);
            struck.knockback(STRUCK_KNOCKBACK, -along.x, -along.z, source, damage); // (it pushes away from the given direction)
            flying.hurtServer(level, source, damage);
            suit.level().playSound(null, struck.getX(), struck.getY(), struck.getZ(), SoundEvents.PLAYER_ATTACK_CRIT,
                    SoundSource.PLAYERS, 1.0F, 0.7F);
        }
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
            player.sendOverlayMessage(Component.translatable("message.power_suits.tool_on_foot"));
        }
        return InteractionResult.PASS;
    }

    @Override
    public void addTooltip(ItemStack stack, TooltipContext context, Consumer<Component> tooltip, TooltipFlag flag) {
        tooltip.accept(Component.translatable("item.power_suits.em_projector.tooltip").withStyle(ChatFormatting.GRAY));
        tooltip.accept(Component.translatable("item.power_suits.em_projector.stats", PowerArmorConfig.EM_HOLD_ENERGY.get() * 20, PowerArmorConfig.EM_THROW_ENERGY.get())
                .withStyle(ChatFormatting.DARK_AQUA));
        tooltip.accept(Component.translatable("item.power_suits.power_tool.install").withStyle(ChatFormatting.DARK_GRAY));
    }
}
