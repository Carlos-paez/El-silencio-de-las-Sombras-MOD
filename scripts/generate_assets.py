#!/usr/bin/env python3
"""
generate_assets.py — Genera las texturas PNG y los sonidos OGG (mono) para
el mod "El Silente de las Sombras" (elsilente).

Requisitos:
    pip install numpy pillow soundfile
Opcional (respaldo de codificacion):
    pip install imageio-ffmpeg

Uso:
    python scripts/generate_assets.py
"""

import os
import struct
import shutil
import subprocess
import sys
import wave

import numpy as np
from PIL import Image

SR = 44100

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ASSETS = os.path.join(ROOT, "src", "main", "resources", "assets", "elsilente")
TEX_ENTITY = os.path.join(ASSETS, "textures", "entity")
TEX_ITEM = os.path.join(ASSETS, "textures", "item")
TEX_BLOCK = os.path.join(ASSETS, "textures", "block")
SOUNDS = os.path.join(ASSETS, "sounds")


def ensure_dirs():
    for d in (TEX_ENTITY, TEX_ITEM, TEX_BLOCK, SOUNDS):
        os.makedirs(d, exist_ok=True)


# ---------------------------------------------------------------------------
# DSP helpers
# ---------------------------------------------------------------------------

def lowpass(x, cutoff, sr=SR):
    a = (2.0 * np.pi * cutoff / sr)
    a = a / (a + 1.0)
    y = np.empty_like(x)
    prev = 0.0
    for i in range(len(x)):
        prev = prev + a * (x[i] - prev)
        y[i] = prev
    return y


def highpass_simple(x, cutoff, sr=SR):
    return x - lowpass(x, cutoff, sr)


def bandpass(x, low, high, sr=SR):
    return lowpass(highpass_simple(x, low, sr), high, sr)


def normalize(x, peak=0.9):
    m = np.max(np.abs(x))
    if m < 1e-9:
        return x
    return x * (peak / m)


def ramp(n, start, end):
    return np.linspace(start, end, n, endpoint=False)


def sine(freq_array):
    phase = np.cumsum(2.0 * np.pi * freq_array / SR)
    return np.sin(phase)


def saw(freq_array):
    phase = np.cumsum(freq_array / SR)
    return 2.0 * (phase - np.floor(phase + 0.5))


def exp_decay(n, tau):
    t = np.arange(n) / SR
    return np.exp(-t / tau)


def silence(seconds):
    return np.zeros(int(SR * seconds))


# ---------------------------------------------------------------------------
# Sound recipes
# ---------------------------------------------------------------------------

def make_heartbeat():
    total = np.zeros(int(SR * 0.6))
    n1 = int(SR * 0.12)
    t1 = np.arange(n1) / SR
    beat1 = np.sin(2 * np.pi * 55 * t1) * exp_decay(n1, 0.05)
    total[:n1] += beat1
    n2 = int(SR * 0.22)
    t2 = np.arange(n2) / SR
    beat2 = np.sin(2 * np.pi * 50 * t2) * exp_decay(n2, 0.07) * 0.7
    start2 = int(SR * 0.22)
    total[start2:start2 + n2] += beat2[:len(total) - start2]
    return normalize(total)


def make_whisper():
    n = int(SR * 2.5)
    rng = np.random.default_rng(7)
    noise = lowpass(rng.standard_normal(n), 1200.0)
    env = np.zeros(n)
    syllables = rng.integers(3, 7)
    for _ in range(int(syllables)):
        center = rng.integers(0, n)
        width = rng.integers(int(SR * 0.12), int(SR * 0.35))
        half = width // 2
        lo = max(0, center - half)
        hi = min(n, center + half)
        length = hi - lo
        if length <= 0:
            continue
        env[lo:hi] += np.hanning(length) * rng.uniform(0.5, 1.0)
    fade = int(SR * 0.3)
    env[:fade] *= np.linspace(0, 1, fade)
    env[-fade:] *= np.linspace(1, 0, fade)
    whisper = noise * np.clip(env, 0, 1) * 0.6
    return normalize(whisper, 0.7)


