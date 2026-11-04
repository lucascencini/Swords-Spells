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
    "blade_01", "blade_02", "blade_03", "metal_02", "spell_01", "spell_fire_03", "spell_fire_06",
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


def recolor_blue_to_green(img: Image.Image) -> Image.Image:
    """Recolore les teintes bleues (robe, capuche, orbe) en vert forêt : l'Archère dérive des poses du Mage."""
    rgba = np.array(img.convert("RGBA")).astype(np.float32) / 255.0
    rgb, alpha = rgba[:, :, :3], rgba[:, :, 3:]
    hsv = np.array(Image.fromarray((rgb * 255).astype(np.uint8)).convert("HSV")).astype(np.float32)
    hue = hsv[:, :, 0] * 360 / 255
    blue = (hue > 185) & (hue < 265) & (hsv[:, :, 1] > 40)
    hsv[:, :, 0] = np.where(blue, 95 * 255 / 360, hsv[:, :, 0])
    hsv[:, :, 1] = np.where(blue, hsv[:, :, 1] * 0.62, hsv[:, :, 1])
    hsv[:, :, 2] = np.where(blue, hsv[:, :, 2] * 0.72, hsv[:, :, 2])
    out = np.array(Image.fromarray(hsv.astype(np.uint8), "HSV").convert("RGB")).astype(np.float32) / 255.0
    return Image.fromarray((np.concatenate([out, alpha], axis=2) * 255).astype(np.uint8), "RGBA")


# ---------------------------------------------------------------------------- biomes

def _hsv(img: Image.Image):
    rgba = np.array(img.convert("RGBA"))
    hsv = np.array(Image.fromarray(rgba[:, :, :3]).convert("HSV")).astype(np.float32) / 255.0
    return rgba[:, :, 3:], hsv


def _from_hsv(alpha, hsv) -> Image.Image:
    rgb = np.array(Image.fromarray((np.clip(hsv, 0, 1) * 255).astype(np.uint8), "HSV").convert("RGB"))
    return Image.fromarray(np.concatenate([rgb, alpha], axis=2), "RGBA")


def _green_mask(hsv):
    hue = hsv[:, :, 0] * 360
    return (hue > 40) & (hue < 170) & (hsv[:, :, 1] > 0.18)


def to_snow(img: Image.Image) -> Image.Image:
    """Herbe → neige (blanc légèrement bleuté, variations de luminosité conservées)."""
    alpha, hsv = _hsv(img)
    g = _green_mask(hsv)
    hsv[:, :, 0] = np.where(g, 210 / 360, hsv[:, :, 0])
    hsv[:, :, 1] = np.where(g, 0.05 + 0.08 * (1 - hsv[:, :, 2]), hsv[:, :, 1] * 0.8)
    hsv[:, :, 2] = np.where(g, 0.78 + 0.22 * hsv[:, :, 2], hsv[:, :, 2] * 0.92)
    return _from_hsv(alpha, hsv)


def to_sand(img: Image.Image) -> Image.Image:
    """Herbe → sable chaud."""
    alpha, hsv = _hsv(img)
    g = _green_mask(hsv)
    hsv[:, :, 0] = np.where(g, 40 / 360, hsv[:, :, 0])
    hsv[:, :, 1] = np.where(g, hsv[:, :, 1] * 0.55, hsv[:, :, 1])
    hsv[:, :, 2] = np.where(g, np.minimum(1, hsv[:, :, 2] * 1.12), hsv[:, :, 2])
    return _from_hsv(alpha, hsv)


def to_dungeon(img: Image.Image, value: float) -> Image.Image:
    """Dalle → pierre froide de crypte ; la mousse devient sombre."""
    alpha, hsv = _hsv(img)
    g = _green_mask(hsv)
    hsv[:, :, 0] = np.where(g, 100 / 360, 220 / 360)
    hsv[:, :, 1] = np.where(g, hsv[:, :, 1] * 0.28, 0.10)
    hsv[:, :, 2] = np.where(g, hsv[:, :, 2] * value * 0.75, hsv[:, :, 2] * value)
    return _from_hsv(alpha, hsv)


