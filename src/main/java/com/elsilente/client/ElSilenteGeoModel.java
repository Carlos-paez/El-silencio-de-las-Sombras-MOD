package com.elsilente.client;

import com.elsilente.ElSilenteMod;
import com.elsilente.entity.ElSilenteEntity;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

/**
 * Modelo GeckoLib de El Silente (§12 de MEJORAS.md).
 *
 * El atlas UV (64x64) es el MISMO del modelo java anterior, de modo que la
 * textura el_silente.png y las capas emisivas de ojos no cambian:
 *
 *   (0,0)-(32,16)   cabeza 8x8x8   -> cara frontal en (8,8)-(16,16)
 *   (32,0)-(64,16)  capucha 9x9x9  -> frente de capucha en (40,8)-(48,16)
 *   (16,16)-(40,32) torso 8x12x4   -> pecho en (20,20)-(28,32)
 *   (0,16)-(16,32)  pierna derecha 4x12x4
 *   (40,16)-(56,32) brazo derecho 4x12x4 -> (44,20)-(48,32)
 *   (0,32)-(28,44)  sotana 9x7x5   -> delantera en (5,37)-(14,44)
 *   (28,32)-(48,41) mano derecha 5x4x5 -> (33,37)-(38,41)
 *   (48,32)-(64,48) brazo izquierdo 4x12x4 -> (52,36)-(56,48)
 *   (0,48)-(16,64)  pierna izquierda 4x12x4
 *   (16,48)-(36,57) mano izquierda 5x4x5 -> (21,53)-(26,57)
 *
 * OJO: si cambia el modelo geo, hay que actualizar scripts/generate_assets.py.
 */
public class ElSilenteGeoModel extends GeoModel<ElSilenteEntity> {

    private static final ResourceLocation MODEL = new ResourceLocation(ElSilenteMod.MODID,
            "geo/models/entity/el_silente.geo.json");
    private static final ResourceLocation TEXTURE = new ResourceLocation(ElSilenteMod.MODID,
            "textures/entity/el_silente.png");
    private static final ResourceLocation ANIMATIONS = new ResourceLocation(ElSilenteMod.MODID,
            "animations/el_silente.animation.json");

    @Override
    public ResourceLocation getModelResource(ElSilenteEntity animatable) {
        return MODEL;
    }

    @Override
    public ResourceLocation getTextureResource(ElSilenteEntity animatable) {
        return TEXTURE;
    }

    @Override
    public ResourceLocation getAnimationResource(ElSilenteEntity animatable) {
        return ANIMATIONS;
    }
}
