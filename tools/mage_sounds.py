#!/usr/bin/env python3
"""
Bruitages des nouveaux sorts du Mage, synthétisés : craquement de glace, bouclier de pierre (roche qui
surgit), pierre qui se brise. WAV 44,1 kHz, 16 bits, stéréo.

Usage : python3 tools/mage_sounds.py --dst src/main/resources/sounds
"""
import argparse
from pathlib import Path

import numpy as np
from scipy import signal

from druid_sounds import RATE, save

rng = np.random.default_rng(5)


def t(seconds):
    return np.arange(int(RATE * seconds)) / RATE


def hp(x, f):
    return signal.sosfilt(signal.butter(2, f, btype="high", fs=RATE, output="sos"), x)


def lp(x, f):
    return signal.sosfilt(signal.butter(2, f, btype="low", fs=RATE, output="sos"), x)


def ice():
    tt = t(1.1)
    x = np.zeros(len(tt))
    for _ in range(70):                                         # craquements
        i = int(rng.uniform(0, 0.75) * RATE)
        n = int(rng.uniform(0.004, 0.02) * RATE)
        x[i:i + n] += rng.normal(0, 1, len(x[i:i + n])) * np.exp(-np.arange(len(x[i:i + n])) / (0.003 * RATE)) * rng.uniform(0.3, 1)
    x = hp(x, 2500)
    shimmer = sum(np.sin(2 * np.pi * f * tt) * np.exp(-tt * 3) for f in (2637, 3136, 3951)) * 0.25
    return x + shimmer * np.clip(tt / 0.1, 0, 1)


def stone():
    tt = t(0.9)
    thud = np.sin(2 * np.pi * (90 - 40 * tt) * tt) * np.exp(-tt * 7)
    grit = lp(rng.normal(0, 1, len(tt)), 900) * np.exp(-tt * 5) * 2
    return thud * 1.4 + grit


def shatter():
    tt = t(0.8)
    crack = hp(rng.normal(0, 1, len(tt)), 1200) * np.exp(-tt * 18)
    debris = np.zeros(len(tt))
    for _ in range(25):
        i = int(rng.uniform(0.05, 0.6) * RATE)
        n = int(0.03 * RATE)
        debris[i:i + n] += lp(rng.normal(0, 1, n), 2500) * np.exp(-np.arange(n) / (0.008 * RATE)) * rng.uniform(0.2, 0.7)
    return crack + debris + np.sin(2 * np.pi * 70 * tt) * np.exp(-tt * 10) * 0.8


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--dst", required=True, type=Path)
    dst = ap.parse_args().dst
    for name, fn in (("mage_ice", ice), ("mage_stone", stone), ("mage_shatter", shatter)):
        save(fn(), dst / f"{name}.wav")
        print(name)


if __name__ == "__main__":
    main()
