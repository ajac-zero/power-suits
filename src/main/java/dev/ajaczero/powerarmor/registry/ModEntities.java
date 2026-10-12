package dev.ajaczero.powerarmor.registry;

import dev.ajaczero.powerarmor.PowerArmor;
import dev.ajaczero.powerarmor.entity.PlasmaBolt;
import dev.ajaczero.powerarmor.entity.PowerArmorEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITIES =
            DeferredRegister.create(Registries.ENTITY_TYPE, PowerArmor.MODID);

    public static final DeferredHolder<EntityType<?>, EntityType<PowerArmorEntity>> POWER_ARMOR =
            ENTITIES.register("power_armor", () -> EntityType.Builder.of(PowerArmorEntity::new, MobCategory.MISC)
                    // Hitbox in blocks. The model is about 3 blocks tall (49 px / 16).
                    .sized(1.4F, 3.0F)
                    .eyeHeight(2.6F)
                    // Where the pilot is mounted. The player's own vehicle offset is 0.6, and their
                    // eyes sit 1.62 above their feet, so y=1.58 puts the camera at visor height (~2.6).
                    // z=0.55 moves it forward to the visor, so the shoulder pads sit beside/behind the view.
                    .passengerAttachments(new Vec3(0, 1.58, 0.55))
                    .clientTrackingRange(10)
                    .build("power_armor"));

    /** The plasma cannon's projectile. Updated every tick so its glow and trail stay smooth at 60 blocks/s. */
    public static final DeferredHolder<EntityType<?>, EntityType<PlasmaBolt>> PLASMA_BOLT =
            ENTITIES.register("plasma_bolt", () -> EntityType.Builder.<PlasmaBolt>of(PlasmaBolt::new, MobCategory.MISC)
                    .sized(0.4F, 0.4F)
                    .clientTrackingRange(8)
                    .updateInterval(1)
                    .build("plasma_bolt"));

    private ModEntities() {}
}
