#!/usr/bin/env python3
"""
Import des planches « plateau + éléments » des Ruines, de la Montagne et de l'Arène (tools/source/*_sheet.*).

Chaque planche contient en haut l'illustration du plateau, en bas des éléments sur un damier peint (pas de
vraie transparence). Pour chaque carte :
- le haut de la planche devient le fond (backdrops/<nom>.jpg), bords prolongés pour la caméra, damier
  éventuel remplacé ;
- la grille de jeu est calée en perspective sur les 4 coins du plateau peint (fond, droite, avant, gauche) ;
- les éléments retenus sont détourés (decor/<nom>_*.png), à une taille fixe en unités du monde, leur base
  placée là où le jeu ancre les obstacles ;
- ombres portées et premier plan sont décrits dans le .properties.

Usage : python3 tools/import_maps.py --dst src/main/resources   (appelé aussi par import_sources.py)
"""
import argparse
from pathlib import Path

import cv2
import numpy as np
from PIL import Image
from scipy import ndimage

from import_sources import SOURCE, extend_borders, grid_properties, perspective_grid, save_fg

SPRITE_SCALE = 0.5      # les éléments sont enregistrés à 2× leur taille à l'écran (netteté au zoom)
OBSTACLE_ANCHOR = 0.80  # le jeu pose le centre de la case à 80 % de la hauteur d'un obstacle

# nom -> (planche, ligne de coupe, coins du plateau, marges arrière/avant en cases, bas maximal de la grille)
MAPS = {
    "ruins": ("ruins_sheet.jpg", 413, [(622, 64), (1036, 220), (615, 406), (208, 216)], 0.6, 0.35, 404),
    "mountain": ("mountain_sheet.jpg", 392, [(590, 46), (958, 168), (590, 328), (216, 168)], 0.55, 0.5, None),
    "arena": ("arena_sheet.png", 435, [(632, 268), (932, 345), (632, 448), (336, 346)], 0.3, 0.3, 427),
}

# ombres portées (dx, dy par unité de hauteur, opacité) et premier plan
LIGHT = {
    "ruins": "-0.55, 0.25, 0.42",     # soleil couchant à droite : ombres vers la gauche
    "mountain": "0.38, 0.2, 0.26",
    "arena": "0.12, 0.32, 0.3",       # soleil derrière les gradins : ombres vers l'avant
}

