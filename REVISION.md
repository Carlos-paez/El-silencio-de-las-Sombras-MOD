# Revisión de diseño, estructura e implementación — El Silente de las Sombras

> Documento de revisión técnica generado el **2026-10-14**.
> Alcance: revisión de diseño/estructura usando las skills `minecraft-modding` (lane
> Forge 1.20.1) y `minecraft-world-generation`. Incluye **instrucciones de
> implementación** para corregir lo encontrado en una sesión posterior.
>
> **Estado: pendiente de implementación.** Ninguna corrección de este documento se
> ha aplicado todavía al código.

---

## 0. Resumen para quien retome esto

- **Veredicto: el mod está bien implementado y por encima de la media** para un mod
  de un solo autor. Arquitectura correcta para Forge 1.20.1, registros por
  `DeferredRegister`, separación cliente/servidor real, red con `SimpleChannel`,
  config con `ForgeConfigSpec` y casi todo data-driven.
- Hay **3 bugs confirmados**, **un patrón de diseño repetido** que rompe mecánicas
  anunciadas al recargar el mundo, y **pulido** de inconsistencias.
- El fix de mayor impacto en jugabilidad es el **punto 3** (persistencia de sal y
  trofeo). Los puntos **1 y 2** (idioma) son de riesgo cero y se hacen en minutos.

Prioridad sugerida: **1 → 2 → 4 → 3 → 5 → 6** y después el resto.

---

## 1. Lo que está bien (no tocar salvo motivo)

- Estructura de paquetes clara: `registry/ item/ block/ entity/ event/ client/ network/ config/ util/`.
- Registro por `DeferredRegister` + `RegistryObject` en todos los registries; sin
  inicialización estática frágil.
- Separación cliente/servidor correcta: renderers y FX en `client/` con `Dist.CLIENT`;
  el resto en código común.
- Red (`network/ModNetwork`): `consumerMainThread(...)` (hilo correcto) y dirección
  `PLAY_TO_CLIENT`. Correcto.
- Data-driven de verdad: `dimension`, `dimension_type`, `worldgen/biome`,
  `forge/biome_modifier`, `worldgen/structure*`, `loot_tables`, `advancements`,
  `recipes`. Loot tables con la ruta canónica `elsilente:entities/<mob>`.
- Los criterios de advancements concedidos por código (`invocado`, `recuperado`,
  `escapaste`, `venciste`) **coinciden** con los JSON de `advancements/`.
- `pack.mcmeta` con `pack_format: 15` (correcto para 1.20.1); `mods.toml` con GeckoLib
  mandatorio; Java 17 + UTF-8.
- Gating de spawn solo en `MobSpawnType.NATURAL` (huevos/comandos lo saltan),
  encapsulado en los `checkSpawnRules` de cada mob.
- Comentarios y logs en español, coherentes.

**Nota (falsa alarma descartada):** la estructura `santuario`
(`data/elsilente/worldgen/structure/santuario.json`) usa `"biomes": "#minecraft:is_overworld"`
y **no** hay un `forge:add_structures`. Se verificó contra el `plains.json` vanilla
1.20.1 que **el biome JSON ya no tiene campo `structures`** y que la colocación se
gobierna por el campo `biomes` de la estructura; por tanto **sí debería generarse**.
No requiere cambios. (Si se quiere confirmar en partida: `/locate structure elsilente:santuario`.)

---

## 2. Bugs confirmados

### Bug 1 — `trofeo_umbral` muestra la clave cruda en el inventario (clave de idioma incorrecta)

`ModItems.TROFEO_UMBRAL` se registra como **`BlockItem`**:

```java
// src/main/java/com/elsilente/registry/ModItems.java
public static final RegistryObject<Item> TROFEO_UMBRAL = ITEMS.register("trofeo_umbral",
        () -> new BlockItem(ModBlocks.TROFEO_UMBRAL.get(), new Item.Properties()));
```

Un `BlockItem` resuelve su nombre por la clave **`block.*`**, no `item.*`. Evidencia:

- En el `en_us.json` vanilla 1.20.1 existen `block.minecraft.melon` / `block.minecraft.oak_sapling`
  y **no** existen sus equivalentes `item.minecraft.melon` / `item.minecraft.oak_sapling`.
- En el propio mod, `linea_sal` y `portal_umbral` (también `BlockItem`) usan
  `block.elsilente.*`. El trofeo es la **única** excepción.

