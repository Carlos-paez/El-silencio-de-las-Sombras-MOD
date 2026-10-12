package com.elsilente.event;

import com.elsilente.ElSilenteMod;
import com.elsilente.registry.ModEntities;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Diagnostico del spawn natural: al arrancar el servidor imprime en el log
 * cuantos biomas tienen a cada entidad en su tabla de monstruos y con que peso.
 * Sirve para comprobar que las biome modifiers (elsilente_spawns.json y
 * elsilente_sombra_spawns.json) se aplicaron.
 */
@Mod.EventBusSubscriber(modid = ElSilenteMod.MODID)
public class SpawnAudit {

    private static final int MAX_REPORTES = 3;

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        Registry<Biome> biomes = event.getServer().registryAccess().registryOrThrow(Registries.BIOME);
        audit(biomes, ModEntities.EL_SILENTE.get(), "El Silente", "elsilente_spawns.json");
        audit(biomes, ModEntities.SOMBRA_DEBIL.get(), "Sombra Debil", "elsilente_sombra_spawns.json");
    }

    private static void audit(Registry<Biome> biomes, EntityType<?> type, String nombre, String archivo) {
        int total = 0;
        int conSpawn = 0;
        int peso = 0;
        int reportados = 0;

        for (Biome biome : biomes) {
            total++;
            int pesoBioma = 0;
            for (MobSpawnSettings.SpawnerData data
                    : biome.getMobSettings().getMobs(MobCategory.MONSTER).unwrap()) {
                if (data.type == type) {
                    pesoBioma += data.getWeight().asInt();
                }
            }
            if (pesoBioma <= 0) {
                continue;
            }
            conSpawn++;
            peso = pesoBioma;
            if (reportados < MAX_REPORTES) {
                ElSilenteMod.LOGGER.info("[El Silente] Spawn natural de {} activo en {} (peso {})",
                        nombre, biomes.getKey(biome), pesoBioma);
                reportados++;
            }
        }

        if (conSpawn == 0) {
            ElSilenteMod.LOGGER.warn("[El Silente] Spawn natural de {} NO activo (revisa "
                    + "data/elsilente/forge/biome_modifier/{}).", nombre, archivo);
        } else {
            ElSilenteMod.LOGGER.info("[El Silente] Spawn natural de {} en {}/{} biomas, peso {} por intento",
                    nombre, conSpawn, total, peso);
        }
    }
}
