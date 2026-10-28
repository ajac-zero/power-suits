package dev.ajaczero.powersuits.entity;

import java.util.function.Predicate;

/**
 * What shared code (this package runs on both sides) may ask about the local player's view.
 *
 * Classes like {@code net.minecraft.client.Minecraft} only exist in the game client: a dedicated server doesn't ship
 * them, and code that touches one there crashes with NoClassDefFoundError the moment it runs. So shared code never calls
 * the client directly; it asks through these hooks, which hold safe defaults (no camera, nobody looking) until
 * {@code client/ClientSetup} fills them in on the client. Same idea as {@code SocketAim.visualMuzzle}.
 */
public final class ClientView {
    /** Is this client looking out of this suit's cockpit (it's our own suit, first person, the camera on the pilot)?
     *  Always false on a server. */
    public static Predicate<PowerSuitEntity> inCockpit = suit -> false;

    private ClientView() {}
}
