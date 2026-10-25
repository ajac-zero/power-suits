package dev.ajaczero.powerarmor.registry;

import dev.ajaczero.powerarmor.PowerArmor;
import dev.ajaczero.powerarmor.entity.Bullet;
import dev.ajaczero.powerarmor.entity.FlameJet;
import dev.ajaczero.powerarmor.entity.PowerArmorEntity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModEntities {
    // createEntities: since 1.21.2 every entity type is built with its registry key; this register fills it in.
    public static final DeferredRegister.Entities ENTITIES = DeferredRegister.createEntities(PowerArmor.MODID);

    public static final DeferredHolder<EntityType<?>, EntityType<PowerArmorEntity>> POWER_ARMOR =
            ENTITIES.registerEntityType("power_armor", PowerArmorEntity::new, MobCategory.MISC, builder -> builder
                    // Hitbox in blocks. The model is about 3 blocks tall (49 px / 16). 2.95, not 3: vanilla's pathfinder
                    // wants floor(height + 1) free blocks, so at 3.0 the machine mind couldn't walk its own 3-high
                    // drill hall (it asked for 4). Physically it already fitted through 3-high gaps.
                    .sized(1.4F, 2.95F)
                    .eyeHeight(2.6F)
                    // Where the pilot is mounted. The player's own vehicle offset is 0.6, and their
                    // eyes sit 1.62 above their feet, so y=1.58 puts the camera at visor height (~2.6).
                    // z=0.55 moves it forward to the visor, so the shoulder pads sit beside/behind the view.
                    .passengerAttachments(new Vec3(0, 1.58, 0.55))
                    .clientTrackingRange(10));

    /** The chaingun's bullet. Updated every tick so its tracer stays smooth at 80 blocks/s. */
    public static final DeferredHolder<EntityType<?>, EntityType<Bullet>> BULLET =
            ENTITIES.registerEntityType("bullet", Bullet::new, MobCategory.MISC, builder -> builder
                    .sized(0.2F, 0.2F)
                    .clientTrackingRange(8)
                    .updateInterval(1));

    /** The flamethrower's stream: one gob of burning fuel per tick, half a second of life. Updated every tick (fast). */
    public static final DeferredHolder<EntityType<?>, EntityType<FlameJet>> FLAME_JET =
            ENTITIES.registerEntityType("flame_jet", FlameJet::new, MobCategory.MISC, builder -> builder
                    .sized(0.25F, 0.25F)
                    .clientTrackingRange(6)
                    .updateInterval(1));

    private ModEntities() {}
}
