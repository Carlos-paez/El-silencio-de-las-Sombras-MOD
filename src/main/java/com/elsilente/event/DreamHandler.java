package com.elsilente.event;

import com.elsilente.ElSilenteMod;
import com.elsilente.client.ClientNightmare;
import com.elsilente.entity.SombraDebilEntity;
import com.elsilente.network.ModNetwork;
import com.elsilente.network.NightmareFlashPacket;
import com.elsilente.registry.ModSounds;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.player.PlayerSleepInBedEvent;
import net.minecraftforge.event.entity.player.PlayerWakeUpEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Mecanica de pesadilla de las Sombras Debiles.
 *
 * Si un jugador se duerme con una Sombra Debil cerca (8 bloques), al despertar
 * le queda la MITAD de su vida MAXIMA. El aviso se registra al ACOSTARSE
 * (PlayerSleepInBedEvent) y se aplica al DESPERTAR (PlayerWakeUpEvent): asi no
 * importa que la sombra se disipe con la luz del amanecer mientras el mundo
 * salta de noche a día.
 */
@Mod.EventBusSubscriber(modid = ElSilenteMod.MODID)
public class DreamHandler {

    /** true si al acostarse habia una Sombra Debil en radio de 8 bloques. */
    private static final Map<UUID, Boolean> THREAT_WHILE_SLEEPING = new HashMap<>();

    @SubscribeEvent
    public static void onSleepInBed(PlayerSleepInBedEvent event) {
        Player player = event.getEntity();
        if (player.level().isClientSide) {
            return;
        }
        boolean threat = !player.level()
                .getEntitiesOfClass(SombraDebilEntity.class, player.getBoundingBox().inflate(8.0D))
                .isEmpty();
        THREAT_WHILE_SLEEPING.put(player.getUUID(), threat);
    }

    @SubscribeEvent
    public static void onWakeUp(PlayerWakeUpEvent event) {
        Player player = event.getEntity();
        if (player.level().isClientSide) {
            return;
        }
        Boolean threat = THREAT_WHILE_SLEEPING.remove(player.getUUID());
        if (threat == null || !player.isAlive() || player.isCreative() || player.isSpectator()) {
            return;
        }
        if (!threat) {
            // Dormir sin incidentes calma la presencia del mundo de las sombras.
            if (player.getHealth() >= player.getMaxHealth() && player instanceof ServerPlayer serverPlayer) {
                PresenceHandler.lower(serverPlayer, 10);
            }
            return;
        }

        // Mitad de la vida MAXIMA (no de la actual).
        player.setHealth(player.getMaxHealth() / 2.0F);

        if (player.level() instanceof ServerLevel server) {
            server.sendParticles(ParticleTypes.LARGE_SMOKE, player.getX(), player.getY() + 1.0D,
                    player.getZ(), 40, 0.6D, 1.2D, 0.6D, 0.03D);
            server.playSound(null, player.blockPosition(), ModSounds.NIGHTMARE.get(),
                    SoundSource.HOSTILE, 1.0F, 0.9F);
        }
        player.displayClientMessage(Component.translatable("elsilente.message.nightmare")
                .withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.ITALIC), false);
        if (player instanceof ServerPlayer serverPlayer) {
            // Overlay de alucinacion en el cliente (frame de niebla + fundido).
            ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> serverPlayer),
                    new NightmareFlashPacket(ClientNightmare.DEFAULT_DURATION));
            PresenceHandler.raise(serverPlayer, 20);
        }
    }
}