def make_scream():
    n = int(SR * 1.0)
    freq = np.linspace(400, 1800, n)
    body = saw(freq)
    rng = np.random.default_rng(3)
    noise = rng.standard_normal(n)
    mixed = body * 0.7 + noise * 0.3
    mixed = np.tanh(3.0 * mixed)
    env = np.ones(n)
    attack = int(SR * 0.005)
    env[:attack] *= np.linspace(0, 1, attack)
    release = int(SR * 0.15)
    env[-release:] *= np.linspace(1, 0, release)
    return normalize(mixed * env)


def make_vanish():
    n = int(SR * 1.2)
    freq = np.linspace(300, 80, n)
    tone = np.sin(np.cumsum(2 * np.pi * freq / SR))
    rng = np.random.default_rng(11)
    noise = lowpass(rng.standard_normal(n), 900.0) * 0.4
    mixed = tone * 0.8 + noise
    env = np.linspace(0.2, 1.0, n) ** 2
    cut = int(SR * 1.05)
    env[cut:] = 0.0
    return normalize(mixed * env)


def make_attack():
    n = int(SR * 0.15)
    t = np.arange(n) / SR
    body = np.sin(2 * np.pi * 110 * t) * exp_decay(n, 0.03)
    click_n = int(SR * 0.02)
    rng = np.random.default_rng(5)
    click = np.zeros(n)
    click[:click_n] = rng.standard_normal(click_n) * np.linspace(1, 0, click_n)
    return normalize(body + click * 0.8)


def make_chase():
    n = int(SR * 1.5)
    rng = np.random.default_rng(21)
    noise = bandpass(rng.standard_normal(n), 250.0, 500.0)
    env = np.zeros(n)
    breathe = int(SR * 0.5)
    inhale = int(n / 2)
    env[:inhale] = np.linspace(0.1, 1.0, inhale)
    env[inhale:] = np.linspace(1.0, 0.1, n - inhale)
    env *= 0.8 + 0.2 * np.sin(np.linspace(0, 2 * np.pi * 2, n))
    return normalize(noise * env, 0.6)


def make_nightmare():
    """Pesadilla (Fase 2): gemido descendente con temblor y susurro (1.6 s)."""
    n = int(SR * 1.6)
    rng = np.random.default_rng(13)
    voice = saw(ramp(n, 190.0, 70.0))
    tremor = 0.6 + 0.4 * np.sin(2.0 * np.pi * 6.0 * np.arange(n) / SR)
    noise = bandpass(rng.standard_normal(n), 400.0, 1800.0)
    env = np.ones(n)
    env[-int(SR * 0.4):] = np.linspace(1.0, 0.0, int(SR * 0.4))
    return normalize((voice * tremor * 0.8 + noise * 0.5) * env)


def make_shadow_idle():
    """Susurro grave y lento de las Sombras Debiles (2.2 s, suave)."""
    n = int(SR * 2.2)
    rng = np.random.default_rng(17)
    pad = sine(ramp(n, 66.0, 63.0)) * 0.6 + sine(ramp(n, 99.0, 96.0)) * 0.3
    breath = 0.5 + 0.5 * np.sin(2.0 * np.pi * 0.6 * np.arange(n) / SR)
    noise = bandpass(rng.standard_normal(n), 250.0, 900.0) * 0.35
    return normalize((pad + noise) * breath, 0.5)


def make_umbral_roar():
    """Rugido del jefe: barrido grave con crecimiento (1.9 s)."""
    n = int(SR * 1.9)
    rng = np.random.default_rng(23)
    body = saw(ramp(n, 95.0, 38.0))
    growl = bandpass(rng.standard_normal(n), 120.0, 600.0)
    env = np.clip(np.linspace(0.0, 1.6, n) * np.exp(-np.arange(n) / (SR * 1.4)), 0.0, 1.6)
    return normalize((body * 0.8 + growl * 0.6) * env, 0.95)


