package dev.ajaczero.powerarmor.registry;

import dev.ajaczero.powerarmor.PowerArmor;
import dev.ajaczero.powerarmor.entity.PowerArmorEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
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
                    // eyes sit 1.62 above their feet, so 1.58 puts the camera at the visor (~2.6).
                    .passengerAttachments(1.58F)
                    .clientTrackingRange(10)
                    .build("power_armor"));

    private ModEntities() {}
}
