package com.elsilente.client;

import com.elsilente.ElSilenteMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Overlay de pesadilla (client-side, solo cosmético).
 *
 * Cuando el servidor manda {@code NightmareFlashPacket} al despertar con
 * sombras cerca, la pantalla queda un instante en niebla negra-violeta y se
 * oscurece en un fundido de ~3 s. No afecta a los controles. La niebla del
 * overlay se combina con la niebla de presencia en PresenceFxHandler.
 */
@Mod.EventBusSubscriber(modid = ElSilenteMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE,
        value = Dist.CLIENT)
public final class ClientNightmare {

    /** Duración por defecto del overlay (ticks). */
    public static final int DEFAULT_DURATION = 60;

    private static int ticksLeft = 0;

    private ClientNightmare() {
    }

    /** Dispara el overlay (llamado desde el paquete de red en el hilo cliente). */
    public static void trigger(int duration) {
        ticksLeft = Math.max(ticksLeft, duration);
    }

    public static boolean isActive() {
        return ticksLeft > 0;
    }

    /** Intensidad 0..1 del overlay (1 al inicio, decae linealmente). */
    public static float intensity() {
        if (ticksLeft <= 0) {
            return 0.0F;
        }
        return (float) ticksLeft / (float) DEFAULT_DURATION;
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || ticksLeft <= 0) {
            return;
        }
        if (Minecraft.getInstance().level == null) {
            ticksLeft = 0;
            return;
        }
        ticksLeft--;
    }

    /** Registro del overlay (se añade junto a la vignette de presencia). */
    public static void render(Gui gui, GuiGraphics graphics, float partialTick,
                              int width, int height) {
        float i = intensity();
        if (i <= 0.01F) {
            return;
        }
        // Fase 1 (i > 0.6): flash casi opaco de niebla violeta-negra.
        // Fase 2: fundido hacia la vignette normal de presencia.
        int alpha = (int) (235.0F * i);
        int red = (int) (14.0F * i);
        int green = 0;
        int blue = (int) (26.0F * i);
        int color = (alpha << 24) | (red << 16) | (green << 8) | blue;
        graphics.fill(0, 0, width, height, color);

        // Vórtice interior: banda más clara en el centro que late con el overlay.
        int inset = (int) (Math.min(width, height) * 0.18F * (1.0F - i));
        if (inset > 4) {
            int innerAlpha = (int) (90.0F * i);
            int inner = (innerAlpha << 24) | (58 << 16) | (40 << 8) | (96);
            graphics.fill(inset, inset, width - inset, height - inset, inner);
        }
    }
}
