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
3. Désert (tools/source/desert_sheet.png + desert_sky.gif) : planche plateau + éléments sur un damier peint
   (pas de vraie transparence).
   - le damier est retiré : le ciel animé (GIF) apparaît derrière les montagnes ;
   - la pointe avant du plateau, coupée par le cadre, est reconstruite en prolongeant chaque falaise le long
     de son bord, puis les falaises plongent dans une brume de poussière ;
   - les éléments sont détourés : rochers et cactus (obstacles), buissons, fleurs, cailloux (décor au sol).

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


# ---------------------------------------------------------------------------- désert

DESERT_CUT = 486                 # dernière ligne utile du plateau dans la planche (le damier commence dessous)
DESERT_PROPS_Y = 496             # début de la rangée d'éléments
DESERT_EDGE_SLOPE = 0.525        # pente des deux bords avant du plateau (symétriques)
DESERT_EDGE_POINT = (250, 335)   # un point du bord avant gauche
DESERT_TIP_X = 650.0             # abscisse de la pointe avant (rencontre des deux bords)
DESERT_PLATEAU = [(628, 36), (122, 268), (650, 545), (1163, 282)]  # coins : arrière, gauche, avant, droite
SKY_SCALE = 2                    # agrandissement des images du ciel

# éléments de la planche (cadre x0, y0, x1, y1 sous DESERT_PROPS_Y) -> nom du sprite
DESERT_PROPS = {
    "desert_rock_1": (14, 5, 159, 97), "desert_rock_2": (166, 11, 273, 88),
    "desert_rock_3": (12, 108, 139, 179), "desert_rock_4": (32, 263, 141, 338),
    "desert_saguaro_1": (1141, 14, 1184, 129), "desert_saguaro_2": (1198, 6, 1253, 151),
    "desert_barrel_1": (1165, 158, 1219, 231), "desert_barrel_2": (995, 227, 1045, 289),
    "desert_flowers_1": (627, 13, 714, 84), "desert_flowers_2": (730, 23, 832, 99),
    "desert_flowers_3": (842, 26, 917, 110), "desert_flowers_4": (656, 91, 733, 146),
    "desert_flowers_5": (762, 101, 875, 179), "desert_flowers_6": (175, 183, 265, 238),
    "desert_grass_1": (921, 32, 995, 105), "desert_grass_2": (905, 119, 979, 188),
    "desert_grass_3": (673, 155, 748, 213), "desert_agave_1": (487, 6, 601, 92),
    "desert_agave_2": (43, 190, 167, 251), "desert_pebbles_1": (157, 122, 245, 165),
    "desert_pebbles_2": (431, 131, 521, 169), "desert_pebbles_3": (735, 299, 823, 334),
}


def checker_background(rgb: np.ndarray) -> np.ndarray:
    """Pixels du damier peint : gris neutres (les blancs des fleurs, plus clairs, sont conservés)."""
    c = rgb.astype(int)
    return ((c.max(2) - c.min(2)) < 14) & (c.mean(2) < 200)


