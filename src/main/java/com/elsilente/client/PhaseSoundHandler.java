package com.elsilente.client;

import com.elsilente.ElSilenteMod;
import com.elsilente.entity.ElSilenteEntity;
import com.elsilente.registry.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import javax.annotation.Nullable;

/**
 * Música por fases + sonido ambiental procedural (§10 de MEJORAS.md).
 *
 * Todo client-side, coste de servidor cero (El Silente sincroniza su fase con
 * el flag Aggro, asi que el cliente puede detectarla):
 *
 *  - Bucle de persecución (chase_loop, stream): arranca al entrar en CHASE y
 *    se desvanece (fade-out) cuando El Silente desaparece o apaga su aggro.
 *  - Silencio antes del ataque (stalk_silence): a < 10 b en STALK se corta la
 *    ambientación de cueva de vanilla y solo queda un zumbido casi inaudible.
 *  - Cama de cueva (cave_dread): bucle continuo cuyo volumen crece con el
 *    nivel de presencia del jugador.
 */
@Mod.EventBusSubscriber(modid = ElSilenteMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE,
        value = Dist.CLIENT)
public final class PhaseSoundHandler {

    /** Sonido ambiente de cueva de vanilla que se suprime durante el acecho. */
    private static final ResourceLocation AMBIENT_CAVE = new ResourceLocation("minecraft", "ambient.cave");

    @Nullable
    private static ChaseLoopSound chase;
    @Nullable
    private static StalkSilenceSound stalk;
    @Nullable
    private static CaveDreadSound cave;

    private static int tickCounter = 0;

    private PhaseSoundHandler() {
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            chase = null;
            stalk = null;
            cave = null;
            return;
        }
        if (mc.isPaused() || ++tickCounter % 10 != 0) {
            return;
        }

        // ¿Hay algun El Silente por cerca y en que fase esta?
        boolean aggro = false;
        boolean stalkNear = false;
        AABB box = mc.player.getBoundingBox().inflate(64.0D);
        for (ElSilenteEntity silente : mc.level.getEntitiesOfClass(ElSilenteEntity.class, box)) {
            if (silente.isAggro()) {
                aggro = true;
                break;
            }
            if (mc.player.distanceTo(silente) < 10.0F) {
                stalkNear = true;
            }
        }

        // Recicla instancias terminadas.
        if (chase != null && chase.isStopped()) {
            chase = null;
        }
        if (stalk != null && stalk.isStopped()) {
            stalk = null;
        }
        if (cave != null && cave.isStopped()) {
            cave = null;
        }

        if (aggro && chase == null) {
            chase = new ChaseLoopSound();
            mc.getSoundManager().play(chase);
        }
        // Silencio de acecho: solo si NO hay persecucion activa.
        if (stalkNear && !aggro && stalk == null) {
            stalk = new StalkSilenceSound();
            mc.getSoundManager().play(stalk);
        }

