# El Silente de las Sombras (v1.0.0)

Mod de terror y acción para **Minecraft Java 1.20.1** sobre **Minecraft Forge 47.2.0+** (Java 17).

> 📖 **[GUIA.md](GUIA.md)** — guía completa de **historia, progresión, jugabilidad e items**. Empieza por ahí si es la primera vez que juegas al mod.

> **Dependencia requerida:** [**GeckoLib 4.4+**](https://www.curseforge.com/minecraft/mc-mods/geckolib) (`geckolib-forge-1.20.1`, se descarga solo con Forge). El renderizado de El Silente (modelos y animaciones) lo requiere.

---

## 📖 Descripción del Mod

"El Silente de las Sombras" introduce una entidad espectral y peligrosa que acecha al jugador desde la oscuridad absoluta. A diferencia de los monstruos comunes, El Silente prefiere observar inmóvil antes de atacar, huye si se le mantiene la mirada fija, desaparece con la luz o el agua, y embosca salvajemente a sus presas. Si logra acabar contigo, reclamará un botín y lo ocultará en un cofre en las inmediaciones.

---

## 👁️ Mecánicas y Comportamiento por Fases

### 1. Fase STALK (Acecho)
- **Ojos Blancos**: Permanece inmóvil a la distancia mirando fijamente al jugador.
- **Susurros**: Emite susurros posicionales (`elsilente:silente.whisper`).
- **Mirada sostenida (Stare detection)**: Si miras directamente a la entidad (~10°) durante **0,5 s** se desvanece entre humo negro y sonido de disipación, programando un acecho posterior. En persecución **no** se va por mirada: solo con luz, agua o distancia.
- **Vigilancia estática**: si el jugador se duerme, se materializa **al pie de la cama** y le mira hasta que despierta.
- **Movimientos contranatural**: cuando no hay línea de visión y está lejos (>12 b), con un 25 %/s se reubica hasta 6 bloques más cerca, **en silencio y siempre en la oscuridad** (prefiriendo los marcos de una puerta cercana).
- **Transición a Persecución**: Si el jugador se aproxima a menos de 8 bloques, emite un grito desgarrador (`elsilente:silente.scream`) y entra en modo agresivo.

### 2. Fase CHASE (Persecución y Ataque)
- **Ojos Rojos**: Su mirada cambia a rojo brillante y fullbright.
- **Respiración agitada**: Reproduce sonidos de persecución posicional (`elsilente:silente.chase`).
- **Ataque letal**: Inflige 10 de daño por golpe con retroceso considerable.
- **Más rápido y sin retroceso**: velocidad **0,34** (por encima del sprint) y resistencia de retroceso **1,0**.
- **Puntos débiles y desaparición**:
  - Si la luz del bloque es $\ge 8$, se desvanece instantáneamente.
  - Si toca el agua, se disipa al instante.
  - Si el jugador se aleja a más de 40 bloques, se retira.

### 3. Sombras Débiles (entidad neutral)
- **Solo te atacan si te acercas** a menos de 8 bloques: 1 corazón (2 HP) cada 3 segundos.
- **Invulnerables a todo** salvo el **Símbolo del Alba** (y las fuentes de luz las disipan).
- **Pesadilla**: si te duermes con una a menos de 8 bloques, **despiertas con la mitad de tu vida máxima** (aviso: *"¡Algo te observaba mientras dormías!"*).
- **Evolución**: cuando acumulan **10 HP** de daño sobre ti, se transforman en un **El Silente** que te persigue al instante.

---

## ⚡ Sistemas de Aparición

1. **Spawn Natural**:
   - **El Silente** aparece en biomas del Overworld con nivel de luz $\le 7$ (cualquier cueva u noche sin antorchas).
   - **Las Sombras Débiles** ($\texttt{elsilente:sombra\_debil}$) aparecen en zonas con luz $\le 7$ entre 16 y 48 bloques del jugador, **nunca a la vista directa** y con un máximo de **6 por jugador** (peso 50 por intento en 53/64 biomas).
   - Nunca aparece a la vista directa del jugador ni bajo la luz diurna del sol.
   - **Reino de las Sombras**: en la llanura de ceniza nacen Sombras Débiles, El Silente y los **Silentes Verdaderos** (120 HP, 2 Corazones garantizados).
2. **Emboscada Sorpresa (`AmbushHandler`)**:
   - De noche o en zonas oscuras, existe una probabilidad periódica de que se materialice repentinamente a espaldas del jugador (8–12 bloques) entrando directamente en fase CHASE con jumpscare de grito.
3. **Ritual de Invocación (`RitualHandler`)** — invoca al jefe **El Umbral**:
   - **Estructura**:
     - 1 bloque central de **Tierra de Almas** (`minecraft:soul_soil`).
     - Anillo perimetral de **8 bloques de Obsidiana** (`minecraft:obsidian`) rodeando el centro.
     - 4 **Velas encendidas** en las posiciones cardinales (Norte, Sur, Este, Oeste) sobre la obsidiana.
   - **Activación**: en **medianoche exacta** (hora 17400–18600) y con un **Corazón Silente empuñado**, lanza una **Perla de Ender** sobre el bloque central. Cae un rayo, se apagan las velas, se consume el corazón y aparece **El Umbral**.
   - 📖 **Manual paso a paso con diagrama, materiales y solución de problemas: [RITUAL.md](RITUAL.md).**

---

## 💎 Items y Crafteos

### Corazón Silente (`elsilente:corazon_silente`)
- Drop garantizado al derrotar a El Silente (además de posibles Perlas de Ender). El Silente Verdadero del Reino suelta **2**.
- **Uso activo (clic derecho)**: recupera **3 corazones** consumiéndolo, pero sube la presencia **+25** y programa la aparición de un El Silente en 60–150 s (enfriamiento 5 min). *Es SU corazón.*
- Esencia requerida para el ritual, el Símbolo del Alba y el Amuleto del Latido.

### Amuleto del Latido (`elsilente:amuleto_latido`)
- Crafteo en mesa de trabajo:
  ```
  [ Hilo ]      [ Hilo ]         [ Hilo ]
     -        String (S)            -
  Oro (G)   Corazón (H)          Oro (G)
     -        Oro (G)               -
  ```
- **Habilidad pasiva**: Al llevarlo en el inventario o en la mano, palpita en la dirección donde se encuentre El Silente más cercano (en un radio de 32 bloques):
  - *24–32 bloques*: Latidos lentos y apagados.
  - *14–24 bloques*: Latidos moderados.
  - *8–14 bloques*: Latidos rápidos e intensos.
  - *< 8 bloques*: Frecuencia máxima (alarma inminente).

---

### Símbolo del Alba (`elsilente:simbolo_alba`)
- **Único arma** capaz de dañar a las Sombras Débiles (todo lo demás les resulta inútil). Daño **6**.
- **Cargas de luz** (3 por defecto): golpe contra El Silente → +6 de daño (1 carga); clic derecho → **pulso de alba** que disipa Sombras a 12 b (1 carga); agachado + clic → recarga total con un Corazón Silente.
- Suelta partículas de ceniza mientras está empuñado y se repara con obsidiana.
- 🛠️ Crafteo:
  ```
     -        Corazón (C)       -
     -        Obsidiana (O)     -
     -         Palo (S)         -
  ```

## 💀 Mecánica de Robo (`TheftHandler`)

Si El Silente mata al jugador:
1. Extrae un objeto aleatorio de alto valor (con preferencia x3 a armas, herramientas o armadura).
2. Genera un cofre oculto en un radio de 20–40 bloques bautizado como *"Cofre del Silente"* con el item dentro.
3. Envía el mensaje siniestro al jugador: *"Algo ha sido tomado de ti..."*.
4. **Rastro del botín**: mientras el botín esté sin recuperar, el **Amuleto del Latido** late también hacia el Cofre del Silente (latido grave, cuanto más cerca más rápido). Al abrir el cofre el rastro desaparece.

---

## 🌫️ Presencia acumulativa (`PresenceHandler`)

Un medidor invisible (0–100, sincronizado al cliente) mide cuánto te ha notado el mundo de las sombras:

- **Sube** con emboscadas (+20), pesadillas al despertar (+20) y noches a oscuras (+2 cada 10 s).
- **Baja** con la luz (−2 cada 10 s) y durmiendo sin incidentes (−10).
- **Efectos**: niebla más espesa desde **30** (hasta +40 %), **alucinaciones** desde **40** (susurros y humo falsos detrás del jugador, solo visuales), vignette desde **60** y probabilidad de emboscada **+50 % al máximo**. Usar un Corazón Silente sube la presencia +25.

---

## ⚙️ Configuración (`config/elsilente-common.toml`)

Todo lo ajustable se genera con Forge en `config/elsilente-common.toml`:

| Clave | Default | Efecto |
|---|---|---|
| `general.ambushEnabled` | `true` | Activa las emboscadas. |
| `general.ambushChance` | `0.20` | Probabilidad por comprobación. |
| `general.ambushCooldownTicks` | `12000` | Enfriamiento entre emboscadas. |
| `general.theftEnabled` | `true` | Activa el robo de botín. |
| `general.snuffLightsEnabled` | `true` | El Silente apaga las luces cercanas en STALK. |
| `spawns.maxShadowsPerPlayer` | `6` | Cupo de Sombras Débiles por jugador. |
| `presence.presenceEnabled` | `true` | Sistema de presencia acumulativa. |
| `presence.openDoorsEnabled` | `true` | El Silente abre puertas de madera al acechar (§8). |
| `defenses.saltEnabled` | `true` | Línea de sal activa (§7). |
| `defenses.bellEnabled` | `true` | Campana de vigilia activa (§7). |
| `defenses.symbolCharges` | `3` | Cargas de luz del Símbolo del Alba. |
| `endgame.realmEnabled` | `true` | La muerte del Umbral abre la Grieta al Reino de las Sombras. |

---

## 🌀 Reino de las Sombras (fase endgame)

La derrota del Umbral ya no agota el contenido: su muerte abre una **Grieta del Umbral** (bloque `elsilente:portal_umbral`) junto al lugar donde cayó, con un aviso para los jugadores cercanos.

- **Cruzar**: basta con **pisar la grieta** (no tiene colisión). Guarda tu posición de retorno y te lleva a `elsilente:reino_sombras`, una dimensión propia: eterna noche, sin cielo, niebla violacea densa en todo momento y terreno de ceniza (`llanura_ceniza`).
- **Al llegar** se coloca una Grieta de vuelta a tu lado; ambos sentidos se usan pisándolos (con 10 s de enfriamiento para evitar rebotes).
- **Fuente de Corazones Silente**: el reino es su hogar. Los **Silentes Verdaderos** (`elsilente:silente_verdadero`) solo nacen ahí: la versión «verdadera» del jefe (120 HP, 14 de daño, 0,36 de velocidad) que suelta **2 Corazones Silente** garantizados.
- También acechan **El Silente** y **Sombras Débiles** en la oscuridad del reino.
- **Advancements**: *«Cruzaste el Umbral»* y *«Le arrancaste su Corazón»*.
- Se puede desactivar con `endgame.realmEnabled=false`.

---

## 🕯️ Luces apagadas (`SnuffLightGoal`)

En fase STALK, si no le estás mirando, El Silente se desplaza hasta una **antorcha, linterna, vela u hoguera** cercana (≤ 12 b) y la extingue con un soplido de humo antes de continuar el acecho. Tu base deja de ser un refugio seguro: hay que mantener las luces y vigilar las sombras.

---

## 🚪 La base deja de ser segura (`OpenDoorGoal`)

Complemento del apagado de luces: si no hay línea de visión con el jugador y hay una **puerta de madera cerrada** cerca, El Silente camina hasta ella, la abre con un **rasgado de garras** y la cruza hacia tu lado. Cooldown por entidad para evitar el taladro de puertas; configurable (`presence.openDoorsEnabled`).

---

## 🛡️ Defensas activas con coste (§7)

- **Línea de sal** (`elsilente:linea_sal`, 3 azúcares → 3): cojinete que bloquea el **spawn natural** de Sombras en un radio de **12 b** (±10 vertical). No detiene emboscadas y se consume con el tiempo/la lluvia.
- **Campana de vigilia** (`elsilente:campana_vigilia`, campana + 2 arena de almas): clic derecho → cancela la **emboscada de esa noche**, pero atrae **1–2 Sombras Débiles** esa misma noche (enfriamiento 5 min). El bronce también llama.

---

## 🏚️ Santuario del Silente (§11)

Estructura de worldgen en el Overworld: ruinas 9×3×9 con altar de tierra de almas agrietada, lápida, **lectern con «El diario del Vigía»** (lore que explica el ritual y las debilidades) y un cofre con **Corazón Silente + piezas del ritual**. Descubrimiento en exploración, no solo combate.

---

## 🎵 Música y sonido ambiental (§10)

- **Loop de persecución** (`chase_loop`, `stream: true`) que arranca con el grito y frena al desvanecerse.
- **Silencio antes del ataque** (`stalk_silence`): suppressión breve del ambiente al entrar en STALK a < 10 b.
- **Bucle de cueva** (`cave_dread`) reactivo a la presencia, susurros con versiones estéreo y garras en las puertas. Todo generado proceduralmente (`scripts/generate_assets.py`).

---

## 👹 El Umbral (jefe final — Fase 2)

Invocado con el ritual de medianoche (o `/summon elsilente:umbral`).

| Propiedad | Valor |
|---|---|
| Vida | 300 HP · barra de jefe morada |
| Daño / velocidad | 12 por golpe · 0,30 · sin retroceso |
| Fase 1 (>50 %) | Invoca **Sombras Débiles** (1 cada 5 s, máx. 4 vivas) |
| Fase 2 (≤50 %) | Ruge, invoca **El Silente** (1 cada 15 s, máx. 2), recibe **+10 % de daño** y usa el **slam** (telegrado 0,8 s: 6 de daño + empuje + oscuridad a 5 b) |
| Enrage (<25 %) | Rugido, +27 % de velocidad y slam cada 5 s (antes 8 s) |
| Drops (100 %) | **Trofeo del Umbral** + **Guadaña del Segador**. Su muerte abre la **Grieta del Umbral** (§13) |

### Botín y utilidades
- **Trofeo del Umbral** (`elsilente:trofeo_umbral`, bloque): emite ceniza, ilumina (nivel 8), **disipa toda Sombra Débil a 20 bloques** y crea una **zona segura de 12 b**: El Silente no entra (se retira con humo) ni nace dentro.
- **Guadaña del Segador** (`elsilente:guadana_segador`): daño 9, aplica **`Darkness` 5 s** y hace **+30 % de daño de noche**. **Carga de almas**: 1 por criatura muerta (máx. 5); llena, el próximo golpe libera **+8 de daño** y un estallido de **4 a 5 bloques** (y la guadaña brilla).
- **Lámpara del Penitente** (`elsilente:lampara_penitente`): mientras la lleves **repele a las Sombras Débiles a 8 bloques**; clic derecho → **destello** que las empuja a 12 b (enfriamiento 15 s). Sin receta: creativo (ruinas en el futuro).

---

## 📦 Instalación

1. Asegúrate de tener instalado **Minecraft Java 1.20.1** con **Forge 47.2.0** o superior.
2. Copia el archivo compilado:
   ```
   build/libs/elsilente-1.0.0.jar
   ```
   a la carpeta `.minecraft/mods`.
3. Inicia el juego desde el lanzador con el perfil de Forge.

---

## 🧪 Comandos de Prueba

- **Generar entidad**:
  ```mcfunction
  /summon elsilente:el_silente ~ ~ ~
  ```
- **Hacer de noche**:
  ```mcfunction
  /time set night
  ```
- **Obtener items**:
  ```mcfunction
  /give @p elsilente:corazon_silente
  /give @p elsilente:amuleto_latido
  /give @p elsilente:simbolo_alba
  /give @p elsilente:guadana_segador
  /give @p elsilente:lampara_penitente
  /give @p elsilente:trofeo_umbral
  /give @p elsilente:el_silente_spawn_egg
  /give @p elsilente:sombra_debil_spawn_egg
  /give @p elsilente:umbral_spawn_egg
  /give @p elsilente:silente_verdadero_spawn_egg
  /give @p elsilente:portal_umbral
  ```
- **Viajar al reino de las sombras**:
  ```mcfunction
  /execute in elsilente:reino_sombras run tp @p 0 100 0
  ```
- **Invocar entidades**:
  ```mcfunction
  /summon elsilente:sombra_debil ~ ~ ~
  /summon elsilente:umbral ~ ~ ~
  ```
# El-silencio-de-las-Sombras-MOD