def make_umbral_summon():
    """Invocacion del jefe: crescendo oscuro con subidón (1.3 s)."""
    n = int(SR * 1.3)
    rng = np.random.default_rng(29)
    swell = bandpass(rng.standard_normal(n), 150.0, 700.0)
    env = np.linspace(0.0, 1.0, n) ** 2
    env[-int(SR * 0.15):] *= np.linspace(1.0, 0.0, int(SR * 0.15))
    sub = sine(ramp(n, 40.0, 70.0)) * env * 0.6
    return normalize(swell * env + sub, 0.9)


RECIPES = {
    "heartbeat": make_heartbeat,
    "whisper": make_whisper,
    "scream": make_scream,
    "vanish": make_vanish,
    "attack": make_attack,
    "chase": make_chase,
    "nightmare": make_nightmare,
    "shadow_idle": make_shadow_idle,
    "umbral_roar": make_umbral_roar,
    "umbral_summon": make_umbral_summon,
}


def write_wav(path, data):
    data_i = np.clip(data, -1.0, 1.0)
    data_i = (data_i * 32767.0).astype(np.int16)
    with wave.open(path, "w") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(SR)
        w.writeframes(data_i.tobytes())


def find_ffmpeg():
    exe = shutil.which("ffmpeg")
    if exe:
        return exe
    try:
        import imageio_ffmpeg
        return imageio_ffmpeg.get_ffmpeg_exe()
    except Exception:
        return None


def write_ogg(name, data):
    ogg_path = os.path.join(SOUNDS, name + ".ogg")
    try:
        import soundfile as sf
        sf.write(ogg_path, data.astype(np.float32), SR, format="OGG", subtype="VORBIS")
        return ogg_path, "soundfile"
    except Exception as exc:
        print("  soundfile no disponible (%s); probando ffmpeg..." % exc)
    ffmpeg = find_ffmpeg()
    if ffmpeg is None:
        return None, None
    wav_path = os.path.join(SOUNDS, name + ".wav")
    write_wav(wav_path, data)
    cmd = [ffmpeg, "-y", "-loglevel", "error", "-i", wav_path,
           "-ac", "1", "-c:a", "libvorbis", "-qscale:a", "5", ogg_path]
    subprocess.run(cmd, check=True)
    os.remove(wav_path)
    return ogg_path, "ffmpeg"


def generate_sounds():
    print("Generando sonidos...")
    for name, fn in RECIPES.items():
        data = fn()
        path, backend = write_ogg(name, data)
        if path is None:
            print("  [!] No se pudo generar %s.ogg (sin soundfile ni ffmpeg)." % name)
        else:
            print("  [ok] %s.ogg (mono, %s)" % (name, backend))


# ---------------------------------------------------------------------------
# Textures
# ---------------------------------------------------------------------------

COL_VOID = (3, 3, 3, 255)
COL_NOISE1 = (10, 10, 10, 255)
COL_NOISE2 = (20, 20, 22, 255)
COL_HALO = (90, 90, 90, 255)
COL_WHITE = (255, 255, 255, 255)
COL_RED = (255, 48, 48, 255)
COL_TRANSPARENT = (0, 0, 0, 0)


# ---------------------------------------------------------------------------
# Atlas UV de El Silente (64x64). DEBE coincidir con ElSilenteModel.java:
#   cara frontal de la cabeza : (8,8)-(16,16)
#   ojos (capa emisiva)       : x 9-10 y 13-14, y 11-12 (2x2, pequenos y brillantes)
#   frente de la capucha      : (40,8)-(48,16)
#   pecho (costillas)         : (20,20)-(28,32)
#   sotana delantera          : (5,37)-(14,44)
#   mano derecha / izquierda  : (33,37)-(38,41) / (21,53)-(26,57)
#
# Atlas UV de la Sombra Debil (64x64). DEBE coincidir con SombraDebilModel.java:
#   cabeza 6x6x6      : (0,0)-(24,12)   -> cara frontal en (6,6)-(12,12)
#   torso 8x8x4       : (0,12)-(24,24)  -> pecho en (4,16)-(12,24)
#   brazos 2x8x2      : (24,12)-(40,22)
#   piernas 4x6x4     : (0,24)-(32,34)
#   ojos de la sombra : x=7,8 y x=10,11 ; y=8,9
# ---------------------------------------------------------------------------
EYE_CORES = ((9, 11, 11, 13), (13, 15, 11, 13))  # (x0, x1, y0, y1) excluyente
FACE_BOX = (8, 8, 16, 16)                           # cara frontal de la cabeza