# éléments : nom -> (cadre x0, y0, x1, y1 dans la planche, largeur à l'écran en unités du monde)
PROPS = {
    "ruins": {
        "rubble_1": ((886, 463, 980, 543), 64), "rubble_2": ((998, 463, 1077, 540), 56),
        "rubble_3": ((24, 567, 110, 642), 60), "rubble_4": ((131, 570, 216, 644), 60),
        "column_fallen": ((243, 568, 331, 641), 62), "block_1": ((354, 566, 453, 650), 62),
        "block_2": ((479, 566, 573, 651), 60), "arch_broken": ((594, 567, 695, 650), 66),
        "wall_broken": ((704, 573, 796, 652), 60), "column_head": ((300, 736, 386, 806), 56),
        "tomb_1": ((847, 692, 931, 807), 44), "tomb_2": ((954, 698, 1044, 808), 46),
        "tomb_3": ((1058, 722, 1124, 809), 36), "tomb_4": ((1139, 703, 1224, 815), 44),
        "bush_1": ((25, 659, 92, 723), 44), "bush_2": ((115, 663, 176, 719), 40),
        "bush_3": ((200, 667, 260, 722), 40), "bush_4": ((285, 668, 347, 721), 40),
        "bush_5": ((371, 674, 429, 719), 38), "bush_6": ((455, 664, 528, 724), 46),
        "bush_7": ((25, 740, 95, 804), 44), "bush_8": ((116, 751, 182, 802), 40),
        "bush_9": ((212, 744, 281, 804), 42), "slab_1": ((409, 740, 502, 807), 56),
        "slab_2": ((521, 741, 618, 809), 58), "slab_3": ((637, 741, 736, 807), 58),
        "slab_4": ((745, 745, 827, 805), 48),
    },
    "mountain": {
        "rock_small": ((354, 475, 409, 506), 40), "rock_1": ((900, 493, 1008, 575), 64),
        "rock_2": ((893, 588, 1008, 675), 66), "crystal_1": ((472, 503, 550, 579), 46),
        "crystal_2": ((468, 595, 546, 668), 44), "crystal_3": ((565, 596, 639, 667), 42),
        "mound_1": ((575, 522, 661, 576), 54), "mound_2": ((672, 512, 770, 577), 60),
        "mound_3": ((659, 596, 772, 667), 64), "frost_bush_1": ((790, 496, 880, 572), 54),
        "frost_bush_2": ((787, 592, 877, 667), 54), "pine_small": ((208, 547, 269, 614), 44),
        "log": ((477, 690, 607, 758), 70),
        "twig_1": ((186, 627, 240, 674), 40), "twig_2": ((268, 621, 327, 674), 42),
        "twig_3": ((392, 628, 446, 673), 40), "snow_rocks_1": ((279, 715, 342, 756), 42),
        "snow_rocks_2": ((368, 709, 448, 757), 50), "snow_rocks_3": ((833, 711, 903, 756), 44),
        "ice_flower_1": ((935, 691, 1022, 763), 46), "ice_flower_2": ((1047, 704, 1120, 760), 44),
    },
    "arena": {
        "pillar_1": ((30, 446, 100, 630), 40), "pillar_2": ((191, 447, 257, 632), 38),
        "pillar_broken_1": ((111, 519, 174, 630), 38), "pillar_broken_2": ((274, 491, 343, 631), 40),
        "statue_1": ((519, 462, 592, 625), 44), "statue_2": ((623, 450, 697, 631), 44),
        "bust": ((722, 505, 791, 641), 40), "cypress": ((824, 445, 911, 639), 46),
        "banner_1": ((931, 455, 993, 606), 38), "banner_2": ((1001, 459, 1069, 618), 40),
        "amphora_1": ((369, 609, 435, 699), 34), "amphora_2": ((443, 621, 498, 703), 30),
        "amphora_3": ((1003, 621, 1055, 698), 30), "lantern_1": ((521, 643, 571, 706), 26),
        "lantern_2": ((609, 643, 660, 713), 26), "shield": ((1075, 636, 1138, 714), 40),
        "rubble_1": ((370, 729, 463, 805), 60), "rubble_2": ((486, 721, 591, 809), 62),
        "rubble_3": ((663, 716, 803, 818), 72), "bench_1": ((1022, 719, 1124, 791), 70),
        "bench_2": ((1137, 760, 1234, 829), 68), "stones": ((705, 665, 777, 712), 44),
        "tile_1": ((828, 659, 911, 707), 56), "tile_2": ((911, 624, 994, 671), 56),
        "tile_3": ((827, 781, 904, 827), 54), "tile_4": ((924, 763, 1025, 822), 60),
        "tile_5": ((909, 689, 1012, 746), 60), "pebble_1": ((1032, 792, 1076, 822), 26),
    },
}

# éléments flous du premier plan, dans les coins bas : (élément, x, y du bas, échelle, assombrissement)
FOREGROUND = {
    "ruins": {"left": [("ruins_rubble_3", 0, 330, 3.0, 0.32), ("ruins_bush_6", 150, 330, 2.6, 0.3),
                       ("ruins_bush_1", -50, 330, 2.6, 0.26)],
              "right": [("ruins_rubble_1", 180, 330, 3.0, 0.32), ("ruins_bush_7", 40, 330, 2.6, 0.3),
                        ("ruins_bush_9", 330, 330, 2.4, 0.26)]},
    "mountain": {"left": [("mountain_rock_2", 0, 330, 2.6, 0.55), ("mountain_pine_small", 170, 330, 2.6, 0.5)],
                 "right": [("mountain_rock_1", 170, 330, 2.6, 0.55), ("mountain_pine_small", 40, 330, 2.8, 0.5)]},
}


