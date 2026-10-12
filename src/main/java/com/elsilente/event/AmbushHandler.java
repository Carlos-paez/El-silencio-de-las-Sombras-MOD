package com.elsilente.event;

import com.elsilente.ElSilenteMod;
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

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        MinecraftServer server = event.getServer();
        processPendingSpawns(server);

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
            if (now - lastAmbush < 12000L) {
                continue;
            }
            if (player.getRandom().nextFloat() > 0.20F) {
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
