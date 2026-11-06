#!/usr/bin/env python3
"""
Import des illustrations fournies dans tools/source/ vers src/main/resources/.

1. Archère (tools/source/archer_sheet.png) : planche de 40 poses sur fond gris.
   - détourage (fond gris retiré, y compris entre l'arc et sa corde) ;
   - choix des poses : repos + cycle de marche vers le sud-est et vers le nord-est
     (les deux autres directions sont obtenues en miroir dans le jeu) ;
   - toutes les poses sont posées sur un même canevas, pieds alignés en bas au centre ;
   - portrait de l'interface recadré sur la tête.
2. Fond de la forêt (tools/source/forest_backdrop.png) : illustration d'un plateau en forêt.
   - les rochers peints sur le plateau sont effacés (les obstacles du jeu sont générés à part) ;
   - les bords sont prolongés (flou) pour que la caméra ne sorte jamais de l'image ;
   - forest.properties indique où placer la grille de jeu sur l'image.

Usage : python3 tools/import_sources.py --dst src/main/resources
Dépendances : Pillow, numpy, scipy, opencv-python.
"""
import argparse
from pathlib import Path

import cv2
import numpy as np
from PIL import Image, ImageDraw, ImageFilter
from scipy import ndimage

SOURCE = Path(__file__).resolve().parent / "source"

# indices des vignettes dans la planche (lecture ligne par ligne, 20 par ligne)
ARCHER_POSES = {
    "se_idle": 0,
    "se_walk_1": 1,
    "se_walk_2": 2,
    "se_walk_3": 3,
    "ne_idle": 8,
    "ne_walk_1": 7,
    "ne_walk_2": 12,
    "ne_walk_3": 28,
}
FEET_MARGIN = 0.06          # marge sous les pieds (le jeu ancre les sprites à 94 % de leur hauteur)

# grille du jeu : losange de 1050 × 630 unités monde, centré en (0, 294)
GRID_HALF_W = 525
GRID_HALF_H = 315


# ---------------------------------------------------------------------------- archère

def segment_sheet(sheet: np.ndarray):
    """Renvoie (masque premier plan, liste des boîtes des vignettes triées ligne par ligne)."""
    rgb = sheet.astype(int)
    mx, mn = rgb.max(2), rgb.min(2)
    greyish = ((mx - mn) < 16) & (mx > 100)
    lab, _ = ndimage.label(greyish)
    border = set(np.unique(np.concatenate([lab[0], lab[-1], lab[:, 0], lab[:, -1]]))) - {0}
    fg = ~np.isin(lab, list(border))
    fgl, _ = ndimage.label(ndimage.binary_closing(fg, iterations=3))
    boxes = []
    for i, sl in enumerate(ndimage.find_objects(fgl)):
        h = sl[0].stop - sl[0].start
        if h > 90 and (fgl[sl] == i + 1).sum() > 2500:
            boxes.append((sl[0].start, sl[1].start, sl[0].stop, sl[1].stop))
    boxes.sort(key=lambda b: (0 if b[0] < sheet.shape[0] / 2 else 1, b[1]))
    return fg, boxes


def cut_pose(sheet: np.ndarray, fg: np.ndarray, box) -> Image.Image:
    y0, x0, y1, x1 = box
    rgb = sheet[y0:y1, x0:x1].astype(int)
    mask = fg[y0:y1, x0:x1].copy()
    # gris de fond enfermé (entre l'arc et la corde) : grandes zones grises claires
    grey = ((rgb.max(2) - rgb.min(2)) < 18) & (rgb.max(2) > 120) & mask
    glab, gn = ndimage.label(grey)
    for k in range(1, gn + 1):
        if (glab == k).sum() > 40:
            mask &= glab != k
    # ne garder que le personnage (et ses parties détachées non négligeables)
    lab, n = ndimage.label(mask)
    if n > 1:
        areas = ndimage.sum(mask, lab, range(1, n + 1))
        mask = np.isin(lab, [k + 1 for k, a in enumerate(areas) if a >= max(areas) * 0.02])
    # liseré gris résiduel sur les bords : on érode d'un pixel et on adoucit
    alpha = ndimage.binary_erosion(mask, iterations=1).astype(np.float32)
    alpha = ndimage.gaussian_filter(alpha, 0.6)
    rgba = np.dstack([rgb.astype(np.uint8), (np.clip(alpha, 0, 1) * 255).astype(np.uint8)])
    return Image.fromarray(rgba, "RGBA")


