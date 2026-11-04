#!/usr/bin/env python3
"""
Synthèse des ambiances sonores (le pack d'origine n'en contient pas).

Chaque ambiance est une boucle de 16 s, mono 16 kHz 16 bits, dont la fin est fondue dans le début pour
boucler sans couture. Tout est généré à partir de bruit filtré et d'oscillateurs : aucun échantillon
extérieur, résultat reproductible (graines fixes).

Usage : python3 tools/ambience.py --dst src/main/resources/sounds
Dépendances : numpy, scipy.
"""
import argparse
import wave
from pathlib import Path

import numpy as np
from scipy import signal

SR = 16000
DURATION = 16
CROSSFADE = 2


def butter(x, kind, freq, order=2):
    nyq = SR / 2
    f = np.array(freq) / nyq
    b, a = signal.butter(order, f, btype=kind)
    return signal.lfilter(b, a, x)


def slow_noise(rng, n, rate_hz):
    """Variation lente et douce entre 0 et 1 (bruit interpolé)."""
    points = max(4, int(n / SR * rate_hz) + 3)
    v = rng.random(points)
    xs = np.linspace(0, points - 1, n)
    return np.interp(xs, np.arange(points), v)


def envelope(n, attack, release):
    e = np.ones(n)
    a = max(1, int(attack * SR))
    r = max(1, int(release * SR))
    e[:a] = np.linspace(0, 1, a)
    e[-r:] *= np.exp(-np.linspace(0, 5, r))
    return e


def place(track, sound, start):
    end = min(len(track), start + len(sound))
    if start < len(track):
        track[start:end] += sound[: end - start]


def chirp(rng, f0, f1, dur):
    n = int(dur * SR)
    f = np.linspace(f0, f1, n)
    phase = 2 * np.pi * np.cumsum(f) / SR
    tone = np.sin(phase) + 0.25 * np.sin(2 * phase)
    return tone * envelope(n, 0.01, dur * 0.6)


def bird(rng):
    base = rng.uniform(2200, 3800)
    out = []
    for _ in range(int(rng.integers(2, 6))):
        d = rng.uniform(0.05, 0.12)
        out.append(chirp(rng, base * rng.uniform(0.9, 1.1), base * rng.uniform(1.15, 1.5), d))
        out.append(np.zeros(int(rng.uniform(0.03, 0.09) * SR)))
    return np.concatenate(out)


def crow(rng):
    n = int(0.28 * SR)
    t = np.arange(n) / SR
    f = 520 + 60 * np.sin(2 * np.pi * 25 * t)
    saw = signal.sawtooth(2 * np.pi * np.cumsum(f) / SR)
    s = butter(saw, "bandpass", [500, 2200]) * envelope(n, 0.02, 0.15)
    return np.concatenate([s, np.zeros(int(0.12 * SR)), s * 0.8])


def drip(rng):
    n = int(0.09 * SR)
    t = np.arange(n) / SR
    f = rng.uniform(1300, 1900) * np.exp(-t * 18)
    tone = np.sin(2 * np.pi * np.cumsum(f) / SR) * np.exp(-t * 55)
    out = np.zeros(int(0.8 * SR))
    for delay, gain in [(0, 1.0), (0.13, 0.35), (0.29, 0.15), (0.47, 0.06)]:
        place(out, tone * gain, int(delay * SR))
    return out


def wind(rng, n, low, high, gust_rate, depth):
    base = butter(rng.standard_normal(n), "bandpass", [low, high])
    gust = 1 - depth + depth * slow_noise(rng, n, gust_rate)
    return base * gust


def forest(rng, n):
    x = wind(rng, n, 120, 700, 0.25, 0.6) * 0.5
    x += butter(rng.standard_normal(n), "bandpass", [2500, 6000]) * slow_noise(rng, n, 0.6) * 0.12
    for _ in range(14):
        place(x, bird(rng) * rng.uniform(0.15, 0.35), int(rng.uniform(0, n / SR - 1) * SR))
    return x


