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
GRID_N = 15

# coins du plateau peint de la forêt (fond, droite, avant, gauche), à l'intersection des bords visibles ;
# la pointe avant est hors du cadre
FOREST_PLATEAU = [(765, 69), (1465, 384), (839, 713), (100, 386)]


def perspective_grid(plateau, margin_back, margin_front, max_bottom=None):
    """
    Cale la grille de jeu sur le plateau peint, en perspective : les coins du plateau (fond, droite, avant,
    gauche) correspondent aux coins de la grille agrandie d'une marge (en cases) ; la marge avant augmente
    si besoin pour que la grille reste au-dessus de {max_bottom}. Renvoie les coins extérieurs de la grille
    (fond, droite, avant, gauche) en pixels de l'image.
    """
    n = GRID_N
    while True:
        lo, hi = -0.5 - margin_back, n - 0.5 + margin_front
        src = np.float32([[lo, lo], [hi, lo], [hi, hi], [lo, hi]])
        h = cv2.getPerspectiveTransform(src, np.float32(plateau))
        g = np.float32([[[-0.5, -0.5]], [[n - 0.5, -0.5]], [[n - 0.5, n - 0.5]], [[-0.5, n - 0.5]]])
        corners = cv2.perspectiveTransform(g, h)[:, 0, :]
        if max_bottom is None or corners[2][1] <= max_bottom:
            return corners, h
        margin_front += 0.05


def grid_properties(corners, h, pad, name):
    """Lignes du fichier .properties : centre, échelle et coins de la grille (image agrandie de {pad})."""
    center = cv2.perspectiveTransform(np.float32([[[(GRID_N - 1) / 2, (GRID_N - 1) / 2]]]), h)[0, 0]
    scale = (corners[1][0] - corners[3][0]) / (2 * GRID_HALF_W)  # pixels d'image par unité monde
    names = ["top", "right", "bottom", "left"]
    lines = [f"# Placement de la grille sur {name} (généré par tools/import_sources.py)",
             "# centerX/centerY : pixel sous le centre de la grille ; scale : pixels par unité monde (en moyenne)",
             f"centerX = {center[0] + pad:.1f}", f"centerY = {center[1] + pad:.1f}", f"scale = {scale:.4f}",
             "# perspective : coins extérieurs de la grille (fond, droite, avant, gauche), en pixels"]
    lines += [f"grid.{k} = {c[0] + pad:.1f}, {c[1] + pad:.1f}" for k, c in zip(names, corners)]
    return "\n".join(lines) + "\n", scale


def grid_mask(corners, shape, grow=1.0):
    """Masque de l'aire de jeu (quadrilatère de la grille), éventuellement agrandi autour de son centre."""
    c = np.float32(corners)
    c = c.mean(0) + (c - c.mean(0)) * grow
    mask = Image.new("L", (shape[1], shape[0]), 0)
    ImageDraw.Draw(mask).polygon([tuple(p) for p in c], fill=1)
    return np.array(mask).astype(bool)


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


def cut_pose(sheet: np.ndarray, fg: np.ndarray, box, enclosed_grey: bool = True) -> Image.Image:
    y0, x0, y1, x1 = box
    rgb = sheet[y0:y1, x0:x1].astype(int)
    mask = fg[y0:y1, x0:x1].copy()
    # gris de fond enfermé (entre l'arc et la corde) : grandes zones grises claires. Pas pour les
    # personnages en armure, dont le casque est gris.
    grey = ((rgb.max(2) - rgb.min(2)) < 18) & (rgb.max(2) > 120) & mask
    glab, gn = ndimage.label(grey)
    for k in range(1, gn + 1 if enclosed_grey else 1):
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


# ---------------------------------------------------------------------------- guerrier et mage

