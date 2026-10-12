package com.elsilente.block;

import com.elsilente.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Trofeo del Umbral: lampara-trofeo que repele Sombras Debiles (20 b) y
 * emite particulas negras mientras este cargada (chunk cargado).
 */
public class TrofeoUmbralBlock extends Block implements EntityBlock {

    public TrofeoUmbralBlock(Properties properties) {
        super(properties);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TrofeoUmbralBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                  BlockEntityType<T> type) {
        if (level.isClientSide) {
            return null;
        }
        return (lvl, pos, st, blockEntity) -> {
            if (blockEntity instanceof TrofeoUmbralBlockEntity trofeo) {
                TrofeoUmbralBlockEntity.serverTick(lvl, pos, st, trofeo);
            }
        };
    }
}
