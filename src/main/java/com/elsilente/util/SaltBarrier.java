package com.elsilente.util;

import com.elsilente.ElSilenteMod;
import com.elsilente.config.ElSilenteConfig;
import com.elsilente.block.LineaSalBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
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
 * Radio de proteccion de la Linea de sal (§7 de MEJORAS.md).
 *
 * Un bloque de Linea de sal de protege un radio de 12 bloques frente al spawn
 * NATURAL de Sombras (no detiene emboscadas, invocaciones ni huevos). Las
 * posiciones se registran desde los hooks onPlace/onRemove del propio bloque
 * y se validan con pereza en cada consulta (si el bloque ya no es sal, se
 * purga la entrada).
 */
@Mod.EventBusSubscriber(modid = ElSilenteMod.MODID)
public final class SaltBarrier {

    /** Radio de proteccion (bloques) de cada Linea de sal. */
    public static final double RADIUS = 12.0D;
    /** Desvio vertical maximo: la sal protege su planta y una mas arriba/abajo. */
    private static final double VERTICAL = 10.0D;

    /** Posiciones de sal por dimension. */
    private static final Map<ResourceKey<Level>, Set<BlockPos>> SALTS = new HashMap<>();

    private SaltBarrier() {
    }

    public static void add(Level level, BlockPos pos) {
        SALTS.computeIfAbsent(level.dimension(), k -> new HashSet<>()).add(pos.immutable());
    }

    public static void remove(Level level, BlockPos pos) {
        Set<BlockPos> set = SALTS.get(level.dimension());
        if (set != null) {
            set.remove(pos);
        }
    }

    /**
     * ¿Está {@code pos} bajo el efecto de una Linea de sal viva?
     * Entradas obsoletas (bloque destruido, chunk descargado) se purgan aquí.
     */
    public static boolean isProtected(Level level, BlockPos pos) {
        if (!ElSilenteConfig.COMMON.saltEnabled.get()) {
            return false;
        }
        Set<BlockPos> set = SALTS.get(level.dimension());
        if (set == null || set.isEmpty()) {
            return false;
        }
        double r2 = RADIUS * RADIUS;
        double px = pos.getX() + 0.5D;
        double py = pos.getY() + 0.5D;
        double pz = pos.getZ() + 0.5D;

        Iterator<BlockPos> iterator = set.iterator();
        while (iterator.hasNext()) {
            BlockPos salt = iterator.next();
            if (!level.isLoaded(salt)) {
                continue;
            }
            BlockState state = level.getBlockState(salt);
            if (!(state.getBlock() instanceof LineaSalBlock)) {
                // El bloque ya no es sal: la entrada caduco.
                iterator.remove();
                continue;
            }
            double dy = Math.abs(py - (salt.getY() + 0.5D));
            if (dy > VERTICAL) {
                continue;
            }
            double dx = px - (salt.getX() + 0.5D);
            double dz = pz - (salt.getZ() + 0.5D);
            if (dx * dx + dz * dz <= r2) {
                return true;
            }
        }
        return false;
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        SALTS.clear();
    }
}
