# 🌑 Propuestas de mejora — El Silente de las Sombras

> **Estado:** propuesta, **pendiente de codificar**. Complementa a `DISENO.md` (Fases 1 y 2,
> ya implementadas) con la siguiente ronda de mejoras. Todo pensado para el stack actual:
> MC **1.20.1**, Forge **47.2.0**, Java 17, mod id `elsilente`.
>
> Objetivo: pasar de *eventos de terror sueltos* a una **experiencia de terror sostenida**:
> presión ambiental continua, consecuencias sistémicas entre encuentros y defensas que
> exigen decisiones, no solo antorchas.

---

## Orden de prioridad

| # | Mejora | Prioridad | Esfuerzo |
|---|---|---|---|
| 1 | Skin 64×64 de la Sombra Débil (renderizado) | 🔴 Alta | Medio |
| 2 | Sistema de presencia acumulativa | 🔴 Alta | Medio |
| 3 | Alucinaciones client-side + niebla/vignette dinámicos | 🔴 Alta | Medio |
| 4 | El Silente apaga las antorchas antes de atacar | 🟠 Media | Bajo |
| 5 | Rastro del botín (caza del Cofre del Silente) | 🟠 Media | Bajo |
| 6 | Configuración Forge (`ForgeConfigSpec`) | 🟠 Media | Bajo |
| 7 | Defensas activas con coste (sal, campana, mejora del Símbolo) | 🟠 Media | Medio |
| 8 | La base deja de ser segura (abrir puertas) | 🟡 Media-baja | Bajo |
| 9 | Narrativa in-game: advancements + libros de lore | 🟡 Media-baja | Bajo |
| 10 | Música tensa por fases + sonido ambiental procedural | 🟡 Media-baja | Medio |
| 11 | Estructura de worldgen «Santuario del Silente» | 🟢 Baja | Alto |
| 12 | GeckoLib 4 + modelos de Blockbench | 🟢 Baja | Alto (ya aprobado) |
| 13 | Reino de sombras (fase endgame tras el Umbral) | 🟢 Baja | Muy alto |

---

## 1. 🎨 Renderizado de la skin de la Sombra Débil — de 16×16 a 64×64

**Situación actual (verificado en código):**

- [SombraDebilModel.java](src/main/java/com/elsilente/client/SombraDebilModel.java) usa un
  `HumanoidModel` **en miniatura** con cajas y UV reducidos:
  - cabeza `6×6×6` (cara frontal de solo **6×6 px** en (6,6)–(12,12)),
  - torso `8×8×4`, brazos `2×8×2`, piernas `4×6×4`.
- El layer se declara como atlas 64×64 y el PNG ([sombra_debil.png](src/main/resources/assets/elsilente/textures/entity/sombra_debil.png))
  mide 64×64, **pero el mapeo solo aprovecha una fracción del lienzo con detalle equivalente
  a una skin clásica de 16×16**: sin capas secundarias, sin espacio para textura de alta
  densidad y con una silueta que no aprovecha el formato moderno de skin de Minecraft.
- Resultado visible: la Sombra Débil se ve «plana» y de baja resolución justo en los planos
  cercanos donde más asusta (pesadilla al pie de la cama, patrulla a < 8 b).

**Objetivo:** renderizar la Sombra Débil con una **skin real en formato 64×64 de jugador
moderno**, no con un layout de detalle 16×16.

**Tareas:**

1. **Rehacer `SombraDebilModel.createBodyLayer()` con proporciones estándar 64×64:**
   - cabeza `8×8×8` → UV en (0,0) (cara frontal 8×8 en (8,8)),
   - torso `8×12×4` → UV en (16,16),
   - brazo der. / izq. `4×12×4` → UV en (40,16) y (32,48),
   - pierna der. `4×12×4` → UV en (0,16) y (16,48).
2. **Añadir capas overlay del formato 64×64** (hoy no existen):
   - `hat`/capucha → (32,0),
   - `jacket`/torso → (16,32),
   - mangas → (40,32),
   - pantalones → (0,32).
   La capa `hat` ya se declara vacía solo por exigencia de `HumanoidModel`; ahora se
   rellena (capucha rasgada/flotante con transparencia = muy efectiva para la silueta).
   Idea de arte: capa exterior semitransparente (aire/ceniza) que deje ver el cuerpo oscuro
   de debajo — el «ondulado» de la sombra puede vivir en el overlay.
