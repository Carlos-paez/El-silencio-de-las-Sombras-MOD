package com.elsilente.registry;

import com.elsilente.ElSilenteMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

/**
 * Pestaña creativa del mod. En 1.20.1 los items NO aparecen en el inventario
 * creativo salvo que se añadan explicitamente a una pestaña: sin esta clase
 * los tres items del mod (y el huevo de invocación) eran inobtenibles.
 */
public class ModCreativeTabs {

    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, ElSilenteMod.MODID);

    public static final RegistryObject<CreativeModeTab> MAIN = TABS.register("main",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.elsilente.main"))
                    .icon(() -> new ItemStack(ModItems.CORAZON_SILENTE.get()))
                    .displayItems((parameters, output) -> {
                        output.accept(ModItems.CORAZON_SILENTE.get());
                        output.accept(ModItems.AMULETO_LATIDO.get());
                        output.accept(ModItems.SIMBOLO_ALBA.get());
                        output.accept(ModItems.GUADANA_SEGADOR.get());
                        output.accept(ModItems.LAMPARA_PENITENTE.get());
                        output.accept(ModBlocks.TROFEO_UMBRAL.get());
                        output.accept(ModItems.EL_SILENTE_SPAWN_EGG.get());
                        output.accept(ModItems.SOMBRA_DEBIL_SPAWN_EGG.get());
                        output.accept(ModItems.UMBRAL_SPAWN_EGG.get());
                    })
                    .build());

    public static void register(IEventBus bus) {
        TABS.register(bus);
    }
}
