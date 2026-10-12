# Manual de invocación — El Silente de las Sombras

Guía paso a paso del **ritual de invocación**. En su forma actual el ritual es
**mayor**: a medianoche exacta, con un Corazón Silente empuñado, invoca al jefe
**El Umbral** (barra de jefe, invoca sombras y silentes).

---

## 1. Materiales

| Cantidad | Bloque | ID |
|---|---|---|
| 1 | Tierra de Almas | `minecraft:soul_soil` |
| 8 | Obsidiana | `minecraft:obsidian` |
| 4 | Vela encendida (cualquier color) | `minecraft:candle` |
| 1 | **Corazón Silente** (empuñado al lanzar) | `elsilente:corazon_silente` |
| 1 | Perla de Ender (para activar) | `minecraft:ender_pearl` |

Todo en el **Overworld** y todo **a ras de suelo** salvo las velas.

---

## 2. Construcción del altar

Coloca los 9 bloques de abajo en el suelo (3×3). Esquina = obsidiana,
centro de cada lado = obsidiana con una vela **encima**:

```
          norte
    ┌───────┬───────┬───────┐
    │ Obsid.│Vela 🔥│Obsid. │   Obsid. = Obsidiana a ras de suelo
    ├───────┼───────┼───────┤   Vela   = Vela encendida UN BLOQUE
    │Vela 🔥│Tierra │Vela 🔥│           más arriba, sobre la obsidiana
    ├───────┼───────┼───────┤   Tierra = Tierra de Almas (centro)
    │ Obsid.│Vela 🔥│Obsid. │
    └───────┴───────┴───────┘
          sur
```

Reglas que comprueba el juego:

1. El centro **tiene que ser** Tierra de Almas.
2. Los **8 bloques vecinos** (las 4 esquinas y los 4 lados) **tienen que ser Obsidiana**.
3. En las **4 posiciones cardinales** (norte, sur, este y oeste del centro), **sobre** la
   obsidiana, debe haber una **vela encendida**. Si se apaga sola, vuelve a encenderla
   antes de intentarlo: las velas apagadas no valen.

> Nota: antes de la v1.0.0 el código exigía obsidiana *y* vela en el mismo bloque, lo que
> hacía **imposible** activar el ritual. Ya está corregido: la vela va encima.

---

## 3. Activación

1. Espera a que sea **medianoche exacta**: la hora del mundo debe estar entre
   las **17400 y 18600** (`/time set midnight`). De día o a otra hora no funciona.
2. **Empuña un Corazón Silente** en la mano principal (se consume al invocar).
3. De pie a la orilla del altar, **apunta al bloque central de Tierra de Almas**.
4. Lanza una **Perla de Ender** encima de él.

Si la estructura es correcta ocurre, en este orden:

- La perla se anula (no te teletransporta) y **el Corazón Silente desaparece** de tu mano.
- Suena un **trueno** y cae un **destello** con humo negro sobre el altar.
- Las **4 velas se apagan** y suena un **rugido**.
- **El Umbral** aparece **encima de la tierra de almas** con su **barra de jefe**
  y te fija como objetivo.

> Para invocar solo a El Silente (sin jefe) usa `/summon elsilente:el_silente`.

---

## 4. Qué pasa después

- **El Umbral (300 HP)**: 12 de daño por golpe, no retrocede y **nunca se desvanece**.
- **Fase 1 (>50 %)**: invoca **Sombras Débiles** (una cada 5 s, máximo 4 vivas).
- **Fase 2 (≤50 %)**: ruge, invoca **El Silente** (una cada 15 s, máximo 2) y
  **recibe un 10 % más de daño** por golpe.
- **Drops garantizados**: **Trofeo del Umbral** (repele Sombras Débiles a 20 b) y
  **Guadaña del Segador** (`Darkness` 5 s, +30 % de daño de noche).

---

## 5. Solución de problemas

| Sintoma | Causa probable |
|---|---|
| La perla vuela normal y no pasa nada | No es medianoche (hora 17400–18600) o la estructura no es válida. |
| Es de noche pero no invoca | **No estás empuñando el Corazón Silente** en la mano principal. |
| No cae el rayo ni se apagan las velas | Revisa los 8 vecinos: todos deben ser obsidiana. |
| Las velas ya están apagadas | Enciéndelas de nuevo (`flint and steel` o antorcha) antes de lanzar. |
| La perla golpea pero no invoca | Comprueba que golpeó la **Tierra de Almas**, no un bloque vecino lejano. |
| No veo nada en el log | Busca en `logs/latest.log` la línea `[El Silente] Ritual de invocación completado en ...`. |
| Quiero comprobar la estructura sin gastar perlas | Usa `/summon elsilente:el_silente` y verifica la parte visual por separado. |

---

## 6. Comandos de prueba

```
/time set midnight
/give @s minecraft:soul_soil
/give @s minecraft:obsidian 8
/give @s minecraft:candle 4
/give @s minecraft:ender_pearl
/give @s elsilente:corazon_silente
/summon elsilente:umbral
```

---

## 7. Otras formas de aparecer

- **Spawn natural**: de noche o en cuevas con luz ≤ 7, en Overworld, sin jugadores a
  menos de 20 bloques y fuera de tu cono de visión. **El Silente**: peso 40;
  **Sombras Débiles**: peso 80. Al arrancar el servidor, `logs/latest.log` muestra
  `[El Silente] Spawn natural de ... en N/64 biomas, peso ... por intento`.
- **Emboscada** (`AmbushHandler`): de noche, con probabilidad periódica, se materializa
  a 8–12 bloques a tus espaldas y empieza a perseguirte.
