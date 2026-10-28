package dev.ajaczero.powerarmor.client.dev;

import dev.ajaczero.powerarmor.PowerArmor;
import dev.ajaczero.powerarmor.client.SuitHud;
import dev.ajaczero.powerarmor.client.SuitKeys;
import dev.ajaczero.powerarmor.client.VisorConfig;
import dev.ajaczero.powerarmor.client.VisorConfig.Vision;
import dev.ajaczero.powerarmor.client.VisorConfig.VisionTint;
import dev.ajaczero.powerarmor.client.VisorVision;
import dev.ajaczero.powerarmor.entity.PowerArmorEntity;
import dev.ajaczero.powerarmor.registry.ModEntities;
import dev.ajaczero.powerarmor.registry.ModItems;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Locale;
import net.minecraft.client.CameraType;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.fml.loading.FMLPaths;

/**
 * Autopilot scene for the visor's sight in the dark (client/VisorVision, 2026-10-27). A built cave (stone, ore, gravel,
 * mounds; three monsters standing still at 7, 13 and 21 blocks, one torch far in for a real light to compare with) and
 * an open field at midnight (the same monsters out on the grass), from the cockpit, HUD on.
 * <ol>
 *   <li>The comparison the user picked from (dev preview, {@link VisorVision#preview}): each place shot with every
 *   variant ({@code ap_vision_<place>_<variant>.png}): off, vanilla's night-vision potion for reference, night vision
 *   faint / strong in the visor colour and in white, the lamp (parked for a later upgrade) full / softer, and both
 *   together. The cave also turned toward its wall (the lamp's cone on a near surface).</li>
 *   <li>The game's own setting (night vision on, 0.3, the visor colour): NV under the compass
 *   ({@code ap_vision_live_on}); the N key switches it off (it fades in a third of a second, NV gone:
 *   {@code ap_vision_key_off}) and on again (over a second: {@code ap_vision_key_on_*}); the suit runs out of fuel (it
 *   goes with the visor), is refuelled (back over a second: {@code ap_vision_warm_*}); the sun comes up (it fades away
 *   by itself, NV stays faint: {@code ap_vision_day_*}). The player's own on/off is put back at the end.</li>
 * </ol>
 * Log run/autopilot_vision.csv: per tick the light at the eyes, how far the sight had come on, the night-vision floor
 * and lamp strength the last frame used, the suit's power, the switch, the indicator and the HUD's compass rows.
 * ./gradlew runAutopilotVision (~50 s).
 */
final class VisionScene {
    private record Variant(String name, VisorVision.Setting setting, boolean potion) {}

    private static final Variant[] VARIANTS = {
        new Variant("off", set(Vision.OFF, 0F, 0F, VisionTint.VISOR), false),
        new Variant("potion", set(Vision.OFF, 0F, 0F, VisionTint.VISOR), true),
        new Variant("nv20_visor", set(Vision.NIGHT_VISION, 0.20F, 0F, VisionTint.VISOR), false),
        new Variant("nv35_visor", set(Vision.NIGHT_VISION, 0.35F, 0F, VisionTint.VISOR), false),
        new Variant("nv20_white", set(Vision.NIGHT_VISION, 0.20F, 0F, VisionTint.WHITE), false),
        new Variant("nv35_white", set(Vision.NIGHT_VISION, 0.35F, 0F, VisionTint.WHITE), false),
        new Variant("lamp100", set(Vision.LAMP, 0F, 1F, VisionTint.VISOR), false),
        new Variant("lamp60", set(Vision.LAMP, 0F, 0.6F, VisionTint.VISOR), false),
        new Variant("both_visor", set(Vision.BOTH, 0.12F, 1F, VisionTint.VISOR), false),
        new Variant("both_white", set(Vision.BOTH, 0.12F, 1F, VisionTint.WHITE), false),
    };
    /** Ticks per variant: switch, let the lightmap rebuild (once a tick) and the post effect load, shoot. */
    private static final int EACH = 8;
    private static final int CAVE = 110, CAVE_TURNED = CAVE + VARIANTS.length * EACH + 6;
    private static final int FIELD = CAVE_TURNED + VARIANTS.length * EACH + 4;
    private static final int LIVE = FIELD + 80 + VARIANTS.length * EACH;

