package com.elsilente.network;

import com.elsilente.ElSilenteMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.Optional;

/** Canal de red del mod (paquetes servidor -> cliente). */
public final class ModNetwork {

    private static final String VERSION = "1";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(ElSilenteMod.MODID, "main"),
            () -> VERSION, VERSION::equals, VERSION::equals);

    private ModNetwork() {
    }

    public static void register() {
        int id = 0;
        CHANNEL.messageBuilder(PresenceSyncPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(PresenceSyncPacket::encode)
                .decoder(PresenceSyncPacket::decode)
                .consumerMainThread(PresenceSyncPacket::handle)
                .add();
    }
}
