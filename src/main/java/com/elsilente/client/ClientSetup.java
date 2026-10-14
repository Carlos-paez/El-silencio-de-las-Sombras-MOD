package com.elsilente.client;

import com.elsilente.ElSilenteMod;
import com.elsilente.event.ReinoHandler;
import com.elsilente.registry.ModEntities;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterDimensionSpecialEffectsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = ElSilenteMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class ClientSetup {

    @SubscribeEvent
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.EL_SILENTE.get(), ElSilenteRenderer::new);
        event.registerEntityRenderer(ModEntities.SOMBRA_DEBIL.get(), SombraDebilRenderer::new);
        event.registerEntityRenderer(ModEntities.UMBRAL.get(), UmbralRenderer::new);
        // El Silente Verdadero comparte modelo/renderer con El Silente (§13).
        event.registerEntityRenderer(ModEntities.SILENTE_VERDADERO.get(), ElSilenteRenderer::new);
    }

    @SubscribeEvent
    public static void onRegisterDimensionEffects(RegisterDimensionSpecialEffectsEvent event) {
        // Cielo perpetuo y niebla violacea del reino de las sombras (§13).
        event.register(ReinoHandler.REINO_ID, new ReinoSombrasEffects());
    }

    @SubscribeEvent
    public static void onRegisterLayerDefinitions(EntityRenderersEvent.RegisterLayerDefinitions event) {
        // El Silente ya no usa ModelLayerLocation: su modelo lo aporta
        // ElSilenteGeoModel (GeckoLib, §12).
        event.registerLayerDefinition(SombraDebilModel.LAYER, SombraDebilModel::createBodyLayer);
        event.registerLayerDefinition(UmbralModel.LAYER, UmbralModel::createBodyLayer);
    }
}