def to_ice(img: Image.Image) -> Image.Image:
    """Eau → glace pâle."""
    alpha, hsv = _hsv(img)
    blue = (hsv[:, :, 0] * 360 > 170) & (hsv[:, :, 0] * 360 < 250)
    hsv[:, :, 0] = np.where(blue, 195 / 360, hsv[:, :, 0])
    hsv[:, :, 1] = np.where(blue, hsv[:, :, 1] * 0.32, hsv[:, :, 1])
    hsv[:, :, 2] = np.where(blue, np.minimum(1, 0.55 + hsv[:, :, 2] * 0.55), hsv[:, :, 2])
    return _from_hsv(alpha, hsv)


def darker(img: Image.Image, factor: float) -> Image.Image:
    alpha, hsv = _hsv(img)
    hsv[:, :, 2] *= factor
    return _from_hsv(alpha, hsv)


def snow_capped(img: Image.Image) -> Image.Image:
    """Ajoute de la neige sur les parties claires (aiguilles de pin, dessus des rochers)."""
    rgba = np.array(img.convert("RGBA")).astype(np.float32)
    alpha = rgba[:, :, 3:]
    rgb = rgba[:, :, :3] / 255.0
    lum = rgb.mean(axis=2, keepdims=True)
    h = rgb.shape[0]
    height_bias = np.linspace(1.15, 0.85, h).reshape(h, 1, 1)
    k = np.clip((lum * height_bias - 0.27) * 3.6, 0, 1)
    snow = np.array([0.93, 0.96, 1.0]).reshape(1, 1, 3)
    out = rgb * (1 - k) + snow * k
    return Image.fromarray(np.concatenate([out * 255, alpha], axis=2).astype(np.uint8), "RGBA")


def tint(img: Image.Image, hue: float, sat_mul: float, val_mul: float) -> Image.Image:
    alpha, hsv = _hsv(img)
    hsv[:, :, 0] = hue / 360
    hsv[:, :, 1] = np.minimum(1, hsv[:, :, 1] * sat_mul + 0.05)
    hsv[:, :, 2] = np.minimum(1, hsv[:, :, 2] * val_mul)
    return _from_hsv(alpha, hsv)