def lake(rng, n):
    t = np.arange(n) / SR
    lap = (0.5 + 0.5 * np.sin(2 * np.pi * t / 3.2 + 2 * slow_noise(rng, n, 0.3))) ** 3
    x = butter(rng.standard_normal(n), "lowpass", 500) * lap * 0.9
    x += wind(rng, n, 150, 500, 0.2, 0.5) * 0.25
    for _ in range(10):
        m = int(0.12 * SR)
        plop = butter(rng.standard_normal(m), "bandpass", [300, 900]) * envelope(m, 0.004, 0.1)
        place(x, plop * rng.uniform(0.3, 0.6), int(rng.uniform(0, n / SR - 1) * SR))
    for _ in range(4):
        place(x, bird(rng) * 0.15, int(rng.uniform(0, n / SR - 1) * SR))
    return x


def ruins(rng, n):
    x = wind(rng, n, 100, 600, 0.2, 0.7) * 0.55
    for _ in range(3):
        place(x, crow(rng) * 0.25, int(rng.uniform(0, n / SR - 1) * SR))
    for _ in range(4):
        place(x, bird(rng) * 0.12, int(rng.uniform(0, n / SR - 1) * SR))
    return x


def snow(rng, n):
    centre = 300 + 600 * slow_noise(rng, n, 0.15)
    raw = rng.standard_normal(n)
    out = np.zeros(n)
    block = 2048
    for i in range(0, n, block):
        c = centre[i]
        seg = raw[max(0, i - 512): i + block]
        filtered = butter(seg, "bandpass", [c * 0.8, c * 1.25], order=2)
        out[i: i + block] = filtered[-min(block, n - i):]
    howl = out * (0.4 + 0.6 * slow_noise(rng, n, 0.2)) * 1.4
    return howl + wind(rng, n, 2000, 6000, 0.4, 0.8) * 0.06


def desert(rng, n):
    x = wind(rng, n, 1200, 5000, 0.3, 0.8) * 0.35
    x += butter(rng.standard_normal(n), "lowpass", 120) * slow_noise(rng, n, 0.1) * 0.5
    return x


def dungeon(rng, n):
    t = np.arange(n) / SR
    lfo = 0.7 + 0.3 * np.sin(2 * np.pi * t / 8)
    drone = (np.sin(2 * np.pi * 55 * t) + 0.6 * np.sin(2 * np.pi * 82.5 * t) + 0.3 * np.sin(2 * np.pi * 110.3 * t)) * lfo * 0.12
    x = drone + wind(rng, n, 80, 300, 0.15, 0.6) * 0.3
    for _ in range(9):
        place(x, drip(rng) * rng.uniform(0.25, 0.45), int(rng.uniform(0, n / SR - 1) * SR))
    return x


AMBIENCES = {
    "amb_forest": forest,
    "amb_lake": lake,
    "amb_ruins": ruins,
    "amb_snow": snow,
    "amb_desert": desert,
    "amb_dungeon": dungeon,
}


def loopify(x):
    n = DURATION * SR
    xf = CROSSFADE * SR
    out = x[:n].copy()
    fade = np.linspace(0, 1, xf)
    out[:xf] = x[:xf] * fade + x[n:n + xf] * (1 - fade)
    return out


def write_wav(path: Path, x):
    peak = np.max(np.abs(x)) or 1
    pcm = (x / peak * 0.7 * 32767).astype(np.int16)
    with wave.open(str(path), "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(SR)
        w.writeframes(pcm.tobytes())


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--dst", required=True, type=Path)
    args = ap.parse_args()
    args.dst.mkdir(parents=True, exist_ok=True)
    for i, (name, fn) in enumerate(AMBIENCES.items()):
        rng = np.random.default_rng(1000 + i)
        n = (DURATION + CROSSFADE) * SR
        write_wav(args.dst / f"{name}.wav", loopify(fn(rng, n)))
    print("ambiances générées dans", args.dst)


if __name__ == "__main__":
    main()