def feet_center(img: Image.Image) -> float:
    """Abscisse du milieu des pieds (bas de la silhouette), qui sert d'ancre."""
    a = np.array(img.getchannel("A")) > 128
    rows = np.nonzero(a.any(1))[0]
    bottom = rows.max()
    band = a[max(0, bottom - int(len(rows) * 0.12)): bottom + 1]
    xs = np.nonzero(band.any(0))[0]
    return (xs.min() + xs.max()) / 2.0


def build_archer(dst: Path):
    sheet = np.array(Image.open(SOURCE / "archer_sheet.png").convert("RGB"))
    fg, boxes = segment_sheet(sheet)
    if len(boxes) != 40:
        raise SystemExit(f"planche inattendue : {len(boxes)} vignettes trouvées au lieu de 40")
    poses = {}
    for name, index in ARCHER_POSES.items():
        img = cut_pose(sheet, fg, boxes[index])
        bbox = img.getchannel("A").getbbox()
        poses[name] = img.crop(bbox)

    # canevas commun : pieds au centre horizontal, en bas (avec la marge attendue par le jeu)
    half = max(max(feet_center(p), p.width - feet_center(p)) for p in poses.values())
    height = max(p.height for p in poses.values())
    canvas_w = int(np.ceil(half * 2)) + 2
    canvas_h = int(np.ceil(height / (1 - FEET_MARGIN)))
    out = dst / "characters"
    out.mkdir(parents=True, exist_ok=True)
    for name, p in poses.items():
        c = Image.new("RGBA", (canvas_w, canvas_h), (0, 0, 0, 0))
        x = int(round(canvas_w / 2 - feet_center(p)))
        y = int(round(canvas_h * (1 - FEET_MARGIN))) - p.height
        c.paste(p, (x, y), p)
        c.save(out / f"archer_{name}.png", optimize=True)
    for old in out.glob("archer_[0-9].png"):
        old.unlink()  # anciennes poses recolorées du Mage
    build_archer_portrait(poses["se_idle"], dst / "ui" / "portrait_archer.png")
    print(f"archère : {len(poses)} poses ({canvas_w}×{canvas_h})")


def build_archer_portrait(pose: Image.Image, path: Path):
    """Portrait dans le style des autres (cadre octogonal bronze, fond coloré, tête en gros plan)."""
    W, H = 76, 83
    S = 4
    big = Image.new("RGBA", (W * S, H * S), (0, 0, 0, 0))
    d = ImageDraw.Draw(big)
    c = 12 * S

    def octagon(inset):
        x0, y0, x1, y1 = inset, inset, W * S - inset, H * S - inset
        k = c - inset * 0.4
        return [(x0 + k, y0), (x1 - k, y0), (x1, y0 + k), (x1, y1 - k), (x1 - k, y1), (x0 + k, y1), (x0, y1 - k), (x0, y0 + k)]

    d.polygon(octagon(0), fill=(150, 104, 58, 255))
    d.polygon(octagon(2 * S), fill=(60, 40, 22, 255))
    inner = octagon(3 * S)
    bg = Image.new("RGBA", big.size)
    grad = np.linspace(0, 1, big.size[1])[:, None]
    top, bottom = np.array([70, 112, 58]), np.array([22, 44, 24])
    col = (top * (1 - grad) + bottom * grad).astype(np.uint8)
    bg_arr = np.dstack([np.repeat(col[:, None, :], big.size[0], axis=1).reshape(big.size[1], big.size[0], 3),
                        np.full((big.size[1], big.size[0]), 255, np.uint8)])
    bg = Image.fromarray(bg_arr, "RGBA")
    mask = Image.new("L", big.size, 0)
    ImageDraw.Draw(mask).polygon(inner, fill=255)
    big.paste(bg, (0, 0), mask)
    # tête et épaules, centrées sur le visage
    a = np.array(pose.getchannel("A")) > 128
    top = int(np.nonzero(a.any(1))[0].min())
    head_rows = a[top: top + int(pose.height * 0.22)]
    hx = np.nonzero(head_rows.any(0))[0]
    head_cx = (hx.min() + hx.max()) / 2
    crop_h = int(pose.height * 0.46)
    crop_w = crop_h * W / H
    box = (int(head_cx - crop_w / 2), top - 4, int(head_cx + crop_w / 2), top - 4 + crop_h)
    head = pose.crop(box).resize((W * S, H * S), Image.LANCZOS)
    layer = Image.new("RGBA", big.size, (0, 0, 0, 0))
    layer.paste(head, (0, 3 * S), head)
    big.paste(layer, (0, 0), Image.fromarray((np.array(layer.getchannel("A")) * (np.array(mask) / 255)).astype(np.uint8)))
    big.resize((W, H), Image.LANCZOS).save(path, optimize=True)


