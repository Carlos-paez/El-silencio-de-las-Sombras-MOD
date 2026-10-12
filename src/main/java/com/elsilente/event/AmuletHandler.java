package com.elsilente.event;

import com.elsilente.ElSilenteMod;
import com.elsilente.entity.ElSilenteEntity;
import com.elsilente.registry.ModItems;
import com.elsilente.registry.ModSounds;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * El Amuleto del Latido reproduce el sonido EN LA POSICION del monstruo
 * (posicional, direccional) y con intervalos mas cortos cuanto mas cerca esta.
 */
@Mod.EventBusSubscriber(modid = ElSilenteMod.MODID)
public class AmuletHandler {

    private static final Map<UUID, Integer> TIMERS = new HashMap<>();

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Player player = event.player;
        if (player.level().isClientSide) {
            return;
        }
        if (!hasAmulet(player)) {
            TIMERS.remove(player.getUUID());
            return;
        }

        Integer timer = TIMERS.get(player.getUUID());
        if (timer == null) {
            timer = 20;
        }
        if (--timer > 0) {
            TIMERS.put(player.getUUID(), timer);
            return;
        }

        ElSilenteEntity nearest = findNearest(player);
        if (nearest == null) {
            TIMERS.put(player.getUUID(), 40);
            return;
        }

        double dist = Math.sqrt(nearest.distanceToSqr(player));
        int interval;
        float volume;
        float pitch;
        if (dist >= 24.0D) {
            interval = 120;
            volume = 0.25F;
            pitch = 0.9F;
        } else if (dist >= 14.0D) {
            interval = 60;
            volume = 0.45F;
            pitch = 1.0F;
        } else if (dist >= 8.0D) {
            interval = 30;
            volume = 0.7F;
            pitch = 1.0F;
        } else {
            interval = 14;
            volume = 1.0F;
            pitch = 1.2F;
        }

        ServerLevel level = (ServerLevel) player.level();
        level.playSound(null, nearest.blockPosition(), ModSounds.HEARTBEAT.get(),
                SoundSource.HOSTILE, volume, pitch);
        TIMERS.put(player.getUUID(), interval);
    }

    private static ElSilenteEntity findNearest(Player player) {
        List<ElSilenteEntity> nearby = player.level().getEntitiesOfClass(ElSilenteEntity.class,
                player.getBoundingBox().inflate(32.0D));
        ElSilenteEntity nearest = null;
        double best = Double.MAX_VALUE;
        for (ElSilenteEntity entity : nearby) {
            double dist = entity.distanceToSqr(player);
            if (dist < best) {
                best = dist;
                nearest = entity;
            }
        }
        return nearest;
    }

    private static boolean hasAmulet(Player player) {
        if (isAmulet(player.getMainHandItem()) || isAmulet(player.getOffhandItem())) {
            return true;
        }
        for (ItemStack stack : player.getInventory().armor) {
            if (isAmulet(stack)) {
                return true;
            }
        }
        for (ItemStack stack : player.getInventory().items) {
            if (isAmulet(stack)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isAmulet(ItemStack stack) {
        return stack.is(ModItems.AMULETO_LATIDO.get());
    }
}