Las tres lang definen `item.elsilente.trofeo_umbral`. **Efecto:** el Trofeo del Umbral
se ve como la clave cruda `block.elsilente.trofeo_umbral`.

**Fix:** renombrar la clave a `block.elsilente.trofeo_umbral` en:
- `src/main/resources/assets/elsilente/lang/es_es.json`
- `src/main/resources/assets/elsilente/lang/es_ve.json`
- `src/main/resources/assets/elsilente/lang/en_us.json`

**Criterio de aceptación:** en inventario/JEI/tooltip el trofeo muestra "Trofeo del
Umbral" / "Threshold Trophy", nunca una clave.

---

### Bug 2 — `es_ve.json` desincronizado (faltan 10 claves)

`en_us` y `es_es` tienen **72** claves; `es_ve` tiene **62**. Faltan exactamente:

```
advancements.elsilente.corazon_verdadero.title
advancements.elsilente.corazon_verdadero.description
advancements.elsilente.reino_sombras.title
advancements.elsilente.reino_sombras.description
block.elsilente.portal_umbral
elsilente.message.realm.no_return
elsilente.message.realm.opened
elsilente.message.realm.welcome
entity.elsilente.silente_verdadero
item.elsilente.silente_verdadero_spawn_egg
```

**Comando de verificación (PowerShell, desde la raíz del repo):**

```powershell
$dir = "src\main\resources\assets\elsilente\lang"; $map = @{}
Get-ChildItem "$dir\*.json" | ForEach-Object {
  $j = Get-Content $_.FullName -Raw | ConvertFrom-Json
  $map[$_.Name] = @($j.PSObject.Properties.Name)
}
$all = @(); foreach ($k in $map.Keys) { $all += $map[$k] }; $all = $all | Sort-Object -Unique
foreach ($f in ($map.Keys | Sort-Object)) {
  $missing = $all | Where-Object { $map[$f] -notcontains $_ }
  Write-Output "== $f : $($map[$f].Count) keys =="
  if ($missing) { $missing | ForEach-Object { Write-Output "   FALTA: $_" } } else { Write-Output "   (completo)" }
}
```

**Fix:** añadir las 10 claves a `es_ve.json`. Traduce desde `es_es.json` con la
variante venezolana que ya usa el archivo. **Ojo:** `block.elsilente.portal_umbral` es
el resultado del Bug 1 aplicado al portal (ya existe como `block.` en es_es/en_us);
cópialo tal cual.

**Criterio de aceptación:** el comando anterior imprime `(completo)` para los 3
archivos y los 3 tienen el mismo recuento de claves.

---

### Bug 3 — La Línea de sal y el Trofeo dejan de proteger tras recargar el mundo

`util/SaltBarrier.java` y `block/TrofeoUmbralBlockEntity.java` (su set estático
`TROPHIES`) registran posiciones **solo en memoria**, vía `onPlace`/`onRemove`, y se
vacían en `ServerStoppingEvent`. **Nunca se reconstruyen al cargar chunks o el mundo.**

Un chunk ya existente no re-ejecuta `onPlace`, así que tras recargar partida/servidor:
- Las sales existentes no bloquean spawns naturales de Sombras.
- Los trofeos existentes no crean zona segura contra El Silente (`findNearby` devuelve
  `null`).

Las partículas del trofeo y el repel de Sombras Debiles **sí** siguen funcionando
(van por el ticker del BlockEntity), pero la mecánica anunciada de "zona segura" no.

**Opciones de fix:**

- **Opción A (rápida, sin persistencia):** reconstruir al cargar.
  - Trofeo: en `ChunkEvent.Load` (o `LevelEvent.Load`) recorrer
    `levelChunk.getBlockEntities().values()`, y para cada `TrofeoUmbralBlockEntity`
    llamar a su registro. O, mejor, eliminar el set estático y **consultar los
    BlockEntities cercanos** en `findNearby` (más limpio: el BE ya existe con el chunk).
  - Sal: `LineaSalBlock` es un bloque normal (sin BE), así que hay que escanear las
    `LevelChunkSection` del chunk cargado buscando `ModBlocks.LINEA_SAL.get()` y
    registrar esas posiciones. Hacerlo solo si el chunk contiene el bloque.
- **Opción B (robusta, recomendada a medio plazo):** mover ambas estructuras a
  `SavedData` por dimensión (posiciones guardadas con el mundo, marcadas `setDirty()`
  en `onPlace`/`onRemove`). Elimina la necesidad de reconstruir y sobrevive a todo.

