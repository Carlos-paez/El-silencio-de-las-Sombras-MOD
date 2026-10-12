package com.elsilente.block;

import com.elsilente.entity.SombraDebilEntity;
import com.elsilente.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Trofeo del Umbral: bloque-trofeo que emite particulas negras y repele
 * (disipa) cualquier Sombra Debil en un radio de 20 bloques.
 */
public class TrofeoUmbralBlockEntity extends BlockEntity {

    private static final int RADIO = 20;
    private static final int PERIODO = 20;

    private int pulseTimer = PERIODO;

    public TrofeoUmbralBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.TROFEO_UMBRAL.get(), pos, state);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, TrofeoUmbralBlockEntity be) {
        if (--be.pulseTimer > 0) {
            return;
        }
        be.pulseTimer = PERIODO;

        if (level instanceof ServerLevel server) {
            // Particulas negras alrededor del trofeo.
            server.sendParticles(ParticleTypes.ASH, pos.getX() + 0.5D, pos.getY() + 0.8D,
                    pos.getZ() + 0.5D, 6, 0.35D, 0.35D, 0.35D, 0.0D);
            server.sendParticles(ParticleTypes.LARGE_SMOKE, pos.getX() + 0.5D, pos.getY() + 0.6D,
                    pos.getZ() + 0.5D, 2, 0.2D, 0.2D, 0.2D, 0.01D);

            // Ninguna Sombra Debil puede vivir cerca del trofeo.
            for (SombraDebilEntity sombra : server.getEntitiesOfClass(SombraDebilEntity.class,
                    new net.minecraft.world.phys.AABB(pos).inflate(RADIO))) {
                sombra.dissipate();
            }
        }
    }
}
