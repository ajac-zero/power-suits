package dev.ajaczero.powersuits.client.dev;

import dev.ajaczero.powersuits.PowerSuits;
import dev.ajaczero.powersuits.entity.PowerSuitEntity;
import dev.ajaczero.powersuits.item.HelmetStyle;
import dev.ajaczero.powersuits.registry.ModDataComponents;
import dev.ajaczero.powersuits.registry.ModEntities;
import dev.ajaczero.powersuits.registry.ModItems;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Locale;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.inventory.SmithingMenu;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.loading.FMLPaths;

/**
 * Autopilot scene for the helmet styles (design sec. 4c, 2026-10-22): six running suits in a row (no AI: statues) (the standard helmet,
 * then protector, spartan, noble, robot, hunter), each filmed from the in-game third-person view behind it and from the front
 * ({@code ap_style_<n>_<style>_back/front.png}), the whole row ({@code ap_style_row_front/back}), then each painted
 * in its inspiration's colours (shells, frames and bars take paint: {@code ap_style_<n>_<style>_painted}), and the smithing table making a Spartan helmet
 * (template + helmet + totem: {@code ap_menu_smithing.png}). Log run/autopilot_style.csv: each suit's style on the
 * server and on the client (the synced data must arrive), the smithing result. Runs alone with ./gradlew
 * runAutopilotStyles (~30 s).
 */
final class StyleScene {
    private static final String[] NAMES = {"standard", "protector", "spartan", "noble", "robot", "hunter"};
    private static final double SPACING = 4.5;
    private static final int SETUP = 30, EACH = 14;
    private static volatile BlockPos base;
    private static final PowerSuitEntity[] suits = new PowerSuitEntity[NAMES.length];
    private static BufferedWriter csv;
    private static String phase = "-";
    private static volatile String smithed = "-";

    static boolean step(Minecraft mc, int s) {
        if (s == 0) {
            setup(mc);
            open();
        }
        int k = s - SETUP;
        int closeUps = NAMES.length * EACH;
        if (k >= 0 && k < closeUps) {
            int i = k / EACH, t = k % EACH;
            String name = String.format(Locale.ROOT, "style_%d_%s", i, NAMES[i]);
            switch (t) {
                case 0 -> { phase = NAMES[i]; back(mc, i); }
                case 5 -> AutoPilot.shot(mc, name + "_back");
                case 6 -> front(mc, i);
                case 11 -> AutoPilot.shot(mc, name + "_front");
                default -> {}
            }
        }
        int r = k - closeUps;
        switch (r) {
            case 0 -> { phase = "row"; row(mc, false); }
            case 5 -> AutoPilot.shot(mc, "style_row_front");
            case 6 -> row(mc, true);
            case 11 -> AutoPilot.shot(mc, "style_row_back");
            case 12 -> { phase = "painted"; paintAll(mc); }
            default -> {}
        }
        // the painted close-ups: 8 ticks per suit after the paint (camera, then the shot 6 ticks later)
        int p = r - 13;
        if (p >= 0 && p < NAMES.length * 8) {
            int i = p / 8;
            if (p % 8 == 0) {
                front(mc, i);
            } else if (p % 8 == 6) {
                AutoPilot.shot(mc, String.format(Locale.ROOT, "style_%d_%s_painted", i, NAMES[i]));
            }
        }
        int m = r - 13 - NAMES.length * 8; // then the smithing table
        if (m == 0) {
            phase = "smithing";
            smithing(mc);
        } else if (m == 14) {
            AutoPilot.shot(mc, "menu_smithing");
        } else if (m == 16) {
            mc.player.closeContainer();
            close();
            mc.setCameraEntity(mc.player);
            return false;
        }
        if (s >= 2) {
            log(mc, s);
        }
        return true;
    }

    /** Flat ground 100 blocks west, daytime; five running suits in a row along x, facing north. */
    private static void setup(Minecraft mc) {
        AutoPilot.onServer(mc, player -> {
            ServerLevel level = player.level();
            DevUtil.setTime(level, 6000);
            player.stopRiding();
            int x = player.getBlockX() - 100, z = player.getBlockZ();
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
            base = new BlockPos(x, y, z);
            player.setGameMode(GameType.SURVIVAL);
            player.teleportTo(x + 0.5 + SPACING * (NAMES.length - 1) / 2, y, z - 40.5); // out of the pictures
            for (int i = 0; i < NAMES.length; i++) {
                PowerSuitEntity s = ModEntities.POWER_SUIT.get().create(level, EntitySpawnReason.COMMAND);
                ItemStack helmet = new ItemStack(ModItems.SUIT_HELMET.get());
                if (i > 0) {
                    helmet.set(ModDataComponents.HELMET_STYLE.get(), HelmetStyle.values()[i - 1]);
                }
                DevUtil.firebox(s); s.getInventory().setItem(PowerSuitEntity.FUEL_SLOT, new ItemStack(Items.BLAZE_ROD, 4));
                s.getInventory().setItem(PowerSuitEntity.HELMET_SLOT, helmet);
                s.snapTo(x + 0.5 + SPACING * i, y, z + 0.5, 180, 0);
                s.setYBodyRot(180);
                s.setYHeadRot(180);
                s.brain().setOwnerId(player.getUUID());
                s.setNoAi(true); // photo models: their minds turned them to look at each other and the cameras
                level.addFreshEntity(s);
                suits[i] = s;
            }
        });
    }

