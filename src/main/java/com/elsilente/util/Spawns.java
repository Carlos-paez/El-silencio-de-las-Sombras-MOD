package com.elsilente.util;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * Utilidades compartidas para localizar puntos de aparicion oscuros y
 * posiciones validas para cofres.
 */
public final class Spawns {

    private Spawns() {
    }

    /**
     * Busca un punto oscuro en un anillo de distancia alrededor del jugador.
     * Si {@code outsideView} es true, descarta posiciones dentro del cono de vision del jugador.
     */
    public static BlockPos findDarkSpot(ServerLevel level, Player player, int minDist, int maxDist,
                                        int tries, boolean outsideView) {
        RandomSource random = level.getRandom();
        for (int i = 0; i < tries; i++) {
            double angle = random.nextDouble() * Math.PI * 2.0D;
            int dist = minDist + random.nextInt(Math.max(1, maxDist - minDist + 1));
            int dx = (int) Math.round(Math.cos(angle) * dist);
            int dz = (int) Math.round(Math.sin(angle) * dist);
            BlockPos base = player.blockPosition().offset(dx, 0, dz);
            BlockPos surface = level.getHeightmapPos(Heightmap.Types.WORLD_SURFACE, base);

            if (level.getMaxLocalRawBrightness(surface) > 4) {
                continue;
            }
            if (!level.getBlockState(surface).isAir()) {
                continue;
            }
            if (!level.getBlockState(surface.above()).isAir()) {
                continue;
            }
            if (!level.getBlockState(surface.below()).isSolid()) {
                continue;
            }
            if (outsideView && isInView(level, player, surface)) {
                continue;
            }
            return surface;
        }
        return null;
    }

    /** Busca una posicion libre en la superficie para colocar un cofre (preferencia baja luz). */
    public static BlockPos findChestSpot(ServerLevel level, BlockPos origin, int minDist, int maxDist) {
        BlockPos preferred = findChestSpot(level, origin, minDist, maxDist, true);
        if (preferred != null) {
            return preferred;
        }
        return findChestSpot(level, origin, minDist, maxDist, false);
    }

    private static BlockPos findChestSpot(ServerLevel level, BlockPos origin, int minDist, int maxDist,
                                          boolean darkOnly) {
        RandomSource random = level.getRandom();
        for (int i = 0; i < 40; i++) {
            double angle = random.nextDouble() * Math.PI * 2.0D;
            int dist = minDist + random.nextInt(Math.max(1, maxDist - minDist + 1));
            int dx = (int) Math.round(Math.cos(angle) * dist);
            int dz = (int) Math.round(Math.sin(angle) * dist);
            BlockPos base = origin.offset(dx, 0, dz);
            BlockPos surface = level.getHeightmapPos(Heightmap.Types.WORLD_SURFACE, base);

            if (darkOnly && level.getMaxLocalRawBrightness(surface) > 7) {
                continue;
            }
            if (!level.getBlockState(surface).isAir()) {
                continue;
            }
            if (!level.getBlockState(surface.above()).isAir()) {
                continue;
            }
            BlockState below = level.getBlockState(surface.below());
            if (!below.isSolid()) {
                continue;
            }
            return surface;
        }
        return null;
    }

    private static boolean isInView(ServerLevel level, Player player, BlockPos pos) {
        Vec3 look = player.getLookAngle().normalize();
        Vec3 to = Vec3.atCenterOf(pos).subtract(player.getEyePosition()).normalize();
        return look.dot(to) >= 0.7D;
    }
}