def _fill(px, x0, y0, x1, y1, color):
    for y in range(y0, y1):
        for x in range(x0, x1):
            px[x, y] = color


def make_silente_base():
    """Manto carbón deshilachado + cara hundida con ojos pequenos y brillantes.

    Punto intermedio entre el skin anterior (gris medio) y un dweller
    (casi negro): paño mas oscuro con vetas verticales, cara hundida con
    cuencas REDUCIDAS (solo alrededor de los ojos) y ojos 2x2 de blanco
    puro que no dependen de la capa emisiva para verse.
    """
    rng = np.random.default_rng(42)
    grain = rng.integers(0, 7, size=(64, 64))
    grad = np.linspace(5, -7, 64).reshape(64, 1)
    # Vetas verticales: la tela parece deshilachada.
    col = np.arange(64).reshape(1, 64)
    streak = np.sin(col / 2.3) * 3.0 + np.sin(col / 6.7) * 2.0
    g = np.clip(grain + grad + streak + 16, 4, 44).astype(np.uint8)
    rgb = np.zeros((64, 64, 4), dtype=np.uint8)
    rgb[..., 0] = g
    rgb[..., 1] = g
    rgb[..., 2] = np.clip(g.astype(int) + 4, 0, 255).astype(np.uint8)
    rgb[..., 3] = 255
    # frombytes (no fromarray): da una imagen con buffer propio y escribible
    img = Image.frombytes("RGBA", (64, 64), rgb.tobytes())
    px = img.load()

    # Cara palida asomando por la capucha (estilo dweller intermedio)
    _fill(px, 8, 8, 16, 16, (124, 126, 134, 255))
    _fill(px, 8, 8, 9, 16, (66, 68, 76, 255))
    _fill(px, 15, 8, 16, 16, (66, 68, 76, 255))
    _fill(px, 8, 8, 16, 9, (150, 152, 160, 255))
    _fill(px, 8, 15, 16, 16, (84, 86, 94, 255))
    # Cuencas REDUCIDAS: solo alrededor de cada ojo, no toda la cara.
    _fill(px, 8, 10, 12, 13, (24, 25, 31, 255))
    _fill(px, 12, 10, 16, 13, (24, 25, 31, 255))
    # Ojos pequenos (2x2) y de blanco puro: se ven SIEMPRE en la textura base;
    # la capa emisiva los enciende mas (blanco) o los vuelve rojos en CHASE.
    for (x0, x1) in ((9, 11), (13, 15)):
        _fill(px, x0, 11, x1, 13, (255, 255, 255, 255))
    # Boca cosida debajo de las cuencas
    _fill(px, 10, 14, 14, 15, (58, 60, 68, 255))
    for x in (10, 12, 14):
        px[x, 14] = (34, 36, 44, 255)

    # Interior de la capucha (queda detras de la cara)
    _fill(px, 40, 8, 48, 16, (4, 4, 6, 255))

    # Costillas en el pecho
    _fill(px, 23, 20, 25, 32, (30, 32, 38, 255))
    for y in (22, 25, 28):
        _fill(px, 21, y, 27, y + 1, (70, 72, 80, 255))
        _fill(px, 23, y, 25, y + 1, (26, 28, 34, 255))

    # Manos palidas con dedos
    for (x0, y0, x1, y1) in ((33, 37, 38, 41), (21, 53, 26, 57)):
        _fill(px, x0, y0, x1, y1, (150, 152, 160, 255))
        _fill(px, x0, y0, x1, y0 + 1, (198, 200, 208, 255))
        for x in (x0 + 1, x0 + 3):
            _fill(px, x, y0 + 1, x + 1, y1, (52, 54, 62, 255))

    # Sotana: degradado hacia abajo y remate rasgado
    for y in range(37, 44):
        v = max(6, 24 - (y - 37) * 2)
        _fill(px, 5, y, 14, y + 1, (v, v, v + 3, 255))
    for (x, y) in ((7, 43), (11, 43), (9, 42), (13, 43)):
        px[x, y] = (0, 0, 0, 255)
    return img


