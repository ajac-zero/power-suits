package dev.ajaczero.powerarmor.client;

import dev.ajaczero.powerarmor.PowerArmor;
import dev.ajaczero.powerarmor.entity.PowerArmorEntity;
import dev.ajaczero.powerarmor.item.PowerTool;
import dev.ajaczero.powerarmor.registry.ModSounds;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.minecraft.client.sounds.SoundEngine;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * The suit's running sounds, played on each client: the engine's faint, occasional sounds while powered and the
 * running tool's loop (drill whir, chainsaw buzz, chaingun motor whine).
 *
 * The engine has no loop any more (user: the old one, a re-pitched minecart rumble, played as long as the suit was on
 * and "drove me mad"). A powered suit is quiet; now and then its backpack makes a soft sound it can explain: the
 * firebox's fire crackles (vanilla's furnace), the tidal pump bubbles while it's under water, the star reactor hums
 * like a beacon. Working hard (walking, sprinting, a tool running) makes them a little more frequent and louder. The
 * sounds themselves (and their base volume) are picked in sounds.json.
 *
 * Why client side: the server only knows *that* something should sound (synced data: powered, engine, TOOL_RUNNING);
 * each client turns that into sounds. A looping tool sound has to be started, moved along with the suit and faded out
 * every tick (a "tickable" sound instance); the engine's one-shots are just played at the suit's position. One-shot
 * sounds of actions (shots, power up/down) are still played from the server with {@code playSound}.
 */