    private static volatile PowerArmorEntity suit;
    private static volatile BlockPos base;
    private static BufferedWriter csv;
    private static String phase = "-", variant = "-";
    /** The player's own night vision switch, put back at the end. */
    private static boolean savedOn = true;

    private static VisorVision.Setting set(Vision mode, float night, float lamp, VisionTint tint) {
        return new VisorVision.Setting(mode, night, lamp, tint, true);
    }

    static boolean step(Minecraft mc, int s) {
        var o = mc.options;
        mc.gui.toastManager().clear();
        if (s == 0) {
            phase = "setup";
            AutoPilot.releaseAll();
            AutoPilot.aimTarget = null;
            VisorVision.preview = VARIANTS[0].setting();
            build(mc);
        } else if (s == 40) {
            // two seconds after moving there: the client has the chunks by now (a ridden suit moves on the client, and
            // one boarded before its chunks arrive falls through the missing ground)
            board(mc, 0);
        } else if (s == 46) {
            mc.setCameraEntity(mc.player);
            o.setCameraType(CameraType.FIRST_PERSON);
            DevUtil.hideGui(mc, false);
            AutoPilot.setView(0, 4);
            open();
        } else if (s >= CAVE && s < CAVE + VARIANTS.length * EACH) {
            variants(mc, "cave", s - CAVE);
        } else if (s == CAVE_TURNED - 3) {
            AutoPilot.setView(-40, 12); // toward the right-hand wall, 6 blocks off
        } else if (s >= CAVE_TURNED && s < CAVE_TURNED + VARIANTS.length * EACH) {
            variants(mc, "cave_wall", s - CAVE_TURNED);
        } else if (s == FIELD) {
            phase = "field";
            VisorVision.preview = VARIANTS[0].setting();
            board(mc, 40); // out on the grass, 40 blocks to the side of the cave
            AutoPilot.setView(0, 4);
        } else if (s >= FIELD + 80 && s < LIVE) {
            variants(mc, "field", s - FIELD - 80);
        } else if (s == LIVE) {
            // the game's own setting from here on (no preview): night vision on, as configured (0.3, the visor colour),
            // back in the cave. The player's own choice is put back at the end (run/config is a developer's own too).
            phase = "live";
            variant = "config";
            VisorVision.preview = null;
            AutoPilot.onServer(mc, p -> p.removeEffect(MobEffects.NIGHT_VISION));
            savedOn = VisorConfig.NIGHT_VISION_ON.get();
            VisorConfig.NIGHT_VISION_ON.set(true);
            board(mc, 0);
            AutoPilot.setView(0, 4);
        } else if (s == LIVE + 40) {
            AutoPilot.shot(mc, "vision_live_on"); // NV under the compass, in the visor colour
        } else if (s == LIVE + 42 || s == LIVE + 60) {
            // the night vision key (N), through the key binding itself: off (fades in a third of a second), on again
            phase = s == LIVE + 42 ? "key_off" : "key_on";
            KeyMapping.click(SuitKeys.NIGHT_VISION.getKey());
        } else if (s == LIVE + 52) {
            AutoPilot.shot(mc, "vision_key_off"); // dark again, no NV
        } else if (s == LIVE + 62 || s == LIVE + 72 || s == LIVE + 84) {
            AutoPilot.shot(mc, "vision_key_on_" + (s - LIVE - 60));
        } else if (s == LIVE + 90) {
            phase = "fuel_out";
            server(mc, (p, x) -> {
                x.getInventory().setItem(PowerArmorEntity.FUEL_SLOT, ItemStack.EMPTY);
                x.power().setBurn(0, 0);
            });
        } else if (s == LIVE + 94 || s == LIVE + 100) {
            AutoPilot.shot(mc, "vision_out_" + (s - LIVE - 90));
        } else if (s == LIVE + 120) {
            phase = "refuel";
            server(mc, (p, x) -> x.getInventory().setItem(PowerArmorEntity.FUEL_SLOT, new ItemStack(Items.COAL, 40)));
        } else if (s >= LIVE + 122 && s <= LIVE + 152 && (s - LIVE - 122) % 5 == 0) {
            AutoPilot.shot(mc, "vision_warm_" + (s - LIVE - 120));
        } else if (s == LIVE + 160) {
            // out in the field, the sun comes up: it fades away by itself, NV stays (faint: on, waiting for the dark)
            phase = "sunrise";
            board(mc, 40);
            AutoPilot.setView(0, 4);
        } else if (s == LIVE + 205) {
            server(mc, (p, x) -> DevUtil.setTime((ServerLevel) p.level(), 6000));
        } else if (s == LIVE + 208 || s == LIVE + 216 || s == LIVE + 235) {
            AutoPilot.shot(mc, "vision_day_" + (s - LIVE - 205));
        } else if (s == LIVE + 240) {
            VisorConfig.NIGHT_VISION_ON.set(savedOn);
            VisorConfig.NIGHT_VISION_ON.save();
            close();
            return false;
        }
        if (s >= 46) {
            log(mc, s);
        }
        return true;
    }

