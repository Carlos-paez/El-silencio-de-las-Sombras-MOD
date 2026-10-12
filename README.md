# El Silente de las Sombras (v1.0.0)

Mod de terror y acción para **Minecraft Java 1.20.1** sobre **Minecraft Forge 47.2.0+** (Java 17).

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
- Drop garantizado al derrotar a El Silente (además de posibles Perlas de Ender).
- Esencia requerida para la forja de amuletos protectores.

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

---

## 👹 El Umbral (jefe final — Fase 2)

Invocado con el ritual de medianoche (o `/summon elsilente:umbral`).

| Propiedad | Valor |
|---|---|
| Vida | 300 HP · barra de jefe morada |
| Daño / velocidad | 12 por golpe · 0,30 · sin retroceso |
| Fase 1 (>50 %) | Invoca **Sombras Débiles** (1 cada 5 s, máx. 4 vivas) |
| Fase 2 (≤50 %) | Ruge, invoca **El Silente** (1 cada 15 s, máx. 2) y recibe **+10 % de daño** |
| Drops (100 %) | **Trofeo del Umbral** + **Guadaña del Segador** |

### Botín y utilidades
- **Trofeo del Umbral** (`elsilente:trofeo_umbral`, bloque): emite ceniza, ilumina (nivel 8) y **disipa toda Sombra Débil a 20 bloques**.
- **Guadaña del Segador** (`elsilente:guadana_segador`): daño 9, aplica **`Darkness` 5 s** y hace **+30 % de daño de noche**.
- **Lámpara del Penitente** (`elsilente:lampara_penitente`): mientras la lleves **repele a las Sombras Débiles a 8 bloques**. Crafteo:
  ```
  [ Oro ] [ Cian ] [ Oro ]
  [ Hierro ] [Lámpara de almas] [ Hierro ]
     -        [ Oro ]        -
  ```

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
  ```
- **Invocar entidades**:
  ```mcfunction
  /summon elsilente:sombra_debil ~ ~ ~
  /summon elsilente:umbral ~ ~ ~
  ```
# El-silencio-de-las-Sombras-MOD
