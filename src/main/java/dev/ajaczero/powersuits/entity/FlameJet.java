package dev.ajaczero.powersuits.entity;

import dev.ajaczero.powersuits.PowerSuits;
import dev.ajaczero.powersuits.PowerSuitsConfig;
import dev.ajaczero.powersuits.item.SocketAim;
import dev.ajaczero.powersuits.registry.ModEntities;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.item.crafting.SmokingRecipe;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.CandleBlock;
import net.minecraft.world.level.block.CandleCakeBlock;
import net.minecraft.world.level.block.IceBlock;
import net.minecraft.world.level.block.TntBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.BlockSnapshot;
import net.neoforged.neoforge.event.EventHooks;

/**
 * One gob of burning fuel from the flamethrower. The tool throws one every tick while the trigger is held, so a stream of
 * them makes the flame: each flies out at 16 blocks/s, slows down (drag), sags a little (gravity) and burns out after
 * half a second, about 7 blocks out. It's a stream, not a ray (user, 2026-10-25): the flame arcs, takes a moment to get
 * there, and sweeping the nozzle sweeps the stream behind it.
 *
 * <p>What it does (server):
 * <ul>
 *   <li>Mobs and players it passes through (it doesn't stop at them: a stream washes over a crowd) take a little fire
 *   damage and are set alight. Fire-proof mobs (blazes, magma cubes, a fire resistance potion) ignore it: its damage
 *   type is in {@code minecraft:is_fire}. Its damage type is also in {@code no_knockback} (twenty gobs a second would
 *   shove mobs away) and in {@code power_suits:sealed_out}, so a powered, sealed suit pays fuel instead of hull.</li>
 *   <li>Dropped food in the stream cooks (what a smoker would make of it, the whole stack at once) after about a second.
 *   A gob that touched food doesn't set fire to blocks, or the fire would burn the food it just cooked.</li>
 *   <li>Where it hits a block it stops: ice melts, snow and cobwebs burn away, campfires and candles light, TNT goes off,
 *   and now and then the face it hit catches fire (a vanilla fire block: on flammable blocks it spreads, on stone it
 *   dies down in a few seconds, the "burning patches" where the stream lands). The pilot must be allowed to build there
 *   (spawn protection, claims: a block place event like flint and steel's); the machine mind only with mob griefing on.
 *   Config switch for the fire.</li>
 *   <li>Water puts it out: a gob that hits or enters water fizzles.</li>
 * </ul>
 *
 * <p>Clients fly it too (the velocity comes with the spawn packet) and draw it as flame particles. Like the chaingun's
 * bullets, it really starts at the pilot's eye (it goes where the crosshair is) but is drawn coming out of the nozzle:
 * the gap between the two shrinks to nothing over its life. It has no renderer of its own (NoopRenderer).
 */
public class FlameJet extends Projectile {
    /** Blocks per tick at the nozzle, drag per tick, gravity per tick, life in ticks: about 7 blocks of reach. */
    public static final double SPEED = 0.8, DRAG = 0.95, GRAVITY = 0.02;
    public static final int LIFETIME = 11;
    /** How wide it burns: radius around the gob at the nozzle, growing per tick (a cone about 1.5 blocks wide at the end). */
    private static final double RADIUS = 0.25, RADIUS_GROWTH = 0.06;
    /** Ticks of flame a dropped stack must take before it's cooked (about a second of the stream on it). */
    static final int COOK_HEAT = 16;
    private static final String HEAT_KEY = PowerSuits.MODID + ":flame_heat";
    /** One in this many gobs that hit a block sets its face on fire (all of them would wall the pilot in at once). */
    private static final int IGNITE_ONE_IN = 3;
    /** "power_suits:flamethrower": damage_type/flamethrower.json (death message; tags is_fire, no_knockback, sealed_out). */
    public static final ResourceKey<DamageType> FLAMETHROWER = ResourceKey.create(Registries.DAMAGE_TYPE,
            Identifier.fromNamespaceAndPath(PowerSuits.MODID, "flamethrower"));

    /** Server: who it has already burnt (each gob hurts a mob once; the next gobs keep the fire going). */
    private final IntSet burnt = new IntOpenHashSet();
    /** Server: it touched food, so it sets no blocks on fire. */
    private boolean cooking;
    /** Client: it hit something and is waiting to be removed by the server: no more flames. */
    private boolean spent;

    public FlameJet(EntityType<? extends FlameJet> type, Level level) {
        super(type, level);
    }

