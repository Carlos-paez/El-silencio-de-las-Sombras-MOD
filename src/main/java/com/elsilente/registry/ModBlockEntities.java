package com.elsilente.registry;

import com.elsilente.ElSilenteMod;
import com.elsilente.block.TrofeoUmbralBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** Registro de BlockEntity del mod. */
public class ModBlockEntities {

    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, ElSilenteMod.MODID);

    public static final RegistryObject<BlockEntityType<TrofeoUmbralBlockEntity>> TROFEO_UMBRAL =
            BLOCK_ENTITIES.register("trofeo_umbral",
                    () -> BlockEntityType.Builder.of(TrofeoUmbralBlockEntity::new,
                            ModBlocks.TROFEO_UMBRAL.get()).build(null));

    public static void register(IEventBus bus) {
        BLOCK_ENTITIES.register(bus);
    }
}