# planche commune (tools/source/warrior_mage_sheet.png) : vignettes repérées par leur cadre (x0, y0, x1, y1).
# Seules les poses cohérentes sont retenues (le mage tient son bâton ; sud-est de face, nord-est de dos) ;
# sud-ouest et nord-ouest sont obtenus en miroir dans le jeu.
SHEET_POSES = {
    "warrior": {
        "se_idle": (34, 62, 86, 151), "se_walk_1": (196, 61, 246, 151),
        "se_walk_2": (278, 62, 330, 151), "se_walk_3": (367, 62, 424, 151),
        "ne_idle": (1743, 157, 1795, 247), "ne_walk_1": (1579, 156, 1636, 247),
        "ne_walk_2": (1824, 157, 1876, 247), "ne_walk_3": (1990, 156, 2051, 247),
    },
    "mage": {
        "se_idle": (719, 391, 780, 500), "se_walk_1": (107, 392, 182, 500),
        "se_walk_2": (964, 390, 1032, 500), "se_walk_3": (876, 393, 951, 500),
        "ne_idle": (1579, 389, 1640, 500), "ne_walk_1": (1221, 388, 1287, 500),
        "ne_walk_2": (1308, 390, 1367, 500), "ne_walk_3": (1052, 386, 1127, 500),
    },
}
DRUID_POSES = {
    "se_idle": (24, 390, 98, 501), "se_walk_1": (109, 390, 182, 498),
    "se_walk_2": (189, 389, 265, 500), "se_walk_3": (881, 390, 952, 499),
    "ne_idle": (1584, 388, 1648, 500), "ne_walk_1": (1059, 384, 1136, 498),
    "ne_walk_2": (1228, 388, 1294, 499), "ne_walk_3": (1306, 390, 1370, 500),
}
ARMOURED = {"warrior", "mage"}     # gris dans le dessin (casque, manches) : pas de retrait des gris enfermés
PORTRAIT_SKIP = {"druid": 0.08}    # haut de la silhouette à sauter pour le portrait (bois du Druide)
SHEET_UPSCALE = 2            # la planche est petite : agrandie une fois ici plutôt qu'à chaque image
PORTRAIT_COLORS = {"archer": ((70, 112, 58), (22, 44, 24)), "warrior": ((128, 58, 44), (46, 18, 14)),
                   "mage": ((58, 84, 150), (18, 26, 58)), "druid": ((112, 96, 52), (40, 34, 16))}


def place_poses(poses: dict, out: Path, prefix: str):
    """Pose toutes les images sur un même canevas, pieds alignés en bas au centre ; renvoie sa taille."""
    half = max(max(feet_center(p), p.width - feet_center(p)) for p in poses.values())
    height = max(p.height for p in poses.values())
    canvas_w = int(np.ceil(half * 2)) + 2
    canvas_h = int(np.ceil(height / (1 - FEET_MARGIN)))
    out.mkdir(parents=True, exist_ok=True)
    for name, p in poses.items():
        c = Image.new("RGBA", (canvas_w, canvas_h), (0, 0, 0, 0))
        x = int(round(canvas_w / 2 - feet_center(p)))
        y = int(round(canvas_h * (1 - FEET_MARGIN))) - p.height
        c.paste(p, (x, y), p)
        c.save(out / f"{prefix}_{name}.png", optimize=True)
    return canvas_w, canvas_h


def build_sheet_classes(dst: Path):
    build_sheet(dst, "warrior_mage_sheet.png", SHEET_POSES)
    build_sheet(dst, "druid_sheet.jpg", {"druid": DRUID_POSES})


def build_sheet(dst: Path, source: str, poses_by_class: dict):
    sheet = np.array(Image.open(SOURCE / source).convert("RGB"))
    rgb = sheet.astype(int)
    greyish = ((rgb.max(2) - rgb.min(2)) < 16) & (rgb.max(2) > 100)       # fond et ombres au sol
    lab, _ = ndimage.label(greyish)
    border = set(np.unique(np.concatenate([lab[0], lab[-1], lab[:, 0], lab[:, -1]]))) - {0}
    fg = ~np.isin(lab, list(border))
    for cls, table in poses_by_class.items():
        poses = {}
        for name, (x0, y0, x1, y1) in table.items():
            img = cut_pose(sheet, fg, (y0, x0, y1, x1), enclosed_grey=cls not in ARMOURED)
            img = img.crop(img.getchannel("A").getbbox())
            poses[name] = img.resize((img.width * SHEET_UPSCALE, img.height * SHEET_UPSCALE), Image.LANCZOS)
        w, h = place_poses(poses, dst / "characters", cls)
        figure = max(p.height for p in poses.values())
        for old in (dst / "characters").glob(f"{cls}_[0-9].png"):
            old.unlink()  # anciennes poses fixes du pack
        build_archer_portrait(poses["se_idle"], dst / "ui" / f"portrait_{cls}.png", PORTRAIT_COLORS[cls],
                              PORTRAIT_SKIP.get(cls, 0))
        print(f"{cls} : {len(poses)} poses ({w}×{h}), échelle conseillée {69 / figure:.3f}")