def desert_extend(top: np.ndarray, ext: int) -> np.ndarray:
    """
    Ajoute {ext} lignes sous la coupe. La pointe du plateau et le haut des falaises sont reconstruits en
    recopiant chaque falaise le long de son propre bord (fondus aux raccords). Plus bas, l'image ne contient
    plus rien d'utilisable : le bas des falaises est prolongé par recopie verticale, puis tout se fond dans
    une brume de poussière.
    """
    h0, w = top.shape[:2]
    src = top.astype(np.float32)
    sl, tip = DESERT_EDGE_SLOPE, DESERT_TIP_X
    period = 150.0                       # pas de recopie le long du bord
    rise = sl * period
    feather = 22.0
    base = h0 - 1 - feather              # les fondus ne lisent jamais sous la coupe
    overlap = 20                         # fondu au-dessus de la coupe : aucun raccord sur la dernière ligne
    yy, xx = np.mgrid[h0 - overlap:h0 + ext, 0:w].astype(np.float32)

    def sample(img, sx, sy):
        return cv2.remap(img, sx.astype(np.float32), sy.astype(np.float32), cv2.INTER_LINEAR,
                         borderMode=cv2.BORDER_REFLECT)

    def along_edge(direction):
        k = np.maximum(np.ceil((yy - base) / rise), 1)
        sy = yy - k * rise
        sx = xx - direction * k * period
        here = sample(src, sx, sy)
        before = sample(src, sx + direction * period, sy + rise)      # copie précédente (plus haut)
        after = sample(src, sx - direction * period, sy - rise)       # copie suivante (plus bas)
        w_before = (np.clip(1 - (sy - (base - rise)) / feather, 0, 1) * 0.5)[..., None]
        w_after = (np.clip(1 - (base - sy) / feather, 0, 1) * 0.5)[..., None]
        return here * (1 - w_before - w_after) + before * w_before + after * w_after

    left, right = along_edge(1), along_edge(-1)
    seam = np.clip((xx - (tip - 5)) / 10, 0, 1)[..., None]
    synth = left * (1 - seam) + right * seam
    ramp = np.clip((np.arange(overlap, dtype=np.float32) + 1) / (overlap + 1), 0, 1)[:, None, None]
    synth[:overlap] = src[h0 - overlap:] * (1 - ramp) + synth[:overlap] * ramp
    full = np.concatenate([src[:h0 - overlap], synth], 0)

    x = np.arange(w, dtype=np.float32)
    ex, ey = DESERT_EDGE_POINT
    edge = np.where(x < tip, ey + sl * (x - ex), ey + sl * ((2 * tip - ex) - x))
    # bas des falaises : recopie verticale (période {repeat}) à partir de la profondeur d0 ; le départ est
    # fondu sur {blend} lignes, si bien que chaque copie prolonge la précédente sans couture
    repeat, blend = 64, 24
    d0 = np.maximum(132.0, h0 - blend - edge)     # là où la coupe tombe déjà en pleine falaise : avant la coupe
    yc = np.arange(h0 + ext, dtype=np.float32)[:, None]
    depth = yc - edge[None]
    for y in range(h0 - blend, h0 + ext):
        w_ = np.clip((depth[y] - d0) / blend, 0, 1)
        if w_.any():
            full[y] = full[y] * (1 - w_[:, None]) + full[y - repeat] * w_[:, None]
    # brume de poussière : le plateau domine le désert ; hors des falaises (côtés), le sol s'y fond vite
    t = np.clip((depth - 150) / 150, 0, 1)
    outer = np.clip(np.maximum(150 - x, x - 1150) / 40, 0, 1)[None]
    t = np.maximum(t, outer * np.clip((yc - h0 + 2) / 18, 0, 1))
    t = np.where(yc < h0, 0, t) ** 1.1
    haze = cv2.GaussianBlur(full, (0, 0), 18) * 0.35 + np.array([206, 128, 78], np.float32) * 0.65
    return (full * (1 - t[..., None]) + haze * t[..., None]).clip(0, 255).astype(np.uint8)


def desert_pad(rgb: np.ndarray, alpha: np.ndarray, pad: int):
    """
    Marge autour de l'illustration pour la caméra : sur les côtés et en bas, brume de poussière ; au-dessus
    de l'horizon, transparent (le ciel apparaît). Les bords de l'illustration s'y fondent progressivement.
    """
    h, w = alpha.shape
    big = cv2.copyMakeBorder(rgb, pad, pad, pad, pad, cv2.BORDER_REFLECT).astype(np.float32)
    yy, xx = np.mgrid[0:h + 2 * pad, 0:w + 2 * pad].astype(np.float32)
    dx = np.maximum(pad - xx, xx - (w + pad - 1))                 # > 0 hors de l'image, < 0 dedans
    dy = yy - (h + pad - 1)
    d = np.maximum(dx, dy)
    t = (np.clip((d + 110) / 230, 0, 1) ** 1.3)[..., None]
    haze = cv2.GaussianBlur(big, (0, 0), 24) * 0.3 + np.array([206, 128, 78], np.float32) * 0.7
    big = big * (1 - t) + haze * t
    a = np.zeros(big.shape[:2], np.float32)
    a[pad:pad + h, pad:pad + w] = alpha
    horizon = np.clip((yy - pad - 110) / 70, 0, 1)               # sol opaque sous l'horizon
    side = np.clip((dx + 110) / 110, 0, 1)                        # bords gauche et droit
    a = a * (1 - side) + horizon * side
    a[yy > h + pad - 1] = 1
    a[yy < pad] = 0
    return big.clip(0, 255).astype(np.uint8), a


def desert_fit_grid():
    """Plus grand losange aux proportions de la grille dans le plateau (avec une marge de sable)."""
    h, w = 700, 1300
    poly = Image.new("L", (w, h), 0)
    ImageDraw.Draw(poly).polygon(DESERT_PLATEAU, fill=1)
    mask = np.array(poly).astype(bool)
    mask = ndimage.binary_erosion(mask, iterations=14)
    return fit_grid(mask)


