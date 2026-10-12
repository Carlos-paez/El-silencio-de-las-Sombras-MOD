package com.elsilente.registry;

import com.elsilente.ElSilenteMod;
import com.elsilente.block.TrofeoUmbralBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** Bloques del mod (Fase 2): Trofeo del Umbral. */
public class ModBlocks {

    public static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(ForgeRegistries.BLOCKS, ElSilenteMod.MODID);

    public static final RegistryObject<Block> TROFEO_UMBRAL = BLOCKS.register("trofeo_umbral",
            () -> new TrofeoUmbralBlock(BlockBehaviour.Properties.of()
                    .strength(1.5F)
                    .sound(SoundType.METAL)
                    .lightLevel(state -> 8)
                    .noOcclusion()));

    public static void register(IEventBus bus) {
        BLOCKS.register(bus);
    }
}