def build_archer_portrait(pose: Image.Image, path: Path, colors=None, skip=0.0):
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
    top, bottom = (np.array(c) for c in (colors or PORTRAIT_COLORS["archer"]))
    col = (top * (1 - grad) + bottom * grad).astype(np.uint8)
    bg_arr = np.dstack([np.repeat(col[:, None, :], big.size[0], axis=1).reshape(big.size[1], big.size[0], 3),
                        np.full((big.size[1], big.size[0]), 255, np.uint8)])
    bg = Image.fromarray(bg_arr, "RGBA")
    mask = Image.new("L", big.size, 0)
    ImageDraw.Draw(mask).polygon(inner, fill=255)
    big.paste(bg, (0, 0), mask)
    # tête et épaules, centrées sur le visage
    a = np.array(pose.getchannel("A")) > 128
    top = int(np.nonzero(a.any(1))[0].min()) + int(pose.height * skip)
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


def remove_painted_rocks(img: np.ndarray, plateau: np.ndarray, in_grid: np.ndarray) -> np.ndarray:
    """
    Efface les rochers peints dans l'aire de jeu (zones grises peu saturées) : chaque rocher est recouvert
    par un morceau d'herbe voisin, fondu dans l'image par clonage de Poisson (garde la texture peinte).
    Les rochers hors de la grille restent : ils font partie du décor.
    """
    hsv = cv2.cvtColor(img, cv2.COLOR_RGB2HSV).astype(float)
    s, v = hsv[:, :, 1] / 255, hsv[:, :, 2] / 255
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


def extend_borders(img: np.ndarray, pad: int) -> np.ndarray:
    """Agrandit l'image de {pad} pixels de chaque côté : reflet flouté et assombri des bords."""
    big = cv2.copyMakeBorder(img, pad, pad, pad, pad, cv2.BORDER_REFLECT)
    blurred = cv2.GaussianBlur(big, (0, 0), 18)
    h, w = big.shape[:2]
    yy, xx = np.mgrid[0:h, 0:w]
    dist = np.maximum(np.maximum(pad - xx, xx - (w - pad - 1)), np.maximum(pad - yy, yy - (h - pad - 1)))
    t = np.clip(dist / pad, 0, 1)[:, :, None]
    dark = (blurred * (1 - 0.45 * t)).astype(np.uint8)
    blend = np.clip(dist / 90.0, 0, 1)[:, :, None]  # transition douce sur 90 px
    return (big * (1 - blend) + dark * blend).astype(np.uint8)


def build_forest(dst: Path):
    img = np.array(Image.open(SOURCE / "forest_backdrop.png").convert("RGB"))
    plateau = plateau_mask(img)
    corners, h = perspective_grid(FOREST_PLATEAU, 0.55, 0.55, max_bottom=img.shape[0] - 26)
    cleaned = remove_painted_rocks(img, plateau, grid_mask(corners, img.shape, 1.06))
    pad = 220
    extended = extend_borders(cleaned, pad)
    out = dst / "backdrops"
    out.mkdir(parents=True, exist_ok=True)
    Image.fromarray(extended).save(out / "forest.jpg", quality=90, optimize=True)
    text, scale = grid_properties(corners, h, pad, "forest.jpg")
    text += ("# ombres portées : décalage par unité de hauteur (x, y) et opacité ; lumière venant du haut à gauche\n"
             "shadow = 0.42, 0.2, 0.34\n"
             "# premier plan : feuillage flou dans les coins bas, qui se balance\n"
             "foreground = forest_fg\nforeground.sway = true\n")
    (out / "forest.properties").write_text(text, encoding="utf-8")
    print(f"forêt : grille en perspective, coins {np.round(corners).astype(int).tolist()}, échelle {scale:.3f}")


# ---------------------------------------------------------------------------- désert