# ---------------------------------------------------------------------------- fond de forêt

def plateau_mask(img: np.ndarray) -> np.ndarray:
    hsv = cv2.cvtColor(img, cv2.COLOR_RGB2HSV).astype(float)
    h, s, v = hsv[:, :, 0] * 2, hsv[:, :, 1] / 255, hsv[:, :, 2] / 255
    grass = (h > 48) & (h < 95) & (s > 0.35) & (v > 0.55)
    grass = ndimage.binary_opening(ndimage.binary_closing(grass, iterations=6), iterations=3)
    lab, n = ndimage.label(grass)
    areas = ndimage.sum(grass, lab, range(1, n + 1))
    return ndimage.binary_fill_holes(lab == int(np.argmax(areas)) + 1)


def remove_painted_rocks(img: np.ndarray, plateau: np.ndarray, grid) -> np.ndarray:
    """
    Efface les rochers peints dans l'aire de jeu (zones grises peu saturées) : chaque rocher est recouvert
    par un morceau d'herbe voisin, fondu dans l'image par clonage de Poisson (garde la texture peinte).
    Les rochers hors de la grille restent : ils font partie du décor.
    """
    hsv = cv2.cvtColor(img, cv2.COLOR_RGB2HSV).astype(float)
    s, v = hsv[:, :, 1] / 255, hsv[:, :, 2] / 255
    cx, cy, hw = grid
    hh = hw * GRID_HALF_H / GRID_HALF_W
    yy, xx = np.mgrid[0:img.shape[0], 0:img.shape[1]]
    in_grid = (np.abs(xx - cx) / (hw * 1.06) + np.abs(yy - cy) / (hh * 1.06)) <= 1
    rock = (s < 0.30) & (v > 0.2) & (v < 0.95) & plateau
    rock = ndimage.binary_opening(rock, iterations=1)
    lab, n = ndimage.label(rock)
    out = img.copy()
    forbidden = ndimage.binary_dilation(rock, iterations=14) | ~ndimage.binary_erosion(plateau, iterations=4)
    for k in range(1, n + 1):
        blob = lab == k
        if blob.sum() < 50:
            continue
        ys, xs = np.nonzero(blob)
        if not in_grid[int(ys.mean()), int(xs.mean())]:
            continue
        mask = ndimage.binary_dilation(blob, iterations=10)  # rocher + ombre + touffes autour
        my, mx = np.nonzero(mask)
        y0, y1, x0, x1 = my.min(), my.max() + 1, mx.min(), mx.max() + 1
        h, w = y1 - y0, x1 - x0
        patch_mask = (mask[y0:y1, x0:x1] * 255).astype(np.uint8)
        best = None
        for dy in range(-3 * h, 3 * h + 1, max(4, h // 3)):
            for dx in range(-3 * w, 3 * w + 1, max(4, w // 3)):
                if abs(dx) < w and abs(dy) < h:
                    continue
                sy0, sx0 = y0 + dy, x0 + dx
                if sy0 < 0 or sx0 < 0 or sy0 + h > img.shape[0] or sx0 + w > img.shape[1]:
                    continue
                if forbidden[sy0:sy0 + h, sx0:sx0 + w][mask[y0:y1, x0:x1]].any():
                    continue
                dist = abs(dx) + abs(dy) * 1.5  # même rangée = même lumière
                if best is None or dist < best[0]:
                    best = (dist, sy0, sx0)
        if best is None:
            out = cv2.inpaint(out, (mask * 255).astype(np.uint8), 9, cv2.INPAINT_TELEA)
            continue
        _, sy0, sx0 = best
        src = out[sy0:sy0 + h, sx0:sx0 + w].copy()
        center = (int(x0 + w / 2), int(y0 + h / 2))
        out = cv2.seamlessClone(src, out, patch_mask, center, cv2.NORMAL_CLONE)
    return out


def fit_grid(plateau: np.ndarray):
    """Plus grand losange aux proportions de la grille qui tient dans la partie visible du plateau."""
    h, w = plateau.shape
    ys, xs = np.nonzero(plateau)
    best = None
    ratio = GRID_HALF_H / GRID_HALF_W
    for cy in range(int(ys.min()) + 40, h - 40, 4):
        for cx in range(int(xs.min()) + 40, int(xs.max()) - 40, 6):
            lo, hi = 50.0, 900.0
            for _ in range(14):
                hw = (lo + hi) / 2
                if diamond_inside(plateau, cx, cy, hw, hw * ratio):
                    lo = hw
                else:
                    hi = hw
            if best is None or lo > best[2]:
                best = (cx, cy, lo)
    return best


def diamond_inside(mask, cx, cy, hw, hh):
    h, w = mask.shape
    for t in np.linspace(0, 1, 40):
        for px, py in ((cx - hw + t * hw, cy - t * hh), (cx + t * hw, cy - hh + t * hh),
                       (cx + hw - t * hw, cy + t * hh), (cx - t * hw, cy + hh - t * hh)):
            ix, iy = int(round(px)), int(round(py))
            if ix < 0 or iy < 0 or ix >= w or iy >= h or not mask[iy, ix]:
                return False
    return True


def extend_borders(img: np.ndarray, pad: int) -> np.ndarray:
    """Agrandit l'image de {pad} pixels de chaque côté : reflet flouté et assombri des bords."""
    big = cv2.copyMakeBorder(img, pad, pad, pad, pad, cv2.BORDER_REFLECT)
    blurred = cv2.GaussianBlur(big, (0, 0), 18)
    h, w = big.shape[:2]
    yy, xx = np.mgrid[0:h, 0:w]
    dist = np.maximum(np.maximum(pad - xx, xx - (w - pad - 1)), np.maximum(pad - yy, yy - (h - pad - 1)))
    t = np.clip(dist / pad, 0, 1)[:, :, None]
    dark = (blurred * (1 - 0.45 * t)).astype(np.uint8)
    blend = np.clip(dist / 24.0, 0, 1)[:, :, None]  # transition douce sur 24 px
    return (big * (1 - blend) + dark * blend).astype(np.uint8)


def build_forest(dst: Path):
    img = np.array(Image.open(SOURCE / "forest_backdrop.png").convert("RGB"))
    plateau = plateau_mask(img)
    cx, cy, hw = fit_grid(plateau)
    hw *= 0.94  # un peu d'herbe autour de la grille
    cleaned = remove_painted_rocks(img, plateau, (cx, cy, hw))
    pad = 220
    extended = extend_borders(cleaned, pad)
    out = dst / "backdrops"
    out.mkdir(parents=True, exist_ok=True)
    Image.fromarray(extended).save(out / "forest.jpg", quality=90, optimize=True)
    scale = hw / GRID_HALF_W  # pixels d'image par unité monde
    (out / "forest.properties").write_text(
        "# Placement de la grille sur forest.jpg (généré par tools/import_sources.py)\n"
        "# centerX/centerY : pixel de l'image sous le centre de la grille ; scale : pixels par unité monde\n"
        f"centerX = {cx + pad:.1f}\ncenterY = {cy + pad:.1f}\nscale = {scale:.4f}\n", encoding="utf-8")
    print(f"forêt : grille centrée en ({cx}, {cy}), demi-largeur {hw:.0f} px, échelle {scale:.3f}")


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--dst", required=True, type=Path)
    args = ap.parse_args()
    build_archer(args.dst)
    build_forest(args.dst)


if __name__ == "__main__":
    main()
