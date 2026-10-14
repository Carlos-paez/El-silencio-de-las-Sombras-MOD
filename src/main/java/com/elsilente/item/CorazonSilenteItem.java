package com.elsilente.item;

import com.elsilente.entity.ElSilenteEntity;
import com.elsilente.event.PresenceHandler;
import com.elsilente.registry.ModSounds;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * CORAZON SILENTE (§14 de MEJORAS.md): uso activo riesgo/recompensa.
 *
 * Clic derecho: recupera 3 corazones (6 HP) consumiendo el Corazon...
 * pero es SU corazon: sube la presencia +25 y programa la manifestacion de
 * un El Silente cerca en 60-150 s (PENDING_SPAWNS; de dia sin oscuridad no
 * aparece, asi que el riesgo es real solo de noche).
 *
 * Enfriamiento de 5 min por jugador. Sigue siendo el ingrediente del ritual
 * y la recarga del Simbolo del Alba.
 */
public class CorazonSilenteItem extends Item {

    /** Curacion por uso (3 corazones = 6 HP). */
    private static final float HEAL_AMOUNT = 6.0F;
    /** Enfriamiento por jugador (5 min). */
    private static final long USE_COOLDOWN = 6000L;

    private static final Map<UUID, Long> NEXT_USE = new HashMap<>();

    public CorazonSilenteItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide) {
            return InteractionResultHolder.success(stack);
        }
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResultHolder.pass(stack);
        }

        long now = level.getGameTime();
        Long next = NEXT_USE.get(player.getUUID());
        if (next != null && now < next) {
            player.displayClientMessage(Component.translatable("elsilente.message.heart.cooldown")
                    .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC), true);
            return InteractionResultHolder.fail(stack);
        }
        if (player.getHealth() >= player.getMaxHealth()) {
            // Sin heridas no hay razon para abrir el corazon.
            return InteractionResultHolder.pass(stack);
        }

        NEXT_USE.put(player.getUUID(), now + USE_COOLDOWN);
        if (!player.getAbilities().invulnerable) {
            stack.shrink(1);
        }

        // Recompensa: 3 corazones de vida.
        player.heal(HEAL_AMOUNT);

        // Coste: su corazon llama. Presencia +25 y una manifestacion cerca.
        PresenceHandler.raise(serverPlayer, 25);
        ElSilenteEntity.PENDING_SPAWNS.add(new ElSilenteEntity.PendingSpawn(player.getUUID(),
                now + 1200L + level.getRandom().nextInt(1800)));

        // Latido estridente + susurro de respuesta.
        level.playSound(null, player.blockPosition(), ModSounds.HEARTBEAT.get(),
                SoundSource.PLAYERS, 1.0F, 1.6F);
        level.playSound(null, player.blockPosition(), ModSounds.WHISPER.get(),
                SoundSource.HOSTILE, 0.9F, 0.7F);
        if (level instanceof ServerLevel server) {
            server.sendParticles(ParticleTypes.HEART, player.getX(), player.getY() + 1.4D,
                    player.getZ(), 8, 0.3D, 0.5D, 0.3D, 0.1D);
            server.sendParticles(ParticleTypes.SOUL, player.getX(), player.getY() + 1.0D,
                    player.getZ(), 24, 0.5D, 0.8D, 0.5D, 0.02D);
        }
        player.displayClientMessage(Component.translatable("elsilente.message.heart.used")
                .withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.ITALIC), false);
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("elsilente.tooltip.corazon.use")
                .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        tooltip.add(Component.translatable("elsilente.tooltip.corazon.cost")
                .withStyle(ChatFormatting.DARK_GRAY));
    }
}
