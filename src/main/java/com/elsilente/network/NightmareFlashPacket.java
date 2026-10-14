package com.elsilente.network;

import com.elsilente.client.ClientNightmare;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Avisa al cliente de que el jugador ha despertado de una pesadilla para
 * mostrar el overlay de alucinacion (frame de niebla + oscurecimiento).
 * Solo viaja al propio jugador afectado.
 */
public class NightmareFlashPacket {

    /** Duracion del overlay en ticks (3 s). */
    public final int duration;

    public NightmareFlashPacket(int duration) {
        this.duration = duration;
    }

    public static void encode(NightmareFlashPacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.duration);
    }

    public static NightmareFlashPacket decode(FriendlyByteBuf buf) {
        return new NightmareFlashPacket(buf.readVarInt());
    }

    public static void handle(NightmareFlashPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() ->
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                        () -> () -> ClientNightmare.trigger(msg.duration)));
        ctx.get().setPacketHandled(true);
    }
}
