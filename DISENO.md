# 🌑 Diseño de expansión — El Silente de las Sombras (v1.1)

> **Estado:** Fases 1 y 2 **implementadas y superadas** por la ronda de
> mejoras de `MEJORAS.md` (§1–§14 completas: presencia, alucinaciones,
> GeckoLib, reino de sombras, pack de jugabilidad...). Este documento queda
> como diseño histórico de la primera expansión. Guía de juego actualizada:
> `GUIA.md`. Stack: MC **1.20.1**, Forge **47.2.0**, Java 17, mod id `elsilente`.
> Fuente de inspiración: `El silencio de las sombras.md` (propuesta original).

## ✅ Decisiones ya tomadas

| Punto | Decisión |
|---|---|
| Mirada directa al Silente en STALK | **Se disuelve rápido (~0,5–1 s)**. En CHASE solo se retira con luz / agua / >40 b. |
| Guadaña del segador de noche | **+30 % de daño** (no el 3 % literal). |
| Lámpara del penitente | **Repele Sombras débiles en 8 bloques** (sin matarlas). |
| Modelo del mob | **Añadir GeckoLib 4** para importar diseños de Blockbench. |
| Orden de implementación | **Fase 1** (Sombras + mejoras del Silente) → **Fase 2** (Boss). |

---

# FASE 1 — Sombras débiles + mejoras del Silente

## 1.1 Entidad: **Sombra Débil** (`elsilente:sombra_debil`)

| Propiedad | Valor |
|---|---|
| Clase base | **`Mob` (NO `Monster`)** — verificado en bytecode: `ServerPlayer.startSleepInBed` bloquea el sueño con `getEntitiesOfClass(Monster, ±8 b, ±5 b)`. Al no ser `Monster`, **el jugador puede dormir con ella al lado** (requisito de la mecánica del sueño). |
| Vida | 20 HP (10 corazones) |
| Velocidad | 0,22 (más lenta que el jugador andando: 1,0 atributo ≈ 0,21) |
| Seguimiento | 16 b · Resistencia a retroceso 1,0 |
| Aparición | Luz ≤ 7 en Overworld, a 16–48 b del jugador, **fuera de su cono de mirada** (dot ≤ 0,7), **máx. 6 vivas por jugador** (contador por UUID) |
| XP / drops | 0 XP, sin drops (mantiene la tensión) |

**Comportamiento:**

1. **Neutral**: patrulla/flota en silencio. Solo persigue si el jugador está a **< 8 b**.
2. **Ataque**: 2 HP (1 corazón) cada **60 ticks (3 s)** al jugador a < 8 b.
3. **Invulnerable a golpes**: `isInvulnerableTo()` → `true` salvo el **Arma simbólica** (ver 1.3).
4. **Se disipa con luz**: si la luz de su bloque es ≥ 8 → partículas + `elsilente:silente.vanish` y `discard()` (libera cupo).
5. **Mecánica del sueño**: en `PlayerWakeUpEvent`, si hay una Sombra a ≤ 8 b → vida del jugador = **mitad de su vida máxima** + mensaje *"Algo te observaba mientras dormías"* + sonido susurro.
6. **Evolución**: acumula el daño que hace al jugador; al llegar a **10 HP (5 corazones)** → `discard()` + invoca un `ElSilenteEntity` con `startChase(jugador)` (grito + CHASE + partículas).

> Nota de balance: antes de tener el Arma simbólica, la única defensa es la **luz** (antorcha en mano).

## 1.2 Mejoras a **El Silente**

| Mejora | Implementación |
|---|---|
| Más rápido (difícil escapar, no imposible) | `MOVEMENT_SPEED` **0,30 → 0,34**; mantener presión en CHASE (sin frenadas de pathfinding). |
| Sin retroceso | `KNOCKBACK_RESISTANCE` **0,5 → 1,0**. |
| Ojos más pequeños y brillantes | Reducir la cuenca negra (hoy ocupa todo el ancho de la cara) y ojos de **2×2 px** (y 11–12), blanco puro + halo fuerte en la capa emisiva. |
| Esquive rápido (STALK) | Umbral de mirada directa **60 → 10 ticks (0,5 s)** y desaparece. |
| Comportamiento en CHASE | **Sin cambios**: se retira con luz ≥ 8, agua o distancia > 40 b. |
| Vigilancia estática | Ya inmóvil; añadir: **pie de la cama** (si un jugador duerme a ≤ 6 b, se coloca al pie mirándolo) y **marcos de puerta** (colocación junto a puertas en zonas oscuras). |
| Movimientos contranatural | **Teleport corto** (≤ 6 b, 25 %/seg) hacia el jugador cuando **no hay línea de visión** y está a > 12 b. Sin sonido. *(Nada de atravesar paredes "de verdad": rompe IA/colisiones; el teleport se percibe igual.)* |
| Capa de miedo (opcional) | A < 4 b en CHASE: latido fuerte + `Darkness` leve de 2 s (cosmético). |

## 1.3 Item: **Arma simbólica** — `elsilente:simbolo_alba` *(nombre provisional)*

- **Función única**: es el **único** objeto que daña a las Sombras débiles.
- Daño **6** (4 golpes para matar una Sombra de 20 HP); daño normal contra el Silente.
- Crafteo propuesto:
  ```
  [  -  ] [Corazón Silente] [  -  ]
  [  -  ] [   Obsidiana    ] [  -  ]
  [  -  ] [      Palo      ] [  -  ]
  ```
- Visual: partículas de ceniza al empuñarlo.

