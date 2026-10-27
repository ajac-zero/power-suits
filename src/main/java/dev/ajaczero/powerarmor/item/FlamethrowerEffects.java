package dev.ajaczero.powerarmor.item;

import dev.ajaczero.powerarmor.entity.PowerArmorEntity;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.common.NeoForgeMod;

/**
 * Client: the flamethrower's pilot light on one suit. It burns whenever the suit is powered, brighter while the gun
 * sprays, and goes out under water. (The flame stream itself is the FlameJet entity's particles.)
 */
final class FlamethrowerEffects implements ToolEffects {
    private float pilotGlow;

    @Override
    public void tick(PowerArmorEntity suit) {
        boolean lit = suit.isPowered() && !suit.isEyeInFluidType(NeoForgeMod.WATER_TYPE.value());
        float target = !lit ? 0F : suit.socket().isActive() ? 1F : 0.7F;
        this.pilotGlow = ToolEffects.ease(this.pilotGlow, target, 0.4F, 0.1F);
    }

    @Override
    public void lights(PowerArmorEntity suit, float time, float partial, float[] out) {
        // a small flame: it wavers a little
        out[ToolLight.PILOT.ordinal()] = this.pilotGlow * (0.88F + 0.08F * Mth.sin(time * 0.9F + suit.getId()) + 0.04F * Mth.sin(time * 2.3F));
    }
}
