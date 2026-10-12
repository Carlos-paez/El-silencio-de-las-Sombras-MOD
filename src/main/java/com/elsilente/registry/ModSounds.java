package com.elsilente.registry;

import com.elsilente.ElSilenteMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModSounds {

    public static final DeferredRegister<SoundEvent> SOUNDS =
            DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, ElSilenteMod.MODID);

    public static final RegistryObject<SoundEvent> HEARTBEAT = register("silente.heartbeat");
    public static final RegistryObject<SoundEvent> WHISPER = register("silente.whisper");
    public static final RegistryObject<SoundEvent> SCREAM = register("silente.scream");
    public static final RegistryObject<SoundEvent> VANISH = register("silente.vanish");
    public static final RegistryObject<SoundEvent> ATTACK = register("silente.attack");
    public static final RegistryObject<SoundEvent> CHASE = register("silente.chase");

    // Fase 2
    public static final RegistryObject<SoundEvent> NIGHTMARE = register("silente.nightmare");
    public static final RegistryObject<SoundEvent> SHADOW_IDLE = register("silente.shadow_idle");
    public static final RegistryObject<SoundEvent> UMBRAL_ROAR = register("silente.umbral_roar");
    public static final RegistryObject<SoundEvent> UMBRAL_SUMMON = register("silente.umbral_summon");

    private static RegistryObject<SoundEvent> register(String name) {
        return SOUNDS.register(name,
                () -> SoundEvent.createVariableRangeEvent(new ResourceLocation(ElSilenteMod.MODID, name)));
    }

    public static void register(IEventBus bus) {
        SOUNDS.register(bus);
    }
}