    /** One variant every EACH ticks: switch at 0, shoot at EACH - 1. */
    private static void variants(Minecraft mc, String place, int t) {
        Variant v = VARIANTS[t / EACH];
        int k = t % EACH;
        if (k == 0) {
            phase = place;
            variant = v.name();
            VisorVision.preview = v.setting();
            AutoPilot.onServer(mc, p -> {
                if (v.potion()) {
                    p.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 1200, 0, false, false));
                } else {
                    p.removeEffect(MobEffects.NIGHT_VISION);
                }
            });
        } else if (k == EACH - 1) {
            AutoPilot.shot(mc, "vision_" + place + "_" + v.name());
        }
    }

    /**
     * 200 blocks east: a cave (stone shell 15 wide, 9 high inside, 42 long, closed at both ends, so no sky light gets
     * in) and the grass beside it; three monsters that stand still in each. Midnight, normal difficulty (peaceful
     * removes monsters).
     */
    private static void build(Minecraft mc) {
        AutoPilot.onServer(mc, player -> {
            ServerLevel level = player.level();
            level.getServer().setDifficulty(Difficulty.NORMAL, true);
            DevUtil.setTime(level, 18000);
            player.stopRiding();
            int x0 = player.getBlockX() + 200, z0 = player.getBlockZ();
            // generate the chunk first: the height of one that isn't there yet reads as the world's floor (-64), and
            // the cave was once built under it, in the void
            level.getChunk(x0 >> 4, z0 >> 4);
            int g = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x0, z0);
            // the player goes first, so the client gets the chunks before the suit is boarded
            player.teleportTo(x0 + 0.5, g, z0 - 1.5);
            BlockPos b = new BlockPos(x0, g, z0);
            base = b;
            RandomSource r = RandomSource.create(1027L);
            // the shell: x -9..9, y -2..10, z -4..42; inside x -7..7, y 0..8, z -2..40
            for (int dx = -9; dx <= 9; dx++) {
                for (int dz = -4; dz <= 42; dz++) {
                    for (int dy = -2; dy <= 10; dy++) {
                        boolean inside = Math.abs(dx) <= 7 && dz >= -2 && dz <= 40 && dy >= 0 && dy <= 8;
                        level.setBlock(b.offset(dx, dy, dz), inside ? Blocks.AIR.defaultBlockState() : rock(r), 2);
                    }
                }
            }
            // mounds and loose rock on the floor, so depth reads in the shots
            int[][] mounds = {{-5, 9, 2}, {5, 16, 3}, {-4, 27, 2}, {3, 34, 2}};
            for (int[] m : mounds) {
                for (int dx = -m[2]; dx <= m[2]; dx++) {
                    for (int dz = -m[2]; dz <= m[2]; dz++) {
                        int h = m[2] - Math.max(Math.abs(dx), Math.abs(dz));
                        for (int dy = 0; dy <= h; dy++) {
                            level.setBlock(b.offset(m[0] + dx, dy, m[1] + dz), rock(r), 2);
                        }
                    }
                }
            }
            // one torch far in: real light, to compare the visor's with
            level.setBlock(b.offset(-6, 0, 37), Blocks.TORCH.defaultBlockState(), 2);
            // monsters standing still in the cave and out on the grass (40 blocks to the side)
            for (int side : new int[] {0, 40}) {
                statue(level, EntityTypes.ZOMBIE, b.offset(side + 2, 0, 7));
                statue(level, EntityTypes.SKELETON, b.offset(side - 3, 0, 13));
                statue(level, EntityTypes.CREEPER, b.offset(side + 1, 0, 21));
            }
        });
    }

    /** Cave rock: mostly stone, some andesite, tuff, gravel, coal and iron ore. */
    private static BlockState rock(RandomSource r) {
        float f = r.nextFloat();
        return (f < 0.62F ? Blocks.STONE : f < 0.75F ? Blocks.ANDESITE : f < 0.84F ? Blocks.TUFF
                : f < 0.91F ? Blocks.GRAVEL : f < 0.96F ? Blocks.COAL_ORE : Blocks.IRON_ORE).defaultBlockState();
    }

    private static void statue(ServerLevel level, EntityType<? extends Mob> type, BlockPos at) {
        Mob mob = type.create(level, EntitySpawnReason.COMMAND);
        mob.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 180, 0);
        mob.setYHeadRot(180);
        mob.setNoAi(true);
        mob.setPersistenceRequired();
        level.addFreshEntity(mob);
    }

    /** A fresh running suit (firebox, coal, helmet, chaingun) at {@code side} blocks east of the cave's mouth, facing
     *  in (+Z), with the survival pilot inside, hand empty. The last one is removed. */
    private static void board(Minecraft mc, int side) {
        AutoPilot.onServer(mc, player -> {
            ServerLevel level = player.level();
            BlockPos b = base;
            player.stopRiding();
            if (suit != null) {
                suit.discard();
            }
            PowerArmorEntity s = ModEntities.POWER_ARMOR.get().create(level, EntitySpawnReason.COMMAND);
            DevUtil.firebox(s);
            s.getInventory().setItem(PowerArmorEntity.FUEL_SLOT, new ItemStack(Items.COAL, 40));
            s.getInventory().setItem(PowerArmorEntity.HELMET_SLOT, new ItemStack(ModItems.SUIT_HELMET.get()));
            s.getInventory().setItem(PowerArmorEntity.SOCKET_SLOT, new ItemStack(ModItems.CHAINGUN.get()));
            s.snapTo(b.getX() + side + 0.5, b.getY(), b.getZ() + 0.5, 0, 0);
            s.setYBodyRot(0);
            s.setYHeadRot(0);
            s.brain().setOwnerId(player.getUUID());
            level.addFreshEntity(s);
            suit = s;
            player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            player.setGameMode(GameType.SURVIVAL);
            player.setShiftKeyDown(false);
            player.teleportTo(b.getX() + side + 0.5, b.getY(), b.getZ() - 1.5);
            player.startRiding(s, true, true);
        });
    }

    private static void server(Minecraft mc, java.util.function.BiConsumer<ServerPlayer, PowerArmorEntity> action) {
        AutoPilot.onServer(mc, p -> {
            PowerArmorEntity x = suit;
            if (x != null) {
                action.accept(p, x);
            }
        });
    }

    private static void open() {
        try {
            csv = Files.newBufferedWriter(FMLPaths.GAMEDIR.get().resolve("autopilot_vision.csv"));
            csv.write("tick,phase,variant,eye_light,level,night,lamp,lightmap_nv,client_powered,switch_on,indicator,hud_row\n");
        } catch (IOException e) {
            PowerArmor.LOGGER.error("vision csv", e);
        }
    }

    private static void log(Minecraft mc, int s) {
        BufferedWriter w = csv;
        if (w == null) {
            return;
        }
        boolean powered = mc.player.getVehicle() instanceof PowerArmorEntity x && x.isPowered();
        try {
            SuitHud.Shown h = SuitHud.shown();
            w.write(String.format(Locale.ROOT, "%d,%s,%s,%d,%.3f,%.3f,%.3f,%.3f,%b,%b,%s,%s%n", s, phase, variant,
                    VisorVision.lastBrightness(), VisorVision.lastLevel(), VisorVision.lastNight(), VisorVision.lastLamp(),
                    mc.gameRenderer.gameRenderState().lightmapRenderState.nightVisionEffectIntensity, powered,
                    VisorConfig.NIGHT_VISION_ON.get(), VisorVision.indicator(), h == null ? "" : h.compass().replace(',', ' ')));
        } catch (IOException ignored) {
            // closed at the end of the scene
        }
    }

    private static void close() {
        try {
            if (csv != null) {
                csv.close();
            }
        } catch (IOException ignored) {
            // nothing to do
        }
        csv = null;
    }

    private VisionScene() {}
}
