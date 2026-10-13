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
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * Looping sounds, played on each client: the engine (while a suit is piloted and powered; pitch and volume
 * rise with the load) and the running tool (drill whir, chainsaw buzz).
 *
 * Why client side: a looping sound has to be started, moved along with the suit and faded out every frame.
 * The server only knows *that* something should sound (synced data: powered, piloted, TOOL_RUNNING); each
 * client turns that into a "tickable" sound instance that updates itself every tick. One-shot sounds (shots,
 * power up/down) are still played from the server with {@code playSound}.
 */
@EventBusSubscriber(modid = PowerArmor.MODID, value = Dist.CLIENT)
public final class SuitSounds {
    /** Suits further away than this don't get loops started (they'd be inaudible anyway). */
    private static final double RANGE = 32;
    private static final Map<Integer, Loop> ENGINES = new HashMap<>();
    private static final Map<Integer, Loop> TOOLS = new HashMap<>();

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.isPaused()) {
            return;
        }
        ENGINES.values().removeIf(Loop::isStopped);
        TOOLS.values().removeIf(Loop::isStopped);
        for (PowerArmorEntity suit : mc.level.getEntitiesOfClass(PowerArmorEntity.class, mc.player.getBoundingBox().inflate(RANGE))) {
            if (engineOn(suit) && !ENGINES.containsKey(suit.getId())) {
                start(mc, ENGINES, suit, new Loop(ModSounds.ENGINE.get(), suit, SuitSounds::engineOn, SuitSounds::engineVolume, SuitSounds::enginePitch));
            }
            SoundEvent toolSound = toolSound(suit);
            if (toolSound != null && !TOOLS.containsKey(suit.getId())) {
                start(mc, TOOLS, suit, new Loop(toolSound, suit, s -> toolSound(s) == toolSound, s -> 1F, s -> 1F));
            }
        }
    }

    private static void start(Minecraft mc, Map<Integer, Loop> playing, PowerArmorEntity suit, Loop loop) {
        playing.put(suit.getId(), loop);
        mc.getSoundManager().play(loop);
    }

    private static boolean engineOn(PowerArmorEntity suit) {
        return suit.isAlive() && suit.isVehicle() && suit.isPowered();
    }

    // Load 0 (idle) .. ~1.3 (sprinting with a tool running): the engine gets louder and higher.
    private static float engineVolume(PowerArmorEntity suit) {
        return 0.35F + 0.5F * suit.engineLoad();
    }

    private static float enginePitch(PowerArmorEntity suit) {
        return 0.85F + 0.45F * suit.engineLoad();
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

        @Override
        public boolean canStartSilent() {
            return true; // starts at volume ~0 and fades in
        }
    }

    /** Dev logs: how many engine / tool loops are playing. */
    public static String activeLoops() {
        return ENGINES.size() + "/" + TOOLS.size();
    }

    private SuitSounds() {}
}
