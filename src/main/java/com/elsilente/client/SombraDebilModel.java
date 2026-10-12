package com.elsilente.client;

import com.elsilente.ElSilenteMod;
import com.elsilente.entity.SombraDebilEntity;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * Modelo de la Sombra Debil: humanoide menudo y encorvado (sin capucha: la
 * "hat" se deja vacia porque HumanoidModel la exige).
 *
 * Atlas UV (64x64), DEBE coincidir con scripts/generate_assets.py:
 *   cabeza 6x6x6        : (0,0)-(24,12)   -> cara frontal en (6,6)-(12,12)
 *   torso 8x8x4         : (0,12)-(24,24)  -> pecho en (4,16)-(12,24)
 *   brazo der. 2x8x2    : (24,12)-(32,22)
 *   brazo izq. 2x8x2    : (32,12)-(40,22)
 *   pierna der. 4x6x4   : (0,24)-(16,34)
 *   pierna izq. 4x6x4   : (16,24)-(32,34)
 *
 * Ojos: cara (6,6)-(12,12) -> ojos en x=7,8 y x=10,11 ; y=8,9.
 */
public class SombraDebilModel extends HumanoidModel<SombraDebilEntity> {

    public static final ModelLayerLocation LAYER =
            new ModelLayerLocation(new ResourceLocation(ElSilenteMod.MODID, "sombra_debil"), "main");

    public SombraDebilModel(ModelPart root) {
        super(root);
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();

        root.addOrReplaceChild("head",
                CubeListBuilder.create()
                        .texOffs(0, 0)
                        .addBox(-3.0F, -6.0F, -3.0F, 6, 6, 6),
                PartPose.ZERO);

        // HumanoidModel exige la pieza "hat"; la dejamos sin geometria.
        root.addOrReplaceChild("hat", CubeListBuilder.create(), PartPose.ZERO);

        root.addOrReplaceChild("body",
                CubeListBuilder.create()
                        .texOffs(0, 12)
                        .addBox(-4.0F, 0.0F, -2.0F, 8, 8, 4),
                PartPose.ZERO);

        root.addOrReplaceChild("right_arm",
                CubeListBuilder.create()
                        .texOffs(24, 12)
                        .addBox(-1.0F, -2.0F, -1.0F, 2, 8, 2),
                PartPose.offset(-5.0F, 1.0F, 0.0F));

        root.addOrReplaceChild("left_arm",
                CubeListBuilder.create()
                        .texOffs(32, 12)
                        .addBox(-1.0F, -2.0F, -1.0F, 2, 8, 2),
                PartPose.offset(5.0F, 1.0F, 0.0F));

        root.addOrReplaceChild("right_leg",
                CubeListBuilder.create()
                        .texOffs(0, 24)
                        .addBox(-2.0F, 0.0F, -2.0F, 4, 6, 4),
                PartPose.offset(-1.9F, 8.0F, 0.0F));

        root.addOrReplaceChild("left_leg",
                CubeListBuilder.create()
                        .texOffs(16, 24)
                        .addBox(-2.0F, 0.0F, -2.0F, 4, 6, 4),
                PartPose.offset(1.9F, 8.0F, 0.0F));

        return LayerDefinition.create(mesh, 64, 64);
    }

    @Override
    public void setupAnim(SombraDebilEntity entity, float limbSwing, float limbSwingAmount,
                          float ageInTicks, float netHeadYaw, float headPitch) {
        super.setupAnim(entity, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);

        // Balanceo fantasmal: se inclina hacia delante y cabecea suavemente.
        this.body.xRot = 0.10F;
        this.head.xRot += 0.15F;
        this.head.zRot = Mth.sin(ageInTicks * 0.07F) * 0.05F;
        this.rightArm.zRot = 0.08F;
        this.leftArm.zRot = -0.08F;
    }
}
