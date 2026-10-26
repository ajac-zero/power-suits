package dev.ajaczero.powerarmor.dev;

import dev.ajaczero.powerarmor.PowerArmor;
import dev.ajaczero.powerarmor.test.SuitTests;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;

/**
 * Entry point of the development tools (a third @Mod class for the same mod, next to PowerArmor and PowerArmorClient).
 *
 * Everything under {@code src/dev/} (this class, the GameTests, the autopilot scenes, the multiplayer script and the
 * test_pad structure) is a separate Gradle "source set": it can use the mod's code, but the mod can't see it, and it
 * isn't packed into the jar players download. The dev runs (runClient, runGameTestServer, runAutopilot..., see
 * build.gradle) load both halves as one mod, so NeoForge finds this class there and nowhere else.
 *
 * Classes in here with @EventBusSubscriber (AutoPilot, the scenes, MultiplayerScript) register themselves; only the
 * GameTests need a hand.
 */
@Mod(PowerArmor.MODID)
public final class PowerArmorDev {
    public PowerArmorDev(IEventBus modBus) {
        SuitTests.register(modBus); // GameTests: only when game tests are enabled (see SuitTests)
    }
}
