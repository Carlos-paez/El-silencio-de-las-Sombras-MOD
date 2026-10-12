package com.elsilente.client;

import com.elsilente.ElSilenteMod;
import com.elsilente.entity.ElSilenteEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

public class ElSilenteRenderer extends MobRenderer<ElSilenteEntity, ElSilenteModel> {

    private static final ResourceLocation TEXTURE =
            new ResourceLocation(ElSilenteMod.MODID, "textures/entity/el_silente.png");
    private static final ResourceLocation EYES =
            new ResourceLocation(ElSilenteMod.MODID, "textures/entity/el_silente_eyes.png");
    private static final ResourceLocation EYES_ANGRY =
            new ResourceLocation(ElSilenteMod.MODID, "textures/entity/el_silente_eyes_angry.png");

    public ElSilenteRenderer(EntityRendererProvider.Context context) {
        super(context, new ElSilenteModel(context.bakeLayer(ElSilenteModel.LAYER)), 0.0F);
        this.addLayer(new EyesLayer(this));
    }

    @Override
    public ResourceLocation getTextureLocation(ElSilenteEntity entity) {
        return TEXTURE;
    }

    @Override
    protected void scale(ElSilenteEntity entity, PoseStack poseStack, float partialTick) {
        // Modelo de 2 bloques de alto: 1.1 lo mantiene un poco por encima del jugador
        // sin despeguarse de la hitbox (1.99 bloques).
        poseStack.scale(1.1F, 1.1F, 1.1F);
    }

    /**
     * Capa emisiva: dibuja el modelo con la textura de ojos (solo ojos opacos,
     * el resto transparente). Brilla en la oscuridad y cambia a rojo en CHASE.
     */
    private static class EyesLayer extends RenderLayer<ElSilenteEntity, ElSilenteModel> {

        EyesLayer(ElSilenteRenderer renderer) {
            super(renderer);
        }

        @Override
        public void render(PoseStack poseStack, MultiBufferSource buffer, int packedLight, ElSilenteEntity entity,
                           float limbSwing, float limbSwingAmount, float partialTicks, float ageInTicks,
                           float netHeadYaw, float headPitch) {
            ResourceLocation texture = entity.isAggro() ? EYES_ANGRY : EYES;
            VertexConsumer consumer = buffer.getBuffer(RenderType.eyes(texture));
            this.getParentModel().renderToBuffer(poseStack, consumer, 15728640, OverlayTexture.NO_OVERLAY,
                    1.0F, 1.0F, 1.0F, 1.0F);
        }
    }
}
