package com.elsilente;

import com.elsilente.registry.ModBlockEntities;
import com.elsilente.registry.ModBlocks;
import com.elsilente.registry.ModCreativeTabs;
import com.elsilente.registry.ModEntities;
import com.elsilente.registry.ModItems;
import com.elsilente.registry.ModSounds;
import com.mojang.logging.LogUtils;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

@Mod(ElSilenteMod.MODID)
public class ElSilenteMod {

    public static final String MODID = "elsilente";
    public static final Logger LOGGER = LogUtils.getLogger();

    public ElSilenteMod() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();

        ModItems.register(modBus);
        ModBlocks.register(modBus);
        ModBlockEntities.register(modBus);
        ModEntities.register(modBus);
        ModSounds.register(modBus);
        ModCreativeTabs.register(modBus);

        modBus.addListener(ModEntities::onAttributeCreation);
        modBus.addListener(this::commonSetup);
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        event.enqueueWork(ModEntities::registerSpawnPlacements);
        LOGGER.info("[El Silente] Las sombras estan vivas...");
    }
}
