package com.elsilente.item;

import com.elsilente.ElSilenteMod;
import com.elsilente.config.ElSilenteConfig;
import com.elsilente.entity.SombraDebilEntity;
import com.elsilente.event.AmbushHandler;
import com.elsilente.registry.ModEntities;
import com.elsilente.util.Spawns;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraftforge.fml.common.Mod;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * CAMPANA DE VIGILIA (§7 de MEJORAS.md): defensa activa con coste.
 *
 * Al sonar (clic derecho) cancela la emboscada programada para esa noche...
 * pero su bronce llama a las Sombras Debiles: aparecen 1-2 en la oscuridad
 * esa misma noche (trade-off). Enfriamiento de 5 min por jugador.
 */
@Mod.EventBusSubscriber(modid = ElSilenteMod.MODID)
public class CampanaVigiliaItem extends Item {

    /** Enfriamiento por jugador entre toques de campana (5 min). */
    private static final long RING_COOLDOWN = 6000L;

    private static final Map<UUID, Long> NEXT_RING = new HashMap<>();

    public CampanaVigiliaItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide) {
            return InteractionResultHolder.success(stack);
        }
        if (!ElSilenteConfig.COMMON.bellEnabled.get()) {
            return InteractionResultHolder.fail(stack);
        }

        long now = level.getGameTime();
        Long next = NEXT_RING.get(player.getUUID());
        if (next != null && now < next) {
            player.displayClientMessage(Component.translatable("elsilente.message.bell.cooldown")
                    .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC), true);
            level.playSound(null, player.blockPosition(), SoundEvents.BELL_RESONATE,
                    SoundSource.PLAYERS, 0.4F, 0.7F);
            return InteractionResultHolder.fail(stack);
        }
        NEXT_RING.put(player.getUUID(), now + RING_COOLDOWN);

        // Bronce: suena fuerte y se le oye en toda la zona.
        level.playSound(null, player.blockPosition(), SoundEvents.BELL_BLOCK,
                SoundSource.PLAYERS, 1.0F, 0.9F + level.getRandom().nextFloat() * 0.2F);
        if (level instanceof ServerLevel server) {
            server.sendParticles(ParticleTypes.ASH, player.getX(), player.getY() + 1.2D,
                    player.getZ(), 20, 0.5D, 0.6D, 0.5D, 0.02D);
        }

        if (!(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResultHolder.consume(stack);
        }

        // 1) La emboscada programada de esta noche se cancela.
        AmbushHandler.silenceAmbush(serverPlayer);

        // 2) El bronce tambien llama a las Sombras (el coste del poder).
        int spawned = attractShadows(serverPlayer);
        if (spawned > 0) {
            player.displayClientMessage(Component.translatable("elsilente.message.bell.rang", spawned)
                    .withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.ITALIC), false);
        } else {
            player.displayClientMessage(Component.translatable("elsilente.message.bell.rang_quiet")
                    .withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.ITALIC), false);
        }
        return InteractionResultHolder.consume(stack);
    }

    /** Atrae 1-2 Sombras Debiles a 10-24 b, respetando el cupo por jugador. */
    private static int attractShadows(ServerPlayer player) {
        if (!(player.level() instanceof ServerLevel level)) {
            return 0;
        }
        List<SombraDebilEntity> nearby = level.getEntitiesOfClass(SombraDebilEntity.class,
                player.getBoundingBox().inflate(24.0D));
        if (nearby.size() >= ElSilenteConfig.COMMON.maxShadowsPerPlayer.get()) {
            return 0;
        }
        int want = 1 + level.getRandom().nextInt(2);
        int spawned = 0;
        for (int i = 0; i < want; i++) {
            BlockPos pos = Spawns.findDarkSpot(level, player, 10, 24, 32, true);
            if (pos == null) {
                break;
            }
            SombraDebilEntity shadow = ModEntities.SOMBRA_DEBIL.get().create(level);
            if (shadow == null) {
                break;
            }
            shadow.moveTo(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D,
                    player.getYRot(), 0.0F);
            level.addFreshEntity(shadow);
            level.playSound(null, pos, com.elsilente.registry.ModSounds.SHADOW_IDLE.get(),
                    SoundSource.HOSTILE, 0.5F, 1.0F);
            spawned++;
        }
        return spawned;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("elsilente.tooltip.campana")
                .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        tooltip.add(Component.translatable("elsilente.tooltip.campana.cost")
                .withStyle(ChatFormatting.DARK_GRAY));
    }
}
