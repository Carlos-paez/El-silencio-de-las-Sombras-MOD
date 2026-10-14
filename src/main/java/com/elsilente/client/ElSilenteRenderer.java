package com.elsilente.client;

import com.elsilente.ElSilenteMod;
import com.elsilente.entity.ElSilenteEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;

/**
 * Renderer GeckoLib de El Silente (§12 de MEJORAS.md).
 *
 * La capa emisiva de ojos ahora vive sobre el sistema de capas de GeckoLib:
 * dibuja el modelo geo completo con RenderType.eyes() usando la textura de
 * ojos (blanco en STALK, rojo en CHASE); el resto del atlas esta transparente,
 * de modo que solo brillan los ojos en la oscuridad.
 */
public class ElSilenteRenderer extends GeoEntityRenderer<ElSilenteEntity> {

    private static final ResourceLocation TEXTURE =
            new ResourceLocation(ElSilenteMod.MODID, "textures/entity/el_silente.png");
    private static final ResourceLocation EYES =
            new ResourceLocation(ElSilenteMod.MODID, "textures/entity/el_silente_eyes.png");
    private static final ResourceLocation EYES_ANGRY =
            new ResourceLocation(ElSilenteMod.MODID, "textures/entity/el_silente_eyes_angry.png");

    public ElSilenteRenderer(EntityRendererProvider.Context context) {
        super(context, new ElSilenteGeoModel());
        this.addRenderLayer(new EyesLayer(this));
    }

    @Override
    public ResourceLocation getTextureLocation(ElSilenteEntity animatable) {
        return TEXTURE;
    }

    @Override
    public void preRender(PoseStack poseStack, ElSilenteEntity animatable, BakedGeoModel model,
                          MultiBufferSource bufferSource, VertexConsumer buffer, boolean isReRender,
                          float partialTick, int packedLight, int packedOverlay,
                          float red, float green, float blue, float alpha) {
        super.preRender(poseStack, animatable, model, bufferSource, buffer, isReRender,
                partialTick, packedLight, packedOverlay, red, green, blue, alpha);
        // Modelo de 2 bloques de alto: 1.1 lo mantiene un poco por encima del jugador
        // sin despegarse de la hitbox (1.99 bloques).
        poseStack.scale(1.1F, 1.1F, 1.1F);
    }

    @Override
    public RenderType getRenderType(ElSilenteEntity animatable, ResourceLocation texture,
                                    MultiBufferSource bufferSource, float partialTick) {
        // Cortado sin culling: la sotana y la capucha se ven bien desde dentro.
        return RenderType.entityCutoutNoCull(texture);
    }

    /**
     * Capa emisiva de ojos sobre GeckoLib: RenderType.eyes ignora la luz,
     * asi que brillan en la oscuridad y cambian a rojo en CHASE.
     */
    private static class EyesLayer extends GeoRenderLayer<ElSilenteEntity> {

        EyesLayer(ElSilenteRenderer renderer) {
            super(renderer);
        }

        @Override
        public void render(PoseStack poseStack, ElSilenteEntity animatable, BakedGeoModel model,
                           RenderType renderType, MultiBufferSource bufferSource, VertexConsumer buffer,
                           float partialTick, int packedLight, int packedOverlay) {
            RenderType eyesType = RenderType.eyes(animatable.isAggro() ? EYES_ANGRY : EYES);
            VertexConsumer eyesBuffer = bufferSource.getBuffer(eyesType);
            // 15728640 (0xF000F0): luz maxima, igual que AutoGlowingGeoLayer.
            this.getRenderer().reRender(model, poseStack, bufferSource, animatable, eyesType,
                    eyesBuffer, partialTick, 15728640, OverlayTexture.NO_OVERLAY,
                    1.0F, 1.0F, 1.0F, 1.0F);
        }
    }
}
