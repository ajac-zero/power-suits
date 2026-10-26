package dev.ajaczero.powerarmor.client.dev;

import dev.ajaczero.powerarmor.PowerArmor;
import dev.ajaczero.powerarmor.entity.PowerArmorEntity;
import dev.ajaczero.powerarmor.registry.ModEntities;
import dev.ajaczero.powerarmor.registry.ModItems;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Locale;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

/**
 * Autopilot scene for the paint (2026-10-20): a running suit (fuel + helmet: its mind keeps it standing, looking at
 * the player) is right-clicked by a survival player with one dye after another, then with a wet sponge. After each,
 * two shots: the in-game third-person view from behind (eye 2.6 up, 6.5 back, the view the player has most) and a
 * front three-quarter view: {@code ap_paint_<n>_<state>_back/front.png}. Log run/autopilot_paint.csv: per tick the
 * server's and the client's paint (the synced data must reach the client) and the dyes left in the player's hand.
 * Runs alone with ./gradlew runAutopilotPaint (~30 s) and at the end of runAutopilotMech.
 */
final class PaintScene {
    /** The states shot, in order: bare steel, every dye (vanilla's order), then the wet sponge. */
    private static final String[] STATES = {"bare", "white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray",
            "light_gray", "cyan", "purple", "blue", "brown", "green", "red", "black", "wash"};
    private static final int SETUP = 30, EACH = 16;
    private static volatile BlockPos base;
    private static volatile PowerArmorEntity suit;
    private static BufferedWriter csv;
    private static String phase = "-";

    static boolean step(Minecraft mc, int s) {
        if (s == 0) {
            setup(mc);
            open();
        }
        int k = s - SETUP;
        if (k >= 0) {
            int i = k / EACH, t = k % EACH;
            if (i >= STATES.length) {
                close();
                mc.setCameraEntity(mc.player);
                return false;
            }
            String state = STATES[i];
            String name = String.format(Locale.ROOT, "paint_%02d_%s", i, state);
            switch (t) {
                case 0 -> { phase = state; apply(mc, state); }
                case 2 -> back(mc);
                case 7 -> AutoPilot.shot(mc, name + "_back");
                case 8 -> front(mc);
                case 13 -> AutoPilot.shot(mc, name + "_front");
                default -> {}
            }
        }
        if (s >= 2) {
            log(mc, s);
        }
        return true;
    }

    /** Flat ground 100 blocks east, daytime; a running suit facing north and the player in front of it (survival). */
    private static void setup(Minecraft mc) {
        AutoPilot.onServer(mc, player -> {
            ServerLevel level = player.level();
            DevUtil.setTime(level, 6000);
            player.stopRiding();
            int x = player.getBlockX() + 100, z = player.getBlockZ();
            int y = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, x, z);
            base = new BlockPos(x, y, z);
            player.setGameMode(GameType.SURVIVAL);
            player.teleportTo(x + 0.5, y, z - 4.5); // north of the suit: it looks at the player, its back to the camera
            PowerArmorEntity s = ModEntities.POWER_ARMOR.get().create(level, net.minecraft.world.entity.EntitySpawnReason.COMMAND);
            DevUtil.firebox(s); s.getInventory().setItem(PowerArmorEntity.FUEL_SLOT, new ItemStack(Items.BLAZE_ROD, 4));
            s.getInventory().setItem(PowerArmorEntity.HELMET_SLOT, new ItemStack(ModItems.SUIT_HELMET.get()));
            s.snapTo(x + 0.5, y, z + 0.5, 180, 0);
            s.setYBodyRot(180);
            s.setYHeadRot(180);
            s.setOwnerId(player.getUUID());
            level.addFreshEntity(s);
            suit = s;
        });
    }

    /** Right-click the suit like a player would (the entity's own interact, on the server) holding one dye / a sponge. */
    private static void apply(Minecraft mc, String state) {
        if ("bare".equals(state)) {
            return;
        }
        AutoPilot.onServer(mc, p -> {
            PowerArmorEntity s = suit;
            if (s == null) {
                return;
            }
            ItemStack held = "wash".equals(state) ? new ItemStack(Items.WET_SPONGE) : new ItemStack(Items.DYE.pick(DyeColor.byName(state, DyeColor.WHITE)));
            p.setItemInHand(InteractionHand.MAIN_HAND, held);
            s.interact(p, InteractionHand.MAIN_HAND, s.position());
        });
    }

    /** The in-game third-person view from behind (PilotView: 6.5 blocks back, eye 2.6 up), looking level. */
    private static void back(Minecraft mc) {
        camera(mc, new Vec3(0, 2.6, 6.5 + 0.55), new Vec3(0, 2.6, -0.55));
    }

    private static void front(Minecraft mc) {
        camera(mc, new Vec3(2.6, 2.2, -4.4), new Vec3(0, 1.5, 0));
    }

    private static void camera(Minecraft mc, Vec3 from, Vec3 to) {
        BlockPos b = base;
        if (b == null) {
            return;
        }
        mc.options.setCameraType(CameraType.FIRST_PERSON);
        DevUtil.hideGui(mc, true);
        Vec3 at = Vec3.atBottomCenterOf(b);
        // the camera is an armor stand's eye: place the stand that much lower (as HelmetScene does)
        Vec3 drop = new Vec3(0, net.minecraft.world.entity.EntityTypes.ARMOR_STAND.getDimensions().eyeHeight(), 0);
        AutoPilot.lookCamera(mc, at.add(from).subtract(drop), at.add(to).subtract(drop));
    }

    private static void open() {
        try {
            csv = Files.newBufferedWriter(net.neoforged.fml.loading.FMLPaths.GAMEDIR.get().resolve("autopilot_paint.csv"));
            csv.write("tick,phase,server_paint,client_paint,held,held_count\n");
        } catch (IOException e) {
            PowerArmor.LOGGER.error("paint csv", e);
        }
    }

    private static void log(Minecraft mc, int s) {
        PowerArmorEntity x = suit;
        BufferedWriter w = csv;
        if (x == null || w == null) {
            return;
        }
        String p = phase;
        // the client's copy of the same suit: what the renderer reads
        var client = mc.level == null ? null : mc.level.getEntity(x.getId());
        DyeColor clientPaint = client instanceof PowerArmorEntity c ? c.getPaint() : null;
        AutoPilot.onServer(mc, player -> {
            try {
                ItemStack held = player.getMainHandItem();
                w.write(String.format(Locale.ROOT, "%d,%s,%s,%s,%s,%d%n", s, p, x.getPaint(), clientPaint,
                        held.isEmpty() ? "-" : net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(held.getItem()).getPath(), held.getCount()));
            } catch (IOException ignored) {
                // the file was closed at the end of the scene
            }
        });
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

    private PaintScene() {}
}
