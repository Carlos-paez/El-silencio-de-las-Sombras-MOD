package com.elsilente.registry;

import com.elsilente.ElSilenteMod;
import com.elsilente.item.AmuletItem;
import com.elsilente.item.GuadanaSegadorItem;
import com.elsilente.item.LamparaPenitenteItem;
import com.elsilente.item.SimboloAlbaItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraftforge.common.ForgeSpawnEggItem;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModItems {

    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, ElSilenteMod.MODID);

    public static final RegistryObject<Item> CORAZON_SILENTE = ITEMS.register("corazon_silente",
            () -> new Item(new Item.Properties()));

    public static final RegistryObject<Item> AMULETO_LATIDO = ITEMS.register("amuleto_latido",
            () -> new AmuletItem(new Item.Properties().stacksTo(1)));

    public static final RegistryObject<Item> EL_SILENTE_SPAWN_EGG = ITEMS.register("el_silente_spawn_egg",
            () -> new ForgeSpawnEggItem(() -> ModEntities.EL_SILENTE.get(), 0x030303, 0xFFFFFF, new Item.Properties()));

    /** Arma simbolica: unico objeto que daña a las Sombras Debiles. */
    public static final RegistryObject<Item> SIMBOLO_ALBA = ITEMS.register("simbolo_alba",
            () -> new SimboloAlbaItem(new Item.Properties().stacksTo(1)));

    // ---------------------------------------------------------------------------
    // Fase 2: botín del jefe El Umbral + objetos de utilidad.
    // ---------------------------------------------------------------------------

    public static final RegistryObject<Item> TROFEO_UMBRAL = ITEMS.register("trofeo_umbral",
            () -> new BlockItem(ModBlocks.TROFEO_UMBRAL.get(), new Item.Properties()));

    public static final RegistryObject<Item> GUADANA_SEGADOR = ITEMS.register("guadana_segador",
            () -> new GuadanaSegadorItem(new Item.Properties().stacksTo(1)));

    public static final RegistryObject<Item> LAMPARA_PENITENTE = ITEMS.register("lampara_penitente",
            () -> new LamparaPenitenteItem(new Item.Properties().stacksTo(1)));

    public static final RegistryObject<Item> SOMBRA_DEBIL_SPAWN_EGG = ITEMS.register("sombra_debil_spawn_egg",
            () -> new ForgeSpawnEggItem(() -> ModEntities.SOMBRA_DEBIL.get(), 0x0A0A14, 0x9AA4C8, new Item.Properties()));

    public static final RegistryObject<Item> UMBRAL_SPAWN_EGG = ITEMS.register("umbral_spawn_egg",
            () -> new ForgeSpawnEggItem(() -> ModEntities.UMBRAL.get(), 0x2A1B3D, 0xB08CFF, new Item.Properties()));

    public static void register(IEventBus bus) {
        ITEMS.register(bus);
    }
}