3. **Ajustar la caja de colisión y la escala** del renderer: con proporciones de jugador
   completas, revisar `SombraDebilRenderer.scale()` (hoy 0,9×) y el `EntityDimensions` de
   [SombraDebilEntity.java](src/main/java/com/elsilente/entity/SombraDebilEntity.java) para
   conservar la silueta menuda y encorvada (mantener el balanceo fantasmal de `setupAnim`).
4. **Regenerar la textura en [scripts/generate_assets.py](scripts/generate_assets.py)**
   sobre el nuevo atlas: la función de la Sombra Débil debe pintar las regiones estándar
   (cara, torso con costuras, capa overlay) y **sus coordenadas UV DEBEN coincidir con
   `SombraDebilModel.java`** (misma regla que ya existe para `EYE_CORES` de El Silente).
   - Usar el flag `--keep-art` si hay textura hecha a mano que no se debe sobrescribir.
5. **Comprobación de sombras/emisión:** hoy el render es `MobRenderer` estándar; valorar si
   los ojos pálidos deben ir en una capa emisiva (como `el_silente_eyes.png`) una vez la
   cara pase de 6×6 a 8×8 px.

**Criterios de aceptación:**

- [ ] Textura `sombra_debil.png` en formato **64×64 de jugador** (cuerpo + overlay completo).
- [ ] `SombraDebilModel` con proporciones estándar y capas `hat/jacket/sleeves/pants` pintadas.
- [ ] Coordenadas UV sincronizadas entre el modelo Java y `generate_assets.py`.
- [ ] Silueta sigue siendo menuda/encorvada; pesadilla y patrulla se ven bien de cerca (< 4 b).
- [ ] `.\gradlew build` + prueba en cliente (`runClient`) sin errores de textura.

---

## 2. 🌫️ Sistema de presencia acumulativa

Un medidor **oculto** por jugador (mapa por UUID, mismo patrón que `AmbushHandler` y
`PENDING_SPAWNS`) que representa cuánto ha notado tu presencia el mundo de las sombras.

| Fuente (sube la presencia) | Fuente (la baja) |
|---|---|
| Emboscada del Silente | Amanecer con supervivencia |
| Despertar con pesadilla | Dormir sin incidentes |
| Estar en oscuridad (luz ≤ 7) de noche | Permanecer en luz ≥ 8 |
| Estar herido cerca de sombras | Derrotar al Umbral / usar Trofeo |

**Umbrales y efectos** (acumulativa, persistente por sesión en el jugador):

| Nivel | Efectos |
|---|---|
| 0–24 % | Comportamiento actual (sin cambios). |
| 25–49 % | Susurros y `shadow_idle` más frecuentes; sonidos ambientales falsos. |
| 50–74 % | Alucinaciones visuales; niebla más espesa de noche; luces que parpadean. |
| 75–100 % | Aumenta la probabilidad/ritmo de emboscadas; el Silente aparece en STALK en las sombras de tu base. |

**Dónde:** handler nuevo `event/PresenceHandler.java` (server tick), con valores enviados al
cliente para los efectos visuales. Todo configurable (ver §6).

---

## 3. 👁️ Alucinaciones, niebla y efectos de cámara (client-side)

- **Alucinaciones puramente visuales:** entidades falsas que solo existen en el render del
  cliente (una sombra al fondo que se disuelve al girar, un Silente que aparece en el borde
  del FOV y desaparece *antes* de que el servidor se entere). Coste de servidor: cero;
  respeta la filosofía ya escrita en `El silencio de las sombras.md` («se perciben de
  reojo»). Ideas: sonidos falsos reproducidos solo localmente, un bloque que « respira »,
  mensajes de chat fantasma en cursiva.
- **Niebla dinámica:** `ViewportEvent.RenderFog` para espesar la niebla de noche según el
  nivel de presencia y la distancia a la entidad más cercana.
- **Vignette y temblor:** overlay sutil de oscurecimiento de bordes y micro-temblor de
  cámara a < 4 b en CHASE (la «capa de miedo» opcional que quedó sin implementar en
  `DISENO.md` §1.2). Mantener cosmético: **no** aplicar efectos que confundan controles.
- **Pesadilla enriquecida:** al despertar con la mitad de la vida
  ([DreamHandler.java](src/main/java/com/elsilente/event/DreamHandler.java)), mostrar además
  del chat un overlay breve de alucinación (frame de niebla + sonido `nightmare`) en vez del
  mensaje de texto solo.

**Archivos a tocar:** clase nueva `client/` (overlay + niebla), registro en
[ClientSetup.java](src/main/java/com/elsilente/client/ClientSetup.java) con `Dist.CLIENT`.

---

## 4. 🔥 El Silente apaga tu luz antes de atacar