        // Cama de cueva: Overworld, a oscuras y (bajo tierra o de noche).
        boolean overworld = mc.level.dimension() == Level.OVERWORLD;
        BlockPos pos = mc.player.blockPosition();
        boolean dark = mc.level.getBrightness(LightLayer.BLOCK, pos) <= 7;
        boolean caveOk = overworld && dark && (pos.getY() < 40 || mc.level.isNight());
        if (caveOk) {
            int bucket = ClientPresence.getLevel() / 25; // 0..4, fija la voz
            if (cave == null) {
                cave = new CaveDreadSound(bucket);
                mc.getSoundManager().play(cave);
            } else if (cave.presenceBucket != bucket) {
                // El volumen fijo por instancia: se reinicia al cambiar de tramo.
                mc.getSoundManager().stop(cave);
                cave = new CaveDreadSound(bucket);
                mc.getSoundManager().play(cave);
            }
        } else if (cave != null) {
            mc.getSoundManager().stop(cave);
            cave = null;
        }
    }

    // -------------------------------------------------------------------------
    // Bucle de persecución: vuelve con el grito y frena al desvanecerse.
    // -------------------------------------------------------------------------

    private static class ChaseLoopSound extends AbstractTickableSoundInstance {
        private int scanCooldown = 0;

        ChaseLoopSound() {
            super(ModSounds.CHASE_LOOP.get(), SoundSource.HOSTILE, RandomSource.create());
            this.relative = true;
            this.looping = true;
            this.attenuation = SoundInstance.Attenuation.NONE;
            this.volume = 0.0F;
            this.pitch = 1.0F;
            this.delay = 0;
        }

        @Override
        public void tick() {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level == null || mc.player == null) {
                stop();
                return;
            }
            boolean aggro;
            if (--scanCooldown <= 0) {
                scanCooldown = 10;
                aggro = false;
                for (ElSilenteEntity silente : mc.level.getEntitiesOfClass(ElSilenteEntity.class,
                        mc.player.getBoundingBox().inflate(64.0D))) {
                    if (silente.isAggro()) {
                        aggro = true;
                        break;
                    }
                }
                this.wasAggro = aggro;
            } else {
                aggro = this.wasAggro;
            }
            if (aggro) {
                this.volume = Math.min(0.75F, this.volume + 0.05F);
            } else {
                // Frena al desvanecerse.
                this.volume -= 0.03F;
                if (this.volume <= 0.03F) {
                    stop();
                }
            }
        }

        private boolean wasAggro = true;
    }

    // -------------------------------------------------------------------------
    // Silencio antes del ataque: corta la ambientación de cueva a < 10 b.
    // -------------------------------------------------------------------------

    private static class StalkSilenceSound extends AbstractTickableSoundInstance {
        private int scanCooldown = 0;
        private int suppressCooldown = 0;
        private boolean near;

        StalkSilenceSound() {
            super(ModSounds.STALK_SILENCE.get(), SoundSource.HOSTILE, RandomSource.create());
            this.relative = true;
            this.looping = true;
            this.attenuation = SoundInstance.Attenuation.NONE;
            this.volume = 0.0F;
            this.pitch = 1.0F;
            this.delay = 0;
        }

        @Override
        public void tick() {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level == null || mc.player == null) {
                stop();
                return;
            }
            if (--scanCooldown <= 0) {
                scanCooldown = 10;
                near = false;
                for (ElSilenteEntity silente : mc.level.getEntitiesOfClass(ElSilenteEntity.class,
                        mc.player.getBoundingBox().inflate(10.0D))) {
                    if (!silente.isAggro()) {
                        near = true;
                        break;
                    }
                }
            }
            if (near) {
                this.volume = Math.min(0.6F, this.volume + 0.04F);
                // Supresión: mantiene callado el ambiente de cueva de vanilla.
                if (--suppressCooldown <= 0) {
                    suppressCooldown = 20;
                    mc.getSoundManager().stop(AMBIENT_CAVE, SoundSource.AMBIENT);
                }
            } else {
                this.volume -= 0.04F;
                if (this.volume <= 0.03F) {
                    stop();
                }
            }
        }
    }

    // -------------------------------------------------------------------------
    // Cama de cueva: volumen ligado al nivel de presencia (0..100 -> bucket).
    // -------------------------------------------------------------------------

    private static class CaveDreadSound extends AbstractTickableSoundInstance {
        private final int presenceBucket;

        CaveDreadSound(int presenceBucket) {
            super(ModSounds.CAVE_DREAD.get(), SoundSource.AMBIENT, RandomSource.create());
            this.presenceBucket = presenceBucket;
            this.relative = true;
            this.looping = true;
            this.attenuation = SoundInstance.Attenuation.NONE;
            // 0.10 en presencia baja -> 0.55 al maximo.
            this.volume = 0.10F + presenceBucket * 0.1125F;
            this.pitch = 0.95F + presenceBucket * 0.02F;
            this.delay = 0;
        }

        @Override
        public void tick() {
            // El manejador lo reinicia si cambia el tramo de presencia; aqui no
            // hay condicion de parada (lo corta PhaseSoundHandler).
        }
    }
}
