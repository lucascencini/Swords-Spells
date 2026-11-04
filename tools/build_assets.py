#!/usr/bin/env python3
"""
Pipeline d'assets de Mini Dofus.

Prend le dossier `assets/` d'origine (celui du prototype HTML, ~270 fichiers dont
beaucoup de doublons) et produit le jeu d'assets minimal, nettoyé et normalisé
dans `src/main/resources/`.

Ce que fait le script :
  * ne garde que les sprites réellement utilisés par le jeu ;
  * supprime les pixels parasites laissés par le découpage de la planche d'origine
    (composantes connexes minuscules) ;
  * normalise les tuiles : même largeur, même point d'ancrage (centre du losange) ;
  * recadre les portraits ;
  * convertit les sons OGG en WAV 16 bits (Java ne décode pas l'OGG nativement).

Usage :
    python3 tools/build_assets.py --src /chemin/vers/assets --dst src/main/resources

Dépendances : Pillow, numpy, scipy, ffmpeg (dans le PATH).
"""
import argparse
import subprocess
from pathlib import Path

import numpy as np
from PIL import Image
from scipy import ndimage

TILE_WIDTH = 72          # largeur normalisée des tuiles (px)
TILE_FACE_CENTER_Y = 24  # ligne du centre du losange dans l'image normalisée

# nom de sortie -> chemin source (relatif au dossier assets d'origine)
TILES = {
    **{f"grass_{i}": f"terrain/grass_0{i}.png" for i in range(1, 7)},
    "stone": "terrain/stone_path_01.png",
    "water": "terrain/water_01.png",
}
CHARACTERS = {
    f"{cls}_{i}": f"characters/{cls}/{cls}_pose_0{i}.png"
    for cls in ("warrior", "mage") for i in range(1, 8)
}
# (nom, source, nettoyer_les_fragments)
DECOR = [
    ("rock_cluster_1", "environment/rock_cluster_01.png", True),
    ("rock_cluster_2", "environment/rock_cluster_02.png", True),
    ("crate_1", "environment/crate_01.png", True),
    ("crate_2", "environment/crate_02.png", True),
    ("fallen_log", "environment/fallen_log.png", False),
    ("tree_oak_1", "arbre_feuillu_01.png", True),
    ("tree_oak_2", "arbre_feuillu_02.png", True),
    ("tree_oak_3", "arbre_feuillu_03.png", True),
    ("tree_deciduous", "environment/tree_deciduous_01.png", True),
    ("tree_pine_1", "environment/tree_pine_02.png", True),
    ("tree_pine_2", "arbre_pin_02.png", True),
    ("tree_pine_3", "arbre_pin_03.png", True),
    ("bush", "arbre_buisson_03.png", True),
    ("flowers_purple", "environment/flowers_purple.png", False),
    ("flowers_red", "environment/flowers_red.png", False),
    ("flowers_white", "environment/flowers_white.png", False),
    ("flowers_yellow", "environment/flowers_yellow.png", False),
    ("flowers_blue", "environment/flowers_blue.png", False),
]
FX = {
    "arcane": "effects/arcane_effect.png",
    "fire_projectile": "effects/fire_projectile.png",
    "fire_explosion": "effects/fire_explosion.png",
}
PORTRAITS = {
    "warrior": "interface/portraits/warrior_portrait.png",
    "mage": "interface/portraits/mage_portrait.png",
}
ICONS = {
    "boots": "interface/ability_buttons/boots.png",
    "fire": "interface/ability_buttons/fire.png",
    "shield": "interface/ability_buttons/shield.png",
    "strike": "interface/ability_buttons/sword.png",
}
SOUNDS = [
    "blade_02", "blade_03", "metal_02", "spell_01", "spell_fire_03", "spell_fire_06",
    "creature_hurt_01", "item_gem_02", "item_coins_02", "misc_01", "wood_01",
]


def load(path: Path) -> Image.Image:
    return Image.open(path).convert("RGBA")


def keep_main_components(img: Image.Image, min_ratio: float) -> Image.Image:
    """Supprime les composantes connexes dont l'aire < min_ratio * la plus grande."""
    arr = np.array(img)
    mask = arr[:, :, 3] > 20
    labels, n = ndimage.label(mask, structure=np.ones((3, 3)))
    if n <= 1:
        return img
    areas = ndimage.sum(mask, labels, index=range(1, n + 1))
    biggest = max(areas)
    keep = np.zeros_like(mask)
    for idx, area in enumerate(areas, start=1):
        if area >= biggest * min_ratio:
            keep |= labels == idx
    arr[~keep, 3] = 0
    return Image.fromarray(arr)


def trim(img: Image.Image) -> Image.Image:
    bbox = img.getchannel("A").point(lambda a: 255 if a > 20 else 0).getbbox()
    return img.crop(bbox) if bbox else img


def face_center_row(img: Image.Image) -> int:
    """Ligne où le losange atteint sa largeur maximale (= son centre)."""
    a = np.array(img.getchannel("A")) > 40
    rows = [y for y in range(a.shape[0]) if a[y].any()]
    left = {y: int(np.argmax(a[y])) for y in rows}
    leftmost = min(left.values())
    return next(y for y in rows if left[y] <= leftmost + 1)


def normalise_tile(img: Image.Image) -> Image.Image:
    img = trim(keep_main_components(img, 0.2))
    scale = TILE_WIDTH / img.width
    img = img.resize((TILE_WIDTH, round(img.height * scale)), Image.LANCZOS)
    fc = face_center_row(img)
    pad = TILE_FACE_CENTER_Y - fc
    out = Image.new("RGBA", (TILE_WIDTH, img.height + max(pad, 0)), (0, 0, 0, 0))
    out.paste(img, (0, max(pad, 0)))
    return out if pad >= 0 else out.crop((0, -pad, out.width, out.height))


def save(img: Image.Image, path: Path):
    path.parent.mkdir(parents=True, exist_ok=True)
    img.save(path, optimize=True)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--src", required=True, type=Path, help="dossier assets/ d'origine")
    ap.add_argument("--dst", required=True, type=Path, help="dossier resources/ de sortie")
    args = ap.parse_args()
    src, dst = args.src, args.dst

    for name, rel in TILES.items():
        save(normalise_tile(load(src / rel)), dst / "tiles" / f"{name}.png")
    for name, rel in CHARACTERS.items():
        save(load(src / rel), dst / "characters" / f"{name}.png")
    for name, rel, clean in DECOR:
        img = load(src / rel)
        img = trim(keep_main_components(img, 0.3)) if clean else img
        save(img, dst / "decor" / f"{name}.png")
    for name, rel in FX.items():
        save(load(src / rel), dst / "fx" / f"{name}.png")
    for name, rel in PORTRAITS.items():
        save(trim(keep_main_components(load(src / rel), 0.5)), dst / "ui" / f"portrait_{name}.png")
    for name, rel in ICONS.items():
        save(trim(keep_main_components(load(src / rel), 0.5)), dst / "ui" / f"icon_{name}.png")

    (dst / "sounds").mkdir(parents=True, exist_ok=True)
    for name in SOUNDS:
        subprocess.run(
            ["ffmpeg", "-y", "-loglevel", "error", "-i", str(src / "sounds" / f"{name}.ogg"),
             "-ar", "44100", "-ac", "2", "-c:a", "pcm_s16le", str(dst / "sounds" / f"{name}.wav")],
            check=True)
    print("assets générés dans", dst)


if __name__ == "__main__":
    main()
