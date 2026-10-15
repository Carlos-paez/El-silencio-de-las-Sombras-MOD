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

import gzip
import json
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
    return _make_whisper(7)


def make_whisper2():
    """Variante estéreo 2 del susurro (otra semilla/tono)."""
    return _make_whisper(19)


def make_whisper3():
    """Variante estéreo 3 del susurro (mas grave y lenta)."""
    return _make_whisper(31)


def _make_whisper(seed):
    n = int(SR * 2.5)
    rng = np.random.default_rng(seed)
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


# ---------------------------------------------------------------------------
# Mejoras §10: música por fases + sonido ambiental procedural.
# Los bucles se sintetizan PERFECTAMENTE PERIODICOS (componentes con numero
# entero de ciclos + ruido por FFT) para que den la vuelta sin ningun click.
# ---------------------------------------------------------------------------

def periodic_noise(n, low, high, rng, color=1.5):
    """Ruido limitado de banda y periodico de largo n (bucle limpio)."""
    freqs = np.fft.rfftfreq(n, 1.0 / SR)
    mag = np.zeros_like(freqs)
    band = (freqs >= low) & (freqs <= high)
    mag[band] = freqs[band] ** (-color)
    peak = np.max(mag)
    if peak > 1e-12:
        mag /= peak
    spec = mag * np.exp(1j * rng.uniform(0.0, 2.0 * np.pi, size=len(freqs)))
    return np.fft.irfft(spec, n=n)


def make_chase_loop():
    """Bucle de persecución (8 s, stream): sub grave + pulsos de tension."""
    n = int(SR * 8)
    rng = np.random.default_rng(41)
    t = np.arange(n) / SR
    # 49 Hz = 392 ciclos en 8 s (entero) -> bucle perfecto.
    sub = np.sin(2 * np.pi * 49.0 * t) * 0.55
    # Pulso de tension cada 1 s (8 pulsos exactos).
    beat = np.exp(-(t % 1.0) / 0.28)
    pulse = np.sin(2 * np.pi * 98.0 * t) * beat * 0.45
    noise = periodic_noise(n, 180.0, 950.0, rng, color=1.2)
    air = periodic_noise(n, 1200.0, 4500.0, rng, color=1.0)
    mix = sub * (0.5 + 0.5 * beat) + pulse + noise * (0.25 + 0.5 * beat) + air * 0.08 * beat
    return normalize(np.tanh(1.6 * mix), 0.85)


def make_stalk_silence():
    """Silencio antes del ataque (6 s): zumbido casi inaudible y respiracion."""
    n = int(SR * 6)
    rng = np.random.default_rng(53)
    t = np.arange(n) / SR
    # 41 Hz = 246 ciclos en 6 s; respiracion = 2 ciclos en 6 s.
    drone = np.sin(2 * np.pi * 41.0 * t) * 0.5 + np.sin(2 * np.pi * 82.0 * t) * 0.15
    breath = 0.5 + 0.5 * np.sin(2 * np.pi * (1.0 / 3.0) * t)
    bed = periodic_noise(n, 60.0, 260.0, rng, color=1.8) * 0.5
    return normalize((drone + bed) * (0.35 + 0.65 * breath), 0.55)


def make_cave_dread():
    """Cama de cueva (10 s): lecho continuo + goteos resonantes puntuales."""
    n = int(SR * 10)
    rng = np.random.default_rng(61)
    t = np.arange(n) / SR
    bed = periodic_noise(n, 70.0, 420.0, rng, color=1.6)
    air = periodic_noise(n, 500.0, 2400.0, rng, color=1.3) * 0.35
    mix = bed * 0.7 + air * 0.3
    # Goteos: mueren bien antes del final del bucle (no rompen la periodica).
    for drip_t, freq in ((1.7, 780.0), (4.9, 620.0), (7.4, 900.0)):
        start = int(drip_t * SR)
        length = int(SR * 1.0)
        if start + length >= n:
            continue
        tt = np.arange(length) / SR
        drip = np.sin(2 * np.pi * freq * tt * (1.0 - 0.4 * tt)) \
            * exp_decay(length, 0.15) * 0.5
        mix[start:start + length] += drip
    pad = np.sin(2 * np.pi * 55.0 * t) * 0.10  # 550 ciclos en 10 s
    return normalize(mix + pad, 0.6)


def make_claws():
    """Garras rasgando la madera (0,9 s): tres raspanos y golpe final."""
    n = int(SR * 0.9)
    rng = np.random.default_rng(67)
    noise = bandpass(rng.standard_normal(n), 900.0, 4800.0)
    env = np.zeros(n)
    for start in (0, int(SR * 0.22), int(SR * 0.44)):
        length = int(SR * 0.16)
        if start + length > n:
            break
        env[start:start + length] += np.hanning(length)
    body = np.zeros(n)
    hit = int(SR * 0.18)
    start = int(SR * 0.62)
    tt = np.arange(hit) / SR
    body[start:start + hit] += np.sin(2 * np.pi * 95.0 * tt) * exp_decay(hit, 0.05) * 0.8
    return normalize(noise * env * 0.9 + body, 0.75)