def make_silente_eyes(core, halo):
    """Capa emisiva: transparente salvo los ojos y su halo (recortado a la cara)."""
    fx0, fy0, fx1, fy1 = FACE_BOX
    img = Image.new("RGBA", (64, 64), COL_TRANSPARENT)
    px = img.load()
    for (x0, x1, y0, y1) in EYE_CORES:
        for y in range(max(fy0, y0 - 1), min(fy1, y1 + 1)):
            for x in range(max(fx0, x0 - 1), min(fx1, x1 + 1)):
                px[x, y] = halo
        for y in range(y0, y1):
            for x in range(x0, x1):
                px[x, y] = core
    return img


def make_sombra():
    """Sombra Debil: masa oscura ondulada con dos ojos palidos (atlas 64x64).

    Coordenadas espejo de SombraDebilModel.java (ver cabecera del atlas).
    """
    rng = np.random.default_rng(7)
    grain = rng.integers(0, 6, size=(64, 64))
    col = np.arange(64).reshape(1, 64)
    streak = np.sin(col / 1.7) * 4.0 + np.sin(col / 5.0) * 3.0
    g = np.clip(grain + streak + 8, 3, 34).astype(np.uint8)
    rgb = np.zeros((64, 64, 4), dtype=np.uint8)
    rgb[..., 0] = np.clip(g.astype(int) - 2, 0, 255).astype(np.uint8)
    rgb[..., 1] = g
    rgb[..., 2] = np.clip(g.astype(int) + 10, 0, 255).astype(np.uint8)
    rgb[..., 3] = 255
    img = Image.frombytes("RGBA", (64, 64), rgb.tobytes())
    px = img.load()

    # Cara (6,6)-(12,12): hueco oscuro con dos ojos palidos de 2x2.
    _fill(px, 6, 6, 12, 12, (6, 7, 13, 255))
    _fill(px, 7, 8, 9, 10, (214, 226, 255, 255))
    _fill(px, 10, 8, 12, 10, (214, 226, 255, 255))
    # Halo tenue alrededor de los ojos (sin salirse de la cabeza).
    for (x, y) in ((6, 8), (6, 9), (9, 8), (9, 9), (7, 7), (8, 7),
                   (10, 7), (11, 7), (7, 10), (8, 10), (10, 10), (11, 10)):
        px[x, y] = (58, 68, 108, 255)

    # Pecho (4,16)-(12,24): costillas tenues.
    _fill(px, 7, 16, 9, 24, (14, 15, 25, 255))
    for y in (17, 20, 23):
        _fill(px, 5, y, 11, y + 1, (30, 33, 48, 255))

    # Brazos y piernas: tono mas plano, como humo condensado.
    for (x0, y0, x1, y1) in ((24, 12, 40, 22), (0, 24, 32, 34)):
        for y in range(y0, y1):
            for x in range(x0, x1):
                if (x + y) % 5 == 0:
                    px[x, y] = (16, 17, 30, 255)
    return img