**Criterio de aceptación:** colocar sal/trofeo, salir y volver a entrar al mundo
(o reiniciar el servidor) y comprobar que la protección sigue activa sin recolocar
el bloque.

**Archivos a tocar:**
- `src/main/java/com/elsilente/util/SaltBarrier.java`
- `src/main/java/com/elsilente/block/LineaSalBlock.java`
- `src/main/java/com/elsilente/block/TrofeoUmbralBlockEntity.java`
- (nuevo, si se elige B) `src/main/java/com/elsilente/util/...SavedData.java`

---

## 3. Patrón de diseño a corregir (el más importante tras los bugs)

### Punto 4 — Estado estático por jugador sin limpieza

Ninguno de estos mapas/sets se limpia al cerrar sesión del jugador ni al parar el
servidor:

| Archivo | Estado |
|---|---|
| `event/AmbushHandler.java` | `NEXT_CHECK`, `NEXT_AMBUSH`, `AMBUSH_SILENCED_UNTIL` |
| `event/PresenceHandler.java` | `PRESENCE`, `NEXT_EVAL` |
| `event/DreamHandler.java` | `THREAT_WHILE_SLEEPING` |
| `event/TheftHandler.java` | `STOLEN_CHESTS` |
| `entity/ElSilenteEntity.java` | `PENDING_SPAWNS` (además `public` mutable) |

**Efectos:**
- (a) Crecen con cada jugador que entra (fuga pequeña pero permanente).
- (b) En un solo jugador, cargar otro mundo reutiliza el mismo UUID y **arrastra
  presencia, timers de emboscada y spawns pendientes entre mundos**;
  `PENDING_SPAWNS` podría invocar a El Silente en un mundo nuevo.

**Fix mínimo (recomendado para esta ronda):**
- Añadir en cada handler (o en un `SessionStateHandler` central):
  - `@SubscribeEvent public static void onLogout(PlayerEvent.PlayerLoggedOutEvent e)`
    → borrar la entrada de ese UUID.
  - `@SubscribeEvent public static void onServerStopping(ServerStoppingEvent e)`
    → `clear()` de todos los mapas/sets.
- Encapsular `ElSilenteEntity.PENDING_SPAWNS` (hacerlo `private` y exponer
  `queuePendingSpawn(...)` / `clearPendingSpawns()`).

**Mejora a medio plazo:** mover presencia y spawns pendientes a `SavedData` por mundo
(sobrevive reinicios y no depende de estáticos).

**Criterio de aceptación:** tras entrar y salir un jugador, los mapas no retienen su
UUID; al parar el servidor todos quedan vacíos; no se arrastra estado entre mundos.

---

### Punto 5 — Import de clase cliente en código común (`DreamHandler`)

`event/DreamHandler.java` (lado común/servidor) hace
`import com.elsilente.client.ClientNightmare;` y usa `ClientNightmare.DEFAULT_DURATION`.

Hoy **no crashea** porque `DEFAULT_DURATION` es `public static final int = 60`
(constante de compilación): javac la **inlinea** y la clase cliente nunca se carga en
servidor dedicado. Pero si alguien cambia esa constante a algo no constante, el
servidor dedicado lanzará `NoClassDefFoundError`.

**Fix:** definir la constante en código común (p. ej. en
`network/NightmareFlashPacket.java` como `public static final int DEFAULT_DURATION = 60;`)
y referenciarla desde ambos lados. Quitar el import de `client.*` en `DreamHandler`.

**Criterio de aceptación:** ningún archivo fuera de `client/` importa `com.elsilente.client.*`.

---

## 4. Menores / pulido

1. **Cadenas en español hardcodeadas** (no localizables, rompen la coherencia con el
   resto de mensajes traducibles). Pasar a `Component.translatable(...)` + claves:
   - `item/AmuletItem.java` (tooltip `"Late cuando Él está cerca..."`).
   - `item/SimboloAlbaItem.java` (tooltip `"Unico golpe que una Sombra no puede resistir"`).
   - `event/TheftHandler.java` (`"Cofre del Silente"` y mensajes de robo).
2. **`UmbralEntity` no persiste el estado de fases** (`phaseTwoRoared`, `enraged`):
   al recargar re-ruge y re-enfurece. Se autocorrige en un tick, pero conviene
   sobrescribir `addAdditionalSaveData`/`readAdditionalSaveData`.