RECIPES = {
    "heartbeat": make_heartbeat,
    "whisper": make_whisper,
    "whisper2": make_whisper2,
    "whisper3": make_whisper3,
    "scream": make_scream,
    "vanish": make_vanish,
    "attack": make_attack,
    "chase": make_chase,
    "chase_loop": make_chase_loop,
    "stalk_silence": make_stalk_silence,
    "cave_dread": make_cave_dread,
    "claws": make_claws,
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
# Atlas UV de la Sombra Debil (64x64, formato skin de jugador). DEBE coincidir
# con SombraDebilModel.java:
#   cabeza 8x8x8    : (0,0)-(32,16)   -> cara frontal en (8,8)-(16,16)
#   hat (capucha)   : (32,0)-(64,16)  -> frente en (40,8)-(48,16)
#   torso 8x12x4    : (16,16)-(40,32) -> pecho en (20,20)-(28,32)
#   brazo der.      : (40,16)-(56,32) -> frente en (44,20)-(48,32)
#   brazo izq.      : (32,48)-(48,64) -> frente en (36,52)-(40,64)
#   pierna der.     : (0,16)-(16,32)  -> frente en (4,20)-(8,32)
#   pierna izq.     : (16,48)-(32,64) -> frente en (20,52)-(24,64)
#   overlays (bruma semi-transparente):
#     capucha (32,0)-(64,16) · chaqueta (16,32)-(40,48)
#     manga der. (40,32)-(56,48) · manga izq. (48,48)-(64,64)
#     pantalon der. (0,32)-(16,48) · pantalon izq. (0,48)-(16,64)
#   ojos de la sombra : x=9,10 y x=13,14 ; y=11,12
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
    """Sombra Debil: skin 64x64 de jugador (cuerpo opaco + overlay de bruma).

    Formato estandar de skin moderna: cuerpo completo con capas overlay
    semi-transparentes (capucha, chaqueta, mangas y pantalones) que simulan
    la bruma condensada y el borde inferior deshilachado.
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

    # Cara (8,8)-(16,16): hueco oscuro con dos ojos palidos de 2x2.
    _fill(px, 8, 8, 16, 16, (6, 7, 13, 255))
    _fill(px, 9, 11, 11, 13, (214, 226, 255, 255))
    _fill(px, 13, 11, 15, 13, (214, 226, 255, 255))
    # Halo tenue alrededor de los ojos (sin salirse de la cabeza).
    for (x, y) in ((8, 10), (9, 10), (10, 10), (11, 10), (12, 10),
                   (8, 11), (8, 12), (11, 11), (11, 12), (12, 11), (12, 12),
                   (8, 13), (9, 13), (10, 13), (11, 13), (12, 13),
                   (13, 10), (14, 10), (15, 10), (15, 11), (15, 12), (15, 13)):
        px[x, y] = (58, 68, 108, 255)

    # Resto de la cabeza (lados, frente y nuca): vacio casi negro.
    _fill(px, 0, 8, 8, 16, (5, 5, 10, 255))
    _fill(px, 16, 8, 32, 16, (5, 5, 10, 255))
    _fill(px, 8, 0, 16, 8, (4, 4, 8, 255))

    # Pecho (20,20)-(28,32): costillas tenues.
    _fill(px, 23, 20, 25, 32, (14, 15, 25, 255))
    for y in (21, 24, 27, 30):
        _fill(px, 21, y, 27, y + 1, (30, 33, 48, 255))

    # Brazos y piernas: tono mas plano, como humo condensado.
    for (x0, y0, x1, y1) in ((40, 16, 56, 32), (32, 48, 48, 64),
                             (0, 16, 16, 32), (16, 48, 32, 64)):
        for y in range(y0, y1):
            for x in range(x0, x1):
                if (x + y) % 5 == 0:
                    px[x, y] = (16, 17, 30, 255)

    # Capas overlay: bruma eterea semi-transparente. El borde inferior se
    # deshilacha hasta el hueco (aire entre la masa oscura y el vapuleo).
    overlays = ((32, 0, 64, 16), (16, 32, 40, 48), (40, 32, 56, 48),
                (48, 48, 64, 64), (0, 32, 16, 48), (0, 48, 16, 64))
    veil = rng.integers(100, 155, size=(64, 64)).astype(np.uint8)
    for (x0, y0, x1, y1) in overlays:
        for y in range(y0, y1):
            fade = (y - y0) / float(y1 - y0)
            for x in range(x0, x1):
                a = int(veil[x, y] * (1.0 - 0.85 * fade))
                if fade > 0.75 and rng.random() < 0.45:
                    a = 0
                src = px[x, y]
                px[x, y] = (src[0], src[1], src[2], a)
    return img


def make_simbolo_alba():
    """Simbolo del Alba (64x64): hoja de obsidiana con el corazon palido.

    Filo iluminado a la izquierda, gavilan dorado con pomos, empuñadura
    envuelta en cuero y un corazon incrustado que resplandece.
    """
    def paint(x, y, img, size):
        # Hoja: doble filo que se estrecha hasta la punta.
        t = np.clip((y + 0.43) / 0.40, 0.0, 1.0)
        halfw = 0.006 + 0.072 * t ** 0.85
        blade = np.maximum(np.abs(x) - halfw, np.maximum(-0.43 - y, y + 0.02))
        bcov, bluz = _shade(blade, size, bevel=0.040)
        # Obsidiana: filo claro a la izquierda, lomo oscuro a la derecha.
        left = _sstep(0.030, 0.0, halfw + x)
        right = _sstep(0.030, 0.0, halfw - x)
        fuller = _sstep(0.028, 0.010, np.abs(x)) * bcov  # canal central
        bcol = np.array((0.345, 0.265, 0.520), np.float32) * bluz[..., None]
        bcol = bcol + np.array((0.420, 0.380, 0.560), np.float32) * left[..., None] * 0.55
        bcol = bcol * (1.0 - 0.45 * right)[..., None]
        bcol = bcol * (1.0 - 0.35 * fuller)[..., None]
        _over(img, np.clip(bcol, 0.0, 1.0), bcov)
        # Punta con alma de luz.
        _add(img, (0.90, 0.88, 1.00),
             np.exp(-((x ** 2 + (y + 0.40) ** 2) / 0.0022)) * 0.90 * bcov)

        # Gavilan dorado con pomos esfericos.
        bar = _sd_box(x, y, 0.0, -0.005, 0.255, 0.030, 0.018)
        poms = np.minimum(_sd_disc(x, y, -0.285, -0.005, 0.048),
                          _sd_disc(x, y, 0.285, -0.005, 0.048))
        guard = np.minimum(bar, poms)
        gcov, gluz = _shade(guard, size, bevel=0.028)
        facet = 0.90 + 0.12 * np.cos(x * 40.0)
        _over(img, np.array((0.900, 0.700, 0.260), np.float32) * (gluz * facet)[..., None], gcov)
        _add(img, (1.00, 0.94, 0.70),
             np.exp(-(((x + 0.26) ** 2 + (y + 0.03) ** 2) / 0.0035)) * 0.75 * gcov)

        # Corazon palido incrustado en el gavilan.
        gem = _heart_field(x, y + 0.005, size, 0.052, 0.0)
        gemc = np.clip(0.5 - gem * size, 0.0, 1.0)
        _over(img, (0.96, 0.86, 0.86), gemc * 0.95)
        _add(img, (1.00, 0.80, 0.78), np.exp(-((x ** 2 + (y + 0.01) ** 2) / 0.0016)) * 0.80)

        # Empunadura de cuero con vueltas y pomo dorado.
        grip = _sd_box(x, y, 0.0, 0.140, 0.050, 0.125, 0.020)
        gyc, glz = _shade(grip, size, bevel=0.022)
        wrap = 0.80 + 0.28 * np.abs(np.sin((y - 0.14) * 78.0))
        _over(img, np.array((0.330, 0.240, 0.150), np.float32) * (glz * wrap)[..., None], gyc)
        pommel = _sd_disc(x, y, 0.0, 0.290, 0.058)
        pcov, pluz = _shade(pommel, size, bevel=0.025)
        _over(img, np.array((0.880, 0.680, 0.250), np.float32) * pluz[..., None], pcov)
        _add(img, (1.00, 0.95, 0.75),
             np.exp(-(((x + 0.025) ** 2 + (y - 0.265) ** 2) / 0.0012)) * 0.80 * pcov)
    return _render_item(paint)


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
    """Guadaña del Segador (64x64): hoja curva de obsidiana con filo de almas.

    Asta nudosa a la derecha, media luna curva a la izquierda y el filo
    (arco interior) brillando en cian con luz aditiva de almas.
    """
    def paint(x, y, img, size):
        # Asta de madera vertical con grano y nudos.
        pole = _sd_box(x, y, 0.26, 0.06, 0.042, 0.42, 0.025)
        pcov, pluz = _shade(pole, size, bevel=0.030)
        grain = 0.88 + 0.18 * np.sin(y * 62.0 + np.sin(x * 26.0) * 1.6)
        _over(img, np.array((0.365, 0.270, 0.170), np.float32) * (pluz * grain)[..., None], pcov)
        knots = None
        for ky in (-0.14, 0.08, 0.29):
            k = _sd_disc(x, y, 0.26, ky, 0.028)
            knots = k if knots is None else np.minimum(knots, k)
        kcov, kluz = _shade(knots, size, bevel=0.018)
        _over(img, np.array((0.200, 0.140, 0.085), np.float32) * kluz[..., None], kcov * 0.85)

        # Hoja en media luna: banda de arco con punta fina a la izquierda.
        ccx, ccy, R = 0.30, 0.34, 0.68
        rad = np.sqrt((x - ccx) ** 2 + (y - ccy) ** 2)
        t = np.clip((np.degrees(np.arctan2(y - ccy, x - ccx)) % 360.0 - 188.0) / 72.0, 0.0, 1.0)
        halfw = 0.012 + 0.085 * t ** 0.8
        band = np.abs(rad - R) - halfw
        # Cortes rectos en la punta (188°) y en el asta (260°).
        dt = (np.cos(np.radians(188.0)), np.sin(np.radians(188.0)))
        pt = (ccx + R * dt[0], ccy + R * dt[1])
        tt = (-dt[1], dt[0])  # tangente hacia dentro de la hoja
        cut_tip = -((x - pt[0]) * tt[0] + (y - pt[1]) * tt[1])
        da = (np.cos(np.radians(260.0)), np.sin(np.radians(260.0)))
        pa = (ccx + R * da[0], ccy + R * da[1])
        ta = (-da[1], da[0])
        cut_att = (x - pa[0]) * ta[0] + (y - pa[1]) * ta[1]
        blade = np.maximum(band, np.maximum(cut_tip, cut_att))
        bcov, bluz = _shade(blade, size, bevel=0.050)
        # Obsidiana: lomo oscuro hacia el exterior del arco + veteado fino.
        spine = _sstep(-0.02, 0.05, rad - R)
        vetro = 0.90 + 0.16 * np.sin((rad - R) * 90.0 + t * 9.0)
        bcol = (np.array((0.300, 0.230, 0.450), np.float32) -
                np.array((0.150, 0.120, 0.190), np.float32) * spine[..., None]) * (bluz * vetro)[..., None]
        _over(img, np.clip(bcol, 0.0, 1.0), bcov)

        # Filo de almas: linea cian viva sobre el arco interior + halo.
        edge = np.exp(-((rad - (R - halfw + 0.012)) ** 2) / 0.00022) * bcov
        _add(img, (0.55, 1.00, 0.90), edge * 0.85)
        _add(img, (0.25, 0.60, 0.55),
             np.exp(-((rad - (R - halfw)) ** 2) / 0.0016) * bcov * 0.55)

        # Collar metalico donde la hoja se une al asta.
        collar = _sd_box(x, y, 0.20, -0.30, 0.080, 0.028, 0.015)
        ccov2, cluz2 = _shade(collar, size, bevel=0.020)
        _over(img, np.array((0.560, 0.520, 0.470), np.float32) * cluz2[..., None], ccov2)
    return _render_item(paint)


def make_lampara():
    """Lampara del Penitente (64x64): linterna de hierro con un alma dentro.

    Marco con esquinas reforzadas y remaches, cristal traslucido y un alma
    cian que derrama luz por las juntas.
    """
    def paint(x, y, img, size):
        # Cristal y alma (van detras del marco).
        body = _sd_box(x, y, 0.0, 0.045, 0.245, 0.245, 0.035)
        window = _sd_box(x, y, 0.0, 0.045, 0.160, 0.175, 0.030)
        gcov = np.clip(0.5 - window * size, 0.0, 1.0)
        gd = np.sqrt(x ** 2 + (y - 0.045) ** 2)
        glass = np.array((0.240, 0.480, 0.440), np.float32) * (1.0 - 0.45 * np.clip(gd * 1.8, 0, 1))[..., None]
        _over(img, glass, gcov * 0.80)

        # Alma encerrada: cabeza + cola ondulante.
        head = _sd_disc(x, y, 0.0, 0.005, 0.055)
        tail = _sd_polyline(x, y, [(0.0, 0.05), (0.018, 0.11), (-0.012, 0.17)], 0.028)
        soul = np.minimum(head, tail)
        scov = np.clip(0.5 - soul * size, 0.0, 1.0)
        _over(img, (0.78, 1.00, 0.93), scov)
        _add(img, (0.45, 1.00, 0.88), np.exp(-(gd ** 2) / 0.030) * 0.55 * gcov)
        _add(img, (0.85, 1.00, 0.96), np.exp(-(gd ** 2) / 0.0022) * 0.85 * scov)

        # Brillo diagonal del cristal.
        streak = _sd_polyline(x, y, [(-0.10, -0.10), (-0.045, 0.15)], 0.012)
        _over(img, (0.85, 0.98, 0.95), np.clip(0.5 - streak * size, 0.0, 1.0) * gcov * 0.30)

        # Marco de hierro: caja con ventana, tapa, base, asa y remaches.
        frame = np.maximum(body, -window)
        cap = _sd_box(x, y, 0.0, -0.245, 0.245, 0.045, 0.020)
        base = _sd_box(x, y, 0.0, 0.335, 0.275, 0.045, 0.018)
        ring = np.abs(_sd_disc(x, y, 0.0, -0.395, 0.070)) - 0.016
        neck = _sd_box(x, y, 0.0, -0.315, 0.045, 0.035, 0.010)
        frame = np.minimum(frame, np.minimum(cap, base))
        frame = np.minimum(frame, np.minimum(ring, neck))
        fcov, fluz = _shade(frame, size, bevel=0.030)
        iron = 0.88 + 0.20 * np.sin(x * 40.0 + y * 8.0)
        _over(img, np.array((0.295, 0.275, 0.250), np.float32) * (fluz * iron)[..., None], fcov)

        rivets = None
        for (rx, ry) in ((-0.195, -0.155), (0.195, -0.155), (-0.195, 0.245), (0.195, 0.245)):
            r = _sd_disc(x, y, rx, ry, 0.024)
            rivets = r if rivets is None else np.minimum(rivets, r)
        rcov, rluz = _shade(rivets, size, bevel=0.015)
        _over(img, np.array((0.480, 0.450, 0.410), np.float32) * rluz[..., None], rcov)
    return _render_item(paint)


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


def make_linea_sal():
    """Linea de sal (16x16): cojinete de granos blancos agrietado (§7)."""
    rng = np.random.default_rng(101)
    img = Image.new("RGBA", (16, 16), COL_TRANSPARENT)
    px = img.load()
    salt = (232, 234, 238, 255)
    salt_hi = (252, 253, 255, 255)
    salt_lo = (196, 200, 208, 255)
    cx, cy = 7.5, 7.5
    # Cojínente oval de granos.
    for y in range(16):
        for x in range(16):
            d = ((x - cx) / 7.6) ** 2 + ((y - cy) / 5.6) ** 2
            if d <= 1.0:
                v = rng.random()
                if v < 0.18:
                    px[x, y] = salt_lo
                elif v > 0.88:
                    px[x, y] = salt_hi
                else:
                    px[x, y] = salt
    # Grietas del salero.
    for (x, y) in ((4, 6), (5, 7), (6, 7), (7, 8), (8, 8), (9, 9), (10, 9),
                   (6, 10), (7, 11), (11, 6), (10, 7)):
        if px[x, y][3] > 0:
            px[x, y] = (170, 174, 182, 255)
    # Granos sueltos en el borde.
    for _ in range(8):
        x = int(rng.integers(1, 15))
        y = int(rng.integers(2, 14))
        if px[x, y][3] == 0:
            px[x, y] = (236, 238, 242, 255)
    return img


def make_portal_umbral():
    """Grieta del Umbral (16x16): fisura violacea con almas atrapadas (§13)."""
    rng = np.random.default_rng(107)
    img = Image.new("RGBA", (16, 16), COL_TRANSPARENT)
    px = img.load()
    base = (12, 6, 18, 255)
    vein = (86, 40, 130, 255)
    spark = (170, 110, 255, 255)
    wisp = (226, 208, 255, 255)
    cx = 7.5
    # Bulto central oscuro con vetas espirales (como el portal del Nether, pero
    # violaceo y denso): la grieta parece respirar.
    for y in range(16):
        for x in range(16):
            d = ((x - cx) / 6.8) ** 2 + ((y - cx) / 7.8) ** 2
            if d > 1.0:
                continue
            swirl = np.sin(d * 9.0 + rng.random() * 0.6) * 0.5 + 0.5
            v = rng.random()
            if v < 0.10 + swirl * 0.30:
                px[x, y] = vein
            else:
                px[x, y] = base
    # Nucleo de almas: hilo vertical claro que serpentea.
    for y in range(1, 15):
        wob = int(round(np.sin(y / 2.1) * 1.8))
        x = int(cx) + wob
        px[x, y] = spark
        if y % 3 == 0:
            px[x, y - 1] = wisp
    # Chispas sueltas alrededor.
    for _ in range(6):
        x = int(rng.integers(2, 14))
        y = int(rng.integers(2, 14))
        if px[x, y][3] > 0 and px[x, y][:3] != (12, 6, 18):
            px[x, y] = spark
    return img


def make_campana_vigilia():
    """Campana de vigilia (64x64): bronce agrietado con badajo de hueso.

    Perfil con cupula y falda ensanchada, anilla superior, grieta vertical
    (esta campana ya ha sonado de mas) y badajo de hueso asomando abajo.
    """
    def paint(x, y, img, size):
        # Perfil: cupula arriba + falda ensanchada con labio inferior.
        dome = np.sqrt(np.clip(1.0 - ((y + 0.15) / 0.185) ** 2, 0.0, 1.0)) * 0.135
        skirt = 0.130 + 0.155 * _sstep(-0.15, 0.30, y) ** 1.5
        prof = np.where(y < -0.15, dome, skirt) + 0.014 * _sstep(0.215, 0.26, y)
        bell = np.maximum(np.abs(x) - prof, np.maximum(-0.335 - y, y - 0.30))
        bcov, bluz = _shade(bell, size, bevel=0.050)
        # Sombreado horizontal: luz en el hombro izquierdo, sombra a la derecha.
        nx = x / np.maximum(prof, 1e-3)
        hl = np.clip(0.75 - nx * 0.60, 0.32, 1.30)
        streak = 0.92 + 0.14 * np.sin(x * 88.0)
        bcol = np.array((0.560, 0.395, 0.170), np.float32) * (bluz * hl * streak)[..., None]
        _over(img, np.clip(bcol, 0.0, 1.0), bcov)
        # Labio inferior mas oscuro y filo con luz propia.
        _over(img, (0.330, 0.230, 0.100), bcov * _sstep(0.225, 0.265, y) * 0.55)
        _add(img, (0.55, 0.42, 0.20), bcov * np.exp(-((y - 0.292) ** 2) / 0.00008) * 0.50)

        # Grieta vertical: ya ha sonado de mas.
        crack = _sd_polyline(x, y, [(0.02, -0.20), (-0.02, -0.05), (0.03, 0.08), (-0.01, 0.22)], 0.008)
        _over(img, (0.180, 0.120, 0.050), np.clip(0.5 - crack * size, 0.0, 1.0) * bcov * 0.9)

        # Anilla superior.
        loop = np.abs(_sd_disc(x, y, 0.0, -0.375, 0.048)) - 0.014
        lcov, lluz = _shade(loop, size, bevel=0.015)
        _over(img, np.array((0.560, 0.395, 0.170), np.float32) * lluz[..., None], lcov)

        # Badajo de hueso asomando por debajo de la falda.
        stem = _sd_polyline(x, y, [(0.0, 0.20), (0.018, 0.315)], 0.008)
        ball = _sd_disc(x, y, 0.020, 0.345, 0.040)
        clap = np.minimum(stem, ball)
        ccov, cluz = _shade(clap, size, bevel=0.020)
        _over(img, np.array((0.870, 0.845, 0.770), np.float32) * cluz[..., None], ccov)

        # Especular en el hombro izquierdo.
        _add(img, (1.00, 0.88, 0.55),
             np.exp(-(((x + 0.105) ** 2 + (y + 0.13) ** 2) / 0.0030)) * 0.65 * bcov)
    return _render_item(paint)


# ---------------------------------------------------------------------------
# Items 64x64: render suave con campos de distancia (SDF) y supermuestreo.
# Se pinta a ITEM_RES*ITEM_SS (256x256) y se reduce a ITEM_RES, de modo que
# los bordes salen con antialiasing en vez de «cortados». Luz coherente
# arriba-izquierda en todos los items.
# ---------------------------------------------------------------------------

ITEM_RES = 64
ITEM_SS = 4
_LIGHT = (-0.62, -0.62)  # direccion de la luz (y crece hacia abajo)


def _coords(size):
    """Centro de pixel en coordenadas centradas x,y (lienzo de lado 1.0)."""
    yy, xx = np.mgrid[0:size, 0:size].astype(np.float32)
    return (xx + 0.5) / size - 0.5, (yy + 0.5) / size - 0.5


def _sstep(a, b, t):
    """Smoothstep estable (acepta escalares o arrays)."""
    t = np.clip((t - a) / (b - a + 1e-9), 0.0, 1.0)
    return t * t * (3.0 - 2.0 * t)


def _canvas(size):
    """Lienzo RGBA float en premultiplicado (facilita la luz aditiva)."""
    return np.zeros((size, size, 4), np.float32)


def _over(img, rgb, alpha):
    """Compone rgb (0..1) con alpha [0,1] sobre el lienzo premultiplicado."""
    a_old = img[..., 3]
    img[..., :3] = rgb * alpha[..., None] + img[..., :3] * (1.0 - alpha)[..., None]
    img[..., 3] = alpha + a_old * (1.0 - alpha)


def _add(img, rgb, amount):
    """Suma luz aditiva (halos, filos de almas, especulares)."""
    img[..., :3] += np.asarray(rgb, np.float32) * np.clip(amount, 0.0, 3.0)[..., None]


def _finish(img, size):
    """Reduce el lienzo (premultiplicado) con supermuestreo y lo vuelve PNG."""
    ss = img.shape[0] // size
    small = img.reshape(size, ss, size, ss, 4).mean(axis=(1, 3))
    a = small[..., 3:4]
    rgb = np.where(a > 1e-4, small[..., :3] / np.maximum(a, 1e-4), 0.0)
    out = np.concatenate([np.clip(rgb, 0.0, 1.0), np.clip(a, 0.0, 1.0)], axis=2)
    return Image.fromarray((out * 255.0 + 0.5).astype(np.uint8), "RGBA")


def _render_item(paint):
    """Ejecuta paint(x, y, img, size) a resolucion alta y reduce a ITEM_RES."""
    size = ITEM_RES * ITEM_SS
    x, y = _coords(size)
    img = _canvas(size)
    paint(x, y, img, size)
    return _finish(img, ITEM_RES)


# -- primitivas SDF (negativo dentro, positivo fuera) -----------------------


def _sd_disc(x, y, cx, cy, r):
    return np.sqrt((x - cx) ** 2 + (y - cy) ** 2) - r


def _sd_box(x, y, cx, cy, hx, hy, rad=0.0):
    qx = np.abs(x - cx) - (hx - rad)
    qy = np.abs(y - cy) - (hy - rad)
    outside = np.sqrt(np.maximum(qx, 0.0) ** 2 + np.maximum(qy, 0.0) ** 2)
    return outside + np.minimum(np.maximum(qx, qy), 0.0) - rad


def _sd_segment(x, y, a, b):
    px, py = x - a[0], y - a[1]
    bx, by = b[0] - a[0], b[1] - a[1]
    h = np.clip((px * bx + py * by) / (bx * bx + by * by + 1e-9), 0.0, 1.0)
    return np.sqrt((px - bx * h) ** 2 + (py - by * h) ** 2)


def _sd_polyline(x, y, pts, width=0.0):
    d = None
    for i in range(len(pts) - 1):
        seg = _sd_segment(x, y, pts[i], pts[i + 1])
        d = seg if d is None else np.minimum(d, seg)
    return d - width


def _shade(d, size, bevel=0.055):
    """Cobertura + luz lambertiana (arriba-izq) desde un SDF (dentro < 0)."""
    cov = np.clip(0.5 - d * size, 0.0, 1.0)
    gy, gx = np.gradient(d)
    n = np.sqrt(gx * gx + gy * gy) + 1e-6
    lam = np.clip(-(gx / n * _LIGHT[0] + gy / n * _LIGHT[1]), 0.0, 1.0)
    bulge = _sstep(0.0, bevel, -d)
    rim = 1.0 - _sstep(0.0, bevel * 0.7, -d)
    return cov, np.clip(0.60 + 0.65 * lam * bulge - 0.30 * rim, 0.15, 1.6)


def _heart_field(x, y, size, scale=1.0, oy=0.0):
    """Distancia aproximada al corazon clasico (negativa dentro)."""
    xx = x / scale
    yy = -(y - oy) / scale  # y invertido: los lobulos, arriba
    f = (xx ** 2 + yy ** 2 - 1.0) ** 3 - xx ** 2 * (yy ** 3)
    gy, gx = np.gradient(f)
    return np.clip(f / (np.sqrt(gx * gx + gy * gy) + 1e-6) / size, -0.5, 0.5)


def make_corazon():
    """Corazon Silente (64x64): volumen organico con vetas y brillo humedo.

    Campo implicito del corazon + luz arriba-izquierda; contorno oscuro,
    grano de carne, arterias en la cima y gota de luz en el lobulo.
    """
    def paint(x, y, img, size):
        # Masa principal + dos vasos saliendo por arriba (aorta y vena).
        d = _heart_field(x, y, size, 0.42, 0.05)
        aorta = _sd_polyline(x, y, [(-0.14, -0.26), (-0.19, -0.38), (-0.13, -0.47)], 0.045)
        vena = _sd_polyline(x, y, [(0.09, -0.24), (0.15, -0.36), (0.10, -0.44)], 0.036)
        d = np.minimum(d, np.minimum(aorta, vena))
        cov, luz = _shade(d, size, bevel=0.10)

        # Cuerpo: rojo arterial arriba que se apaga hacia abajo.
        t = np.clip((y + 0.45) * 1.15, 0.0, 1.0)
        col = (np.array((0.760, 0.220, 0.245), np.float32) -
               np.array((0.310, 0.130, 0.120), np.float32) * t[..., None])
        col = col * luz[..., None]

        # Grano de carne fino (ruido por texel final, no por subpixel).
        rng = np.random.default_rng(303)
        noise = rng.random((size // 4, size // 4)).astype(np.float32)
        noise = np.kron(noise, np.ones((4, 4), np.float32))[:size, :size]
        col *= (0.93 + 0.14 * noise)[..., None]
        _over(img, col, cov)

        # Vetas oscuras ramificadas dentro de la masa.
        veins = (([(-0.02, -0.08), (0.09, 0.00), (0.14, 0.12)], 0.013),
                 ([(-0.02, -0.08), (-0.13, -0.01), (-0.20, 0.09)], 0.013),
                 ([(0.09, 0.00), (0.12, -0.11)], 0.009),
                 ([(-0.13, -0.01), (-0.15, 0.11), (-0.09, 0.22)], 0.009),
                 ([(-0.02, -0.08), (-0.02, 0.07), (0.04, 0.19)], 0.011))
        vein_d = None
        for pts, wdt in veins:
            vd = _sd_polyline(x, y, pts, wdt)
            vein_d = vd if vein_d is None else np.minimum(vein_d, vd)
        interior = np.clip(0.5 - d * size, 0.0, 1.0)
        vein_cov = np.clip(0.5 - vein_d * size, 0.0, 1.0) * interior
        _over(img, (0.30, 0.05, 0.08), vein_cov * 0.60)

        # Arterias de la cima: aorta viva, vena mas fria.
        _over(img, (0.46, 0.11, 0.13), np.clip(0.5 - aorta * size, 0.0, 1.0) * 0.45)
        _over(img, (0.27, 0.13, 0.26), np.clip(0.5 - vena * size, 0.0, 1.0) * 0.50)

        # Brillo humedo arriba-izquierda + punto especular.
        _add(img, (0.95, 0.55, 0.58),
             np.exp(-(((x + 0.20) ** 2 + (y + 0.20) ** 2) / 0.014)) * 0.50 * interior)
        _add(img, (1.00, 0.90, 0.92),
             np.exp(-(((x + 0.15) ** 2 + (y + 0.25) ** 2) / 0.0016)) * 0.85 * interior)

        # Contorno oscuro definiendo la silueta.
        _over(img, (0.15, 0.02, 0.04), (1.0 - _sstep(0.0, 0.022, -d)) * 0.55 * interior)
    return _render_item(paint)


def make_amuleto():
    """Amuleto del Latido (64x64): aro de oro facetado con corazon pulsante.

    Terciopelo interior con viñeta, luz derramada por el corazon sobre el
    terciopelo y cordel de dos hebras trenzado hacia arriba.
    """
    def paint(x, y, img, size):
        cx, cy = 0.0, 0.03
        dist = np.sqrt((x - cx) ** 2 + (y - cy) ** 2)

        # Disco interior: terciopelo oscuro con viñeta radial y pelusa fina.
        inner = _sd_disc(x, y, cx, cy, 0.305)
        inner_cov = np.clip(0.5 - inner * size, 0.0, 1.0)
        vig = _sstep(0.31, 0.03, dist)
        velvet = (np.array((0.150, 0.045, 0.085), np.float32) +
                  np.array((0.090, 0.030, 0.045), np.float32) * vig[..., None])
        rng = np.random.default_rng(404)
        fuzz = rng.random((size // 4, size // 4)).astype(np.float32)
        fuzz = np.kron(fuzz, np.ones((4, 4), np.float32))[:size, :size]
        velvet *= (0.88 + 0.20 * fuzz)[..., None]
        _over(img, velvet, inner_cov)

        # Luz que derrama el corazon sobre el terciopelo.
        _add(img, (0.55, 0.09, 0.11), np.exp(-(dist ** 2) / 0.050) * 0.60 * inner_cov)

        # Corazon central.
        heart = _heart_field(x - cx, y - cy - 0.01, size, 0.135, 0.0)
        hcov, hluz = _shade(heart, size, bevel=0.045)
        ht = np.clip((y - cy + 0.08) * 5.0, 0.0, 1.0)
        hcol = (np.array((0.790, 0.210, 0.245), np.float32) -
                np.array((0.260, 0.100, 0.105), np.float32) * ht[..., None]) * hluz[..., None]
        _over(img, hcol, hcov)
        _add(img, (1.00, 0.55, 0.58),
             np.exp(-(((x + 0.05) ** 2 + (y + 0.02) ** 2) / 0.0016)) * 0.80 * hcov)

        # Cordel de dos hebras trenzado (sale por detras del aro hacia arriba).
        ycut = y + 0.30
        twist = np.sin((y + 0.5) * 40.0)
        s1 = np.maximum(np.abs(x - 0.030 * twist) - 0.020, ycut)
        s2 = np.maximum(np.abs(x + 0.030 * twist) - 0.020, ycut)
        cord = np.minimum(s1, s2)
        ccov, cluz = _shade(cord, size, bevel=0.025)
        braid = 0.82 + 0.30 * np.abs(np.cos((y + 0.5) * 40.0))
        _over(img, np.array((0.500, 0.395, 0.235), np.float32) * (cluz * braid)[..., None], ccov)

        # Aro de oro facetado.
        ring = np.abs(dist - 0.360) - 0.055
        rcov, rluz = _shade(ring, size, bevel=0.045)
        ang = np.arctan2(y - cy, x - cx)
        facet = 0.90 + 0.10 * np.cos(ang * 12.0)
        gold = np.array((0.930, 0.730, 0.250), np.float32) * (rluz * facet)[..., None]
        _over(img, gold, rcov)
        # Especular del aro (arriba-izquierda).
        _add(img, (1.00, 0.96, 0.78),
             np.exp(-(((x + 0.24) ** 2 + (y + 0.22) ** 2) / 0.0055)) * 0.55 * rcov)
    return _render_item(paint)


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
        os.path.join(TEX_BLOCK, "linea_sal.png"): make_linea_sal(),
        os.path.join(TEX_BLOCK, "portal_umbral.png"): make_portal_umbral(),
        os.path.join(TEX_ITEM, "campana_vigilia.png"): make_campana_vigilia(),
    }
    for path, img in files.items():
        if keep_art and os.path.exists(path):
            print("  [keep] %s (arte propio, no se toca)" % os.path.relpath(path, ROOT))
            continue
        img.save(path)
        print("  [ok] %s" % os.path.relpath(path, ROOT))


# ---------------------------------------------------------------------------
# §11: Estructura de worldgen «Santuario del Silente» (NBT de 1.20.1).
# Genera data/elsilente/structures/santuario.nbt con ruinas: altar de tierra
# de almas, lapida, libreria de lore (libro 1) y cofre con Corazon Silente +
# piezas del ritual. Las UV/IDs aqui DEBEN coincidir con los JSON de
# worldgen (structure/template_pool/structure_set) del mismo nombre.
# ---------------------------------------------------------------------------

STRUCT_DATA = os.path.join(ROOT, "src", "main", "resources", "data", "elsilente", "structures")
# 1.20.1
DATA_VERSION_1201 = 3465


def _nbt_string(s):
    data = s.encode("utf-8")
    return struct.pack(">h", len(data)) + data


def _nbt_list(elem_type, payloads):
    return bytes([elem_type]) + struct.pack(">i", len(payloads)) + b"".join(payloads)


def _nbt_compound(entries):
    """entries: [(nombre, tipo_nbt, payload_bytes), ...]"""
    out = b""
    for name, typ, payload in entries:
        out += bytes([typ]) + _nbt_string(name) + payload
    return out + b"\x00"


def _nbt_int_list(values):
    return _nbt_list(3, [struct.pack(">i", v) for v in values])


def _written_book(title, author, pages):
    page_payloads = [_nbt_string(json.dumps({"text": p}, ensure_ascii=False)) for p in pages]
    return [
        ("id", 8, _nbt_string("minecraft:written_book")),
        ("Count", 1, struct.pack(">b", 1)),
        ("tag", 10, _nbt_compound([
            ("title", 8, _nbt_string(title)),
            ("author", 8, _nbt_string(author)),
            ("generation", 3, struct.pack(">i", 0)),
            ("pages", 9, _nbt_list(8, page_payloads)),
        ])),
    ]


def make_santuario_nbt():
    """Ruinas 9x3x9: altar de soul soil, lapida, lectern con diario y cofre."""
    w, h, d = 9, 3, 9
    wall_props = {"up": "false", "north": "false", "east": "false",
                  "south": "false", "west": "false", "waterlogged": "false"}
    palette = [
        ("minecraft:air", None),                          # 0
        ("minecraft:cracked_stone_bricks", None),         # 1
        ("minecraft:mossy_stone_bricks", None),           # 2
        ("minecraft:stone_bricks", None),                 # 3
        ("minecraft:soul_soil", None),                    # 4
        ("minecraft:soul_sand", None),                    # 5
        ("minecraft:mossy_cobblestone_wall", wall_props),  # 6 (lapida)
        ("minecraft:lectern", {"facing": "south", "has_book": "true", "powered": "false"}),  # 7
        ("minecraft:chest", {"facing": "west", "type": "single", "waterlogged": "false"}),  # 8
        ("minecraft:coarse_dirt", None),                  # 9
        ("minecraft:dead_bush", None),                    # 10
        ("minecraft:cobblestone_wall", wall_props),       # 11 (pilaire roto)
    ]

    # Suelo por defecto: ladrillos agrietados con musgo en el borde.
    grid = [[[1 for _ in range(w)] for _ in range(d)] for _ in range(h)]
    for z in range(d):
        for x in range(w):
            if x in (0, 8) or z in (0, 8):
                grid[0][z][x] = 2 if (x + z) % 3 == 0 else 1
            elif (x + z) % 5 == 0:
                grid[0][z][x] = 3
            else:
                grid[0][z][x] = 1
    # Altar roto: tierra de almas agrietada en el centro (3x3) con nucleo de
    # arena de almas; base de tierra tosca para el arbusto.
    for z in range(3, 6):
        for x in range(3, 6):
            grid[0][z][x] = 4
    grid[0][4][4] = 5
    grid[0][6][1] = 9

    # Superficie (aire por defecto) con los elementos de la ruina.
    features = {
        (4, 3): 7,    # lectern con el diario del Vigia
        (6, 4): 8,    # cofre del santuario
        (7, 7): 6,    # lapida (poste de mamposteria musgosa)
        (1, 1): 11,   # pilaire roto
        (1, 6): 10,   # arbusto muerto sobre tierra tosca
        (2, 2): 3,    # escombros
    }
    for (x, z), idx in features.items():
        grid[1][z][x] = idx

    # Libro de lore (secciones que justifican ritual y debilidades).
    book = _written_book(
        "El diario del Vigia",
        "Vigia sin nombre",
        [
            "Escribo por si alguien encuentra estas ruinas. El Silente no mata por hambre: acecha, apaga las luces y solo entonces cruza la puerta.",
            "Ritual de medianoche: tierra de almas al centro, ocho obsidianas alrededor y velas encendidas en las cardinales. Lanza una perla de ender empuñando un Corazon Silente.",
            "La luz lo disipa y la sal corta el nacimiento de las Sombras (no su emboscada). La Campana calla la noche... pero atrae su caza. Recarga el Simbolo con un Corazon: agachate y usalo.",
        ])

    chest_items = [
        (0, "elsilente:corazon_silente", 1, None),
        (1, "minecraft:obsidian", 4, None),
        (2, "minecraft:candle", 2, None),
        (3, "minecraft:ender_pearl", 1, None),
        (4, "elsilente:linea_sal", 2, None),
        (5, "minecraft:bone", 2, None),
    ]
    chest_nbt = [
        ("id", 8, _nbt_string("minecraft:chest")),
        ("Items", 9, _nbt_list(10, [
            _nbt_compound([
                ("Slot", 1, struct.pack(">b", slot)),
                ("id", 8, _nbt_string(item)),
                ("Count", 1, struct.pack(">b", count)),
            ] + ([("tag", 10, _nbt_compound(tag))] if tag else []))
            for (slot, item, count, tag) in chest_items
        ])),
    ]
    lectern_nbt = [
        ("id", 8, _nbt_string("minecraft:lectern")),
        ("Book", 10, _nbt_compound(book)),
        ("Page", 3, struct.pack(">i", 0)),
    ]
    block_entities = {(4, 1, 3): lectern_nbt, (6, 1, 4): chest_nbt}

    # Recorrer todos los bloques (aire incluido: la plantilla despeja el hueco).
    block_payloads = []
    for y in range(h):
        for z in range(d):
            for x in range(w):
                state = grid[y][z][x]
                entries = [
                    ("pos", 9, _nbt_int_list([x, y, z])),
                    ("state", 3, struct.pack(">i", state)),
                ]
                nbt = block_entities.get((x, y, z))
                if nbt:
                    entries.append(("nbt", 10, _nbt_compound(nbt)))
                block_payloads.append(_nbt_compound(entries))

    palette_payloads = []
    for name, props in palette:
        entries = [("Name", 8, _nbt_string(name))]
        if props:
            entries.append(("Properties", 10, _nbt_compound([
                (k, 8, _nbt_string(v)) for k, v in props.items()
            ])))
        palette_payloads.append(_nbt_compound(entries))

    root = [
        ("size", 9, _nbt_int_list([w, h, d])),
        ("entities", 9, _nbt_list(10, [])),
        ("blocks", 9, _nbt_list(10, block_payloads)),
        ("palette", 9, _nbt_list(10, palette_payloads)),
        ("DataVersion", 3, struct.pack(">i", DATA_VERSION_1201)),
    ]
    # _nbt_compound ya cierra con TAG_End; no lleva byte extra al final.
    raw = bytes([10]) + _nbt_string("") + _nbt_compound(root)
    return gzip.compress(raw)


def generate_structure():
    print("Generando estructura de worldgen...")
    os.makedirs(STRUCT_DATA, exist_ok=True)
    path = os.path.join(STRUCT_DATA, "santuario.nbt")
    with open(path, "wb") as fh:
        fh.write(make_santuario_nbt())
    print("  [ok] data/elsilente/structures/santuario.nbt (%d bytes)" % os.path.getsize(path))


def main():
    ensure_dirs()
    generate_textures()
    generate_structure()
    if "--textures-only" not in sys.argv:
        generate_sounds()
    print("Listo.")


if __name__ == "__main__":
    main()
