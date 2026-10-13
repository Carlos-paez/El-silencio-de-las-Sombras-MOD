package com.elsilente.network;

import com.elsilente.client.ClientPresence;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Sincroniza el nivel de presencia (0-100) del servidor al cliente.
 * Solo viaja el nivel del propio jugador receptor.
 */
public class PresenceSyncPacket {

    private final int level;

    public PresenceSyncPacket(int level) {
        this.level = level;
    }

    public static void encode(PresenceSyncPacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.level);
    }

    public static PresenceSyncPacket decode(FriendlyByteBuf buf) {
        return new PresenceSyncPacket(buf.readVarInt());
    }

    public static void handle(PresenceSyncPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() ->
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPresence.setLevel(msg.level)));
        ctx.get().setPacketHandled(true);
    }
}
