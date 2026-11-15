"""Genere l'icone d'application Tunturo (TM-3) : logo B1 "sommet en courbes", esquisse retenue par Luc le
2026-11-15, jeu de couleurs 1 (fond primary Mousse, courbes primaryContainer).

Ecrit, sous app/src/main/res :
- drawable/ic_launcher_foreground.xml : le dessin, calque avant de l'icone adaptative (108 dp) ;
- drawable/ic_launcher_monochrome.xml : le meme en une couleur, pour l'icone a theme d'Android 13 ;
- mipmap-*/ic_launcher.png et ic_launcher_round.png : les icones figees d'Android 7 (sans icone adaptative).

Usage : python3 tools/app-icon/generate_icon.py (depuis la racine du depot). Pillow requis pour les PNG.
"""
from pathlib import Path

from PIL import Image, ImageDraw

RESOURCES = Path(__file__).resolve().parents[2] / "app/src/main/res"

BACKGROUND = "#536525"
FOREGROUND = "#D5EC9C"

# Le logo B1 en esquisse, dans un carre de 64 : trois courbes de niveau fermees, quatre cubiques chacune
# apres le point de depart, et le point du sommet.
CONTOURS = [
    [(8, 46), (8, 28), (22, 14), (34, 14), (48, 14), (58, 28), (56, 44), (54, 54), (14, 56), (8, 46)],
    [(17, 42), (17, 30), (26, 21), (34, 21), (44, 21), (49, 30), (48, 40), (47, 47), (20, 49), (17, 42)],
    [(26, 37), (26, 31), (30, 27), (34, 27), (39, 27), (41, 32), (40, 36), (39, 40), (27, 41), (26, 37)],
]
SUMMIT = (33.5, 33.5, 2.0)
STROKE = 2.6
LOGO_CENTER = (32.0, 33.5)

# Le logo couvre 62 % de la zone visible d'une icone adaptative (72 dp sur 108), largeur de dessin 47.
CANVAS = 108.0
VISIBLE = 72.0
SCALE = 0.62 * VISIBLE / 47.0


def path_data(points):
    start, rest = points[0], points[1:]
    segments = " ".join(
        "C" + " ".join(f"{x:g},{y:g}" for x, y in rest[index:index + 3]) for index in range(0, len(rest), 3))
    return f"M{start[0]:g},{start[1]:g} {segments} Z"


def vector_drawable(color, comment):
    translate_x = CANVAS / 2 - LOGO_CENTER[0] * SCALE
    translate_y = CANVAS / 2 - LOGO_CENTER[1] * SCALE
    summit_x, summit_y, summit_radius = SUMMIT
    paths = "\n".join(
        f'        <path\n            android:pathData="{path_data(contour)}"\n'
        f'            android:strokeColor="{color}"\n            android:strokeWidth="{STROKE:g}" />'
        for contour in CONTOURS)
    summit = (f"M{summit_x:g},{summit_y - summit_radius:g} A{summit_radius:g},{summit_radius:g} 0 1,1 "
              f"{summit_x:g},{summit_y + summit_radius:g} A{summit_radius:g},{summit_radius:g} 0 1,1 "
              f"{summit_x:g},{summit_y - summit_radius:g} Z")
    return f'''<?xml version="1.0" encoding="utf-8"?>
<!-- {comment}
     Fichier genere par tools/app-icon/generate_icon.py : modifier le script, pas ce fichier. -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
    <group
        android:scaleX="{SCALE:.4f}"
        android:scaleY="{SCALE:.4f}"
        android:translateX="{translate_x:.4f}"
        android:translateY="{translate_y:.4f}">
{paths}
        <path
            android:pathData="{summit}"
            android:fillColor="{color}" />
    </group>
</vector>
'''


def cubic_points(start, first, second, end, steps=48):
    points = []
    for index in range(1, steps + 1):
        t = index / steps
        u = 1 - t
        points.append((
            u ** 3 * start[0] + 3 * u * u * t * first[0] + 3 * u * t * t * second[0] + t ** 3 * end[0],
            u ** 3 * start[1] + 3 * u * u * t * first[1] + 3 * u * t * t * second[1] + t ** 3 * end[1],
        ))
    return points


def legacy_icon(size, round_shape):
    """Une icone figee : la zone visible de l'icone adaptative, en rond ou en carre arrondi."""
    supersampling = 8
    full = size * supersampling
    image = Image.new("RGBA", (full, full), (0, 0, 0, 0))
    draw = ImageDraw.Draw(image)
    if round_shape:
        draw.ellipse((0, 0, full - 1, full - 1), fill=BACKGROUND)
    else:
        draw.rounded_rectangle((0, 0, full - 1, full - 1), radius=full * 0.22, fill=BACKGROUND)
    # Dans la zone visible, un point du logo (x, y) tombe en (x * SCALE + decalage) sur 72.
    unit = full / VISIBLE

    def place(point):
        return ((VISIBLE / 2 + (point[0] - LOGO_CENTER[0]) * SCALE) * unit,
                (VISIBLE / 2 + (point[1] - LOGO_CENTER[1]) * SCALE) * unit)

    stroke = STROKE * SCALE * unit
    for contour in CONTOURS:
        polyline = [contour[0]]
        for index in range(1, len(contour), 3):
            polyline += cubic_points(polyline[-1], *contour[index:index + 3])
        placed = [place(point) for point in polyline]
        draw.line(placed, fill=FOREGROUND, width=round(stroke), joint="curve")
        radius = stroke / 2
        for x, y in placed[::6]:
            draw.ellipse((x - radius, y - radius, x + radius, y + radius), fill=FOREGROUND)
    summit_x, summit_y = place(SUMMIT[:2])
    summit_radius = SUMMIT[2] * SCALE * unit
    draw.ellipse((summit_x - summit_radius, summit_y - summit_radius,
                  summit_x + summit_radius, summit_y + summit_radius), fill=FOREGROUND)
    return image.resize((size, size), Image.LANCZOS)


def main():
    (RESOURCES / "drawable/ic_launcher_foreground.xml").write_text(vector_drawable(
        FOREGROUND, "Calque avant de l'icone Tunturo : logo B1 en esquisse, couleur primaryContainer de Mousse."))
    (RESOURCES / "drawable/ic_launcher_monochrome.xml").write_text(vector_drawable(
        "#FF000000", "Icone a theme (Android 13 et plus) : seule la forme compte, le systeme la colore."))
    for density, size in {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}.items():
        folder = RESOURCES / f"mipmap-{density}"
        legacy_icon(size, round_shape=False).save(folder / "ic_launcher.png", optimize=True)
        legacy_icon(size, round_shape=True).save(folder / "ic_launcher_round.png", optimize=True)


if __name__ == "__main__":
    main()
