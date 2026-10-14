package com.elsilente.event;

import com.elsilente.ElSilenteMod;
import com.elsilente.config.ElSilenteConfig;
import com.elsilente.entity.ElSilenteEntity;
import com.elsilente.registry.ModEntities;
import com.elsilente.registry.ModSounds;
import com.elsilente.util.Spawns;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * Emboscadas sorpresa por jugador y procesamiento de reapariciones programadas
 * tras un desvanecimiento.
 */
@Mod.EventBusSubscriber(modid = ElSilenteMod.MODID)
public class AmbushHandler {

    private static final Map<UUID, Long> NEXT_CHECK = new HashMap<>();
    private static final Map<UUID, Long> NEXT_AMBUSH = new HashMap<>();

    /**
     * Emboscadas silenciadas por jugador hasta el proximo amanecer (Campana
     * de vigilia, §7): la campana calla la noche... pero atrae sombras.
     */
    private static final Map<UUID, Long> AMBUSH_SILENCED_UNTIL = new HashMap<>();

    /** Cancela la emboscada de esta noche para el jugador (llamado por la Campana). */
    public static void silenceAmbush(ServerPlayer player) {
        long now = player.level().getGameTime();
        long hour = player.level().getDayTime() % 24000L;
        // Hasta el proximo amanecer del ciclo (mas un margen de 600 t).
        long remaining = 24000L - hour + 600L;
        AMBUSH_SILENCED_UNTIL.put(player.getUUID(), now + remaining);
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        MinecraftServer server = event.getServer();
        processPendingSpawns(server);

        if (!ElSilenteConfig.COMMON.ambushEnabled.get()) {
            return;
        }
        float chance = ElSilenteConfig.COMMON.ambushChance.get().floatValue();
        long cooldown = ElSilenteConfig.COMMON.ambushCooldownTicks.get().longValue();

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.isSpectator()) {
                continue;
            }
            ServerLevel level = player.serverLevel();
            long now = level.getGameTime();
            long next = NEXT_CHECK.computeIfAbsent(player.getUUID(),
                    k -> now + 6000L + player.getRandom().nextInt(3600));
            if (now < next) {
                continue;
            }
            NEXT_CHECK.put(player.getUUID(), now + 6000L + player.getRandom().nextInt(3600));

            long lastAmbush = NEXT_AMBUSH.getOrDefault(player.getUUID(), Long.MIN_VALUE / 2);
            if (now - lastAmbush < cooldown) {
                continue;
            }
            // La Campana de vigilia puede haber callado la emboscada esta noche.
            long silenced = AMBUSH_SILENCED_UNTIL.getOrDefault(player.getUUID(), 0L);
            if (now < silenced) {
                continue;
            }
            AMBUSH_SILENCED_UNTIL.remove(player.getUUID());
            // La presencia acumulada aumenta la probabilidad de emboscada (+50% al maximo).
            float presenceBoost = 1.0F + (PresenceHandler.getLevel(player) / (float) PresenceHandler.MAX) * 0.5F;
            if (player.getRandom().nextFloat() > Math.min(1.0F, chance * presenceBoost)) {
                continue;
            }

            boolean night = level.isNight();
            BlockPos pos = Spawns.findDarkSpot(level, player, 8, 12, 40, true);
            if (pos == null) {
                continue;
            }
            if (!night && level.getMaxLocalRawBrightness(pos) > 4) {
                continue;
            }

            ElSilenteEntity entity = ModEntities.EL_SILENTE.get().create(level);
            if (entity == null) {
                continue;
            }
            entity.moveTo(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D, player.getYRot(), 0.0F);
            level.addFreshEntity(entity);
            entity.startChase(player);
            PresenceHandler.raise(player, 20);
            level.playSound(null, pos, ModSounds.SCREAM.get(), SoundSource.HOSTILE, 1.5F, 1.0F);
            level.sendParticles(ParticleTypes.LARGE_SMOKE, pos.getX() + 0.5D, pos.getY() + 1.0D,
                    pos.getZ() + 0.5D, 30, 0.4D, 0.8D, 0.4D, 0.02D);
            NEXT_AMBUSH.put(player.getUUID(), now);
        }
    }

    private static void processPendingSpawns(MinecraftServer server) {
        Iterator<ElSilenteEntity.PendingSpawn> iterator = ElSilenteEntity.PENDING_SPAWNS.iterator();
        while (iterator.hasNext()) {
            ElSilenteEntity.PendingSpawn pending = iterator.next();
            ServerPlayer player = server.getPlayerList().getPlayer(pending.playerId);
            if (player == null) {
                iterator.remove();
                continue;
            }
            ServerLevel level = player.serverLevel();
            if (level.getGameTime() < pending.at) {
                continue;
            }
            iterator.remove();
            BlockPos pos = Spawns.findDarkSpot(level, player, 20, 30, 40, true);
            if (pos == null) {
                pos = Spawns.findDarkSpot(level, player, 12, 30, 40, true);
            }
            if (pos == null) {
                continue;
            }
            ElSilenteEntity entity = ModEntities.EL_SILENTE.get().create(level);
            if (entity == null) {
                continue;
            }
            entity.moveTo(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D, player.getYRot(), 0.0F);
            level.addFreshEntity(entity);
            level.playSound(null, pos, ModSounds.WHISPER.get(), SoundSource.HOSTILE, 0.5F, 0.9F);
        }
    }
}