3. **Pesos de spawn altos:** El Silente peso 40 (`elsilente_spawns.json`) y Sombra
   Débil peso 80 (`elsilente_sombra_spawns.json`) en `#minecraft:is_overworld`. Como
   `checkSpawnRules` rechaza la mayoría de intentos, se desperdician ciclos de spawn y
   se puede desplazar fauna hostil vanilla. Bajar pesos y/o usar `spawn_costs`.
4. **`ReinoHandler.safeArrival` pregenera 3×3 chunks en el hilo del servidor** al
   entrar (posible tirón). Valorar *chunk ticket* / generación menos agresiva.
5. **Números mágicos no configurables:** radio del trofeo (20), radio de sal (12),
   ticks del "stare", etc. Exponerlos en `ElSilenteConfig.defenses`.
6. **Sin tags de item/block:** el Símbolo del Alba es `SwordItem` pero no está en
   `#minecraft:swords`; los bloques no tienen tags `mineable/*` → compatibilidad rara
   con herramientas/encantamientos de otros mods. Añadir un `ModTags`.
7. **`TheftHandler` coloca un cofre real** en el mundo (puede caer en granja/nieve) y
   no lo elimina si nadie lo abre. **Verificar que no haya duplicación** con el drop
   por muerte (orden `LivingDeathEvent` vs `dropAllDeathLoot`).
8. **`ElSilenteEntity` (957 líneas)** con las metas como clases internas: funcional,
   pero candidato a extraer a ficheros `goal/` propios.
9. **Datagen cableado y sin usar:** todo el JSON es a mano. Migrar recetas/loot/lang a
   datagen evitaría derivas como los Bugs 1 y 2.

---

## 5. Recomendaciones de jugabilidad

1. **Prioridad absoluta:** arreglar el Punto 3 — una defensa que deja de funcionar al
   recargar rompe la confianza en la mecánica más que no tenerla.
2. **Símbolo del Alba con doble recurso** (durabilidad de `Tier` + cargas): confuso.
   Elegir uno (probablemente cargas; quitar la durabilidad).
3. **Indicador de presencia:** hoy solo se intuye por niebla/susurros. Un medidor sutil
   o un latido del Amuleto más claro ayudaría a entender la progresión 0–100.
4. **Curva del Corazón Silente:** El Silente da 1, el Verdadero 2, y el
   ritual/recarga consumen. Revisar si el farmeo se vuelve tedioso o trivial.
5. **Pesadilla** (te baja a media vida al despertar con Sombra cerca) es dura; hacerla
   configurable para perfiles no-hardcore.
6. **Profundidad del jefe Umbral:** hoy es melee + slam + invocación. Valorar una o
   dos mecánicas de arena/fases extra.
7. **El "stare" de 0.5 s** para desvanecer a El Silente es corto; decisión de diseño
   consciente (horror vs. facilidad de escape).

---

## 6. Plan de implementación (para la próxima sesión)

### Fase 0 — Preparación
- [ ] Confirmar entorno: `java` por defecto es 26 y **rompe Gradle 8.1.1**; usar JDK 17.
      En PowerShell:
      `$env:JAVA_HOME="C:\Users\Usuario\.gradle\jdks\eclipse_adoptium-17-amd64-windows\jdk-17.0.20.1+1"`
- [ ] Build de referencia: `.\gradlew build` → `build/libs/elsilente-<version>.jar`.
- [ ] Convenciones: comentarios/logs/docs en **español**, acentos como **escapes
      unicode** (`\u00c9l`), JSON de datos en **LF**, commits en **español** sin
      Conventional Commits, sobre `main`.

### Fase 1 — Idioma (riesgo cero, 15 min)
- [ ] Bug 1: renombrar `item.elsilente.trofeo_umbral` → `block.elsilente.trofeo_umbral`
      en las 3 lang.
- [ ] Bug 2: añadir las 10 claves que faltan a `es_ve.json`.
- [ ] Verificar con el script de PowerShell de la sección 2 (los 3 deben salir
      `(completo)` y con el mismo recuento).
- [ ] `.\gradlew build` y, opcional, `runClient` para ver el nombre del trofeo.

### Fase 2 — Limpieza de estado estático (Punto 4)
- [ ] Añadir `onLogout` + `onServerStopping` a `AmbushHandler`, `PresenceHandler`,
      `DreamHandler`, `TheftHandler`.
