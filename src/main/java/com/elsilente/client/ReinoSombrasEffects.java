package com.elsilente.client;

import net.minecraft.client.renderer.DimensionSpecialEffects;
import net.minecraft.world.phys.Vec3;

/**
 * Efectos de cliente del Reino de las Sombras (§13 de MEJORAS.md).
 *
 * Cielo perpetuo: sin luna ni estrellas, niebla violacea constante y densa
 * (isFoggyAt siempre true) para que el reino se sienta cerrado y agotador.
 */
public class ReinoSombrasEffects extends DimensionSpecialEffects {

    private static final Vec3 FOG_COLOR = new Vec3(0.045D, 0.018D, 0.078D);

    public ReinoSombrasEffects() {
        // Sin nubes, SkyType.NONE, sin forzar lightmap brillante, sin luz ambiental fija.
        super(Float.MAX_VALUE, false, SkyType.NONE, false, false);
    }

    @Override
    public Vec3 getBrightnessDependentFogColor(Vec3 color, float brightness) {
        return FOG_COLOR;
    }

    @Override
    public boolean isFoggyAt(int x, int y) {
        // Niebla densa en todas partes, incluso en lo alto.
        return true;
    }
}