def make_simbolo_alba():
    """Hoja de obsidiana con el corazon palido en la empuñadura (16x16)."""
    img = Image.new("RGBA", (16, 16), COL_TRANSPARENT)
    px = img.load()
    obsidian = (46, 34, 74, 255)
    obsidian_hi = (108, 86, 156, 255)
    edge = (18, 12, 30, 255)
    guard = (154, 122, 62, 255)
    guard_hi = (226, 196, 122, 255)
    handle = (86, 66, 44, 255)
    glow = (255, 246, 232, 255)

    # Hoja (x7-8, y1-10) con filo iluminado a la izquierda.
    for y in range(1, 11):
        _fill(px, 7, y, 9, y + 1, obsidian)
        px[7, y] = obsidian_hi
        px[9 - 1, y] = obsidian
    _fill(px, 7, 0, 9, 1, obsidian_hi)
    px[8, 0] = obsidian_hi
    px[6, 10] = edge
    px[9, 10] = edge

    # Gavilan con el simbolo (corazon tenue).
    _fill(px, 4, 11, 12, 12, guard)
    for x in (4, 5, 10, 11):
        px[x, 11] = guard_hi
    for (x, y) in ((6, 11), (7, 11), (9, 11), (10, 11), (8, 12)):
        px[x, y] = glow

    # Empuñadura.
    _fill(px, 7, 12, 9, 16, handle)
    _fill(px, 7, 15, 9, 16, (58, 44, 30, 255))
    return img


def make_umbral():
    """El Umbral (jefe): piel de obsidiana agrietada con vetas violetas y ojos claros."""
    rng = np.random.default_rng(77)
    grain = rng.integers(0, 8, size=(64, 64))
    col = np.arange(64).reshape(1, 64)
    row = np.arange(64).reshape(64, 1)
    streak = np.sin(col / 3.1) * 3.0 + np.sin(row / 4.7) * 2.0
    g = np.clip(grain + streak + 8, 4, 40).astype(np.uint8)
    rgb = np.zeros((64, 64, 4), dtype=np.uint8)
    rgb[..., 0] = np.clip(g.astype(int) + 6, 0, 255).astype(np.uint8)
    rgb[..., 1] = np.clip(g.astype(int) - 2, 0, 255).astype(np.uint8)
    rgb[..., 2] = np.clip(g.astype(int) + 14, 0, 255).astype(np.uint8)
    rgb[..., 3] = 255
    img = Image.frombytes("RGBA", (64, 64), rgb.tobytes())
    px = img.load()

    # Grietas violetas dispersas.
    for _ in range(70):
        x = int(rng.integers(0, 64))
        y = int(rng.integers(0, 56))
        for step in range(int(rng.integers(3, 9))):
            if y + step < 64:
                px[x, y + step] = (126, 84, 190, 255)

    # Cara (10,10)-(20,20): oscura con dos ojos brillantes.
    _fill(px, 10, 10, 20, 20, (16, 12, 24, 255))
    for (x0, x1) in ((12, 14), (16, 18)):
        _fill(px, x0 - 1, 13, x1 + 1, 17, (150, 96, 210, 255))
        _fill(px, x0, 14, x1, 16, (255, 240, 255, 255))
    # Grieta-boca.
    _fill(px, 14, 17, 16, 19, (60, 40, 90, 255))

    # Pecho (6,26)-(18,42): grieta luminosa central.
    _fill(px, 11, 26, 13, 42, (24, 18, 34, 255))
    for y in range(27, 42, 3):
        _fill(px, 11, y, 13, y + 1, (150, 100, 220, 255))

    # Cuernos y manos: tono hueso violaceo.
    for (x0, y0, x1, y1) in ((40, 0, 56, 7), (0, 46, 16, 62), (20, 46, 36, 62)):
        for y in range(y0, y1):
            for x in range(x0, x1):
                if (x + y) % 3 == 0:
                    px[x, y] = (86, 78, 104, 255)
    return img


def make_guadana():
    """Guadaña del Segador: hoja curva de obsidiana y asta larga (16x16)."""
    img = Image.new("RGBA", (16, 16), COL_TRANSPARENT)
    px = img.load()
    blade = (58, 44, 92, 255)
    blade_hi = (140, 116, 196, 255)
    edge = (230, 226, 245, 255)
    pole = (74, 56, 38, 255)
    pole_hi = (112, 86, 58, 255)
    for y in range(3, 16):
        _fill(px, 8, y, 10, y + 1, pole)
        px[8, y] = pole_hi
    for (x, y) in ((3, 2), (4, 2), (5, 3), (6, 3), (7, 4), (6, 4), (5, 4), (4, 3),
                   (3, 3), (2, 3), (2, 2), (2, 1)):
        px[x, y] = blade
    for (x, y) in ((3, 1), (4, 1), (5, 2), (6, 2), (7, 3)):
        px[x, y] = edge
    for (x, y) in ((5, 4), (6, 5), (7, 5)):
        px[x, y] = blade_hi
    _fill(px, 7, 3, 10, 4, pole_hi)
    return img


