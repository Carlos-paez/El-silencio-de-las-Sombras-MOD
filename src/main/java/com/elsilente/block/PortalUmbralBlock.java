package com.elsilente.block;

import com.elsilente.event.ReinoHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Grieta del Umbral (§13 de MEJORAS.md): portal al reino de las sombras.
 *
 * Se abre al derrotar al Umbral y al pisarla teletransporta al reino
 * (o de vuelta si estas dentro). Sin colision: se cruza caminando.
 */
public class PortalUmbralBlock extends Block {

    private static final VoxelShape SHAPE = Block.box(1.0D, 0.0D, 1.0D, 15.0D, 16.0D, 15.0D);

    public PortalUmbralBlock(Properties properties) {
        super(properties);
    }

    @Override
    public void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
        if (level.isClientSide || !(entity instanceof ServerPlayer player)) {
            return;
        }
        if (level.dimension() == ReinoHandler.REINO) {
            ReinoHandler.tryExit(player);
        } else {
            ReinoHandler.tryEnter(player);
        }
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        // Almas y humo danzando en la grieta (cosmetico, client-side).
        level.addParticle(ParticleTypes.SOUL,
                pos.getX() + random.nextDouble(), pos.getY() + random.nextDouble(),
                pos.getZ() + random.nextDouble(), 0.0D, 0.02D, 0.0D);
        if (random.nextInt(3) == 0) {
            level.addParticle(ParticleTypes.LARGE_SMOKE,
                    pos.getX() + random.nextDouble(), pos.getY() + random.nextDouble() * 0.5D,
                    pos.getZ() + random.nextDouble(), 0.0D, 0.01D, 0.0D);
        }
    }
}
