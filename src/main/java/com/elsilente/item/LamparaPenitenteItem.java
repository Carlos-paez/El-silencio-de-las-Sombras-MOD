package com.elsilente.item;

import com.elsilente.entity.SombraDebilEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.List;

/**
 * LAMPARA DEL PENITENTE (elsilente:lampara_penitente).
 *
 * Mientras la lleves en el inventario repele (aleja) a las Sombras Debiles
 * de un radio de 8 bloques: no las mata, solo las mantiene fuera.
 */
public class LamparaPenitenteItem extends Item {

    private static final double RADIO = 8.0D;

    public LamparaPenitenteItem(Properties properties) {
        super(properties);
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
        tooltip.add(Component.literal("Repele a las Sombras Debiles (8 bloques)")
                .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
    }
}
