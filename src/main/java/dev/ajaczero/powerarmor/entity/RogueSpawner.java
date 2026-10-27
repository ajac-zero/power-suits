package dev.ajaczero.powerarmor.entity;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.ajaczero.powerarmor.PowerArmor;
import dev.ajaczero.powerarmor.PowerArmorConfig;
import dev.ajaczero.powerarmor.registry.ModEntities;
import dev.ajaczero.powerarmor.registry.ModItems;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import org.jetbrains.annotations.Nullable;

/**
 * Rogue suits turn up like the wandering trader (design sec. 8): once per in-game day the overworld rolls a small
 * chance (config); on a hit, a rogue with a chaingun or an EM projector appears 48-64 blocks from a random player, on
 * the surface. Only one at a time, never on peaceful, not with mob spawning off. The day countdown is saved with the
 * world ({@link SavedData}: a per-world file in data/), so quitting doesn't reset it.
 */
@EventBusSubscriber(modid = PowerArmor.MODID)
public final class RogueSpawner {
    private static final int DAY = 24000;
    public static final int MIN_DISTANCE = 48, MAX_DISTANCE = 64;

    /** The saved countdown to the next roll. */
    static final class Countdown extends SavedData {
        int ticksLeft = DAY;

        Countdown() {}

        Countdown(int ticksLeft) {
            this.ticksLeft = ticksLeft;
        }

        /** How it's saved: since 1.21.5 saved data is described by a codec instead of hand-written load/save. */
        static final Codec<Countdown> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.INT.optionalFieldOf("TicksLeft", DAY).forGetter(c -> c.ticksLeft)
        ).apply(i, Countdown::new));
    }

    /** Saved in the world as data/powerarmor/rogues.dat. */
    private static final SavedDataType<Countdown> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath(PowerArmor.MODID, "rogues"), Countdown::new, Countdown.CODEC);

    @SubscribeEvent
    public static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level) || level.dimension() != Level.OVERWORLD) {
            return;
        }
        Countdown countdown = level.getDataStorage().computeIfAbsent(TYPE);
        if (--countdown.ticksLeft > 0) {
            if (countdown.ticksLeft % 1200 == 0) {
                countdown.setDirty(); // save the countdown now and then, not every tick
            }
            return;
        }
        countdown.ticksLeft = DAY;
        countdown.setDirty();
        if (level.getDifficulty() == Difficulty.PEACEFUL || !level.getGameRules().get(GameRules.SPAWN_MOBS)
                || level.getRandom().nextDouble() >= PowerArmorConfig.ROGUE_SPAWN_CHANCE.get() || anyRogue(level)) {
            return;
        }
        List<ServerPlayer> players = level.getPlayers(p -> !p.isSpectator() && p.isAlive());
        if (!players.isEmpty()) {
            ServerPlayer player = players.get(level.getRandom().nextInt(players.size()));
            PowerArmorEntity rogue = spawnNear(level, player.blockPosition(), MIN_DISTANCE, MAX_DISTANCE);
            if (rogue != null) {
                PowerArmor.LOGGER.info("A rogue power armor turned up at {} ({} blocks from {})", rogue.blockPosition(),
                        Math.round(rogue.distanceTo(player)), player.getName().getString());
            }
        }
    }

    /** A rogue suit already walks the loaded world. */
    private static boolean anyRogue(ServerLevel level) {
        for (var e : level.getAllEntities()) {
            if (e instanceof PowerArmorEntity suit && suit.isRogue()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Put a rogue on the surface somewhere {@code min}-{@code max} blocks (horizontally) from {@code centre}: dry, solid
     * ground with room for the suit, in a loaded chunk. Ten tries; null if none worked.
     */
    @Nullable
    public static PowerArmorEntity spawnNear(ServerLevel level, BlockPos centre, int min, int max) {
        RandomSource random = level.getRandom();
        for (int i = 0; i < 10; i++) {
            float angle = random.nextFloat() * Mth.TWO_PI;
            double d = min + random.nextDouble() * (max - min);
            int x = centre.getX() + Mth.floor(Mth.cos(angle) * d), z = centre.getZ() + Mth.floor(Mth.sin(angle) * d);
            if (!level.isLoaded(new BlockPos(x, 0, z))) {
                continue;
            }
            BlockPos pos = new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
            BlockPos ground = pos.below();
            if (!level.getBlockState(ground).isFaceSturdy(level, ground, Direction.UP) || !level.getFluidState(pos).isEmpty()
                    || !level.getFluidState(ground).isEmpty()) {
                continue;
            }
            PowerArmorEntity rogue = ModEntities.POWER_ARMOR.get().create(level, EntitySpawnReason.NATURAL);
            if (rogue == null) {
                return null;
            }
            float yaw = random.nextFloat() * 360F;
            rogue.snapTo(x + 0.5, pos.getY(), z + 0.5, yaw, 0);
            rogue.setYBodyRot(yaw);
            rogue.setYHeadRot(yaw);
            if (!level.noCollision(rogue, rogue.getBoundingBox())) {
                continue;
            }
            // its weapon: a chaingun 2 in 5, an EM projector or a flamethrower otherwise
            int pick = random.nextInt(5);
            makeRogue(rogue, new ItemStack(pick < 2 ? ModItems.CHAINGUN.get() : pick < 4 ? ModItems.EM_PROJECTOR.get() : ModItems.FLAMETHROWER.get()));
            level.addFreshEntity(rogue);
            return rogue;
        }
        return null;
    }

    /** A rogue mind with a chaingun (or an EM projector). It needs no fuel and no nuggets. */
    public static void makeRogue(PowerArmorEntity suit, boolean chaingun) {
        makeRogue(suit, new ItemStack(chaingun ? ModItems.CHAINGUN.get() : ModItems.EM_PROJECTOR.get()));
    }

    /** A rogue mind with this weapon (chaingun, EM projector or flamethrower). */
    public static void makeRogue(PowerArmorEntity suit, ItemStack weapon) {
        suit.setMind(SuitMind.ROGUE);
        suit.getInventory().setItem(PowerArmorEntity.SOCKET_SLOT, weapon);
    }

    private RogueSpawner() {}
}
