package com.elsilente.registry;

import com.elsilente.ElSilenteMod;
import com.elsilente.block.LineaSalBlock;
import com.elsilente.block.TrofeoUmbralBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** Bloques del mod: Trofeo del Umbral (Fase 2) y Linea de sal (§7). */
public class ModBlocks {

    public static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(ForgeRegistries.BLOCKS, ElSilenteMod.MODID);

    public static final RegistryObject<Block> TROFEO_UMBRAL = BLOCKS.register("trofeo_umbral",
            () -> new TrofeoUmbralBlock(BlockBehaviour.Properties.of()
                    .strength(1.5F)
                    .sound(SoundType.METAL)
                    .lightLevel(state -> 8)
                    .noOcclusion()));

    /** Linea de sal: cojinete que bloquea el spawn natural de Sombras (se consume). */
    public static final RegistryObject<Block> LINEA_SAL = BLOCKS.register("linea_sal",
            () -> new LineaSalBlock(BlockBehaviour.Properties.of()
                    .strength(0.3F)
                    .sound(SoundType.SAND)
                    .randomTicks()
                    .noOcclusion()));

    public static void register(IEventBus bus) {
        BLOCKS.register(bus);
    }
}
