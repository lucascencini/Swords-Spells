#!/usr/bin/env python3
"""
Import de l'interface fournie :
- portraits haute définition (tools/source/portraits_hd.jpg, 4 illustrations encadrées) :
  ui/art_<classe>.png (illustration entière, écran de préparation) et ui/portrait_<classe>.png (visage
  recadré dans le cadre octogonal des fiches et de la frise) ;
- boutons de pierre (tools/source/menu_buttons.png, damier peint) : ui/stone_*.png.

Usage : python3 tools/import_ui.py --dst src/main/resources
"""
import argparse
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw
from scipy import ndimage

SOURCE = Path(__file__).resolve().parent / "source"

# illustration : cadre (x0, y0, x1, y1) ; visage : centre (x, y) et hauteur du recadrage, dans la planche
ART = {
    "mage": ((14, 14, 500, 500), (262, 172, 230)),
    "warrior": ((524, 14, 1010, 500), (738, 165, 240)),
    "druid": ((14, 524, 500, 1010), (236, 650, 250)),
    "archer": ((524, 524, 1010, 1010), (762, 660, 200)),
}
PORTRAIT = (76, 83)


def octagon(w, h, inset, c):
    x0, y0, x1, y1 = inset, inset, w - inset, h - inset
    k = c - inset * 0.4
    return [(x0 + k, y0), (x1 - k, y0), (x1, y0 + k), (x1, y1 - k), (x1 - k, y1), (x0 + k, y1), (x0, y1 - k), (x0, y0 + k)]


def build_portraits(dst: Path):
    sheet = Image.open(SOURCE / "portraits_hd.jpg").convert("RGB")
    ui = dst / "ui"
    for cls, (frame, (fx, fy, fh)) in ART.items():
        art = sheet.crop(frame)
        # coins hors du cadre octogonal : transparents
        mask = Image.new("L", art.size, 0)
        ImageDraw.Draw(mask).polygon(octagon(art.width, art.height, 0, art.width * 0.11), fill=255)
        art = art.convert("RGBA")
        art.putalpha(mask)
        art.resize((240, 240), Image.LANCZOS).save(ui / f"art_{cls}.png", optimize=True)
        # visage dans le cadre bronze des fiches, dessiné à 4× puis réduit
        W, H = PORTRAIT
        S = 4
        fw = fh * W / H
        face = sheet.crop((int(fx - fw / 2), int(fy - fh / 2), int(fx + fw / 2), int(fy + fh / 2)))
        big = Image.new("RGBA", (W * S, H * S), (0, 0, 0, 0))
        d = ImageDraw.Draw(big)
        c = 12 * S
        d.polygon(octagon(W * S, H * S, 0, c), fill=(150, 104, 58, 255))
        d.polygon(octagon(W * S, H * S, 2 * S, c), fill=(60, 40, 22, 255))
        inner = Image.new("L", big.size, 0)
        ImageDraw.Draw(inner).polygon(octagon(W * S, H * S, 3 * S, c), fill=255)
        big.paste(face.resize(big.size, Image.LANCZOS), (0, 0), inner)
        big.resize((W, H), Image.LANCZOS).save(ui / f"portrait_{cls}.png", optimize=True)
    print("portraits HD :", ", ".join(ART))


# boutons et décorations de la planche (cadre x0, y0, x1, y1) ; « enclosed » : retirer aussi le damier
# enfermé (entre les feuilles), pas pour la pierre qui a la même teinte grise
BUTTONS = {
    "stone_button_1": ((408, 62, 752, 152), False), "stone_button_2": ((408, 155, 752, 240), False),
    "stone_button_3": ((405, 248, 760, 338), False), "stone_round_back": ((470, 393, 568, 497), False),
    "stone_round_left": ((428, 530, 500, 600), False), "stone_round_close": ((544, 530, 616, 600), False),
    "stone_arrow_left": ((722, 540, 820, 590), False), "stone_arrow_right": ((846, 540, 940, 590), False),
    "ivy_corner": ((865, 0, 1024, 330), True), "flower_mound": ((300, 640, 790, 800), True),
}


def cut_button(sheet: np.ndarray, box, enclosed: bool) -> Image.Image:
    x0, y0, x1, y1 = box
    c = sheet[y0:y1, x0:x1]
    neutral = (c.max(2) - c.min(2)) < 8
    v = c.mean(2)
    tones = neutral & ((np.abs(v - 94) < 12) | (np.abs(v - 148) < 12))          # les deux cases du damier
    bg = tones | ((v > 100) & (v < 142) & neutral & ndimage.binary_dilation(tones, iterations=1))
    lab, _ = ndimage.label(bg)
    border = set(np.unique(np.concatenate([lab[0], lab[-1], lab[:, 0], lab[:, -1]]))) - {0}
    fg = ~np.isin(lab, list(border))
    if enclosed:
        sizes = ndimage.sum(bg, lab, range(1, lab.max() + 1))
        fg &= ~np.isin(lab, [i + 1 for i in range(len(sizes)) if sizes[i] >= 25])
    fg = ndimage.binary_opening(fg, iterations=1)
    parts, n = ndimage.label(fg)
    if n > 1:
        sizes = ndimage.sum(fg, parts, range(1, n + 1))
        fg = np.isin(parts, [i + 1 for i in range(n) if sizes[i] >= 0.02 * sizes.max()])
    alpha = ndimage.gaussian_filter(fg.astype(np.float32), 0.6)
    img = Image.fromarray(np.dstack([c.astype(np.uint8), (alpha * 255).astype(np.uint8)]), "RGBA")
    return img.crop(img.getchannel("A").getbbox())


def build_buttons(dst: Path):
    sheet = np.array(Image.open(SOURCE / "menu_buttons.png").convert("RGB")).astype(int)
    for name, (box, enclosed) in BUTTONS.items():
        img = cut_button(sheet, box, enclosed)
        if name == "ivy_corner":
            # coupé par le cadre à gauche et en bas : on fond ces bords pour ne jamais voir de ligne droite
            a = np.array(img).astype(np.float32)
            h, w = a.shape[:2]
            ramp_x = np.clip(np.arange(w) / (w * 0.45), 0, 1) ** 1.4
            ramp_y = np.clip((h - 1 - np.arange(h)) / (h * 0.35), 0, 1) ** 1.4
            a[..., 3] *= ramp_y[:, None] * ramp_x[None, :]
            img = Image.fromarray(a.astype(np.uint8), "RGBA")
        img.save(dst / "ui" / f"{name}.png", optimize=True)
    print("boutons :", len(BUTTONS), "images")


def build_all(dst: Path):
    build_portraits(dst)
    build_buttons(dst)


if __name__ == "__main__":
    ap = argparse.ArgumentParser()
    ap.add_argument("--dst", required=True, type=Path)
    build_all(ap.parse_args().dst)
