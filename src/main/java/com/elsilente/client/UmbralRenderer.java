package com.elsilente.client;

import com.elsilente.ElSilenteMod;
import com.elsilente.entity.UmbralEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

/** Renderer de El Umbral: jefe enorme (hitbox 2.6 bloques, escala 1.3). */
public class UmbralRenderer extends MobRenderer<UmbralEntity, UmbralModel> {

    private static final ResourceLocation TEXTURE =
            new ResourceLocation(ElSilenteMod.MODID, "textures/entity/umbral.png");

    public UmbralRenderer(EntityRendererProvider.Context context) {
        super(context, new UmbralModel(context.bakeLayer(UmbralModel.LAYER)), 0.7F);
    }

    @Override
    public ResourceLocation getTextureLocation(UmbralEntity entity) {
        return TEXTURE;
    }

    @Override
    protected void scale(UmbralEntity entity, PoseStack poseStack, float partialTick) {
        poseStack.scale(1.3F, 1.3F, 1.3F);
    }
}