DESERT_CUT = 486                 # dernière ligne utile du plateau dans la planche (le damier commence dessous)
DESERT_PROPS_Y = 496             # début de la rangée d'éléments
DESERT_EDGE_SLOPE = 0.525        # pente des deux bords avant du plateau (symétriques)
DESERT_EDGE_POINT = (250, 335)   # un point du bord avant gauche
DESERT_TIP_X = 650.0             # abscisse de la pointe avant (rencontre des deux bords)
DESERT_PLATEAU_GRID = [(628, 36), (1163, 282), (650, 545), (122, 268)]  # coins : fond, droite, avant, gauche
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
    cliff_cols = np.clip(np.minimum(x - 140, 1160 - x) / 30, 0, 1)   # hors des falaises : sol, pas de recopie
    for y in range(h0 - blend, h0 + ext):
        w_ = np.clip((depth[y] - d0) / blend, 0, 1) * cliff_cols
        if w_.any():
            full[y] = full[y] * (1 - w_[:, None]) + full[y - repeat] * w_[:, None]
    # brume de poussière : le plateau domine le désert ; hors des falaises (côtés), le sol s'y fond vite
    t = np.clip((depth - 150) / 150, 0, 1)
    outer = np.clip(np.maximum(150 - x, x - 1150) / 40, 0, 1)[None]
    t = np.maximum(t, outer * np.clip((yc - h0 + 30) / 90, 0, 1))
    t = np.where(yc < h0 - 30, 0, t) ** 1.1
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
    corners, h = perspective_grid(DESERT_PLATEAU_GRID, 0.5, 0.6)
    out = dst / "backdrops"
    out.mkdir(parents=True, exist_ok=True)
    Image.fromarray(big).save(out / "desert.jpg", quality=90, optimize=True)
    Image.fromarray((a_big * 255).clip(0, 255).astype(np.uint8)).save(out / "desert.alpha.png", optimize=True)
    text, scale = grid_properties(corners, h, pad, "desert.jpg")
    text += ("# ombres portées : soleil en haut à droite (ombres longues vers le bas à gauche)\n"
             "shadow = -0.62, 0.3, 0.42\n"
             "# premier plan : rochers et plantes floues à contre-jour, voile de poussière qui dérive\n"
             "foreground = desert_fg\nforeground.dust = true\n")

    # ciel animé : images agrandies et adoucies, empilées verticalement dans un seul JPEG
    gif = Image.open(SOURCE / "desert_sky.gif")
    frames = []
    for i in range(gif.n_frames):
        gif.seek(i)
        f = gif.convert("RGB").resize((gif.width * SKY_SCALE, gif.height * SKY_SCALE), Image.LANCZOS)
        frames.append(np.array(f.filter(ImageFilter.GaussianBlur(1.2))))
    Image.fromarray(np.concatenate(frames, 0)).save(out / "desert_sky.jpg", quality=88, optimize=True)
    (out / "desert.properties").write_text(
        text +
        "# ciel animé derrière l'illustration : images empilées dans desert_sky.jpg\n"
        f"sky = desert_sky\nskyFrames = {len(frames)}\n"
        f"# ligne de l'illustration où se pose le bas du ciel (horizon derrière les montagnes)\nskyBottom = {pad + 230}\n",
        encoding="utf-8")
    print(f"désert : grille en perspective, coins {np.round(corners).astype(int).tolist()}, échelle {scale:.3f}")

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


# ---------------------------------------------------------------------------- premier plan

