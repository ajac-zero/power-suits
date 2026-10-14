package dev.ajaczero.powerarmor.client.dev;

import dev.ajaczero.powerarmor.PowerArmor;
import dev.ajaczero.powerarmor.entity.PowerArmorEntity;
import dev.ajaczero.powerarmor.entity.RogueSpawner;
import dev.ajaczero.powerarmor.registry.ModEntities;
import dev.ajaczero.powerarmor.registry.ModItems;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Autopilot scene for rogue suits (Phase 8 step 5), after the work scene:
 * <ol>
 *   <li>the spawner puts a rogue 48-64 blocks from the player (logged); it's then brought 18 blocks away for the fight:
 *   it shoots the cows and the player (resistance V: unhurt), the player's own chaingun suit takes it on;</li>
 *   <li>a lightning bolt reboots it (convulse, slump, green visor); the player climbs in and claims it, then gets out;</li>
 *   <li>a rogue with an EM projector hurls a cow and then the player; both friendly suits shoot it; it drops its tool.</li>
 * </ol>
 * Logs run/autopilot_rogue.csv.
 */
final class RogueScene {
    private static volatile BlockPos base;
    private static volatile PowerArmorEntity rogue, emRogue, friend;
    private static final List<LivingEntity> cows = new CopyOnWriteArrayList<>();
    private static volatile double spawnDistance = -1;
    private static BufferedWriter csv;
    private static String phase = "-";

    static boolean step(Minecraft mc, int s) {
        switch (s) {
            case 0 -> { phase = "gun"; setup(mc); }
            case 4 -> open(mc);
            case 6 -> camera(mc, new Vec3(6, 6, -16), new Vec3(9, 1.5, 4));
            case 20, 35, 50, 65, 80, 100, 120, 140 -> AutoPilot.shot(mc, "rogue_gun_" + s);
            case 150 -> { phase = "zap"; AutoPilot.onServer(mc, p -> {
                              if (rogue != null && rogue.isAlive()) {
                                  var bolt = EntityType.LIGHTNING_BOLT.create(p.serverLevel());
                                  bolt.moveTo(rogue.position());
                                  p.serverLevel().addFreshEntity(bolt);
                              } }); }
            case 152 -> closeUp(mc);
            case 154, 160, 170, 190, 220, 250, 275, 290 -> AutoPilot.shot(mc, "rogue_zap_" + s);
            case 300 -> { phase = "claim"; AutoPilot.onServer(mc, p -> {
                              if (rogue != null && rogue.isAlive()) {
                                  rogue.clearFire();
                                  p.teleportTo(rogue.getX() - 2, rogue.getY(), rogue.getZ());
                                  p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                                  rogue.interact(p, net.minecraft.world.InteractionHand.MAIN_HAND);
                              } }); }
            case 304 -> { mc.setCameraEntity(mc.player); mc.options.setCameraType(CameraType.THIRD_PERSON_BACK); mc.options.hideGui = false; }
            case 312 -> AutoPilot.shot(mc, "rogue_claimed");
            case 316 -> { phase = "em"; AutoPilot.onServer(mc, p -> {
                              p.stopRiding();
                              if (base != null) {
                                  p.teleportTo(base.getX() - 3.5, base.getY(), base.getZ() + 2.5); // within the rogue's sight
                              }
                              emSetup(p.serverLevel()); }); }
            // The player's own third-person camera this time: with a scripted camera entity the client stops sending the
            // player's position (LocalPlayer only does for the camera entity), so the server never saw them lifted.
            case 318 -> { mc.setCameraEntity(mc.player); mc.options.setCameraType(CameraType.THIRD_PERSON_BACK); mc.options.hideGui = false; }
            case 330, 340, 350, 360, 370, 380, 392, 404, 416, 430, 450, 480 -> AutoPilot.shot(mc, "rogue_em_" + s);
            // a heavy target (decided 2026-10-13): the player pilots the rebooted suit (its gun taken out) and walks at a new
            // EM rogue; the beam can't lift a suit, it holds it: the walk slows to a crawl, the field drags it a little
            case 520 -> { phase = "heavy"; AutoPilot.onServer(mc, p -> {
                              if (friend != null) {
                                  friend.getInventory().clearContent(); // the player's other suit sits this one out
                                  friend.setBurn(0, 0);
                              }
                              if (rogue != null && rogue.isAlive() && base != null) {
                                  rogue.getInventory().setItem(PowerArmorEntity.SOCKET_SLOT, ItemStack.EMPTY);
                                  rogue.getInventory().setItem(PowerArmorEntity.FUEL_SLOT, new ItemStack(Items.BLAZE_ROD, 8));
                                  rogue.teleportTo(base.getX() + 0.5, base.getY(), base.getZ() - 6.5);
                                  p.teleportTo(base.getX() - 1.5, base.getY(), base.getZ() - 6.5);
                                  rogue.interact(p, net.minecraft.world.InteractionHand.MAIN_HAND);
                                  PowerArmorEntity r = ModEntities.POWER_ARMOR.get().create(p.serverLevel());
                                  RogueSpawner.makeRogue(r, false);
                                  r.moveTo(base.getX() + 0.5, base.getY(), base.getZ() + 9.5, 180, 0);
                                  p.serverLevel().addFreshEntity(r);
                                  emRogue = r;
                              } }); }
            case 530 -> AutoPilot.hold(mc.options.keyUp, true); // walk at it
            case 540, 555, 570, 590 -> AutoPilot.shot(mc, "rogue_heavy_" + s);
            case 600 -> AutoPilot.releaseAll();
            case 610 -> { close(); return false; }
            default -> {}
        }
        if (s >= 318 && s < 610 && emRogue != null) {
            AutoPilot.aimTarget = emRogue.position().add(0, 2, 0); // keep facing the EM rogue
        }
        if (s >= 610) {
            AutoPilot.aimTarget = null;
        }
        if (s >= 5 && s < 610) {
            log(mc, s);
        }
        return true;
    }