def build_desert(dst: Path):
    sheet = np.array(Image.open(SOURCE / "desert_sheet.png").convert("RGB"))
    top = sheet[:DESERT_CUT]
    # ciel : damier relié au bord supérieur
    lab, _ = ndimage.label(checker_background(top))
    sky = np.isin(lab, list(set(np.unique(lab[0])) - {0}))
    sky = ndimage.binary_dilation(sky, iterations=1)                        # liseré gris des montagnes
    alpha = cv2.GaussianBlur((~sky).astype(np.float32), (0, 0), 0.8)
    rgb = top.copy()
    rgb[sky] = (196, 112, 86)                                               # sous le ciel : teinte des montagnes
    ext = 320
    rgb = desert_extend(rgb, ext)
    alpha = np.concatenate([alpha, np.ones((ext, alpha.shape[1]), np.float32)], 0)
    alpha[:24] *= np.linspace(0, 1, 24)[:, None]                            # sommet coupé : fondu dans le ciel

    pad = 220
    big, a_big = desert_pad(rgb, alpha, pad)
    cx, cy, hw = desert_fit_grid()
    hw *= 0.97
    out = dst / "backdrops"
    out.mkdir(parents=True, exist_ok=True)
    Image.fromarray(big).save(out / "desert.jpg", quality=90, optimize=True)
    Image.fromarray((a_big * 255).clip(0, 255).astype(np.uint8)).save(out / "desert.alpha.png", optimize=True)
    scale = hw / GRID_HALF_W

    # ciel animé : images agrandies et adoucies, empilées verticalement dans un seul JPEG
    gif = Image.open(SOURCE / "desert_sky.gif")
    frames = []
    for i in range(gif.n_frames):
        gif.seek(i)
        f = gif.convert("RGB").resize((gif.width * SKY_SCALE, gif.height * SKY_SCALE), Image.LANCZOS)
        frames.append(np.array(f.filter(ImageFilter.GaussianBlur(1.2))))
    Image.fromarray(np.concatenate(frames, 0)).save(out / "desert_sky.jpg", quality=88, optimize=True)
    (out / "desert.properties").write_text(
        "# Placement de la grille sur desert.jpg (généré par tools/import_sources.py)\n"
        "# centerX/centerY : pixel de l'image sous le centre de la grille ; scale : pixels par unité monde\n"
        f"centerX = {cx + pad:.1f}\ncenterY = {cy + pad:.1f}\nscale = {scale:.4f}\n"
        "# ciel animé derrière l'illustration : images empilées dans desert_sky.jpg\n"
        f"sky = desert_sky\nskyFrames = {len(frames)}\n"
        f"# ligne de l'illustration où se pose le bas du ciel (horizon derrière les montagnes)\nskyBottom = {pad + 230}\n",
        encoding="utf-8")
    print(f"désert : grille centrée en ({cx}, {cy}), demi-largeur {hw:.0f} px, échelle {scale:.3f}")

    # éléments
    props = sheet[DESERT_PROPS_Y:]
    bg = checker_background(props)
    fg = ndimage.binary_closing(ndimage.binary_opening(~bg, iterations=1), iterations=4)
    lab, _ = ndimage.label(fg)
    decor = dst / "decor"
    for name, (x0, y0, x1, y1) in DESERT_PROPS.items():
        sub = lab[y0:y1 + 1, x0:x1 + 1]
        ids, counts = np.unique(sub[sub > 0], return_counts=True)
        m = np.zeros(fg.shape, bool)
        m[y0:y1 + 1, x0:x1 + 1] = sub == ids[np.argmax(counts)]
        m &= ~bg
        parts, n = ndimage.label(m)
        if n > 1:                                                           # fragments isolés
            sizes = ndimage.sum(m, parts, range(1, n + 1))
            m = np.isin(parts, [i + 1 for i in range(n) if sizes[i] >= 0.08 * sizes.max()])
        ys, xs = np.nonzero(m)
        by0, by1, bx0, bx1 = ys.min(), ys.max() + 1, xs.min(), xs.max() + 1
        a8 = (m[by0:by1, bx0:bx1] * 255).astype(np.uint8)
        a8 = cv2.GaussianBlur(np.maximum(cv2.erode(a8, np.ones((3, 3), np.uint8)), (a8 * 0.35).astype(np.uint8)),
                              (3, 3), 0.7)
        img = np.dstack([props[by0:by1, bx0:bx1], a8])
        Image.fromarray(img).save(decor / f"{name}.png", optimize=True)
    print(f"désert : {len(DESERT_PROPS)} éléments détourés")


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--dst", required=True, type=Path)
    args = ap.parse_args()
    build_archer(args.dst)
    build_forest(args.dst)
    build_desert(args.dst)


if __name__ == "__main__":
    main()
