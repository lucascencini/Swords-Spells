#!/usr/bin/env python3
"""
Sons de l'Arène, synthétisés (aucun fichier extérieur) :
- amb_arena.wav : murmure de foule en boucle de 16 s sans couture (format des autres ambiances, 16 kHz mono) ;
- arena_cheer.wav : clameur (combattant à terre, victoire), 44,1 kHz stéréo comme les bruitages.

Une foule = beaucoup de « voix » : bruit filtré dans la bande de la voix (formants), modulé par des syllabes
irrégulières (3 à 6 par seconde), chaque voix à sa hauteur et à sa distance.

Usage : python3 tools/arena_sounds.py --dst src/main/resources/sounds
"""
import argparse
import wave
from pathlib import Path

import numpy as np
from scipy import signal

rng = np.random.default_rng(21)


def band(x, lo, hi, rate):
    sos = signal.butter(2, [lo, hi], btype="band", fs=rate, output="sos")
    return signal.sosfilt(sos, x)


def voices(seconds, rate, count, excitement):
    n = int(seconds * rate)
    t = np.arange(n) / rate
    out = np.zeros(n)
    for _ in range(count):
        f1 = rng.uniform(350, 800)                                  # premier formant
        f2 = f1 * rng.uniform(2.0, 3.2)
        src = rng.normal(0, 1, n)
        v = band(src, f1 * 0.7, f1 * 1.3, rate) + 0.5 * band(src, f2 * 0.8, min(f2 * 1.2, rate / 2 - 100), rate)
        rate_syl = rng.uniform(3, 6) * (1 + excitement)
        phase = rng.uniform(0, 2 * np.pi)
        syll = np.clip(np.sin(2 * np.pi * rate_syl * t + phase + 0.8 * np.sin(2 * np.pi * 0.7 * t)), 0, None) ** 1.5
        talk = np.clip(np.sin(2 * np.pi * rng.uniform(0.05, 0.2) * t + rng.uniform(0, 6)), 0, None) ** 0.5
        out += v * syll * (0.4 + 0.6 * talk) * rng.uniform(0.3, 1.0)
    return out


def loop_crowd(rate=16000, seconds=16.0):
    fade = 2.0
    x = voices(seconds + fade, rate, 70, 0.0)
    x += band(rng.normal(0, 1, len(x)), 200, 2500, rate) * 0.6      # rumeur lointaine
    n, f = int(seconds * rate), int(fade * rate)
    w = np.linspace(0, 1, f)
    y = x[:n].copy()
    y[:f] = x[:f] * w + x[n:n + f] * (1 - w)                         # bouclage sans couture
    return y


def cheer(rate=44100, seconds=2.6):
    x = voices(seconds, rate, 90, 1.2) * 1.6
    x += band(rng.normal(0, 1, len(x)), 600, 5000, rate) * 2.2      # « ouais ! » aigu
    t = np.arange(len(x)) / rate
    env = np.clip(t / 0.25, 0, 1) * np.exp(-np.clip(t - 0.6, 0, None) * 1.4)
    claps = np.zeros(len(x))
    for _ in range(160):
        i = int(rng.uniform(0.2, seconds - 0.1) * rate)
        k = int(0.012 * rate)
        claps[i:i + k] += rng.normal(0, 1, len(claps[i:i + k])) * np.exp(-np.arange(len(claps[i:i + k])) / (0.003 * rate))
    return x * env + band(claps, 800, 6000, rate) * 3 * env


def save(x, path, rate, stereo, peak):
    x = x / max(1e-9, np.abs(x).max()) * peak
    pcm = (np.clip(x, -1, 1) * 32767).astype(np.int16)
    if stereo:
        pcm = np.repeat(pcm[:, None], 2, axis=1)
    with wave.open(str(path), "wb") as w:
        w.setnchannels(2 if stereo else 1)
        w.setsampwidth(2)
        w.setframerate(rate)
        w.writeframes(pcm.tobytes())


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--dst", required=True, type=Path)
    dst = ap.parse_args().dst
    save(loop_crowd(), dst / "amb_arena.wav", 16000, False, 0.55)
    save(cheer(), dst / "arena_cheer.wav", 44100, True, 0.8)
    print("amb_arena, arena_cheer")


if __name__ == "__main__":
    main()
