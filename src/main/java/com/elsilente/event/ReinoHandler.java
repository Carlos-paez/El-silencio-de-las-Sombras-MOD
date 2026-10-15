package com.elsilente.event;

import com.elsilente.ElSilenteMod;
import com.elsilente.config.ElSilenteConfig;
import com.elsilente.entity.UmbralEntity;
import com.elsilente.registry.ModBlocks;
import com.elsilente.registry.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.portal.PortalInfo;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.ITeleporter;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.function.Function;

/**
 * REINO DE LAS SOMBRAS (§13 de MEJORAS.md).
 *
 * La derrota del Umbral abre una «Grieta del Umbral»: un portal a una
 * dimension propia (elsilente:reino_sombras,datapack en data/elsilente/).
 * El reino es la fuente de los Corazones Silente y hogar de los Silentes
 * Verdaderos, la version «verdadera» del jefe.
 *
 * Al cruzar se guarda la posicion de retorno del jugador (NBT persistente)
 * y se coloca una Grieta de vuelta junto al punto de llegada.
 */
@Mod.EventBusSubscriber(modid = ElSilenteMod.MODID)
public final class ReinoHandler {

    /** Identificador de la dimension (debe coincidir con data/elsilente/dimension/). */
    public static final ResourceLocation REINO_ID = new ResourceLocation(ElSilenteMod.MODID, "reino_sombras");
    public static final ResourceKey<Level> REINO =
            ResourceKey.create(Registries.DIMENSION, REINO_ID);

    private static final String TAG_COOLDOWN = "elsilentePortalCooldown";
    private static final String TAG_RETURN_DIM = "elsilenteReturnDim";
    private static final String TAG_RETURN_POS = "elsilenteReturnPos";
    /** Enfriamiento entre cruces: evita el rebote dentro de la grieta. */
    private static final long PORTAL_COOLDOWN_TICKS = 200L;

    private ReinoHandler() {
    }

