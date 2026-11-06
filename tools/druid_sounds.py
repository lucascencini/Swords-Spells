#!/usr/bin/env python3
"""
Bruitages des sorts du Druide, synthétisés (aucun fichier extérieur) :
croassement, bourdonnement d'abeilles, souffle de tornade, coup de tonnerre, tintement de potion.
WAV 44,1 kHz, 16 bits, stéréo, comme les autres sons du jeu.

Usage : python3 tools/druid_sounds.py --dst src/main/resources/sounds
"""
import argparse
import wave
from pathlib import Path

import numpy as np

RATE = 44100
rng = np.random.default_rng(7)


def t(seconds):
    return np.arange(int(RATE * seconds)) / RATE


def env(n, attack, release):
    e = np.ones(n)
    a = max(1, int(RATE * attack))
    r = max(1, int(RATE * release))
    e[:a] = np.linspace(0, 1, a)
    e[-r:] *= np.linspace(1, 0, r)
    return e


def lowpass(x, cutoff):
    """Filtre passe-bas du premier ordre (appliqué deux fois)."""
    k = np.exp(-2 * np.pi * cutoff / RATE)
    for _ in range(2):
        y = np.empty_like(x)
        acc = 0.0
        for i, v in enumerate(x):
            acc = (1 - k) * v + k * acc
            y[i] = acc
        x = y
    return x


def crow():
    out = np.zeros(int(RATE * 0.75))
    for start, dur, f0 in ((0.0, 0.26, 620), (0.36, 0.3, 560)):
        tt = t(dur)
        f = f0 * (1 - 0.35 * tt / dur)
        phase = 2 * np.pi * np.cumsum(f) / RATE
        tone = np.sign(np.sin(phase)) * 0.4 + np.sin(phase * 2) * 0.3        # timbre rauque
        tone += rng.normal(0, 0.35, len(tt))
        tone = lowpass(tone, 2400) * env(len(tt), 0.01, 0.12) * (1 + 0.5 * np.sin(2 * np.pi * 38 * tt))
        s = int(RATE * start)
        out[s:s + len(tt)] += tone
    return out


def buzz():
    tt = t(1.0)
    f = 210 + 18 * np.sin(2 * np.pi * 5 * tt) + 9 * np.sin(2 * np.pi * 13 * tt)
    phase = 2 * np.pi * np.cumsum(f) / RATE
    saw = (phase / (2 * np.pi)) % 1 * 2 - 1
    x = lowpass(saw + 0.3 * np.sin(phase * 2.01), 1800)
    return x * env(len(tt), 0.08, 0.35) * (0.7 + 0.3 * np.sin(2 * np.pi * 7 * tt))


def wind():
    tt = t(1.3)
    noise = rng.normal(0, 1, len(tt))
    x = lowpass(noise, 600) * 2.2 + lowpass(noise, 2400) * 0.4
    swell = np.sin(np.pi * tt / tt[-1]) ** 1.5 * (0.75 + 0.25 * np.sin(2 * np.pi * 4.5 * tt))
    return x * swell


def thunder():
    tt = t(1.6)
    crack = rng.normal(0, 1, len(tt)) * np.exp(-tt * 28)
    rumble = lowpass(rng.normal(0, 1, len(tt)), 160) * 6 * np.exp(-tt * 2.2) * (0.6 + 0.4 * np.sin(2 * np.pi * 3 * tt))
    return (crack * 0.9 + rumble) * env(len(tt), 0.002, 0.4)


def chime():
    tt = t(1.2)
    x = np.zeros(len(tt))
    for i, (f, d) in enumerate(((1318.5, 0.0), (1760, 0.09), (2093, 0.18), (2637, 0.27))):
        s = int(RATE * d)
        n = len(tt) - s
        tn = tt[:n]
        x[s:] += (np.sin(2 * np.pi * f * tn) + 0.3 * np.sin(2 * np.pi * f * 2.76 * tn)) * np.exp(-tn * 4.5) * 0.5
    bubbles = sum(np.sin(2 * np.pi * (400 + 300 * k) * tt * (1 + tt)) * np.exp(-((tt - 0.5 - 0.12 * k) * 30) ** 2)
                  for k in range(4))
    return x + bubbles * 0.25


def save(x, path, peak=0.8):
    x = x / max(1e-9, np.abs(x).max()) * peak
    pcm = (np.clip(x, -1, 1) * 32767).astype(np.int16)
    stereo = np.repeat(pcm[:, None], 2, axis=1)
    with wave.open(str(path), "wb") as w:
        w.setnchannels(2)
        w.setsampwidth(2)
        w.setframerate(RATE)
        w.writeframes(stereo.tobytes())


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--dst", required=True, type=Path)
    args = ap.parse_args()
    for name, fn in (("druid_crow", crow), ("druid_bees", buzz), ("druid_wind", wind),
                     ("druid_thunder", thunder), ("druid_chime", chime)):
        save(fn(), args.dst / f"{name}.wav")
        print(name)


if __name__ == "__main__":
    main()