## 1.4 Migración a **GeckoLib** (diseño de modelos en Blockbench)

- Dependencia Forge: `software.bernie.geckolib:geckolib-forge-1.20.1:4.4.9`
  (maven: `https://dl.cloudsmith.io/public/geckolib3/geckolib/maven/`, vía `fg.deobf(...)`) — *verificar la última 4.x estable al codificar*.
- Cambios: `build.gradle` + `mods.toml` (dependencia), renderer de El Silente → `GeoEntityRenderer`, capa emisiva de ojos reimplementada sobre el sistema de colores de GeckoLib.
- **Flujo de arte a partir de ahora**: tú modelas/animas en Blockbench (plugin GeckoLib) y exportas `.geo.json` + `.animation.json` + tu PNG. Hasta que llegue tu diseño, se mantiene el modelo Java actual para no romper nada.
- ⚠️ Si cambia el atlas UV hay que actualizar `scripts/generate_assets.py` (o pasar a arte manual con el flag `--keep-art` que hay que añadirle para **no sobrescribir tus PNG**).

---

# FASE 2 — Boss **El Umbral** + objetos

## 2.1 Invocación: ritual ampliado

- Mismo altar que hoy (tierra de almas + 8 obsidianas + 4 velas) pero exigiendo **medianoche exacta** (ventana de ±600 ticks sobre 18000) y **sostener un Corazón Silente** al lanzar la perla.
- Añade: rayo + apagado de velas + rugido + barra de jefe. Reutiliza `RitualHandler`.

## 2.2 Entidad: **El Umbral** (`elsilente:umbral`)

| Propiedad | Valor |
|---|---|
| Vida | 300 HP · barra de jefe (`ServerBossEvent`, nombre *"El Umbral"*) |
| Ataque / velocidad | 12 de daño · 0,30 · resistencia retroceso 1,0 |
| XP | 100 |

**Fases de combate:**

1. **100 % – 50 %**: invoca **Sombras débiles** (cada 100 ticks, **máx. 4 simultáneas**).
2. **≤ 50 %**: además invoca **El Silente** (cada 300 ticks, **máx. 2**) y **recibe +10 % de daño por golpe**.

**Drops (100 %):** `trofeo_umbral` + `guadana_segador`.

## 2.3 Objetos nuevos

| Objeto | Efecto |
|---|---|
| **Trofeo del Umbral** (`elsilente:trofeo_umbral`, bloque) | Emite partículas negras; **disipa Sombras débiles a 20 b** y **anula su spawn natural en radio 20 b** (handler de servidor). |
| **Guadaña del segador** (`elsilente:guadana_segador`) | Daño 9 · aplica efecto **`Darkness` 5 s** (100 ticks) · **+30 % de daño si es de noche**. |
| **Lámpara del penitente** (`elsilente:lampara_penitente`) | **Repele Sombras débiles a 8 b** (no las mata: las mantiene fuera de radio). Receta: lámpara de almas + 2 pepitas de oro + 2 pepitas de hierro + tinte cian. |

---

## 📦 Registros previstos

- `ModEntities`: `sombra_debil`, `umbral` (+ huevos de invocación).
- `ModItems`: `simbolo_alba`, `guadana_segador`, `lampara_penitente`.
- `ModBlocks` (**nuevo registro**): `trofeo_umbral`.
- `ModSounds`: `silente.nightmare` (sueño), `silente.shadow_idle`, `silente.umbral_roar`, `silente.umbral_summon`.
- Lang `es_es` + `en_us` en sincronía; `scripts/generate_assets.py` para texturas/sonidos nuevos (o tu arte a mano).

## ⚠️ Riesgos y comprobaciones

1. **Sueño**: confirmado que el bloqueo usa `Monster` → la Sombra **no** debe heredar de `Monster`.
2. **Rendimiento**: caps estrictos (6 Sombras/jugador, 4+2 en el boss); nada de spawning continuo.
3. **GeckoLib**: la capa emisiva de ojos debe rehacerse; verificar blanco (STALK) / rojo (CHASE).
4. **Multiplayer**: contadores por UUID (mismo patrón que `PENDING_SPAWNS`).
5. **Sin tests unitarios** en el proyecto → validación con `./gradlew build` + `runClient` + lectura de `run/logs/latest.log`.

## ✅ Checklist de codificación

**Fase 1**
- [ ] Dependencia GeckoLib en `build.gradle` + `mods.toml` *(pendiente: se hace al recibir el modelo de Blockbench)*
- [x] `SombraDebilEntity` (atributos, goals, invulnerabilidad, luz, sueño, evolución)
- [x] Spawn placement + cap por jugador + lang + huevo
- [x] Mejoras del Silente (velocidad, retroceso, esquive 0,5 s, teleport, pie de la cama)
- [x] Textura de ojos reducida (2×2) en `generate_assets.py`
- [x] `simbolo_alba` (crafteo, daño, partículas)
- [x] Flag `--keep-art` en `generate_assets.py`
- [x] README + este documento actualizados · build + prueba en cliente

**Fase 2**
- [x] Ritual ampliado (medianoche + corazón)
- [x] `UmbralEntity` (barra, fases, invocaciones, +10 %)
- [x] Bloque `trofeo_umbral` (partículas + repelente 20 b)
- [x] `guadana_segador` (+30 % nocturno, `Darkness`)
- [x] `lampara_penitente` (repelente 8 b)
- [x] Sonidos nuevos + lang + README/RITUAL.md · build + prueba en cliente