@EventBusSubscriber(modid = PowerArmor.MODID, value = Dist.CLIENT)
public final class SuitSounds {
    /** Suits further away than this don't get loops started (they'd be inaudible anyway). */
    private static final double RANGE = 32;
    /** The tool loops playing now, by suit entity id (one per suit). Each loop holds its suit, and the suit its whole
     *  level: entries must go as soon as the loop stops playing, or a world left behind stays in memory. */
    private static final Map<Integer, Loop> TOOLS = new HashMap<>();
    private static final RandomSource RANDOM = RandomSource.create();
    /** Dev log: engine sounds played since the game started. */
    private static int enginePlayed;

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.isPaused()) {
            return;
        }
        // Not only the loops that stopped themselves (faded out): the sound engine can drop a sound without telling it
        // (it silences everything when you leave a world or change dimension, SoundEngine.stopAll). A loop it no longer
        // plays is dead too; forgetting it lets the next tick start a fresh one if the tool still runs.
        TOOLS.values().removeIf(loop -> loop.isStopped() || !mc.getSoundManager().isActive(loop));
        for (PowerArmorEntity suit : mc.level.getEntitiesOfClass(PowerArmorEntity.class, mc.player.getBoundingBox().inflate(RANGE))) {
            engineTick(mc, suit);
            SoundEvent toolSound = toolSound(suit);
            if (toolSound != null && !TOOLS.containsKey(suit.getId())) {
                start(mc, TOOLS, suit, new Loop(toolSound, suit, s -> toolSound(s) == toolSound, SuitSounds::toolVolume, SuitSounds::toolPitch));
            }
        }
    }

    private static void start(Minecraft mc, Map<Integer, Loop> playing, PowerArmorEntity suit, Loop loop) {
        // The engine may refuse a sound (all channels busy, sound turned off): only remember a loop that really plays,
        // or the suit would never get its sound (the map says it has one).
        if (mc.getSoundManager().play(loop) != SoundEngine.PlayResult.NOT_STARTED) {
            playing.put(suit.getId(), loop);
        }
    }

    /** Leaving a world: drop every loop (and with them the suits and the level they belong to). */
    @SubscribeEvent
    public static void loggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        TOOLS.values().forEach(Loop::stopNow);
        TOOLS.clear();
    }

    /**
     * Now and then a soft sound from the engine. The chance per tick gives the average gap: about 7 s at rest, 3 s
     * flat out (Minecraft's own ambient block sounds, like the furnace's crackle, are rolled the same way, so they never
     * fall into a rhythm).
     */
    private static void engineTick(Minecraft mc, PowerArmorEntity suit) {
        SoundEvent sound = engineSound(suit);
        if (sound == null) {
            return;
        }
        float load = Math.min(1F, suit.engineLoad()); // 0 standing .. 1 sprinting
        float chance = 1F / Mth.lerp(load, 140F, 60F);
        if (RANDOM.nextFloat() >= chance) {
            return;
        }
        float volume = 0.6F + 0.4F * load; // times the volume in sounds.json
        float pitch = 0.9F + 0.2F * RANDOM.nextFloat();
        // From the backpack (2.5 blocks up). distanceDelay false: no speed-of-sound delay for something this close.
        mc.level.playLocalSound(suit.getX(), suit.getY() + 2.5, suit.getZ(), sound, SoundSource.NEUTRAL, volume, pitch, false);
        enginePlayed++;
    }

    /** The installed engine's sound, or null while it's silent (off, flooded, or a tidal engine out of the water). */
    private static SoundEvent engineSound(PowerArmorEntity suit) {
        if (!suit.isAlive() || !suit.isPowered() || suit.getEngine() == null) {
            return null; // a powered suit always runs: piloted or driven by its mind
        }
        return switch (suit.getEngine()) {
            case FIREBOX -> ModSounds.FIREBOX.get();
            // Bubbles need water round the pump; running on rain or buckets it has nothing to bubble through.
            case TIDAL -> suit.power().isEngineUnderWater() ? ModSounds.TIDAL.get() : null;
            case REACTOR -> ModSounds.REACTOR.get();
        };
    }

    private static float toolVolume(PowerArmorEntity suit) {
        return suit.getSocketStack().getItem() instanceof PowerTool tool ? tool.runSoundVolume(suit) : 1F;
    }

    private static float toolPitch(PowerArmorEntity suit) {
        return suit.getSocketStack().getItem() instanceof PowerTool tool ? tool.runSoundPitch(suit) : 1F;
    }

    private static SoundEvent toolSound(PowerArmorEntity suit) {
        return suit.isAlive() && suit.isToolRunning() && suit.getSocketStack().getItem() instanceof PowerTool tool ? tool.runSound() : null;
    }

    /** A loop that follows its suit, eases toward its target volume / pitch, and fades out when it should stop. */
    private static final class Loop extends AbstractTickableSoundInstance {
        private final PowerArmorEntity suit;
        private final java.util.function.Predicate<PowerArmorEntity> keepPlaying;
        private final java.util.function.ToDoubleFunction<PowerArmorEntity> targetVolume, targetPitch;

        Loop(SoundEvent sound, PowerArmorEntity suit, java.util.function.Predicate<PowerArmorEntity> keepPlaying,
             java.util.function.ToDoubleFunction<PowerArmorEntity> targetVolume, java.util.function.ToDoubleFunction<PowerArmorEntity> targetPitch) {
            super(sound, SoundSource.NEUTRAL, SoundInstance.createUnseededRandom());
            this.suit = suit;
            this.keepPlaying = keepPlaying;
            this.targetVolume = targetVolume;
            this.targetPitch = targetPitch;
            this.looping = true;
            this.delay = 0;
            this.volume = 0.05F; // fade in
            this.pitch = (float) targetPitch.applyAsDouble(suit);
            this.follow();
        }

        @Override
        public void tick() {
            boolean on = !this.suit.isRemoved() && this.keepPlaying.test(this.suit);
            float volume = on ? (float) this.targetVolume.applyAsDouble(this.suit) : 0F;
            this.volume = Mth.lerp(on ? 0.25F : 0.35F, this.volume, volume);
            this.pitch = Mth.lerp(0.15F, this.pitch, (float) this.targetPitch.applyAsDouble(this.suit));
            this.follow();
            if (!on && this.volume < 0.02F) {
                this.stop();
            }
        }

        private void follow() {
            this.x = this.suit.getX();
            this.y = this.suit.getY() + 1.5;
            this.z = this.suit.getZ();
        }

        void stopNow() {
            this.stop();
        }

        @Override
        public boolean canStartSilent() {
            return true; // starts at volume ~0 and fades in
        }
    }

    /** Dev logs: engine sounds played so far / tool loops playing. */
    public static String activeLoops() {
        return enginePlayed + "/" + TOOLS.size();
    }

    private SuitSounds() {}
}