    /** Server: by day, 100 blocks east of the work scene. The player (survival, resistance V so nothing hurts them),
     *  their chaingun suit, four cows; a rogue from the real spawner, then brought into view. */
    private static void setup(Minecraft mc) {
        AutoPilot.onServer(mc, player -> {
            ServerLevel level = player.serverLevel();
            level.setDayTime(6000);
            level.getServer().setDifficulty(net.minecraft.world.Difficulty.NORMAL, true);
            int x = player.getBlockX() + 100, z = player.getBlockZ();
            int y = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, x, z);
            BlockPos at = new BlockPos(x, y, z);
            player.teleportTo(x + 0.5, y, z + 0.5);
            player.setGameMode(GameType.SURVIVAL);
            player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 12000, 4));
            player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 12000, 2));
            PowerArmorEntity f = ModEntities.POWER_ARMOR.get().create(level);
            f.getInventory().setItem(PowerArmorEntity.FUEL_SLOT, new ItemStack(Items.BLAZE_ROD, 16));
            f.getInventory().setItem(PowerArmorEntity.SOCKET_SLOT, new ItemStack(ModItems.CHAINGUN.get()));
            f.setStoredNuggets(256);
            f.setOwnerId(player.getUUID());
            f.moveTo(x + 3.5, y, z - 1.5, 0, 0);
            level.addFreshEntity(f);
            cows.clear();
            for (int i = 0; i < 4; i++) {
                var cow = EntityType.COW.create(level);
                cow.moveTo(x + 12.5 + i * 2, y, z + 6.5 + (i % 2) * 3, 0, 0);
                level.addFreshEntity(cow);
                cows.add(cow);
            }
            PowerArmorEntity r = RogueSpawner.spawnNear(level, at, RogueSpawner.MIN_DISTANCE, RogueSpawner.MAX_DISTANCE);
            if (r != null) {
                spawnDistance = Math.sqrt(r.distanceToSqr(Vec3.atBottomCenterOf(at)));
                RogueSpawner.makeRogue(r, true);
                r.teleportTo(x + 18.5, y, z + 6.5); // for the camera: the spawner put it out of sight
                rogue = r;
            }
            friend = f;
            base = at;
        });
    }

    /** Server: an EM rogue 15 blocks west, a cow beside it. */
    private static void emSetup(ServerLevel level) {
        BlockPos at = base;
        if (at == null) {
            return;
        }
        PowerArmorEntity r = ModEntities.POWER_ARMOR.get().create(level);
        RogueSpawner.makeRogue(r, false);
        r.moveTo(at.getX() - 13.5, at.getY(), at.getZ() + 8.5, 180, 0);
        level.addFreshEntity(r);
        var cow = EntityType.COW.create(level); // one cow to hurl first, then the player
        cow.moveTo(at.getX() - 11.5, at.getY(), at.getZ() + 4.5, 0, 0);
        level.addFreshEntity(cow);
        cows.add(cow);
        emRogue = r;
    }

    private static void camera(Minecraft mc, Vec3 from, Vec3 at) {
        BlockPos b = base;
        if (b == null) {
            return;
        }
        mc.options.setCameraType(CameraType.FIRST_PERSON);
        mc.options.hideGui = true;
        Vec3 drop = new Vec3(0, EntityType.ARMOR_STAND.getDimensions().eyeHeight(), 0);
        AutoPilot.lookCamera(mc, Vec3.atBottomCenterOf(b).add(from).subtract(drop), Vec3.atBottomCenterOf(b).add(at).subtract(drop));
    }

    /** 4 blocks in front of the zapped rogue, at visor height. */
    private static void closeUp(Minecraft mc) {
        PowerArmorEntity r = rogue;
        if (r != null && mc.level.getEntity(r.getId()) instanceof PowerArmorEntity client) {
            float yaw = client.getYHeadRot() * net.minecraft.util.Mth.DEG_TO_RAD;
            Vec3 forward = new Vec3(-net.minecraft.util.Mth.sin(yaw), 0, net.minecraft.util.Mth.cos(yaw));
            Vec3 face = client.position().add(0, 2.4, 0);
            Vec3 drop = new Vec3(0, EntityType.ARMOR_STAND.getDimensions().eyeHeight(), 0);
            mc.options.setCameraType(CameraType.FIRST_PERSON);
            mc.options.hideGui = true;
            AutoPilot.lookCamera(mc, face.add(forward.scale(4.5)).add(forward.yRot(1.2F).scale(1.5)).subtract(drop), face.subtract(drop));
        }
    }

    private static void open(Minecraft mc) {
        try {
            csv = Files.newBufferedWriter(mc.gameDirectory.toPath().resolve("autopilot_rogue.csv"));
            csv.write("tick,phase,spawnDistance,rogueMind,rogueHealth,roguePowered,rogueStunned,rogueTarget,rogueShots,rogueEnergy,visorR,visorG,visorB,"
                    + "rogueOwner,friendTarget,friendShots,cowsAlive,playerY,playerVy,playerHealth,playerVehicle,emHealth,emTarget,emBeam,emToolDropped,clientY,clientVy,clientNoGravity,vehicleSpeed\n");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static void log(Minecraft mc, int s) {
        PowerArmorEntity r = rogue, em = emRogue, f = friend;
        var server = mc.getSingleplayerServer();
        var player = server == null ? null : server.getPlayerList().getPlayer(mc.player.getUUID());
        if (csv == null || r == null || f == null || player == null || base == null) {
            return;
        }
        float[] visor = mc.level.getEntity(r.getId()) instanceof PowerArmorEntity client ? client.getVisorColor() : new float[3];
        boolean emDropped = !player.serverLevel().getEntitiesOfClass(ItemEntity.class, new AABB(base).inflate(40),
                e -> e.getItem().is(ModItems.EM_PROJECTOR.get())).isEmpty();
        try {
            csv.write(String.format(Locale.ROOT, "%d,%s,%.1f,%s,%.1f,%b,%b,%s,%d,%d,%.2f,%.2f,%.2f,%b,%s,%d,%d,%.2f,%.2f,%.1f,%s,%.1f,%s,%s,%b,%.2f,%.2f,%b,%.2f%n",
                    s, phase, spawnDistance, r.getMind(), r.getHealth(), r.isPowered(), r.isStunned(), name(r.getTarget()), r.getToolUses(),
                    r.getTotalEnergy(), visor[0], visor[1], visor[2], player.getUUID().equals(r.getOwnerId()), name(f.getTarget()), f.getToolUses(),
                    cows.stream().filter(LivingEntity::isAlive).count(), player.getY() - base.getY(), player.getDeltaMovement().y,
                    player.getHealth(), player.getVehicle() == null ? "-" : player.getVehicle() == r ? "rebooted" : "other",
                    em == null ? -1F : em.getHealth(), em == null ? "-" : name(em.getTarget()),
                    em == null || em.getBeamTarget() == null ? "-" : em.getBeamTarget().getType().toShortString(), emDropped,
                    mc.player.getY() - base.getY(), mc.player.getDeltaMovement().y, mc.player.isNoGravity(),
                    mc.player.getVehicle() == null ? -1 : Math.hypot(mc.player.getVehicle().getX() - mc.player.getVehicle().xo,
                            mc.player.getVehicle().getZ() - mc.player.getVehicle().zo) * 20));
        } catch (IOException e) {
            PowerArmor.LOGGER.error("[autopilot] rogue csv", e);
        }
    }

    private static String name(LivingEntity e) {
        return e == null ? "-" : e instanceof PowerArmorEntity suit ? (suit.isRogue() ? "rogue" : "suit") : e.getType().toShortString();
    }

    private static void close() {
        try {
            if (csv != null) {
                csv.close();
            }
        } catch (IOException ignored) {
        }
    }

    private RogueScene() {}
}
