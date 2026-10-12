package com.elsilente.client;

import com.elsilente.ElSilenteMod;
import com.elsilente.entity.ElSilenteEntity;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * Modelo propio de El Silente: humanoide con capucha, sotana y manos largas.
 *
 * IMPORTANTE sobre el atlas UV (64x64), porque de aqui sale la textura
 * (scripts/generate_assets.py debe respetarlo):
 *
 *   (0,0)-(32,16)   cabeza 8x8x8   -> cara frontal en (8,8)-(16,16)
 *   (32,0)-(64,16)  capucha 8x8x8  -> frente de capucha en (40,8)-(48,16)
 *   (16,16)-(40,32) torso 8x12x4   -> pecho en (20,20)-(28,32)
 *   (0,16)-(16,32)  pierna derecha 4x12x4
 *   (40,16)-(56,32) brazo derecho 4x12x4 -> (44,20)-(48,32)
 *   (0,32)-(28,44)  sotana 9x7x5   -> delantera en (5,37)-(14,44)
 *   (28,32)-(48,41) mano derecha 5x4x5 -> (33,37)-(38,41)
 *   (48,32)-(64,48) brazo izquierdo 4x12x4 -> (52,36)-(56,48)
 *   (0,48)-(16,64)  pierna izquierda 4x12x4
 *   (16,48)-(36,57) mano izquierda 5x4x5 -> (21,53)-(26,57)
 *
 * Ojos: x=9,10 y x=13,14 ; y=9..13 (dentro de la cara). Van pintados tambien en
 * la textura base (casi blancos), de modo que se ven siempre; la capa emisiva
 * los enciende en blanco (STALK) o los pone en rojo (CHASE).
 */
public class ElSilenteModel extends HumanoidModel<ElSilenteEntity> {

    public static final ModelLayerLocation LAYER =
            new ModelLayerLocation(new ResourceLocation(ElSilenteMod.MODID, "el_silente"), "main");

    public ElSilenteModel(ModelPart root) {
        super(root);
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();

        // Cabeza: la cara frontal ocupa (8,8)-(16,16).
        root.addOrReplaceChild("head",
                CubeListBuilder.create()
                        .texOffs(0, 0)
                        .addBox(-4.0F, -8.0F, -4.0F, 8, 8, 8),
                PartPose.ZERO);

        // Capucha: +0.5 de grosor y desplazada hacia +Z. OJO: la cara texturizada del
        // modelo mira a -Z (EntityRenderDispatcher rota con 180-yaw), asi que la cara de
        // la cabeza queda en z=-4 y la capucha NO debe pasar de z=-3.5 o la tapa entera.
        root.addOrReplaceChild("hat",
                CubeListBuilder.create()
                        .texOffs(32, 0)
                        .addBox(-4.0F, -8.0F, -3.0F, 8, 8, 8, new CubeDeformation(0.5F)),
                PartPose.ZERO);

        // Torso.
        PartDefinition body = root.addOrReplaceChild("body",
                CubeListBuilder.create()
                        .texOffs(16, 16)
                        .addBox(-4.0F, 0.0F, -2.0F, 8, 12, 4),
                PartPose.ZERO);

        // Sotana: cuelga del torso (se mueve con el) y tapa los muslos.
        body.addOrReplaceChild("robe",
                CubeListBuilder.create()
                        .texOffs(0, 32)
                        .addBox(-4.5F, 11.0F, -2.5F, 9, 7, 5),
                PartPose.ZERO);

        // Brazo derecho + mano alargada (garra).
        PartDefinition rightArm = root.addOrReplaceChild("right_arm",
                CubeListBuilder.create()
                        .texOffs(40, 16)
                        .addBox(-3.0F, -2.0F, -2.0F, 4, 12, 4),
                PartPose.offset(-5.0F, 2.0F, 0.0F));
        rightArm.addOrReplaceChild("right_hand",
                CubeListBuilder.create()
                        .texOffs(28, 32)
                        .addBox(-2.5F, 9.0F, -2.5F, 5, 4, 5),
                PartPose.ZERO);

        // Brazo izquierdo + mano alargada.
        PartDefinition leftArm = root.addOrReplaceChild("left_arm",
                CubeListBuilder.create()
                        .texOffs(48, 32)
                        .addBox(-3.0F, -2.0F, -2.0F, 4, 12, 4),
                PartPose.offset(5.0F, 2.0F, 0.0F));
        leftArm.addOrReplaceChild("left_hand",
                CubeListBuilder.create()
                        .texOffs(16, 48)
                        .addBox(-2.5F, 9.0F, -2.5F, 5, 4, 5),
                PartPose.ZERO);

        // Piernas (cubiertas por la sotana hasta la rodilla).
        root.addOrReplaceChild("right_leg",
                CubeListBuilder.create()
                        .texOffs(0, 16)
                        .addBox(-2.0F, 0.0F, -2.0F, 4, 12, 4),
                PartPose.offset(-1.9F, 12.0F, 0.0F));
        root.addOrReplaceChild("left_leg",
                CubeListBuilder.create()
                        .texOffs(0, 48)
                        .addBox(-2.0F, 0.0F, -2.0F, 4, 12, 4),
                PartPose.offset(1.9F, 12.0F, 0.0F));

        return LayerDefinition.create(mesh, 64, 64);
    }

    @Override
    public void setupAnim(ElSilenteEntity entity, float limbSwing, float limbSwingAmount,
                          float ageInTicks, float netHeadYaw, float headPitch) {
        super.setupAnim(entity, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);

        // La capucha gira con la cabeza (sino se quedaría quieta y la cabeza se saldría).
        this.hat.copyFrom(this.head);

        // Inclinación característica de la cabeza + balanceo lento del torso.
        this.head.zRot = 0.07F;
        this.body.zRot = Mth.sin(ageInTicks * 0.05F) * 0.025F;
    }
}
