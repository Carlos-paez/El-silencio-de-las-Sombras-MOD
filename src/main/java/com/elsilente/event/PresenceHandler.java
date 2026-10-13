package com.elsilente.event;

import com.elsilente.ElSilenteMod;
import com.elsilente.config.ElSilenteConfig;
import com.elsilente.network.ModNetwork;
import com.elsilente.network.PresenceSyncPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Presencia acumulativa por jugador (0-100): cuanto mas te ha notado el mundo
 * de las sombras, mas intensa es la ambientacion (susurros, niebla,
 * alucinaciones -> PresenceFxHandler en cliente).
 *
 * Fuentes:
 *  - Emboscada de El Silente        +20
 *  - Despertar con pesadilla        +20
 *  - Noche en oscuridad (luz <= 7)  +2 cada 10 s
 *  - Luz >= 8 (antorcha, dia)       -2 cada 10 s
 *  - Dormir sin incidentes          -10
 *
 * El nivel se sincroniza al cliente con PresenceSyncPacket.
 */
@Mod.EventBusSubscriber(modid = ElSilenteMod.MODID)
public class PresenceHandler {

    public static final int MAX = 100;

    private static final Map<UUID, Integer> PRESENCE = new HashMap<>();
    private static final Map<UUID, Long> NEXT_EVAL = new HashMap<>();

    /** Intervalo de evaluacion ambiental: 200 ticks (10 s). */
    private static final long EVAL_INTERVAL = 200L;

    private PresenceHandler() {
    }

    public static int getLevel(Player player) {
        return PRESENCE.getOrDefault(player.getUUID(), 0);
    }

    public static void raise(ServerPlayer player, int amount) {
        if (!ElSilenteConfig.COMMON.presenceEnabled.get()) {
            return;
        }
        int value = Math.min(MAX, getLevel(player) + amount);
        PRESENCE.put(player.getUUID(), value);
        sync(player, value);
    }

    public static void lower(ServerPlayer player, int amount) {
        int value = Math.max(0, getLevel(player) - amount);
        PRESENCE.put(player.getUUID(), value);
        sync(player, value);
    }

    private static void sync(ServerPlayer player, int value) {
        ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new PresenceSyncPacket(value));
    }

    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            sync(player, getLevel(player));
        }
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        if (!ElSilenteConfig.COMMON.presenceEnabled.get()) {
            return;
        }
        for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
            if (player.isSpectator() || player.isCreative()) {
                continue;
            }
            long now = player.level().getGameTime();
            long next = NEXT_EVAL.computeIfAbsent(player.getUUID(), k -> now + EVAL_INTERVAL);
            if (now < next) {
                continue;
            }
            NEXT_EVAL.put(player.getUUID(), now + EVAL_INTERVAL);

            int before = getLevel(player);
            int delta = evaluate(player);
            int after = Math.max(0, Math.min(MAX, before + delta));
            if (after != before) {
                PRESENCE.put(player.getUUID(), after);
                sync(player, after);
            }
        }
    }

    /** Evalua el entorno del jugador: +2 oscuridad nocturna, -2 luz, -1 calma. */
    private static int evaluate(ServerPlayer player) {
        BlockPos pos = player.blockPosition();
        int light = player.serverLevel().getMaxLocalRawBrightness(pos);
        if (player.level().isNight() && light <= 7) {
            return 2;
        }
        if (light >= 8) {
            return -2;
        }
        return -1;
    }
}