Hoy la luz solo lo repele *reativamente* (se disipa a ≥ 8). Invertir la dinámica: en fase
STALK, si no hay línea de visión y hay una fuente de luz a < 12 b, con cierta probabilidad
se desplaza hasta ella y la **sopla/apaga** (pathfinding hacia la antorcha, partícula de
humo, sonido de soplido, `LevelEvent` de extinción) antes de reanudar el acecho.

- **Por qué:** convierte tu base de un refugio seguro en una fortaleza que hay que *mantener*;
  es terror de manual (la luz que se apaga sola) y aprovecha que ya prefiere los marcos de
  puerta.
- **Dónde:** goal nuevo en [ElSilenteEntity.java](src/main/java/com/elsilente/entity/ElSilenteEntity.java)
  (`SnuffLightGoal`), con cap por entidad y configurable (§6). Nunca apagar en dificultad
  «pacífica» ni bases de otros jugadores en regiones protegidas sin config.

---

## 5. 🗺️ El botín deja rastro (ciclo completo del robo)

[TheftHandler.java](src/main/java/com/elsilente/event/TheftHandler.java) hoy esconde el
«Cofre del Silente» a 20–40 b y solo avisa con texto: es castigo sin caza.

- Hacer que el **Amuleto del Latido** también lata hacia el cofre robado (o soltar un
  **mapa manchado** que señale la zona), con la misma escala de intensidad por distancia
  que ya usa [AmuletHandler.java](src/main/java/com/elsilente/event/AmuletHandler.java).
- El cofre podría exigir el **Símbolo del Alba** o una acción para abrirse (no un
  clic derecho), y guardarse una localización por jugador en NBT/persistent data.
- **Resultado:** pérdida → caza → recuperación, en vez de pérdida arbitraria.

---

## 6. ⚙️ Configuración Forge (`ForgeConfigSpec`)

Nada está configurado hoy: frecuencias, daños y caps están hardcodeados. Archivo nuevo
`config/ElSilenteConfig.java` registrado en `Mod` con `ModConfigEvent`:

- `general.ambushChance` / `general.ambushCooldownTicks` (hoy 0.20 y 12000 fijos).
- `general.presenceEnabled` (§2), `general.snuffLightsEnabled` (§4).
- `spawns.maxShadowsPerPlayer` (hoy 6), pesos de spawn, nivel de luz.
- `difficulty.silenteDamage`, `theftEnabled`, `theftMinItems`.
- **Multiplayer:** caps estrictos por UUID (mismo patrón que `PENDING_SPAWNS`); nada de
  spawning continuo sin techo.

---

## 7. 🛡️ Defensas activas con coste

La luz es hoy la única respuesta pasiva. Añadir opciones que obliguen a elegir:

| Ítem | Efecto | Coste / límite |
|---|---|---|
| **Línea de sal** (bloque) | Impide el spawn natural de Sombras en su radio (10–15 b). | Se consume con el tiempo/lluvia; no detiene emboscadas. |
| **Campana de vigilia** | Al sonar, cancela la emboscada programada de esa noche. | Atrae Sombras Débiles esa misma noche (trade-off). |
| **Símbolo del Alba mejorado** | Cargas de luz sagrada: daña también a El Silente y disipa Sombras a distancia. | Consumible/recarga con Corazón Silente. |

**Registros:** [ModItems.java](src/main/java/com/elsilente/registry/ModItems.java),
[ModBlocks.java](src/main/java/com/elsilente/registry/ModBlocks.java) (si hay bloque),
lang `es_es` + `en_us` **en sincronía**, recetas nuevas, texturas vía `generate_assets.py`.

---

## 8. 🚪 La base deja de ser segura

El Silente ya prefiere los marcos de puerta; el salto natural es **cruzarlas**: goal de
abrir puertas (o rasgarlas con sonido de garras) cuando acecha a < 8 b. Complemento barato
y muy efectivo para el terror doméstico; condicionar a que no haya línea de visión y a la
config (§6).

---

## 9. 📖 Narrativa in-game (advancements + lore)

Hoy todo el conocimiento vive fuera del juego (README/RITUAL). Añadir:

- **Advancements** (recurso `data/elsilente/advancement/`) que enseñen las mecánicas:
  *«Algo te observaba mientras dormías»*, *«Sobrevive a una emboscada»*, *«Recupera el
  botín»*, *«Invoca al Umbral»*, *«Derrota al Umbral»*. Textos en español (y `en_us`).
- **Libros de lore** en ruinas/cofres (§11) que justifiquen el ritual y las debilidades,
  en vez de que el jugador tenga que leer el README.