def noise_field(h, w, scale, seed):
    """Bruit lissé (somme d'octaves) dans [0, 1]."""
    rng = np.random.default_rng(seed)
    out = np.zeros((h, w), np.float32)
    amp, total = 1.0, 0.0
    for octave in range(4):
        s = max(2, int(scale / (2 ** octave)))
        small = rng.random((h // s + 2, w // s + 2)).astype(np.float32)
        out += amp * cv2.resize(small, (w + 2 * s, h + 2 * s), interpolation=cv2.INTER_CUBIC)[s:s + h, s:s + w]
        total += amp
        amp *= 0.5
    out /= total
    return (out - out.min()) / (out.max() - out.min())


def corner_mask(h, w, seed, reach=1.0):
    """Masse irrégulière partant du coin bas-gauche (bords feuillus), en [0, 1]."""
    yy, xx = np.mgrid[0:h, 0:w].astype(np.float32)
    d = np.hypot(xx / (w * 0.95 * reach), (h - yy) / (h * 0.95 * reach))
    n = noise_field(h, w, 26, seed)
    m = np.clip((1.0 - d) * 2.4 + (n - 0.5) * 1.6, 0, 1)
    return cv2.GaussianBlur(m, (0, 0), 1.2)


def save_fg(rgb, alpha, path, blur):
    """Élément de premier plan : flou (hors de la mise au point) et assombri (à contre-jour)."""
    rgba = np.dstack([rgb.astype(np.float32) * alpha[..., None], alpha])           # prémultiplié pour flouter
    rgba = cv2.GaussianBlur(rgba, (0, 0), blur)
    a = np.clip(rgba[..., 3], 1e-4, 1)
    rgb_out = np.clip(rgba[..., :3] / a[..., None], 0, 255)
    Image.fromarray(np.dstack([rgb_out, a * 255]).astype(np.uint8), "RGBA").save(path, optimize=True)


def foliage(w, h, seed):
    """Branches feuillues partant du coin bas-gauche (dessinées en 3× puis réduites) : couleurs et alpha."""
    rng = np.random.default_rng(seed)
    S = 3
    img = Image.new("RGBA", (w * S, h * S), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    greens = [(34, 58, 30), (46, 78, 36), (60, 96, 42), (28, 48, 34), (74, 110, 48)]
    stems = []
    for branch in range(9):
        # tige courbe depuis le coin vers le haut / la droite
        ang = rng.uniform(0.15, 1.35)
        length = rng.uniform(0.55, 1.0) * min(w, h * 1.3) * S
        x, y = rng.uniform(-0.05, 0.25) * w * S, h * S * rng.uniform(0.95, 1.1)
        bend = rng.uniform(-0.6, 0.6)
        pts = []
        for t in np.linspace(0, 1, 40):
            a = ang + bend * t
            pts.append((x + np.cos(a) * length * t, y - np.sin(a) * length * t))
        stems.append((pts, int(4 * S * (1 - 0.5 * branch / 9))))
        for t in np.linspace(0.08, 1, 34):
            px, py = pts[int(t * 39)]
            for side_sign in (-1, 1):
                a = ang + bend * t + side_sign * rng.uniform(0.6, 1.2)
                L = rng.uniform(26, 44) * S * (1.1 - 0.4 * t)
                W = L * rng.uniform(0.38, 0.52)
                cx, cy = px + np.cos(a) * L * 0.45, py - np.sin(a) * L * 0.45
                leaf = [(cx + np.cos(a) * L / 2, cy - np.sin(a) * L / 2),
                        (cx + np.cos(a + 1.57) * W / 2, cy - np.sin(a + 1.57) * W / 2),
                        (cx - np.cos(a) * L / 2, cy + np.sin(a) * L / 2),
                        (cx - np.cos(a + 1.57) * W / 2, cy + np.sin(a + 1.57) * W / 2)]
                g = greens[rng.integers(len(greens))]
                d.polygon(leaf, fill=g + (255,))
    for pts, width in stems:                                     # tiges par-dessus : jamais de jour au milieu
        d.line([(float(x), float(y)) for x, y in pts], fill=(40, 34, 24, 255), width=width)
    small = np.array(img.resize((w, h), Image.LANCZOS)).astype(np.float32)
    yy, xx = np.mgrid[0:h, 0:w].astype(np.float32)
    edge = np.clip(yy / 60, 0, 1) * np.clip((w - 1 - xx) / 60, 0, 1)        # jamais coupé net
    return small[..., :3], small[..., 3] / 255 * edge


def build_foreground(dst: Path):
    out = dst / "backdrops"
    # forêt : branches feuillues dessinées dans les verts de l'illustration, depuis chaque coin bas
    for side, seed in (("left", 3), ("right", 5)):
        rgb, alpha = foliage(420, 300, seed)
        if side == "right":
            rgb, alpha = rgb[:, ::-1], alpha[:, ::-1]
        save_fg(rgb, alpha, out / f"forest_fg_{side}.png", 2.6)
    # désert : rochers et plantes de la planche, agrandis, assombris et flous
    decor = dst / "decor"

    def layer(canvas, name, x, y, scale, dark):
        im = Image.open(decor / f"{name}.png")
        im = im.resize((int(im.width * scale), int(im.height * scale)), Image.LANCZOS)
        a = np.array(im).astype(np.float32)
        a[..., :3] *= dark
        canvas.alpha_composite(Image.fromarray(a.clip(0, 255).astype(np.uint8)), (x, y - im.height))

    for side, parts in (("left", [("desert_rock_1", 0, 330, 2.4, 0.4), ("desert_grass_1", 170, 330, 2.0, 0.36),
                                  ("desert_agave_2", -60, 330, 2.2, 0.32)]),
                        ("right", [("desert_rock_3", 140, 330, 2.6, 0.4), ("desert_agave_1", 30, 330, 2.0, 0.36),
                                   ("desert_grass_2", 300, 330, 1.8, 0.32)])):
        canvas = Image.new("RGBA", (460, 330), (0, 0, 0, 0))
        for name, x, y, sc, dark in parts:
            layer(canvas, name, x, y, sc, dark)
        arr = np.array(canvas).astype(np.float32)
        save_fg(arr[..., :3], arr[..., 3] / 255, out / f"desert_fg_{side}.png", 2.6)
    # poussière : texture raccordable horizontalement, voile chaud
    h, w = 256, 1024
    n = noise_field(h, w + 256, 90, 11)
    n = n[:, :w]
    n = np.concatenate([n, n[:, ::-1]], 1)                                          # miroir : raccord parfait
    n = cv2.resize(n, (w, h))
    a = np.clip((n - 0.35) * 1.8, 0, 1) * (np.linspace(0, 1, h) ** 1.5)[:, None]
    rgb = np.zeros((h, w, 3), np.float32) + np.array([236, 170, 110], np.float32)
    Image.fromarray(np.dstack([rgb, a * 255]).astype(np.uint8), "RGBA").save(out / "dust.png", optimize=True)
    print("premier plan : forêt (feuillage), désert (rochers, plantes, poussière)")


# ---------------------------------------------------------------------------- effets du Druide

FX_CROW = [(14, 151, 65, 184), (111, 139, 162, 172), (194, 132, 251, 170), (675, 141, 722, 175)]
FX_BEES = [(120, 3, 132, 14), (131, 14, 141, 24)]
FX_TORNADO_SPIN = [(6, 2, 80, 100), (103, 2, 170, 97), (191, 6, 264, 94), (286, 5, 348, 92),
                   (14, 117, 81, 200), (103, 109, 174, 203), (191, 114, 263, 203), (285, 111, 352, 202)]
FX_TORNADO_END = [(375, 10, 438, 91), (388, 120, 443, 200)]
FX_LIGHTNING = [(4, 112, 55, 206), (93, 2, 147, 207), (182, 2, 237, 207)]
FX_POTION = [(42, 88, 178, 263), (225, 88, 360, 264), (409, 87, 544, 264), (600, 88, 740, 261), (815, 88, 955, 248),
             (1041, 87, 1175, 261), (1202, 87, 1336, 260), (1366, 88, 1499, 262), (1528, 87, 1661, 262),
             (1709, 87, 1842, 263), (1888, 88, 2022, 262), (43, 275, 176, 448), (228, 275, 360, 447),
             (409, 275, 542, 448), (613, 275, 745, 446), (825, 275, 957, 446), (1024, 275, 1157, 448),
             (1289, 274, 1434, 441), (1518, 275, 1650, 448), (1705, 275, 1838, 448), (1891, 275, 2022, 446)]


def cut_fx(rgb: np.ndarray, box, pad: int = 4) -> Image.Image:
    """Élément d'effet sur fond gris : fond (et ombre au sol) reliés au bord du cadre retirés, bords adoucis."""
    x0, y0, x1, y1 = box
    h, w = rgb.shape[:2]
    x0, y0, x1, y1 = max(0, x0 - pad), max(0, y0 - pad), min(w, x1 + pad), min(h, y1 + pad)
    c = rgb[y0:y1, x0:x1].astype(int)
    grey = ((c.max(2) - c.min(2)) < 18) & (c.max(2) > 85)
    lab, _ = ndimage.label(grey)
    border = set(np.unique(np.concatenate([lab[0], lab[-1], lab[:, 0], lab[:, -1]]))) - {0}
    mask = ~np.isin(lab, list(border))
    parts, n = ndimage.label(mask)
    if n > 1:
        sizes = ndimage.sum(mask, parts, range(1, n + 1))
        mask = np.isin(parts, [i + 1 for i in range(n) if sizes[i] >= 6])
    alpha = ndimage.gaussian_filter(ndimage.binary_erosion(mask, iterations=1).astype(np.float32) * 0.8
                                    + mask.astype(np.float32) * 0.2, 0.5)
    img = Image.fromarray(np.dstack([c.astype(np.uint8), (np.clip(alpha, 0, 1) * 255).astype(np.uint8)]), "RGBA")
    bbox = img.getchannel("A").getbbox()
    return img.crop(bbox) if bbox else img


def cut_glow(rgb: np.ndarray, box, pad: int = 4) -> Image.Image:
    """Éclair : seul le trait lumineux est gardé (opacité selon la luminosité), la dalle sombre est ignorée."""
    x0, y0, x1, y1 = box
    c = rgb[max(0, y0 - pad):y1 + pad, max(0, x0 - pad):x1 + pad].astype(np.float32)
    lum = c @ np.array([0.3, 0.55, 0.15], np.float32)
    bg = np.median(lum[:6])
    alpha = np.clip((lum - bg - 14) / 70, 0, 1)
    rgb_out = np.clip(c * 0.7 + np.array([255, 232, 140], np.float32) * 0.3, 0, 255)   # éclat jaune
    img = Image.fromarray(np.dstack([rgb_out, alpha * 255]).astype(np.uint8), "RGBA")
    return img.crop(img.getchannel("A").getbbox())


def save_frames(frames, out: Path, prefix: str, anchor: str, height=None):
    """Images d'une animation sur un canevas commun (ancre : « bottom » = posées au sol, « center »)."""
    if height:
        frames = [f.resize((max(1, round(f.width * height / max(g.height for g in frames))),
                            max(1, round(f.height * height / max(g.height for g in frames)))), Image.LANCZOS)
                  for f in frames]
    w = max(f.width for f in frames)
    h = max(f.height for f in frames)
    for i, f in enumerate(frames, 1):
        c = Image.new("RGBA", (w, h), (0, 0, 0, 0))
        y = h - f.height if anchor == "bottom" else (h - f.height) // 2
        c.alpha_composite(f, ((w - f.width) // 2, y))
        c.save(out / f"{prefix}_{i}.png", optimize=True)
    return w, h


def build_druid_fx(dst: Path):
    out = dst / "fx"
    out.mkdir(parents=True, exist_ok=True)
    load = lambda n: np.array(Image.open(SOURCE / n).convert("RGB"))
    crow = load("fx_crow.png")
    save_frames([cut_fx(crow, b) for b in FX_CROW], out, "crow", "center")
    bees = load("fx_bees.png")
    for i, b in enumerate(FX_BEES, 1):
        bee = cut_fx(bees, b, pad=2)
        bee.resize((bee.width * 2, bee.height * 2), Image.LANCZOS).save(out / f"bee_{i}.png", optimize=True)
    tornado = load("fx_tornado.png")
    spin = [cut_fx(tornado, b) for b in FX_TORNADO_SPIN]
    end = [cut_fx(tornado, b) for b in FX_TORNADO_END]
    save_frames(spin + end, out, "tornado", "bottom")
    lightning = load("fx_lightning.png")
    save_frames([cut_glow(lightning, b) for b in FX_LIGHTNING], out, "lightning", "bottom")
    potion = load("fx_potion.jpg")
    save_frames([cut_fx(potion, b) for b in FX_POTION], out, "potion", "center", height=96)
    print(f"effets du Druide : corbeau 4, abeilles 2, tornade {len(spin) + len(end)}, éclair 3, potion {len(FX_POTION)}")


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--dst", required=True, type=Path)
    args = ap.parse_args()
    build_archer(args.dst)
    build_sheet_classes(args.dst)
    build_druid_fx(args.dst)
    build_forest(args.dst)
    build_desert(args.dst)
    build_foreground(args.dst)


if __name__ == "__main__":
    main()