def checker(rgb: np.ndarray, snow: bool) -> np.ndarray:
    """Damier clair (blanc / gris clair, neutre) relié au bord, et ombres grises qui le prolongent."""
    c = rgb.astype(int)
    neutral = (c.max(2) - c.min(2)) < 9
    bg = neutral & (c.mean(2) > 200)
    if snow:
        # la neige est aussi blanche et neutre : on ne garde que ce qui ressemble localement à un damier
        # (deux teintes nettes, ~250 et ~215, sans valeurs intermédiaires)
        v = c.mean(2)
        light = (neutral & (v >= 242)).astype(np.float32)
        dark = (neutral & (v >= 204) & (v <= 226)).astype(np.float32)
        other = 1 - light - dark
        k = (11, 11)
        fl, fd, fo = (cv2.blur(x, k) for x in (light, dark, other))
        pattern = (fl > 0.2) & (fd > 0.2) & (fo < 0.12)
        bg &= ndimage.binary_dilation(pattern, iterations=3)
    lab, _ = ndimage.label(bg)
    border = set(np.unique(np.concatenate([lab[0], lab[-1], lab[:, 0], lab[:, -1]]))) - {0}
    bg = np.isin(lab, list(border))
    if not snow:
        # ombres peintes sur le damier : gris neutres plus sombres, accolés au fond
        shade = neutral & (c.mean(2) > 120)
        lab2, _ = ndimage.label(shade | bg)
        bg |= np.isin(lab2, list(set(np.unique(lab2[bg])) - {0})) & shade
    return bg


def cut_prop(rgb: np.ndarray, box, width_world: float, snow: bool) -> Image.Image:
    x0, y0, x1, y1 = box
    pad = 3
    c = rgb[max(0, y0 - pad):y1 + pad, max(0, x0 - pad):x1 + pad]
    mask = ~checker(c, snow)
    mask = ndimage.binary_opening(mask, iterations=1)
    parts, n = ndimage.label(mask)
    if n > 1:
        sizes = ndimage.sum(mask, parts, range(1, n + 1))
        mask = np.isin(parts, [i + 1 for i in range(n) if sizes[i] >= 0.05 * sizes.max()])
    alpha = ndimage.gaussian_filter(ndimage.binary_erosion(mask, iterations=1).astype(np.float32) * 0.75
                                    + mask.astype(np.float32) * 0.25, 0.6)
    img = Image.fromarray(np.dstack([c.astype(np.uint8), (np.clip(alpha, 0, 1) * 255).astype(np.uint8)]), "RGBA")
    img = img.crop(img.getchannel("A").getbbox())
    # taille : largeur voulue à l'écran ×2
    w = int(round(width_world / SPRITE_SCALE))
    h = int(round(img.height * w / img.width))
    img = img.resize((w, h), Image.LANCZOS)
    # base de l'objet (centre de son emprise au sol) à OBSTACLE_ANCHOR de la hauteur : marge transparente dessous
    base = h - 0.22 * w
    total = max(h, int(round(base / OBSTACLE_ANCHOR)))
    canvas = Image.new("RGBA", (w, total), (0, 0, 0, 0))
    canvas.alpha_composite(img, (0, 0))
    return canvas


def fill_checker(top: np.ndarray, snow: bool) -> np.ndarray:
    """Remplace le damier qui apparaît dans le fond (coins de la Montagne) par un sol enneigé fondu."""
    bg = checker(top, snow)
    if not bg.any():
        return top
    bg = ndimage.binary_dilation(bg, iterations=2)
    out = top.astype(np.float32)
    fill = np.array([236, 242, 248], np.float32)
    noise = cv2.GaussianBlur(np.random.default_rng(3).normal(0, 1, bg.shape).astype(np.float32), (0, 0), 6) * 40
    filled = fill[None, None] + noise[..., None] * np.array([1, 1, 0.6], np.float32)
    w = cv2.GaussianBlur(bg.astype(np.float32), (0, 0), 6)[..., None]
    return (out * (1 - w) + filled * w).clip(0, 255).astype(np.uint8)