def make_lampara():
    """Lampara del Penitente: linterna de almas con nucleo cian (16x16)."""
    img = Image.new("RGBA", (16, 16), COL_TRANSPARENT)
    px = img.load()
    frame = (48, 44, 40, 255)
    frame_hi = (96, 88, 78, 255)
    core = (140, 255, 230, 255)
    core_hi = (230, 255, 248, 255)
    _fill(px, 7, 0, 9, 2, frame)
    _fill(px, 5, 2, 11, 3, frame_hi)
    _fill(px, 5, 13, 11, 14, frame)
    for y in range(3, 13):
        _fill(px, 5, y, 6, y + 1, frame)
        _fill(px, 10, y, 11, y + 1, frame)
    _fill(px, 6, 3, 10, 13, core)
    _fill(px, 7, 4, 9, 7, core_hi)
    return img


def make_trofeo():
    """Trofeo del Umbral: bloque oscuro con nucleo violeta (16x16, todos los lados)."""
    rng = np.random.default_rng(91)
    grain = rng.integers(0, 10, size=(16, 16))
    g = np.clip(grain + 16, 12, 60).astype(np.uint8)
    rgb = np.zeros((16, 16, 4), dtype=np.uint8)
    rgb[..., 0] = np.clip(g.astype(int) + 4, 0, 255).astype(np.uint8)
    rgb[..., 1] = g
    rgb[..., 2] = np.clip(g.astype(int) + 12, 0, 255).astype(np.uint8)
    rgb[..., 3] = 255
    img = Image.frombytes("RGBA", (16, 16), rgb.tobytes())
    px = img.load()
    for y in range(4, 12):
        for x in range(4, 12):
            d = max(abs(x - 7.5), abs(y - 7.5))
            if d < 2.0:
                px[x, y] = (226, 196, 255, 255)
            elif d < 3.5:
                px[x, y] = (150, 96, 220, 255)
    for (x, y) in ((1, 1), (14, 1), (1, 14), (14, 14)):
        px[x, y] = (120, 110, 140, 255)
    return img


def erode(mask):
    result = mask.copy()
    result[1:, :] &= mask[:-1, :]
    result[:-1, :] &= mask[1:, :]
    result[:, 1:] &= mask[:, :-1]
    result[:, :-1] &= mask[:, 1:]
    return result


def heart_mask(size, scale=1.0, ox=0.0, oy=0.0):
    yy, xx = np.mgrid[0:size, 0:size]
    x = ((xx + 0.5) / size) * 2.0 - 1.0
    y = 1.0 - ((yy + 0.5) / size) * 2.0
    x = (x - ox) / scale
    y = (y - oy) / scale
    f = (x ** 2 + y ** 2 - 1.0) ** 3 - x ** 2 * (y ** 3)
    return f <= 0.0


def make_corazon():
    """Corazon agrietado: degradado, contorno, grieta central y brillo."""
    img = Image.new("RGBA", (16, 16), COL_TRANSPARENT)
    mask = heart_mask(16, 1.05, 0.0, 0.15)
    outline = mask & ~erode(mask)
    px = img.load()
    h, w = mask.shape
    for y in range(h):
        t = y / float(h - 1)
        fill = (int(176 - 62 * t), int(48 - 24 * t), int(58 - 26 * t), 255)
        shade = (int(120 - 44 * t), int(30 - 15 * t), int(38 - 18 * t), 255)
        for x in range(w):
            if outline[y, x]:
                px[x, y] = (58, 10, 16, 255)
            elif mask[y, x]:
                px[x, y] = shade if x > 9 else fill
    # Grieta que recorre el corazon
    for (x, y) in ((8, 3), (7, 4), (8, 5), (7, 6), (8, 7), (7, 8), (8, 9), (7, 10)):
        if 0 <= y < h and mask[y, x]:
            px[x, y] = (44, 6, 12, 255)
    # Brillo
    for (x, y) in ((4, 3), (5, 3), (4, 4), (5, 4)):
        if 0 <= y < h and mask[y, x]:
            px[x, y] = (226, 132, 142, 255)
    return img


