package dev.ajaczero.powerarmor.client.dev;

import dev.ajaczero.powerarmor.PowerArmor;
import dev.ajaczero.powerarmor.entity.PowerArmorEntity;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Locale;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * DEV TOOL, inert unless started with -Dpowerarmor.mp=driver|watch (./gradlew runMpDriver / runMpWatch, against
 * runServerMp). Both clients log what THEY see of the suit each tick to mp_client.csv (own game directory); the driver
 * also walks forward once seated. The watcher takes screenshots at the phase changes (ap_mp_*.png).
 * Compare with the server's run/mp_server.csv (by hand: there is no report script yet).
 */
@EventBusSubscriber(modid = PowerArmor.MODID, value = Dist.CLIENT)
public final class MultiplayerScene {
    private static final String ROLE = System.getProperty("powerarmor.mp", "");
    private static int tick;
    private static BufferedWriter csv;

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (ROLE.isEmpty()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            return;
        }
        mc.options.pauseOnLostFocus = false;
        try {
            if (csv == null) {
                csv = Files.newBufferedWriter(mc.gameDirectory.toPath().resolve("mp_client.csv"));
                csv.write("tick,x,z,paint,canOperate,hasPilot,iAmRiding,suitCount\n");
            }
            PowerArmorEntity suit = mc.level.getEntitiesOfClass(PowerArmorEntity.class, mc.player.getBoundingBox().inflate(40)).stream().findFirst().orElse(null);
            if (ROLE.equals("driver")) {
                mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
                boolean riding = mc.player.getVehicle() instanceof PowerArmorEntity;
                AutoPilot.hold(mc.options.keyUp, riding && tick % 400 < 300); // walks while seated and the suit runs
                DevUtil.hideGui(mc, false);
            } else {
                mc.options.setCameraType(CameraType.FIRST_PERSON);
                DevUtil.hideGui(mc, false);
                if (suit != null) { // face the suit
                    double dx = suit.getX() - mc.player.getX(), dz = suit.getZ() - mc.player.getZ();
                    float yaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90);
                    mc.player.setYRot(yaw); mc.player.yRotO = yaw; mc.player.setXRot(5);
                }
                if (tick % 50 == 25) {
                    Screenshot.grab(mc.gameDirectory, "ap_mp_" + String.format("%04d", tick) + ".png", mc.gameRenderer.mainRenderTarget(), 1, msg -> {});
                }
            }
            if (suit != null) {
                csv.write(String.format(Locale.ROOT, "%d,%.2f,%.2f,%s,%b,%b,%b,%d%n", tick, suit.getX(), suit.getZ(), suit.getPaint(),
                        suit.canOperate(), suit.getControllingPassenger() != null, mc.player.getVehicle() == suit, 1));
            } else {
                csv.write(tick + ",,,,,,,0\n");
            }
            csv.flush();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        tick++;
    }

    private MultiplayerScene() {}
}