    /** Each suit's paint in the last shots: its inspiration's colours (the frames and bars take paint too). */
    private static final DyeColor[] PAINTS = {DyeColor.BLUE, DyeColor.BROWN, DyeColor.GREEN, DyeColor.LIGHT_GRAY, DyeColor.YELLOW, DyeColor.RED};

    /** Right-click every suit with its dye, as a player would. */
    private static void paintAll(Minecraft mc) {
        AutoPilot.onServer(mc, p -> {
            for (int i = 0; i < suits.length; i++) {
                PowerSuitEntity s = suits[i];
                if (s != null) {
                    p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DYE.pick(PAINTS[i])));
                    s.interact(p, InteractionHand.MAIN_HAND, s.position());
                }
            }
            p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        });
    }

    /**
     * A smithing table next to the player, opened like a click on it would, with the Spartan template, a helmet and a
     * totem put in its slots (the result slot fills itself from the recipe).
     */
    private static void smithing(Minecraft mc) {
        mc.setCameraEntity(mc.player);
        mc.options.setCameraType(CameraType.FIRST_PERSON);
        DevUtil.hideGui(mc, false);
        AutoPilot.onServer(mc, p -> {
            ServerLevel level = p.level();
            BlockPos table = p.blockPosition().north(2);
            level.setBlockAndUpdate(table, Blocks.SMITHING_TABLE.defaultBlockState());
            p.openMenu(Blocks.SMITHING_TABLE.defaultBlockState().getMenuProvider(level, table));
            if (p.containerMenu instanceof SmithingMenu menu) {
                menu.getSlot(0).set(new ItemStack(ModItems.SPARTAN_TEMPLATE.get()));
                menu.getSlot(1).set(new ItemStack(ModItems.SUIT_HELMET.get()));
                menu.getSlot(2).set(new ItemStack(Items.TOTEM_OF_UNDYING));
                ItemStack result = menu.getSlot(3).getItem();
                HelmetStyle style = result.get(ModDataComponents.HELMET_STYLE.get());
                smithed = result.isEmpty() ? "empty" : style == null ? "standard" : style.getSerializedName();
            } else {
                smithed = "no_menu";
            }
        });
    }

    /** The in-game third-person view from behind suit i (PilotView: 6.5 blocks back, eye 2.6 up), looking level. */
    private static void back(Minecraft mc, int i) {
        camera(mc, new Vec3(SPACING * i, 2.6, 6.5 + 0.55), new Vec3(SPACING * i, 2.6, -0.55));
    }

    /** Close front view of suit i's helmet and shoulders. */
    private static void front(Minecraft mc, int i) {
        // nearly straight in front at the visor's height (a little from -x: looking south, that's the picture's right)
        camera(mc, new Vec3(SPACING * i - 0.6, 2.75, -2.6), new Vec3(SPACING * i, 2.6, 0));
    }

    /** The whole row from the front or from behind. */
    private static void row(Minecraft mc, boolean behind) {
        double mid = SPACING * (NAMES.length - 1) / 2;
        camera(mc, new Vec3(mid, 3.2, behind ? 18 : -18), new Vec3(mid, 1.6, 0));
    }

    private static void camera(Minecraft mc, Vec3 from, Vec3 to) {
        BlockPos b = base;
        if (b == null) {
            return;
        }
        mc.options.setCameraType(CameraType.FIRST_PERSON);
        DevUtil.hideGui(mc, true);
        Vec3 at = Vec3.atBottomCenterOf(b);
        // the camera is an armor stand's eye: place the stand that much lower (as PaintScene does)
        Vec3 drop = new Vec3(0, EntityTypes.ARMOR_STAND.getDimensions().eyeHeight(), 0);
        AutoPilot.lookCamera(mc, at.add(from).subtract(drop), at.add(to).subtract(drop));
    }

    private static void open() {
        try {
            csv = Files.newBufferedWriter(FMLPaths.GAMEDIR.get().resolve("autopilot_style.csv"));
            csv.write("tick,phase,server_styles,client_styles,paint,smithed\n");
        } catch (IOException e) {
            PowerSuits.LOGGER.error("style csv", e);
        }
    }

    private static String styles(PowerSuitEntity[] row) {
        StringBuilder out = new StringBuilder();
        for (PowerSuitEntity s : row) {
            HelmetStyle style = s == null ? null : s.getHelmetStyle();
            out.append(out.isEmpty() ? "" : " ").append(s == null ? "?" : style == null ? "standard" : style.getSerializedName());
        }
        return out.toString();
    }

    private static void log(Minecraft mc, int s) {
        BufferedWriter w = csv;
        if (w == null || suits[0] == null) {
            return;
        }
        // the clients' copies of the same suits: what the renderer reads
        PowerSuitEntity[] client = new PowerSuitEntity[suits.length];
        for (int i = 0; i < suits.length; i++) {
            var e = suits[i] == null || mc.level == null ? null : mc.level.getEntity(suits[i].getId());
            client[i] = e instanceof PowerSuitEntity c ? c : null;
        }
        String p = phase, clientStyles = styles(client);
        AutoPilot.onServer(mc, player -> {
            try {
                w.write(String.format(Locale.ROOT, "%d,%s,%s,%s,%s,%s%n", s, p, styles(suits), clientStyles, suits[0].getPaint(), smithed));
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

    private StyleScene() {}
}
