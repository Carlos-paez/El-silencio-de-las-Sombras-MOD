package com.elsilente.client;

import com.elsilente.ElSilenteMod;
import com.elsilente.entity.SombraDebilEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

/** Renderer de la Sombra Debil: menuda (0,9x) y con sombra corta. */
public class SombraDebilRenderer extends MobRenderer<SombraDebilEntity, SombraDebilModel> {

    private static final ResourceLocation TEXTURE =
            new ResourceLocation(ElSilenteMod.MODID, "textures/entity/sombra_debil.png");

    public SombraDebilRenderer(EntityRendererProvider.Context context) {
        super(context, new SombraDebilModel(context.bakeLayer(SombraDebilModel.LAYER)), 0.3F);
    }

    @Override
    public ResourceLocation getTextureLocation(SombraDebilEntity entity) {
        return TEXTURE;
    }

    @Override
    protected void scale(SombraDebilEntity entity, PoseStack poseStack, float partialTick) {
        poseStack.scale(0.9F, 0.9F, 0.9F);
    }
}
