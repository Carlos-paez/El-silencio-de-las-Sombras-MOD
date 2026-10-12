package com.elsilente.client;

import com.elsilente.ElSilenteMod;
import com.elsilente.registry.ModEntities;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = ElSilenteMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class ClientSetup {

    @SubscribeEvent
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.EL_SILENTE.get(), ElSilenteRenderer::new);
        event.registerEntityRenderer(ModEntities.SOMBRA_DEBIL.get(), SombraDebilRenderer::new);
        event.registerEntityRenderer(ModEntities.UMBRAL.get(), UmbralRenderer::new);
    }

    @SubscribeEvent
    public static void onRegisterLayerDefinitions(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(ElSilenteModel.LAYER, ElSilenteModel::createBodyLayer);
        event.registerLayerDefinition(SombraDebilModel.LAYER, SombraDebilModel::createBodyLayer);
        event.registerLayerDefinition(UmbralModel.LAYER, UmbralModel::createBodyLayer);
    }
}
