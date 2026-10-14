package com.elsilente.registry;

import com.elsilente.ElSilenteMod;
import com.elsilente.entity.ElSilenteEntity;
import com.elsilente.entity.SombraDebilEntity;
import com.elsilente.entity.UmbralEntity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModEntities {

    public static final DeferredRegister<EntityType<?>> ENTITIES =
            DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, ElSilenteMod.MODID);

    public static final RegistryObject<EntityType<ElSilenteEntity>> EL_SILENTE = ENTITIES.register("el_silente",
            () -> EntityType.Builder
                    .of(ElSilenteEntity::new, MobCategory.MONSTER)
                    .sized(0.6F, 1.99F)
                    .clientTrackingRange(10)
                    .build("elsilente:el_silente"));

    /** Sombra Debil: neutral, invulnerable salvo el arma simbolica. */
    public static final RegistryObject<EntityType<SombraDebilEntity>> SOMBRA_DEBIL = ENTITIES.register("sombra_debil",
            () -> EntityType.Builder
                    .of(SombraDebilEntity::new, MobCategory.MONSTER)
                    .sized(0.6F, 1.7F)
                    .clientTrackingRange(8)
                    .build("elsilente:sombra_debil"));

    /** El Umbral: jefe final, solo invocable con el ritual de medianoche. */
    public static final RegistryObject<EntityType<UmbralEntity>> UMBRAL = ENTITIES.register("umbral",
            () -> EntityType.Builder
                    .of(UmbralEntity::new, MobCategory.MONSTER)
                    .sized(0.9F, 2.6F)
                    .clientTrackingRange(10)
                    .build("elsilente:umbral"));

    /** Silente Verdadero (§13): la version «verdadera» que habita el reino de sombras. */
    public static final RegistryObject<EntityType<ElSilenteEntity>> SILENTE_VERDADERO = ENTITIES.register("silente_verdadero",
            () -> EntityType.Builder
                    .of(ElSilenteEntity::new, MobCategory.MONSTER)
                    .sized(0.6F, 1.99F)
                    .clientTrackingRange(10)
                    .build("elsilente:silente_verdadero"));

    public static void register(IEventBus bus) {
        ENTITIES.register(bus);
    }

    public static void onAttributeCreation(EntityAttributeCreationEvent event) {
        event.put(EL_SILENTE.get(), ElSilenteEntity.createAttributes().build());
        event.put(SOMBRA_DEBIL.get(), SombraDebilEntity.createAttributes().build());
        event.put(UMBRAL.get(), UmbralEntity.createAttributes().build());
        event.put(SILENTE_VERDADERO.get(), ElSilenteEntity.createRealmAttributes().build());
    }

    public static void registerSpawnPlacements() {
        SpawnPlacements.register(EL_SILENTE.get(),
                SpawnPlacements.Type.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                ElSilenteEntity::checkSpawnRules);
        SpawnPlacements.register(SOMBRA_DEBIL.get(),
                SpawnPlacements.Type.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                SombraDebilEntity::checkSpawnRules);
        // El jefe nunca aparece de forma natural: solo ritual, huevo o comando.
        SpawnPlacements.register(UMBRAL.get(),
                SpawnPlacements.Type.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                (type, level, spawnType, pos, random) -> spawnType != MobSpawnType.NATURAL);
        // Silente Verdadero: solo nace de forma natural en su reino (§13).
        SpawnPlacements.register(SILENTE_VERDADERO.get(),
                SpawnPlacements.Type.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                (type, level, spawnType, pos, random) -> {
                    if (spawnType != MobSpawnType.NATURAL) {
                        return true;
                    }
                    if (level.getLevel().dimension() != com.elsilente.event.ReinoHandler.REINO) {
                        return false;
                    }
                    if (level.getMaxLocalRawBrightness(pos) > 7) {
                        return false;
                    }
                    // Jamas pegado al jugador (12 bloques).
                    return level.getLevel().getNearestPlayer(pos.getX(), pos.getY(), pos.getZ(),
                            12.0D, false) == null;
                });
    }
}
