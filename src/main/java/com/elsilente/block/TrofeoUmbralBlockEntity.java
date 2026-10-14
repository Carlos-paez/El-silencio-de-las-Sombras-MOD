package com.elsilente.block;

import com.elsilente.ElSilenteMod;
import com.elsilente.entity.SombraDebilEntity;
import com.elsilente.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

/**
 * Trofeo del Umbral: bloque-trofeo que emite particulas negras y repele
 * (disipa) cualquier Sombra Debil en un radio de 20 bloques.
 *
 * Ademas registra sus posiciones para la ZONA SEGURA (§14): El Silente no
 * entra en un radio de 12 bloques alrededor de un trofeo (ver
 * {@link #findNearby}).
 */
@Mod.EventBusSubscriber(modid = ElSilenteMod.MODID)
public class TrofeoUmbralBlockEntity extends BlockEntity {

    private static final int RADIO = 20;
    private static final int PERIODO = 20;

    /** Posiciones de trofeo por dimension (zona segura de El Silente). */
    private static final Map<ResourceKey<Level>, Set<BlockPos>> TROPHIES = new HashMap<>();

    private int pulseTimer = PERIODO;

    public TrofeoUmbralBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.TROFEO_UMBRAL.get(), pos, state);
    }

    // -------------------------------------------------------------------------
    // Zona segura: registro de trofeos (hooks onPlace/onRemove del bloque).
    // -------------------------------------------------------------------------

    public static void track(Level level, BlockPos pos) {
        TROPHIES.computeIfAbsent(level.dimension(), k -> new HashSet<>()).add(pos.immutable());
    }

    public static void untrack(Level level, BlockPos pos) {
        Set<BlockPos> set = TROPHIES.get(level.dimension());
        if (set != null) {
            set.remove(pos);
        }
    }

    /**
     * Trofeo vivo mas cercano a {@code origin} dentro de {@code radius}, o
     * null. Entradas obsoletas (trofeo destruido) se purgan con pereza.
     */
    public static BlockPos findNearby(Level level, BlockPos origin, double radius) {
        Set<BlockPos> set = TROPHIES.get(level.dimension());
        if (set == null || set.isEmpty()) {
            return null;
        }
        double r2 = radius * radius;
        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;
        Iterator<BlockPos> iterator = set.iterator();
        while (iterator.hasNext()) {
            BlockPos trophy = iterator.next();
            if (!level.isLoaded(trophy)) {
                continue;
            }
            if (!(level.getBlockState(trophy).getBlock() instanceof TrofeoUmbralBlock)) {
                iterator.remove();
                continue;
            }
            double dist = origin.distSqr(trophy);
            if (dist <= r2 && dist < bestDist) {
                bestDist = dist;
                best = trophy;
            }
        }
        return best;
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        TROPHIES.clear();
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