    public FlameJet(Level level, Entity owner, Vec3 from, Vec3 velocity) {
        this(ModEntities.FLAME_JET.get(), level);
        this.setOwner(owner);
        this.setPos(from);
        this.setDeltaMovement(velocity);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
    }

    /** How far out it burns at this age (blocks). */
    static double radius(int age) {
        return RADIUS + RADIUS_GROWTH * age;
    }

    @Override
    public void tick() {
        super.tick();
        Level level = this.level();
        Vec3 from = this.position();
        Vec3 velocity = this.getDeltaMovement();
        Vec3 to = from.add(velocity);
        // Blocks and water surfaces stop it (lava doesn't: it's fire already).
        BlockHitResult hit = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.WATER, this));
        boolean hitsBlock = hit.getType() == HitResult.Type.BLOCK;
        Vec3 end = hitsBlock ? hit.getLocation() : to;
        if (level.isClientSide()) {
            this.clientTick(from, end, hitsBlock);
        } else if (level instanceof ServerLevel server) {
            this.burnEntities(server, from, end);
            if (this.isRemoved()) {
                return;
            }
            if (hitsBlock) {
                boolean water = level.getFluidState(hit.getBlockPos()).is(FluidTags.WATER);
                if (water) {
                    this.fizzle(server, end);
                } else {
                    this.hitBlock(server, hit);
                }
                this.discard();
                return;
            }
            if (this.tickCount > LIFETIME || this.isInWater()) {
                if (this.isInWater()) {
                    this.fizzle(server, end);
                }
                this.discard();
                return;
            }
        }
        this.setPos(end);
        this.setDeltaMovement(velocity.scale(DRAG).add(0, -GRAVITY, 0));
    }

    // ---- server ---------------------------------------------------------------------------------------------------

    /** Everything within its radius along this tick's path: mobs burn, food cooks. */
    private void burnEntities(ServerLevel level, Vec3 from, Vec3 to) {
        double r = radius(this.tickCount);
        AABB box = new AABB(from, to).inflate(r);
        Entity owner = this.getOwner();
        for (Entity e : level.getEntities(this, box, e -> e.isAlive() && !e.isSpectator())) {
            if (e instanceof ItemEntity item) {
                this.cook(level, item);
                continue;
            }
            if (!(e instanceof LivingEntity living) || !this.canHitEntity(e) || !this.burnt.add(e.getId())) {
                continue;
            }
            if (living.fireImmune()) {
                continue; // blazes, magma cubes, striders...: the flame washes over them
            }
            // (projectile, shooter): vanilla's own order despite the parameter names, see Bullet.
            DamageSource source = this.damageSources().source(FLAMETHROWER, this, owner);
            living.hurtServer(level, source, PowerSuitsConfig.FLAMETHROWER_DAMAGE.get().floatValue());
            if (!living.hasEffect(MobEffects.FIRE_RESISTANCE)) {
                living.igniteForSeconds(PowerSuitsConfig.FLAMETHROWER_BURN_SECONDS.get().floatValue());
            }
        }
    }

    /** Never the suit that fired it, nor its pilot. */
    @Override
    protected boolean canHitEntity(Entity target) {
        Entity owner = this.getOwner();
        return target != owner && !(target instanceof PowerSuitEntity suit && suit.hasPassenger(e -> e == owner))
                && !(owner instanceof PowerSuitEntity self && self.hasPassenger(e -> e == target))
                && !(target instanceof FlameJet) && target.isPickable();
    }

    /**
     * Food on the ground heats up while the stream plays on it; once hot enough the whole stack becomes what a smoker
     * would make of it (raw beef: steak; kelp: dried kelp; potatoes: baked). Only food: a smoker's recipes are the food
     * subset of the furnace's, so ores and logs on the ground stay what they are.
     */
    private void cook(ServerLevel level, ItemEntity item) {
        ItemStack stack = item.getItem();
        SingleRecipeInput input = new SingleRecipeInput(stack);
        Optional<RecipeHolder<SmokingRecipe>> recipe = level.recipeAccess().getRecipeFor(RecipeType.SMOKING, input, level);
        if (recipe.isEmpty()) {
            return;
        }
        this.cooking = true;
        var data = item.getPersistentData();
        int heat = data.getIntOr(HEAT_KEY, 0) + 1;
        if (heat < COOK_HEAT) {
            data.putInt(HEAT_KEY, heat);
            return;
        }
        data.remove(HEAT_KEY);
        ItemStack cooked = recipe.get().value().assemble(input);
        cooked.setCount(cooked.getCount() * stack.getCount());
        item.setItem(cooked);
        level.sendParticles(ParticleTypes.SMOKE, item.getX(), item.getY() + 0.2, item.getZ(), 6, 0.1, 0.1, 0.1, 0.02);
        level.playSound(null, item.getX(), item.getY(), item.getZ(), SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.4F, 1.6F);
    }

    /** It hit a block: melt, burn away, light, set off, or set the face on fire. */
    private void hitBlock(ServerLevel level, BlockHitResult hit) {
        BlockPos pos = hit.getBlockPos();
        Direction face = hit.getDirection();
        BlockState state = level.getBlockState(pos);
        Entity owner = this.getOwner();
        if (!this.mayAlter(level, pos)) {
            return;
        }
        if (state.is(Blocks.ICE) || state.is(Blocks.FROSTED_ICE)) {
            // What a torch's light does to ice in time (IceBlock.melt): water, or nothing in the Nether.
            if (level.environmentAttributes().getValue(EnvironmentAttributes.WATER_EVAPORATES, pos)) {
                level.removeBlock(pos, false);
            } else {
                level.setBlockAndUpdate(pos, IceBlock.meltsInto());
            }
            level.gameEvent(GameEvent.BLOCK_DESTROY, pos, GameEvent.Context.of(owner, state));
            this.hiss(level, pos);
            return;
        }
        if (state.is(Blocks.SNOW) || state.is(Blocks.SNOW_BLOCK) || state.is(Blocks.POWDER_SNOW) || state.is(Blocks.COBWEB)) {
            level.removeBlock(pos, false);
            level.gameEvent(GameEvent.BLOCK_DESTROY, pos, GameEvent.Context.of(owner, state));
            this.hiss(level, pos);
            return;
        }
        if (CampfireBlock.canLight(state) || CandleBlock.canLight(state) || CandleCakeBlock.canLight(state)) {
            level.setBlock(pos, state.setValue(BlockStateProperties.LIT, true), Block.UPDATE_ALL_IMMEDIATE);
            level.gameEvent(owner, GameEvent.BLOCK_CHANGE, pos);
            return;
        }
        if (!PowerSuitsConfig.FLAMETHROWER_IGNITES_BLOCKS.get() || this.cooking) {
            return;
        }
        if (state.getBlock() instanceof TntBlock) {
            // Like a burning arrow (TntBlock.onProjectileHit): primed, credited to whoever fired.
            if (state.onCaughtFire(level, pos, face, owner instanceof LivingEntity living ? living : null, ItemStack.EMPTY)) {
                level.removeBlock(pos, false);
            }
            return;
        }
        BlockPos firePos = pos.relative(face);
        if (this.random.nextInt(IGNITE_ONE_IN) != 0 || !BaseFireBlock.canBePlacedAt(level, firePos, face)
                || !level.getEntitiesOfClass(ItemEntity.class, new AABB(firePos).inflate(1.0)).isEmpty()) {
            return; // (no fire next to dropped items: they'd burn)
        }
        if (EventHooks.onBlockPlace(owner, BlockSnapshot.create(level.dimension(), level, firePos), face)) {
            return; // a claim / protection mod said no
        }
        level.setBlock(firePos, BaseFireBlock.getState(level, firePos), Block.UPDATE_ALL_IMMEDIATE);
        level.gameEvent(owner, GameEvent.BLOCK_PLACE, firePos);
    }

    /** May this stream change the block at pos? A pilot: where they may build (spawn protection, adventure mode). The
     *  machine mind: friendly suits never touch blocks (they guard you, not your house); rogues if mobs may grief. */
    private boolean mayAlter(ServerLevel level, BlockPos pos) {
        Entity owner = this.getOwner();
        if (owner instanceof Player player) {
            return level.mayInteract(player, pos) && player.mayBuild();
        }
        if (owner instanceof PowerSuitEntity suit) {
            return suit.isRogue() && level.getGameRules().get(GameRules.MOB_GRIEFING);
        }
        return level.getGameRules().get(GameRules.MOB_GRIEFING);
    }

    private void hiss(ServerLevel level, BlockPos pos) {
        if (this.random.nextInt(3) == 0) {
            level.playSound(null, pos, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.3F, 1.8F + this.random.nextFloat() * 0.2F);
        }
    }

    private void fizzle(ServerLevel level, Vec3 at) {
        level.sendParticles(ParticleTypes.BUBBLE, at.x, at.y, at.z, 2, 0.1, 0.1, 0.1, 0.02);
        if (this.random.nextInt(4) == 0) {
            level.sendParticles(ParticleTypes.CLOUD, at.x, at.y + 0.2, at.z, 1, 0.1, 0.05, 0.1, 0.01);
            level.playSound(null, at.x, at.y, at.z, SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.25F, 1.5F + this.random.nextFloat() * 0.4F);
        }
    }

    // ---- client ---------------------------------------------------------------------------------------------------

    /** Client: the gap from the real path (from the eye) to the nozzle this client draws the stream from, learnt on the
     *  first tick, shrinking to nothing by the end of its life. */
    private Vec3 gap;

    /** Client: where it's drawn at this point of its path. */
    private Vec3 drawn(Vec3 real, float age) {
        if (this.gap == null) {
            this.gap = Vec3.ZERO;
            LivingEntity operator = this.getOwner() instanceof LivingEntity owner ? owner : null;
            PowerSuitEntity suit = operator instanceof PowerSuitEntity self ? self
                    : operator != null && operator.getVehicle() instanceof PowerSuitEntity ridden ? ridden : null;
            if (suit != null) {
                this.gap = SocketAim.visualMuzzle.apply(suit, operator).subtract(this.position());
            }
        }
        double left = Math.max(0, 1 - age / (LIFETIME * 0.7));
        return real.add(this.gap.scale(left));
    }

    /** Particles along this tick's path: a hot core near the nozzle that widens and smokes as it burns out. Never right
     *  next to the pilot's eye (particles at the cockpit glass look like huge blobs). */
    private void clientTick(Vec3 from, Vec3 to, boolean hitsBlock) {
        if (this.spent) {
            return;
        }
        Level level = this.level();
        Entity owner = this.getOwner();
        Vec3 eye = owner instanceof LivingEntity living ? living.getEyePosition() : null;
        Vec3 velocity = this.getDeltaMovement();
        int age = this.tickCount;
        double spread = 0.04 + 0.03 * age;
        // One flame per gob per tick (user, 2026-10-25: three made the stream too thick): ~11 in the air per stream.
        int n = 1;
        for (int i = 0; i < n; i++) {
            float t = (i + this.random.nextFloat()) / n;
            Vec3 p = this.drawn(from.lerp(to, t), age - 1 + t);
            p = p.add(this.random.nextGaussian() * spread, this.random.nextGaussian() * spread, this.random.nextGaussian() * spread);
            if (eye != null && p.distanceToSqr(eye) < 0.8 * 0.8) {
                continue;
            }
            // Slower than the gob, so the flames trail behind it and hang in the air for a moment.
            Vec3 v = velocity.scale(0.18);
            level.addParticle(age < 2 && this.random.nextBoolean() ? ParticleTypes.SMALL_FLAME : ParticleTypes.FLAME,
                    p.x, p.y, p.z, v.x, v.y + 0.01, v.z);
            if (age > LIFETIME - 4 && this.random.nextInt(3) == 0 && (eye == null || p.distanceToSqr(eye) > 1.5 * 1.5)) {
                level.addParticle(ParticleTypes.SMOKE, p.x, p.y, p.z, v.x * 0.5, 0.03, v.z * 0.5);
            }
        }
        if (hitsBlock || this.isInWater()) {
            this.spent = true;
            if (!this.isInWater()) {
                for (int i = 0; i < 1 + this.random.nextInt(2); i++) { // splashes against the wall
                    double sx = this.random.nextGaussian() * 0.15, sz = this.random.nextGaussian() * 0.15;
                    level.addParticle(ParticleTypes.FLAME, to.x, to.y, to.z, sx * 0.3, 0.04, sz * 0.3);
                }
                if (this.random.nextInt(5) == 0) {
                    level.addParticle(ParticleTypes.LARGE_SMOKE, to.x, to.y, to.z, 0, 0.03, 0);
                }
            }
        }
    }

    // ---- misc -----------------------------------------------------------------------------------------------------

    @Override
    protected void addAdditionalSaveData(ValueOutput tag) {
        super.addAdditionalSaveData(tag);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput tag) {
        super.readAdditionalSaveData(tag);
    }

    /** Not worth saving: half a second of flame. */
    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        return false;
    }

    /** Gravity and drag are applied in tick(). */
    @Override
    public boolean isNoGravity() {
        return true;
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return distance < 64 * 64;
    }
}