- [ ] Encapsular `ElSilenteEntity.PENDING_SPAWNS`.
- [ ] (Opcional) `SavedData` para presencia y spawns pendientes.
- [ ] Criterio: no quedan UUIDs tras logout; mapas vacíos al parar el servidor.

### Fase 3 — Constante común (Punto 5)
- [ ] Mover `DEFAULT_DURATION` a `NightmareFlashPacket` (o `ModConstants`) y usarla
      desde servidor y cliente; quitar el import de `client.*` en `DreamHandler`.
- [ ] `grep` de seguridad: ningún archivo fuera de `client/` debe importar
      `com.elsilente.client.*`.

### Fase 4 — Persistencia de sal y trofeo (Bug 3, mayor impacto)
- [ ] Elegir opción A (reconstrucción al cargar) u opción B (`SavedData`).
- [ ] Implementar y probar el criterio de aceptación del Bug 3.
- [ ] Marcar `setDirty()` si se usa `SavedData`.

### Fase 5 — Pulido y jugabilidad (a demanda)
- [ ] Pasar las 3 cadenas hardcodeadas a lang (sección 4.1).
- [ ] Persistir fases de `UmbralEntity` (sección 4.2).
- [ ] Ajustar pesos de spawn (sección 4.3).
- [ ] Revisar `ReinoHandler.safeArrival` (sección 4.4).
- [ ] Exponer constantes en config (sección 4.5).
- [ ] Añadir `ModTags` (sección 4.6).
- [ ] Verificar duplicación en `TheftHandler` (sección 4.7).
- [ ] Decisiones de jugabilidad de la sección 5.

### Fase 6 — Cierre
- [ ] `.\gradlew build` limpio.
- [ ] No romper la excepción GeckoLib: **no quitar**
      `property 'mixin.env.disableRefMap', 'true'` de la run config `client` de
      `build.gradle`.
- [ ] Commit(s) en español sobre `main`. Sugerencia:
      `Corrige claves de idioma del trofeo y sincroniza es_ve`.
- [ ] Actualizar `AGENTS.md` si se añaden nuevos archivos/patrones relevantes.

---

## 7. Cómo verificar todo junto (smoke test)

1. `.\gradlew build` sin errores.
2. `runClient`: comprobar nombre del Trofeo del Umbral (Bug 1) con locale `es_ve`,
   `es_es` y `en_us` (Bug 2).
3. Colocar una Línea de sal y un Trofeo; salir y volver a entrar al mundo; confirmar
   que la protección sigue activa (Bug 3).
4. Entrar/salir de partida y parar el servidor; confirmar que no queda estado de
   jugadores (Punto 4).
5. `/locate structure elsilente:santuario` (solo confirmación; no requiere cambios).

---

## 8. Índice rápido de archivos implicados

| Archivo | Motivo |
|---|---|
| `src/main/resources/assets/elsilente/lang/{es_es,es_ve,en_us}.json` | Bugs 1 y 2 |
| `src/main/java/com/elsilente/registry/ModItems.java` | Confirma `TROFEO_UMBRAL` como `BlockItem` |
| `src/main/java/com/elsilente/util/SaltBarrier.java` | Bug 3 |
| `src/main/java/com/elsilente/block/LineaSalBlock.java` | Bug 3 |
| `src/main/java/com/elsilente/block/TrofeoUmbralBlockEntity.java` | Bug 3 |
| `src/main/java/com/elsilente/event/AmbushHandler.java` | Punto 4 |
| `src/main/java/com/elsilente/event/PresenceHandler.java` | Punto 4 |
| `src/main/java/com/elsilente/event/DreamHandler.java` | Puntos 4 y 5 |
| `src/main/java/com/elsilente/event/TheftHandler.java` | Puntos 4 y 4.1/4.7 |
| `src/main/java/com/elsilente/entity/ElSilenteEntity.java` | Punto 4 (`PENDING_SPAWNS`) |
| `src/main/java/com/elsilente/entity/UmbralEntity.java` | Sección 4.2 |
| `src/main/java/com/elsilente/item/AmuletItem.java` | Sección 4.1 |
| `src/main/java/com/elsilente/item/SimboloAlbaItem.java` | Sección 4.1 |
| `src/main/java/com/elsilente/event/ReinoHandler.java` | Sección 4.4 |
| `src/main/resources/data/elsilente/forge/biome_modifier/*.json` | Sección 4.3 |