def arena_bottom(big: np.ndarray, y0: int) -> np.ndarray:
    """
    Sous la coupe de l'Arène, le reflet des gradins serait absurde : on prolonge les dernières lignes
    (le mur et le sable du premier rang) vers le bas, très flou et assombri, comme un premier plan hors champ.
    """
    out = big.astype(np.float32)
    h = big.shape[0]
    band = out[y0 - 24:y0].mean(0)                                 # couleur des dernières lignes, colonne par colonne
    rows = np.arange(h - y0, dtype=np.float32)[:, None, None]
    fill = band[None] * (1 - np.clip(rows / 260, 0, 0.75))         # s'assombrit vers le bas
    fill = cv2.GaussianBlur(fill, (0, 0), 14)
    k = np.clip((np.arange(-12, h - y0 - 12, dtype=np.float32) + 12) / 24, 0, 1)[:, None, None]
    out[y0:] = out[y0:] * (1 - k) + fill * k
    return out.clip(0, 255).astype(np.uint8)


def build_map(dst: Path, name: str):
    sheet_name, cut, plateau, back, front, max_bottom = MAPS[name]
    sheet = np.array(Image.open(SOURCE / sheet_name).convert("RGB"))
    snow = name == "mountain"
    top = fill_checker(sheet[:cut], snow)
    pad = 220
    big = extend_borders(top, pad)
    if name == "arena":
        big = arena_bottom(big, pad + cut)
    out = dst / "backdrops"
    Image.fromarray(big).save(out / f"{name}.jpg", quality=90, optimize=True)
    corners, h = perspective_grid(plateau, back, front, max_bottom=max_bottom)
    text, scale = grid_properties(corners, h, pad, f"{name}.jpg")
    text += f"# ombres portées : décalage par unité de hauteur (x, y) et opacité\nshadow = {LIGHT[name]}\n"
    if name in FOREGROUND:
        text += f"# premier plan flou dans les coins bas\nforeground = {name}_fg\n"
    (out / f"{name}.properties").write_text(text, encoding="utf-8")

    decor = dst / "decor"
    for prop, (box, width) in PROPS[name].items():
        cut_prop(sheet, box, width, snow).save(decor / f"{name}_{prop}.png", optimize=True)
    print(f"{name} : grille en perspective, coins {np.round(corners).astype(int).tolist()}, "
          f"{len(PROPS[name])} éléments")


def build_foreground(dst: Path, name: str):
    decor = dst / "decor"
    for side, parts in FOREGROUND[name].items():
        canvas = Image.new("RGBA", (460, 330), (0, 0, 0, 0))
        for prop, x, y, sc, dark in parts:
            im = Image.open(decor / f"{prop}.png")
            im = im.crop(im.getchannel("A").getbbox())
            im = im.resize((int(im.width * sc * SPRITE_SCALE * 2), int(im.height * sc * SPRITE_SCALE * 2)), Image.LANCZOS)
            a = np.array(im).astype(np.float32)
            a[..., :3] *= dark
            if name == "mountain":
                a[..., :3] = a[..., :3] * 0.8 + np.array([40, 60, 90]) * 0.2      # contre-jour bleuté
            canvas.alpha_composite(Image.fromarray(a.clip(0, 255).astype(np.uint8)), (x, y - im.height))
        arr = np.array(canvas).astype(np.float32)
        save_fg(arr[..., :3], arr[..., 3] / 255, dst / "backdrops" / f"{name}_fg_{side}.png", 2.6)


def build_all(dst: Path):
    for name in MAPS:
        build_map(dst, name)
    for name in FOREGROUND:
        build_foreground(dst, name)


if __name__ == "__main__":
    ap = argparse.ArgumentParser()
    ap.add_argument("--dst", required=True, type=Path)
    build_all(ap.parse_args().dst)