def make_amuleto():
    """Aro de oro con el corazon silente en el centro y cordel."""
    img = Image.new("RGBA", (16, 16), COL_TRANSPARENT)
    px = img.load()
    cord = (126, 100, 64, 255)
    gold_hi = (255, 232, 146, 255)
    gold = (233, 187, 66, 255)
    gold_lo = (154, 112, 30, 255)
    dark = (26, 19, 10, 255)
    # Cordel
    for y in range(0, 3):
        px[7, y] = cord
        px[8, y] = cord
    # Fondo interior del aro
    cx, cy = 7.5, 8.5
    for y in range(16):
        for x in range(16):
            d = ((x - cx) ** 2 + (y - cy) ** 2) ** 0.5
            if d < 4.3:
                px[x, y] = dark
    # Corazon central
    small = heart_mask(16, 2.7, 0.0, -0.05)
    outline = small & ~erode(small)
    for y in range(16):
        for x in range(16):
            if outline[y, x]:
                px[x, y] = (88, 16, 24, 255)
            elif small[y, x]:
                px[x, y] = (176, 48, 58, 255) if y < 8 else (132, 34, 44, 255)
    # Aro de oro con brillo arriba
    for y in range(16):
        for x in range(16):
            d = ((x - cx) ** 2 + (y - cy) ** 2) ** 0.5
            if 4.3 <= d <= 5.7:
                px[x, y] = gold_hi if (y - cy) < -2.0 else (gold if (y - cy) < 2.0 else gold_lo)
    # Destello
    px[5, 5] = (255, 255, 255, 255)
    return img


def generate_textures():
    print("Generando texturas...")
    # --keep-art: no pisa los PNG que el usuario haya pintado a mano.
    keep_art = "--keep-art" in sys.argv
    files = {
        os.path.join(TEX_ENTITY, "el_silente.png"): make_silente_base(),
        os.path.join(TEX_ENTITY, "el_silente_eyes.png"): make_silente_eyes(
            (255, 255, 255, 255), (158, 196, 255, 110)),
        os.path.join(TEX_ENTITY, "el_silente_eyes_angry.png"): make_silente_eyes(
            (255, 54, 30, 255), (255, 96, 40, 130)),
        os.path.join(TEX_ENTITY, "sombra_debil.png"): make_sombra(),
        os.path.join(TEX_ENTITY, "umbral.png"): make_umbral(),
        os.path.join(TEX_ITEM, "corazon_silente.png"): make_corazon(),
        os.path.join(TEX_ITEM, "amuleto_latido.png"): make_amuleto(),
        os.path.join(TEX_ITEM, "simbolo_alba.png"): make_simbolo_alba(),
        os.path.join(TEX_ITEM, "guadana_segador.png"): make_guadana(),
        os.path.join(TEX_ITEM, "lampara_penitente.png"): make_lampara(),
        os.path.join(TEX_BLOCK, "trofeo_umbral.png"): make_trofeo(),
    }
    for path, img in files.items():
        if keep_art and os.path.exists(path):
            print("  [keep] %s (arte propio, no se toca)" % os.path.relpath(path, ROOT))
            continue
        img.save(path)
        print("  [ok] %s" % os.path.relpath(path, ROOT))


def main():
    ensure_dirs()
    generate_textures()
    if "--textures-only" not in sys.argv:
        generate_sounds()
    print("Listo.")


if __name__ == "__main__":
    main()