    // ---------------------------------------------------------------------------
    // Apertura del portal: la muerte del Umbral.
    // ---------------------------------------------------------------------------

    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        if (event.getEntity().level().isClientSide) {
            return;
        }
        if (!(event.getEntity() instanceof UmbralEntity)) {
            return;
        }
        if (!ElSilenteConfig.COMMON.realmEnabled.get()) {
            return;
        }
        if (!(event.getEntity().level() instanceof ServerLevel level)) {
            return;
        }
        if (level.dimension() == REINO) {
            return;
        }
        openPortal(level, event.getEntity().blockPosition());
    }

    /** Abre la Grieta del Umbral junto al lugar donde cayo el jefe. */
    private static void openPortal(ServerLevel level, BlockPos deathPos) {
        BlockPos base = findPortalBase(level, deathPos);
        if (base == null) {
            ElSilenteMod.LOGGER.warn("[El Silente] No se pudo abrir la grieta cerca de {}", deathPos);
            return;
        }
        level.setBlock(base, ModBlocks.PORTAL_UMBRAL.get().defaultBlockState(), 3);
        level.setBlock(base.above(), ModBlocks.PORTAL_UMBRAL.get().defaultBlockState(), 3);
        level.playSound(null, base, ModSounds.UMBRAL_SUMMON.get(), SoundSource.HOSTILE, 1.4F, 0.7F);
        level.sendParticles(ParticleTypes.LARGE_SMOKE, base.getX() + 0.5D, base.getY() + 0.5D,
                base.getZ() + 0.5D, 40, 0.5D, 0.8D, 0.5D, 0.02D);
        level.sendParticles(ParticleTypes.SOUL, base.getX() + 0.5D, base.getY() + 0.5D,
                base.getZ() + 0.5D, 24, 0.3D, 0.6D, 0.3D, 0.01D);
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(base.getX() + 0.5D, base.getY(), base.getZ() + 0.5D) < 64.0D * 64.0D) {
                player.displayClientMessage(Component.translatable("elsilente.message.realm.opened"), false);
            }
        }
        ElSilenteMod.LOGGER.info("[El Silente] Grieta del Umbral abierta en {} (reino de sombras)", base);
    }

    /** Columna con suelo solido y aire encima, cerca de donde cayo el jefe. */
    private static BlockPos findPortalBase(ServerLevel level, BlockPos deathPos) {
        BlockPos start = new BlockPos(deathPos.getX(), level.getHeight(Heightmap.Types.MOTION_BLOCKING,
                deathPos.getX(), deathPos.getZ()), deathPos.getZ());
        for (int dy = -2; dy <= 2; dy++) {
            BlockPos candidate = start.offset(0, dy, 0);
            if (level.getBlockState(candidate).getCollisionShape(level, candidate).isEmpty()
                    && level.getBlockState(candidate.above()).getCollisionShape(level, candidate.above()).isEmpty()
                    && !level.getBlockState(candidate.below()).getCollisionShape(level, candidate.below()).isEmpty()) {
                return candidate;
            }
        }
        return start;
    }

    // ---------------------------------------------------------------------------
    // Cruce: Overworld <-> Reino.
    // ---------------------------------------------------------------------------

    /** Llamar desde PortalUmbralBlock al pisar la grieta estando fuera del reino. */
    public static void tryEnter(ServerPlayer player) {
        if (onCooldown(player)) {
            return;
        }
        ServerLevel reino = player.server.getLevel(REINO);
        if (reino == null) {
            player.displayClientMessage(Component.translatable("elsilente.message.realm.no_return"), false);
            return;
        }
        // Guarda la posicion de vuelta (dimension actual + bloque).
        player.getPersistentData().putString(TAG_RETURN_DIM,
                player.level().dimension().location().toString());
        player.getPersistentData().putLongArray(TAG_RETURN_POS, new long[]{
                player.blockPosition().getX(), player.blockPosition().getY(), player.blockPosition().getZ()});

        BlockPos arrival = safeArrival(reino, player.blockPosition().getX(), player.blockPosition().getZ());

        teleport(player, reino, arrival);
        placeReturnPortal(reino, arrival);
        player.displayClientMessage(Component.translatable("elsilente.message.realm.welcome"), true);
        ElSilenteMod.LOGGER.info("[El Silente] {} cruza al reino de sombras en {}", player.getScoreboardName(), arrival);
    }

    /** Llamar desde PortalUmbralBlock al pisar una grieta dentro del reino. */
    public static void tryExit(ServerPlayer player) {
        if (onCooldown(player)) {
            return;
        }
        ServerLevel back = resolveReturnLevel(player);
        if (back == null) {
            return;
        }
        BlockPos dest = resolveReturnPos(player, back);
        teleport(player, back, dest);
        ElSilenteMod.LOGGER.info("[El Silente] {} vuelve del reino de sombras en {}", player.getScoreboardName(), dest);
    }

    /**
     * Posicion segura de llegada al reino.
     *
     * El reino es una dimension nueva: al cruzar por primera vez sus chunks
     * ainda no existen. Si teletransportamos al jugador usando solo el
     * heightmap sin garantizar terreno, cae al vacio (no hay chunk cargado) y
     * muere. Por eso:
     *   1. Pre-generamos los chunks alrededor del punto de llegada.
     *   2. Recalculamos el Y sobre terreno ya generado.
     *   3. Si aun asi no hay suelo solido debajo, coloca una plataforma de
     *      obsidiana para que nunca haya vacio bajo los pies.
     */
    private static BlockPos safeArrival(ServerLevel level, int x, int z) {
        // Fuerza la generacion de los 9 chunks (3x3) alrededor del destino.
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                level.getChunk((x >> 4) + dx, (z >> 4) + dz);
            }
        }
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
        BlockPos arrival = new BlockPos(x, y, z);
        // Sin suelo solido debajo -> plataforma de obsidiana (nunca vacio).
        if (level.getBlockState(arrival.below()).getCollisionShape(level, arrival.below()).isEmpty()) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    BlockPos floor = arrival.below().offset(dx, 0, dz);
                    level.setBlock(floor, Blocks.OBSIDIAN.defaultBlockState(), 3);
                }
            }
        }
        return arrival;
    }

    /** Coloca una Grieta de vuelta (2 bloques) al lado del punto de llegada. */
    private static void placeReturnPortal(ServerLevel level, BlockPos arrival) {
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            BlockPos side = arrival.relative(direction);
            if (level.getBlockState(side).getCollisionShape(level, side).isEmpty()
                    && level.getBlockState(side.above()).getCollisionShape(level, side.above()).isEmpty()) {
                level.setBlock(side, ModBlocks.PORTAL_UMBRAL.get().defaultBlockState(), 3);
                level.setBlock(side.above(), ModBlocks.PORTAL_UMBRAL.get().defaultBlockState(), 3);
                return;
            }
        }
    }

    private static ServerLevel resolveReturnLevel(ServerPlayer player) {
        String dim = player.getPersistentData().getString(TAG_RETURN_DIM);
        if (!dim.isEmpty()) {
            try {
                ServerLevel level = player.server.getLevel(
                        ResourceKey.create(Registries.DIMENSION, new ResourceLocation(dim)));
                if (level != null) {
                    return level;
                }
            } catch (RuntimeException ignored) {
                // Dimension invalida en el NBT: cae al Overworld.
            }
        }
        return player.server.overworld();
    }

    private static BlockPos resolveReturnPos(ServerPlayer player, ServerLevel level) {
        long[] pos = player.getPersistentData().getLongArray(TAG_RETURN_POS);
        if (pos.length == 3) {
            BlockPos saved = new BlockPos((int) pos[0], (int) pos[1], (int) pos[2]);
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, saved.getX(), saved.getZ());
            return new BlockPos(saved.getX(), y, saved.getZ());
        }
        return level.getSharedSpawnPos();
    }

    private static boolean onCooldown(ServerPlayer player) {
        long last = player.getPersistentData().getLong(TAG_COOLDOWN);
        return player.level().getGameTime() - last < PORTAL_COOLDOWN_TICKS;
    }

    /** Teletransporta al jugador respetando rotacion, sin sonido de portal vanilla. */
    private static void teleport(ServerPlayer player, ServerLevel dest, BlockPos destPos) {
        player.getPersistentData().putLong(TAG_COOLDOWN, player.level().getGameTime());
        ITeleporter teleporter = new ITeleporter() {
            @Override
            public PortalInfo getPortalInfo(Entity entity, ServerLevel level,
                                            Function<ServerLevel, PortalInfo> portalSupplier) {
                return new PortalInfo(Vec3.atBottomCenterOf(destPos), Vec3.ZERO,
                        entity.getYRot(), entity.getXRot());
            }

            @Override
            public boolean playTeleportSound(ServerPlayer p, ServerLevel currentLevel, ServerLevel destLevel) {
                return false;
            }
        };        // changeDimension puede devolver una instancia NUEVA del jugador; usamos
        // la devuelta para reiniciar la caida y el movimiento residual, evitando
        // danio de caida/vacio al entrar al reino.
        Entity teleported = player.changeDimension(dest, teleporter);
        if (teleported != null) {
            teleported.fallDistance = 0.0F;
            teleported.setDeltaMovement(Vec3.ZERO);
        }
        dest.sendParticles(ParticleTypes.SOUL, destPos.getX() + 0.5D, destPos.getY() + 0.5D, destPos.getZ() + 0.5D, 16, 0.3D, 0.5D, 0.3D, 0.02D);
    }
}
