package com.elsilente.event;

import com.elsilente.ElSilenteMod;
import com.elsilente.entity.UmbralEntity;
import com.elsilente.registry.ModEntities;
import com.elsilente.registry.ModItems;
import com.elsilente.registry.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ThrownEnderpearl;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CandleBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.event.entity.ProjectileImpactEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Ritual de invocacion (Fase 2): noche y MEDIANOCHE EXACTA (ventana de +/-600
 * ticks sobre las 18000) + perla de ender lanzada al centro de soul soil
 * rodeado de 8 obsidianas con velas encendidas en las cardinales, con un
 * CORAZON SILENTE empuñado. Invoca al jefe EL UMBRAL y consume el corazon.
 */
@Mod.EventBusSubscriber(modid = ElSilenteMod.MODID)
public class RitualHandler {

    private static final BlockPos[] CARDINALS = {
            new BlockPos(0, 0, -1), new BlockPos(0, 0, 1),
            new BlockPos(1, 0, 0), new BlockPos(-1, 0, 0)
    };

    @SubscribeEvent
    public static void onProjectileImpact(ProjectileImpactEvent event) {
        if (!(event.getProjectile() instanceof ThrownEnderpearl pearl)) {
            return;
        }
        if (!(pearl.level() instanceof ServerLevel level)) {
            return;
        }
        if (!level.isNight()) {
            return;
        }
        // Fase 2: medianoche exacta (ventana de +/-600 ticks sobre las 18000).
        long hora = level.getDayTime() % 24000L;
        if (hora < 17400L || hora > 18600L) {
            return;
        }
        // El invocador debe empuñar un Corazón Silente.
        if (!(pearl.getOwner() instanceof ServerPlayer owner)
                || !owner.getMainHandItem().is(ModItems.CORAZON_SILENTE.get())) {
            return;
        }
        HitResult hit = event.getRayTraceResult();
        if (!(hit instanceof BlockHitResult blockHit)) {
            return;
        }
        BlockPos impact = blockHit.getBlockPos();
        BlockPos center = findRitualCenter(level, impact);
        if (center == null || !isValidRitual(level, center)) {
            return;
        }

        event.setCanceled(true);
        pearl.discard();

        ElSilenteMod.LOGGER.info("[El Silente] Ritual de invocación completado en {} por {}",
                center, pearl.getOwner() instanceof ServerPlayer p ? p.getGameProfile().getName() : "desconocido");

        level.playSound(null, center, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.WEATHER, 1.0F, 1.0F);
        level.sendParticles(ParticleTypes.FLASH, center.getX() + 0.5D, center.getY() + 1.0D,
                center.getZ() + 0.5D, 8, 0.0D, 0.0D, 0.0D, 0.0D);
        level.sendParticles(ParticleTypes.LARGE_SMOKE, center.getX() + 0.5D, center.getY() + 1.0D,
                center.getZ() + 0.5D, 60, 0.6D, 0.6D, 0.6D, 0.03D);

        for (BlockPos offset : CARDINALS) {
            // Las velas van SOBRE la obsidiana de las posiciones cardinales.
            // (Antes se comprobaba en el mismo nivel que la obsidiana, lo que
            // hacía imposible que el ritual se validara nunca.)
            BlockPos candlePos = center.offset(offset).above();
            BlockState state = level.getBlockState(candlePos);
            if (state.getBlock() instanceof CandleBlock && state.hasProperty(BlockStateProperties.LIT)) {
                level.setBlock(candlePos, state.setValue(BlockStateProperties.LIT, false), 3);
            }
        }

        Player summoner = pearl.getOwner() instanceof ServerPlayer serverPlayer ? serverPlayer
                : level.getNearestPlayer(center.getX(), center.getY(), center.getZ(), 16.0D, false);

        // El Corazón Silente se consume en la invocación.
        if (owner.getMainHandItem().is(ModItems.CORAZON_SILENTE.get())) {
            owner.getMainHandItem().shrink(1);
        }

        UmbralEntity boss = ModEntities.UMBRAL.get().create(level);
        if (boss == null) {
            return;
        }
        boss.moveTo(center.getX() + 0.5D, center.getY() + 1.0D, center.getZ() + 0.5D, 0.0F, 0.0F);
        level.addFreshEntity(boss);
        level.playSound(null, center, ModSounds.UMBRAL_ROAR.get(), SoundSource.HOSTILE, 1.5F, 1.0F);
        if (summoner != null) {
            boss.setTarget(summoner);
        }
    }

    private static BlockPos findRitualCenter(ServerLevel level, BlockPos impact) {
        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;
        for (int dx = -4; dx <= 4; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -4; dz <= 4; dz++) {
                    BlockPos candidate = impact.offset(dx, dy, dz);
                    if (!level.getBlockState(candidate).is(Blocks.SOUL_SOIL)) {
                        continue;
                    }
                    double dist = candidate.distSqr(impact);
                    if (dist <= 16.0D && dist < bestDist) {
                        bestDist = dist;
                        best = candidate;
                    }
                }
            }
        }
        return best;
    }

    private static boolean isValidRitual(ServerLevel level, BlockPos center) {
        if (!level.getBlockState(center).is(Blocks.SOUL_SOIL)) {
            return false;
        }
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dz == 0) {
                    continue;
                }
                if (!level.getBlockState(center.offset(dx, 0, dz)).is(Blocks.OBSIDIAN)) {
                    return false;
                }
            }
        }
        for (BlockPos offset : CARDINALS) {
            BlockState state = level.getBlockState(center.offset(offset).above());
            if (!(state.getBlock() instanceof CandleBlock)) {
                return false;
            }
            if (!state.hasProperty(BlockStateProperties.LIT) || !state.getValue(BlockStateProperties.LIT)) {
                return false;
            }
        }
        return true;
    }
}
