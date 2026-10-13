package com.elsilente.client;

import com.elsilente.ElSilenteMod;
import com.elsilente.registry.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Efectos CLIENT de la presencia acumulativa (PresenceHandler sincroniza el
 * nivel via PresenceSyncPacket -> ClientPresence):
 *
 *  - Niebla dinámica (>= 30): la niebla se espresa hasta un 40% al maximo.
 *  - Alucinaciones (>= 40): susurros y humo falsos detras del jugador, sin
 *    entidad real (coste de servidor: cero). Cada alucinacion dispara un
 *    pulso de vignette.
 *  - Vignette (>= 60): oscurecimiento sutil de los bordes que crece con el
 *    nivel; cosmetico, no afecta a los controles.
 */
@Mod.EventBusSubscriber(modid = ElSilenteMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE,
        value = Dist.CLIENT)
public class PresenceFxHandler {

    /** Intensidad temporal (0..1) de la vignette tras una alucinacion. */
    private static float pulse = 0.0F;
    private static int tickCounter = 0;

    private PresenceFxHandler() {
    }

    // -------------------------------------------------------------------------
    // Alucinaciones: susurros y humo falsos en el cliente.
    // -------------------------------------------------------------------------

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.isPaused()) {
            return;
        }
        pulse *= 0.995F;
        if (++tickCounter % 10 != 0) {
            return;
        }

        int level = ClientPresence.getLevel();
        if (level < 40) {
            return;
        }
        // Probabilidad por cada 10 ticks: ~0.25% con presencia 40, ~1.5% con 100.
        float chance = 0.0025F + (level - 40) / 60.0F * 0.0125F;
        if (mc.player.getRandom().nextFloat() > chance) {
            return;
        }

        hallucinate(mc, level);
    }

    /** Materializa una sombra efimera detras del jugador (solo visual). */
    private static void hallucinate(Minecraft mc, int level) {
        Player player = mc.player;
        // Detras del jugador (yaw + 180, margen +-70 grados), a 6-14 bloques.
        float yaw = player.getYRot() + 180.0F + (player.getRandom().nextFloat() - 0.5F) * 140.0F;
        double rad = Math.toRadians(yaw);
        double dist = 6.0D + player.getRandom().nextDouble() * 8.0D;
        double x = player.getX() - Math.sin(rad) * dist;
        double z = player.getZ() + Math.cos(rad) * dist;
        double y = player.getY();

        if (!mc.level.hasChunkAt(player.blockPosition())) {
            return;
        }
        // Susurro posicional falso (solo lo oye este cliente).
        mc.level.playLocalSound(x, y + 1.0D, z, ModSounds.WHISPER.get(),
                SoundSource.HOSTILE, 0.6F, 0.8F + player.getRandom().nextFloat() * 0.4F, false);
        // Humo negro condensandose.
        for (int i = 0; i < 12; i++) {
            mc.level.addParticle(ParticleTypes.LARGE_SMOKE,
                    x + (player.getRandom().nextDouble() - 0.5D) * 0.6D,
                    y + player.getRandom().nextDouble() * 1.8D,
                    z + (player.getRandom().nextDouble() - 0.5D) * 0.6D,
                    0.0D, 0.02D, 0.0D);
        }
        // Pulso de vignette: la vision se nubla un instante.
        pulse = Math.min(1.0F, 0.4F + level / 200.0F);
    }

    // -------------------------------------------------------------------------
    // Niebla dinámica + color de niebla oscurecido.
    // -------------------------------------------------------------------------

    @SubscribeEvent
    public static void onRenderFog(ViewportEvent.RenderFog event) {
        float t = fogFactor();
        if (t <= 0.0F) {
            return;
        }
        // Hasta 40% mas de densidad (niebla mas cerca) al maximo de presencia.
        event.setFarPlaneDistance(event.getFarPlaneDistance() * (1.0F - 0.4F * t));
        event.setNearPlaneDistance(event.getNearPlaneDistance() * (1.0F - 0.2F * t));
    }

    @SubscribeEvent
    public static void onFogColor(ViewportEvent.ComputeFogColor event) {
        float t = fogFactor();
        if (t <= 0.0F) {
            return;
        }
        // El mundo se vuelve mas negro-azulado.
        event.setRed(event.getRed() * (1.0F - 0.55F * t));
        event.setGreen(event.getGreen() * (1.0F - 0.60F * t));
        event.setBlue(event.getBlue() * (1.0F - 0.35F * t));
    }

    /** Factor 0..1 de intensidad de la niebla de presencia (solo Overworld). */
    private static float fogFactor() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.level.dimension() != Level.OVERWORLD) {
            return 0.0F;
        }
        int level = ClientPresence.getLevel();
        if (level < 30) {
            return 0.0F;
        }
        return (level - 30) / 70.0F;
    }

    // -------------------------------------------------------------------------
    // Vignette de oscuridad en los bordes de la pantalla.
    // -------------------------------------------------------------------------

    @Mod.EventBusSubscriber(modid = ElSilenteMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD,
            value = Dist.CLIENT)
    public static class ModBus {

        @SubscribeEvent
        public static void onRegisterOverlays(RegisterGuiOverlaysEvent event) {
            event.registerAboveAll("presence_vignette", ModBus::renderVignette);
        }

        private static void renderVignette(Gui gui, GuiGraphics graphics, float partialTick,
                                           int width, int height) {
            int level = ClientPresence.getLevel();
            float base = level >= 60 ? (level - 60) / 40.0F * 0.35F : 0.0F;
            float intensity = Math.max(base, pulse * 0.45F);
            if (intensity <= 0.01F) {
                return;
            }
            int alpha = (int) (255.0F * Math.min(1.0F, intensity));
            // Gradiente desde cada borde hacia dentro (mas oscuro en el borde).
            int steps = 10;
            int maxBand = Math.min(width, height) / 5;
            int step = Math.max(1, maxBand / steps);
            for (int i = 0; i < steps; i++) {
                int bandAlpha = alpha * (steps - i) / (steps * 2);
                if (bandAlpha <= 0) {
                    continue;
                }
                int color = (bandAlpha << 24); // negro con alfa variable
                int offset = i * step;
                int opposite = offset + step;
                graphics.fill(0, offset, width, opposite, color);
                graphics.fill(0, height - opposite, width, height - offset, color);
                graphics.fill(offset, 0, opposite, height, color);
                graphics.fill(width - opposite, 0, width - offset, height, color);
            }
        }
    }
}