def draw_cactus(arms: int, seed: int) -> Image.Image:
    """Cactus en pixel art (le pack n'en contient pas) : dessiné à 4× puis réduit pour adoucir les bords."""
    from PIL import ImageDraw
    rng = np.random.default_rng(seed)
    S = 4
    W, H = 46, 84
    img = Image.new("RGBA", (W * S, H * S), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    dark, mid, light = (46, 92, 52, 255), (74, 140, 70, 255), (128, 186, 96, 255)
    outline = (30, 52, 30, 255)

    def column(x0, y0, x1, y1):
        d.rounded_rectangle([x0 * S, y0 * S, x1 * S, y1 * S], radius=(x1 - x0) * S // 2, fill=outline)
        d.rounded_rectangle([(x0 + 1) * S, (y0 + 1) * S, (x1 - 1) * S, (y1 - 1) * S], radius=(x1 - x0 - 2) * S // 2, fill=mid)
        d.rectangle([(x0 + 2) * S, (y0 + 4) * S, (x0 + 4) * S, (y1 - 3) * S], fill=light)
        d.rectangle([(x1 - 4) * S, (y0 + 4) * S, (x1 - 2) * S, (y1 - 3) * S], fill=dark)

    column(16, 6, 30, 80)
    if arms >= 1:
        column(4, 24, 14, 50)
        d.rectangle([10 * S, 44 * S, 18 * S, 50 * S], fill=outline)
        d.rectangle([10 * S, 45 * S, 18 * S, 49 * S], fill=mid)
    if arms >= 2:
        column(32, 14, 42, 40)
        d.rectangle([28 * S, 34 * S, 36 * S, 40 * S], fill=outline)
        d.rectangle([28 * S, 35 * S, 36 * S, 39 * S], fill=mid)
    for _ in range(40):
        x = int(rng.integers(5, W - 5)) * S
        y = int(rng.integers(8, H - 8)) * S
        if img.getpixel((x, y))[3] > 0:
            d.point([(x, y), (x + 1, y), (x, y + 1)], fill=(235, 230, 200, 255))
    d.ellipse([12 * S, 77 * S, 34 * S, 83 * S], fill=(120, 96, 60, 255))
    return img.resize((W, H), Image.LANCZOS)


def build_biomes(src: Path, dst: Path):
    tiles = dst / "tiles"
    grass = [load(tiles / f"grass_{i}.png") for i in range(1, 7)]
    stone = load(tiles / "stone.png")
    water = load(tiles / "water.png")
    for i, g in enumerate(grass, start=1):
        save(to_snow(g), tiles / f"snow_{i}.png")
        save(to_sand(g), tiles / f"sand_{i}.png")
    save(to_snow(stone), tiles / "snow_stone.png")
    save(to_ice(water), tiles / "ice.png")
    save(to_sand(tint(stone, 36, 0.75, 1.08)), tiles / "sand_stone.png")
    variants = [stone, stone.transpose(Image.FLIP_LEFT_RIGHT), stone, stone.transpose(Image.FLIP_LEFT_RIGHT), stone, stone]
    for i, (v, value) in enumerate(zip(variants, [0.62, 0.66, 0.56, 0.6, 0.7, 0.52]), start=1):
        save(to_dungeon(v, value), tiles / f"dungeon_{i}.png")
    save(to_dungeon(stone, 0.42), tiles / "dungeon_stone.png")
    save(darker(water, 0.55), tiles / "dungeon_water.png")

    decor = dst / "decor"
    for name in ("tree_pine_1", "tree_pine_2", "tree_pine_3", "bush"):
        save(snow_capped(load(decor / f"{name}.png")), decor / f"snow_{name}.png")
    for name in ("rock_cluster_1", "rock_cluster_2"):
        rock = load(decor / f"{name}.png")
        save(snow_capped(tint(rock, 215, 0.5, 1.0)), decor / f"snow_{name}.png")
        save(tint(rock, 28, 1.8, 1.05), decor / f"desert_{name}.png")
    save(tint(load(decor / "bush.png"), 38, 0.8, 0.95), decor / "dry_bush.png")
    save(draw_cactus(2, 1), decor / "cactus_1.png")
    save(draw_cactus(1, 2), decor / "cactus_2.png")
    save(draw_cactus(0, 3), decor / "cactus_3.png")
    save(trim(keep_main_components(load(src / "pilier_pierre.png"), 0.4)), decor / "pillar.png")
    save(trim(keep_main_components(load(src / "misc/stone_monument.png"), 0.4)), decor / "monument.png")
    save(trim(keep_main_components(load(src / "torche.png"), 0.3)), decor / "torch.png")
    save(trim(load(src / "misc/rocks_small.png")), decor / "debris_1.png")
    save(trim(load(src / "misc/rocks_tiny.png")), decor / "debris_2.png")


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
    # Archère : dérivée du Mage
    for i in range(1, 8):
        save(recolor_blue_to_green(load(src / f"characters/mage/mage_pose_0{i}.png")), dst / "characters" / f"archer_{i}.png")
    save(recolor_blue_to_green(trim(keep_main_components(load(src / PORTRAITS["mage"]), 0.5))), dst / "ui" / "portrait_archer.png")
    for name, rel in ICONS.items():
        save(trim(keep_main_components(load(src / rel), 0.5)), dst / "ui" / f"icon_{name}.png")

    build_biomes(src, dst)

    (dst / "sounds").mkdir(parents=True, exist_ok=True)
    for name in SOUNDS:
        subprocess.run(
            ["ffmpeg", "-y", "-loglevel", "error", "-i", str(src / "sounds" / f"{name}.ogg"),
             "-ar", "44100", "-ac", "2", "-c:a", "pcm_s16le", str(dst / "sounds" / f"{name}.wav")],
            check=True)
    print("assets générés dans", dst)


if __name__ == "__main__":
    main()
