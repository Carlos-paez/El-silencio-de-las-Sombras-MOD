package com.elsilente.client;

import com.elsilente.ElSilenteMod;
import com.elsilente.entity.UmbralEntity;
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
 * Modelo de El Umbral: humanoide enorme con cuernos.
 *
 * Atlas UV (64x64), DEBE coincidir con scripts/generate_assets.py:
 *   cabeza 10x10x10   : (0,0)-(40,20)    -> cara frontal en (10,10)-(20,20)
 *   cuerno der. 2x5x2 : (40,0)-(48,7)
 *   cuerno izq. 2x5x2 : (48,0)-(56,7)
 *   torso 12x16x6     : (0,20)-(36,42)   -> pecho en (6,26)-(18,42)
 *   brazo der. 4x16x4 : (0,42)-(16,62)
 *   brazo izq. 4x16x4 : (16,42)-(32,62)
 *   pierna der. 4x16x4: (32,42)-(48,62)
 *   pierna izq. 4x16x4: (48,42)-(64,62)
 *
 * Ojos: cara (10,10)-(20,20) -> x=12,13 y x=16,17 ; y=14,15.
 */
public class UmbralModel extends HumanoidModel<UmbralEntity> {

    public static final ModelLayerLocation LAYER =
            new ModelLayerLocation(new ResourceLocation(ElSilenteMod.MODID, "umbral"), "main");

    public UmbralModel(ModelPart root) {
        super(root);
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();

        PartDefinition head = root.addOrReplaceChild("head",
                CubeListBuilder.create()
                        .texOffs(0, 0)
                        .addBox(-5.0F, -10.0F, -5.0F, 10, 10, 10),
                PartPose.ZERO);

        // HumanoidModel exige la pieza "hat"; la dejamos sin geometria.
        root.addOrReplaceChild("hat", CubeListBuilder.create(), PartPose.ZERO);

        // Cuernos: hijos de la cabeza para que giren con ella.
        head.addOrReplaceChild("right_horn",
                CubeListBuilder.create()
                        .texOffs(40, 0)
                        .addBox(-1.0F, 0.0F, -1.0F, 2, 5, 2),
                PartPose.offsetAndRotation(-3.5F, -10.0F, 0.0F, 0.0F, 0.0F, 0.35F));
        head.addOrReplaceChild("left_horn",
                CubeListBuilder.create()
                        .texOffs(48, 0)
                        .addBox(-1.0F, 0.0F, -1.0F, 2, 5, 2),
                PartPose.offsetAndRotation(3.5F, -10.0F, 0.0F, 0.0F, 0.0F, -0.35F));

        root.addOrReplaceChild("body",
                CubeListBuilder.create()
                        .texOffs(0, 20)
                        .addBox(-6.0F, 0.0F, -3.0F, 12, 16, 6),
                PartPose.ZERO);

        root.addOrReplaceChild("right_arm",
                CubeListBuilder.create()
                        .texOffs(0, 42)
                        .addBox(-2.0F, -2.0F, -2.0F, 4, 16, 4),
                PartPose.offset(-8.0F, 2.0F, 0.0F));

        root.addOrReplaceChild("left_arm",
                CubeListBuilder.create()
                        .texOffs(16, 42)
                        .addBox(-2.0F, -2.0F, -2.0F, 4, 16, 4),
                PartPose.offset(8.0F, 2.0F, 0.0F));

        root.addOrReplaceChild("right_leg",
                CubeListBuilder.create()
                        .texOffs(32, 42)
                        .addBox(-2.0F, 0.0F, -2.0F, 4, 16, 4),
                PartPose.offset(-3.0F, 8.0F, 0.0F));

        root.addOrReplaceChild("left_leg",
                CubeListBuilder.create()
                        .texOffs(48, 42)
                        .addBox(-2.0F, 0.0F, -2.0F, 4, 16, 4),
                PartPose.offset(3.0F, 8.0F, 0.0F));

        return LayerDefinition.create(mesh, 64, 64);
    }

    @Override
    public void setupAnim(UmbralEntity entity, float limbSwing, float limbSwingAmount,
                          float ageInTicks, float netHeadYaw, float headPitch) {
        super.setupAnim(entity, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);

        // Presencia imponente: pecho hacia delante y balanceo pesado.
        this.body.xRot = 0.08F;
        this.rightArm.zRot += 0.06F;
        this.leftArm.zRot -= 0.06F;
        this.head.yRot += Mth.cos(ageInTicks * 0.03F) * 0.04F;
    }
}
