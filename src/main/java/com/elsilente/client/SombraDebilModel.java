package com.elsilente.client;

import com.elsilente.ElSilenteMod;
import com.elsilente.entity.SombraDebilEntity;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * Modelo de la Sombra Debil: humanoide encorvado en formato ESTANDAR de skin
 * 64x64 de jugador (proporciones completas + capas overlay), en lugar del
 * layout miniatura anterior (cabeza 6x6x6, brazos 2x8x2...) de detalle 16x16.
 *
 * Atlas UV (64x64), DEBE coincidir con scripts/generate_assets.py:
 *   cabeza 8x8x8   : (0,0)-(32,16)   -> cara frontal en (8,8)-(16,16)
 *   hat (capucha)  : (32,0)-(64,16)  -> frente de la capucha en (40,8)-(48,16)
 *   torso 8x12x4   : (16,16)-(40,32) -> pecho en (20,20)-(28,32)
 *   brazo der.     : (40,16)-(56,32) -> frente en (44,20)-(48,32)
 *   brazo izq.     : (32,48)-(48,64) -> frente en (36,52)-(40,64)
 *   pierna der.    : (0,16)-(16,32)  -> frente en (4,20)-(8,32)
 *   pierna izq.    : (16,48)-(32,64) -> frente en (20,52)-(24,64)
 *   capas overlay (semi-transparentes, "bruma" condensada):
 *   capucha        : (32,0)-(64,16)
 *   chaqueta       : (16,32)-(40,48)
 *   manga der.     : (40,32)-(56,48)
 *   manga izq.     : (48,48)-(64,64)
 *   pantalon der.  : (0,32)-(16,48)
 *   pantalon izq.  : (0,48)-(16,64)
 *
 * Ojos: cara (8,8)-(16,16) -> ojos en x=9,10 y x=13,14 ; y=11,12.
 */
public class SombraDebilModel extends HumanoidModel<SombraDebilEntity> {

    public static final ModelLayerLocation LAYER =
            new ModelLayerLocation(new ResourceLocation(ElSilenteMod.MODID, "sombra_debil"), "main");

    // Capas overlay del formato 64x64 (bruma eterea sobre el cuerpo oscuro).
    private final ModelPart jacket;
    private final ModelPart rightSleeve;
    private final ModelPart leftSleeve;
    private final ModelPart rightPant;
    private final ModelPart leftPant;

    public SombraDebilModel(ModelPart root) {
        super(root);
        this.jacket = root.getChild("jacket");
        this.rightSleeve = root.getChild("right_sleeve");
        this.leftSleeve = root.getChild("left_sleeve");
        this.rightPant = root.getChild("right_pant");
        this.leftPant = root.getChild("left_pant");
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();

        // Piezas base de HumanoidModel con proporciones estandar de jugador.
        root.addOrReplaceChild("head",
                CubeListBuilder.create()
                        .texOffs(0, 0)
                        .addBox(-4.0F, -8.0F, -4.0F, 8, 8, 8),
                PartPose.ZERO);

        // Capucha: capa exterior semitransparente alrededor de la cabeza.
        root.addOrReplaceChild("hat",
                CubeListBuilder.create()
                        .texOffs(32, 0)
                        .addBox(-4.0F, -8.0F, -4.0F, 8, 8, 8, new CubeDeformation(0.5F)),
                PartPose.ZERO);

        root.addOrReplaceChild("body",
                CubeListBuilder.create()
                        .texOffs(16, 16)
                        .addBox(-4.0F, 0.0F, -2.0F, 8, 12, 4),
                PartPose.ZERO);

        root.addOrReplaceChild("right_arm",
                CubeListBuilder.create()
                        .texOffs(40, 16)
                        .addBox(-3.0F, -2.0F, -2.0F, 4, 12, 4),
                PartPose.offset(-5.0F, 2.0F, 0.0F));

        root.addOrReplaceChild("left_arm",
                CubeListBuilder.create()
                        .texOffs(32, 48)
                        .addBox(-1.0F, -2.0F, -2.0F, 4, 12, 4),
                PartPose.offset(5.0F, 2.0F, 0.0F));

        root.addOrReplaceChild("right_leg",
                CubeListBuilder.create()
                        .texOffs(0, 16)
                        .addBox(-2.0F, 0.0F, -2.0F, 4, 12, 4),
                PartPose.offset(-1.9F, 12.0F, 0.0F));

        root.addOrReplaceChild("left_leg",
                CubeListBuilder.create()
                        .texOffs(16, 48)
                        .addBox(-2.0F, 0.0F, -2.0F, 4, 12, 4),
                PartPose.offset(1.9F, 12.0F, 0.0F));

        // Capas overlay (bruma): mismas UV que el formato 64x64 de jugador.
        CubeDeformation bruma = new CubeDeformation(0.25F);
        root.addOrReplaceChild("jacket",
                CubeListBuilder.create()
                        .texOffs(16, 32)
                        .addBox(-4.0F, 0.0F, -2.0F, 8, 12, 4, bruma),
                PartPose.ZERO);
        root.addOrReplaceChild("right_sleeve",
                CubeListBuilder.create()
                        .texOffs(40, 32)
                        .addBox(-3.0F, -2.0F, -2.0F, 4, 12, 4, bruma),
                PartPose.offset(-5.0F, 2.0F, 0.0F));
        root.addOrReplaceChild("left_sleeve",
                CubeListBuilder.create()
                        .texOffs(48, 48)
                        .addBox(-1.0F, -2.0F, -2.0F, 4, 12, 4, bruma),
                PartPose.offset(5.0F, 2.0F, 0.0F));
        root.addOrReplaceChild("right_pant",
                CubeListBuilder.create()
                        .texOffs(0, 32)
                        .addBox(-2.0F, 0.0F, -2.0F, 4, 12, 4, bruma),
                PartPose.offset(-1.9F, 12.0F, 0.0F));
        root.addOrReplaceChild("left_pant",
                CubeListBuilder.create()
                        .texOffs(0, 48)
                        .addBox(-2.0F, 0.0F, -2.0F, 4, 12, 4, bruma),
                PartPose.offset(1.9F, 12.0F, 0.0F));

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

        // Las capas overlay siguen a las piezas base.
        this.jacket.copyFrom(this.body);
        this.rightSleeve.copyFrom(this.rightArm);
        this.leftSleeve.copyFrom(this.leftArm);
        this.rightPant.copyFrom(this.rightLeg);
        this.leftPant.copyFrom(this.leftLeg);
    }
}