---

## 10. 🎵 Música por fases y sonido ambiental procedural

- **Loop de persecución** que arranca con el grito y frena al desvanecerse (sonido
  `stream: true` en `sounds.json`, o tag de música reutilizando el sistema de jukebox).
- **Silencio antes del ataque:** suppressión breve del ambiente cuando entra en STALK a
  < 10 b (el silencio asusta más que el grito).
- Ampliar [scripts/generate_assets.py](scripts/generate_assets.py) con bucles de cueva
  reaccionando a la presencia (§2), ecos y versiones estéreo de `whisper` para mejor
  posicionamiento. Nuevos eventos de sonido → registrar en
  [ModSounds.java](src/main/java/com/elsilente/registry/ModSounds.java) + `sounds.json` +
  subtítulos en ambos lang.

---

## 11. 🏚️ Estructura de worldgen: «Santuario del Silente»

Estructura pequeña generada en Overworld (plantilla `.nbt` en `data/elsilente/structures/`):
ruinas con altar roto (tierra de almas agrietada), lápida, libro de lore y un cofre con un
Corazón Silente / piezas del ritual. Da **descubrimiento en exploración**, no solo combate.
Cuidado con el rendimiento: peso de generación bajo y compatibilidad con `SpawnAudit`.

---

## 12. 🧩 GeckoLib 4 + modelos de Blockbench *(ya aprobado en DISENO.md)*

Pendiente desde la Fase 1: dependencia
`software.bernie.geckolib:geckolib-forge-1.20.1:4.4.9` (verificar última 4.x estable),
`GeoEntityRenderer`, animaciones de Blockbench y **rehacer la capa emisiva de ojos**
(blanco STALK / rojo CHASE) sobre el sistema de color de GeckoLib. Si se cambia el atlas UV,
actualizar `scripts/generate_assets.py` (o arte manual con `--keep-art`). La migración de
la Sombra Débil (§1) debe quedar hecha **antes o junto** a este paso para no rehacer dos veces
el modelo.

---

## 13. 🌀 Reino de las sombras (apuesta grande)

Que la derrota del Umbral abra un **portal/reino de sombras** (dimensión propia o efecto de
espacio a nivel de bloque) en vez de agotar el contenido en un botín final. Fase endgame:
fuente de los Corazones, variantes del Silente y la versión «verdadera» del jefe. Es el
trabajo más grande del documento; solo después de §1–§7.

---

## ⚠️ Riesgos y comprobaciones comunes

1. **Sincronía UV modelo ↔ script:** toda textura generada debe coincidir con el modelo
   Java (regla ya establecida para `EYE_CORES`; ahora también para la Sombra Débil, §1).
2. **Sleep-blocker:** la Sombra Débil **no** debe heredar de `Monster` (bloqueo de sueño en
   `ServerPlayer.startSleepInBed`). Al cambiar su modelo no se toca la clase de entidad,
   pero si se amplía la hitbox revisar los radios de la mecánica del sueño (8 b).
3. **Multiplayer:** contadores/umbrales por UUID, con caps estrictos.
4. **Rendimiento client-side:** alucinaciones y niebla baratas; nada de entidades falsas
   con AI, solo render.
5. **Lang:** mantener `es_es.json` y `en_us.json` en sincronía; preferir `\u00c9` en `.java`.
6. **Validación:** sin tests unitarios → `.\gradlew build` + `.\gradlew runClient` +
   lectura de `run/logs/latest.log`.

---

## ✅ Checklist de codificación

**Prioridad 1**
- [ ] Skin 64×64 de la Sombra Débil: modelo con proporciones estándar + capas overlay
- [ ] Textura regenerada en `generate_assets.py` con UV sincronizadas (§1)
- [ ] Revisión de escala/hitbox y sombra emisiva de los ojos

**Prioridad 2**
- [ ] `PresenceHandler` (presencia por UUID, umbrales, fuentes)
- [ ] Alucinaciones client-side + niebla dinámica + vignette
- [ ] Overlay de pesadilla al despertar

**Prioridad 3**
- [ ] `SnuffLightGoal` (apagar antorchas) + config
- [ ] Rastro del botín (amuleto/mapa → Cofre del Silente)
- [ ] `ForgeConfigSpec` con claves de §6
- [ ] Defensas con coste (sal, campana, Símbolo mejorado)

**Prioridad 4**
- [ ] Puertas + advancements/lore + música por fases
- [ ] Estructura «Santuario del Silente»
- [ ] GeckoLib (junto a §1)
- [ ] Reino de las sombras (fase endgame)
