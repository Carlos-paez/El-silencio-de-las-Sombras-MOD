package com.elsilente.item;

import com.elsilente.entity.SombraDebilEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * LAMPARA DEL PENITENTE (elsilente:lampara_penitente).
 *
 * Mientras la lleves en el inventario repele (aleja) a las Sombras Debiles
 * de un radio de 8 bloques: no las mata, solo las mantiene fuera.
 *
 * Destello activo (§14, clic derecho): con 15 s de enfriamiento empuja con
 * fuerza todas las Sombras a 12 bloques con un fogonazo de luz.
 */
public class LamparaPenitenteItem extends Item {

    private static final double RADIO = 8.0D;
    /** Radio del destello activo (bloques). */
    private static final double DESTELLO_RADIO = 12.0D;
    /** Enfriamiento del destello (15 s). */
    private static final long DESTELLO_COOLDOWN = 300L;

    private static final Map<UUID, Long> NEXT_FLASH = new HashMap<>();

    public LamparaPenitenteItem(Properties properties) {
        super(properties);
    }

    // -------------------------------------------------------------------------
    // Destello activo (clic derecho).
    // -------------------------------------------------------------------------

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide) {
            return InteractionResultHolder.success(stack);
        }
        long now = level.getGameTime();
        Long next = NEXT_FLASH.get(player.getUUID());
        if (next != null && now < next) {
            // Aun recargando: la lampara parpadea sin fuerza.
            level.playSound(null, player.blockPosition(), SoundEvents.LANTERN_PLACE,
                    SoundSource.PLAYERS, 0.3F, 1.6F);
            return InteractionResultHolder.fail(stack);
        }
        NEXT_FLASH.put(player.getUUID(), now + DESTELLO_COOLDOWN);

        int empujadas = 0;
        for (SombraDebilEntity sombra : level.getEntitiesOfClass(SombraDebilEntity.class,
                player.getBoundingBox().inflate(DESTELLO_RADIO))) {
            Vec3 away = sombra.position().subtract(player.position());
            double horizontal = Math.max(1.0E-4D, Math.sqrt(away.x * away.x + away.z * away.z));
            sombra.knockback(1.8D, away.x / horizontal, away.z / horizontal);
            empujadas++;
            if (level instanceof ServerLevel server) {
                server.sendParticles(ParticleTypes.SOUL, sombra.getX(), sombra.getY() + 0.6D,
                        sombra.getZ(), 8, 0.3D, 0.4D, 0.3D, 0.01D);
            }
        }

        level.playSound(null, player.blockPosition(), SoundEvents.LANTERN_PLACE,
                SoundSource.PLAYERS, 1.0F, 1.1F);
        if (level instanceof ServerLevel server) {
            server.sendParticles(ParticleTypes.FLASH, player.getX(), player.getY() + 1.0D,
                    player.getZ(), 1, 0.0D, 0.0D, 0.0D, 0.0D);
            server.sendParticles(ParticleTypes.SOUL, player.getX(), player.getY() + 1.0D,
                    player.getZ(), 20, 1.0D, 0.6D, 1.0D, 0.02D);
        }
        if (empujadas > 0) {
            player.displayClientMessage(Component.translatable("elsilente.message.lampara.flash", empujadas)
                    .withStyle(ChatFormatting.AQUA, ChatFormatting.ITALIC), true);
        }
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
        super.inventoryTick(stack, level, entity, slot, selected);
        if (level.isClientSide || !(entity instanceof Player player)) {
            return;
        }
        // Una comprobación por segundo (el Item es compartido: nada de estado propio).
        if (player.tickCount % 20 != 0) {
            return;
        }

        List<SombraDebilEntity> cercanas = level.getEntitiesOfClass(SombraDebilEntity.class,
                player.getBoundingBox().inflate(RADIO));
        for (SombraDebilEntity sombra : cercanas) {
            Vec3 away = sombra.position().subtract(player.position());
            double horizontal = Math.max(1.0E-4D, Math.sqrt(away.x * away.x + away.z * away.z));
            sombra.knockback(0.6D, away.x / horizontal, away.z / horizontal);
            if (level instanceof ServerLevel server) {
                server.sendParticles(ParticleTypes.SOUL, sombra.getX(), sombra.getY() + 0.6D, sombra.getZ(),
                        4, 0.2D, 0.2D, 0.2D, 0.0D);
            }
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("elsilente.tooltip.lampara")
                .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        tooltip.add(Component.translatable("elsilente.tooltip.lampara.flash")
                .withStyle(ChatFormatting.DARK_AQUA));
    }
}
