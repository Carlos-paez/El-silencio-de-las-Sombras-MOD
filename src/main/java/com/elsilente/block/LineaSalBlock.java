package com.elsilente.block;

import com.elsilente.config.ElSilenteConfig;
import com.elsilente.util.SaltBarrier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * LINEA DE SAL (§7 de MEJORAS.md): defensa pasiva con coste.
 *
 * Impide el spawn NATURAL de Sombras en un radio de 12 bloques (ver
 * {@link SaltBarrier}); NO detiene emboscadas ni invocaciones. Se consume con
 * el tiempo: en lluvia (a la intemperie) pierde una capa por tick aleatorio y
 * en seco con un 15% de probabilidad. Cada capa encoge la altura visual del
 * bloque hasta disolverse.
 */
public class LineaSalBlock extends Block {

    /** Capas de residuo: 3 (recien colocada) -> 0 (a punto de disolverse). */
    public static final IntegerProperty RESIDUO = IntegerProperty.create("residuo", 0, 3);

    public LineaSalBlock(Properties properties) {
        super(properties);
        // IntegerProperty usa su cota baja (0) por defecto: fijamos 3 para que
        // toda colocacion empiece con el salero completo.
        registerDefaultState(stateDefinition.any().setValue(RESIDUO, 3));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(RESIDUO);
    }

    @Override
    public void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (!ElSilenteConfig.COMMON.saltEnabled.get()) {
            return;
        }        // La lluvia a la intemperie disuelve la sal mucho mas rapido.
        boolean rain = level.isRainingAt(pos);
        if (!rain && random.nextFloat() > 0.15F) {
            return;
        }
        int residuo = state.getValue(RESIDUO);
        if (residuo <= 0) {
            dissolve(level, pos);
            return;
        }
        level.setBlock(pos, state.setValue(RESIDUO, residuo - 1), 3);
        if (rain) {
            level.sendParticles(net.minecraft.core.particles.ParticleTypes.SPLASH,
                    pos.getX() + 0.5D, pos.getY() + 0.2D, pos.getZ() + 0.5D, 4, 0.2D, 0.1D, 0.2D, 0.0D);
        }
    }

    /** La ultima capa se va con las primeras gotas de lluvia. */
    private void dissolve(ServerLevel level, BlockPos pos) {
        level.sendParticles(net.minecraft.core.particles.ParticleTypes.ASH,
                pos.getX() + 0.5D, pos.getY() + 0.2D, pos.getZ() + 0.5D, 10, 0.3D, 0.15D, 0.3D, 0.01D);
        level.playSound(null, pos, SoundEvents.GRAVEL_BREAK, SoundSource.BLOCKS, 0.6F, 1.4F);
        level.removeBlock(pos, false);
        SaltBarrier.remove(level, pos);
    }

    // -------------------------------------------------------------------------
    // Forma: cojinete fino (2-8 px) que se encoge con las capas.
    // -------------------------------------------------------------------------

    private static VoxelShape shapeFor(BlockState state) {
        double height = 2.0D + 2.0D * state.getValue(RESIDUO);
        return Shapes.box(0.0D, 0.0D, 0.0D, 16.0D, height, 16.0D);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return shapeFor(state);
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos,
                                         CollisionContext context) {
        return shapeFor(state);
    }

    @Override
    public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        BlockPos below = pos.below();
        return !level.getBlockState(below).getCollisionShape(level, below).isEmpty();
    }

    @Override
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighborState,
                                  LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        if (direction == Direction.DOWN && !canSurvive(state, level, pos)) {
            return Blocks.AIR.defaultBlockState();
        }
        return super.updateShape(state, direction, neighborState, level, pos, neighborPos);
    }

    @Override
    public boolean isPathfindable(BlockState state, BlockGetter level, BlockPos pos,
                                  PathComputationType type) {
        // Se puede pisar sin buscar ruta encima.
        return true;
    }

    // -------------------------------------------------------------------------
    // Registro en el barrera de sal: onPlace/onRemove cubren colocacion,
    // destruccion, disolucion y colocacion por estructuras.
    // -------------------------------------------------------------------------

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean isMoving) {
        super.onPlace(state, level, pos, oldState, isMoving);
        if (!state.is(oldState.getBlock())) {
            SaltBarrier.add(level, pos);
        }
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock())) {
            SaltBarrier.remove(level, pos);
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }
}
