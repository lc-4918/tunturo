"""Genere l'icone d'application Tunturo (TM-3) : logo B1 "sommet en courbes", esquisse retenue par Luc le
2026-11-15, jeu de couleurs 1 (fond primary Mousse, courbes primaryContainer).

Ecrit, sous app/src/main/res :
- drawable/ic_launcher_foreground.xml : le dessin, calque avant de l'icone adaptative (108 dp) ;
- drawable/ic_launcher_monochrome.xml : le meme en une couleur, pour l'icone a theme d'Android 13 ;
- mipmap-*/ic_launcher.png et ic_launcher_round.png : les icones figees d'Android 7 (sans icone adaptative).

Avec --web DOSSIER, ecrit aussi les icones du site Trailmap (favicon.svg, favicon.ico, PNG 16 a 512,
apple-touch-icon) dans DOSSIER, typiquement ~/Projets/trailmap/front/public/favicon.

Usage : python3 tools/app-icon/generate_icon.py [--web DOSSIER] (depuis la racine du depot). Pillow requis.
"""
import argparse
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


def legacy_icon(size, round_shape, corner=0.22, logo_fraction=0.62, stroke_width=STROKE):
    """Une icone figee : la zone visible de l'icone adaptative, en rond ou en carre arrondi.

    [logo_fraction] est la part de la largeur prise par le logo ; [stroke_width] le trait, dans le carre
    de 64 du dessin. Les favicons grossissent l'un et l'autre : a 16 px, le dessin de l'icone se brouille.
    """
    scale = logo_fraction * VISIBLE / 47.0
    supersampling = 8
    full = size * supersampling
    image = Image.new("RGBA", (full, full), (0, 0, 0, 0))
    draw = ImageDraw.Draw(image)
    if round_shape:
        draw.ellipse((0, 0, full - 1, full - 1), fill=BACKGROUND)
    else:
        draw.rounded_rectangle((0, 0, full - 1, full - 1), radius=full * corner, fill=BACKGROUND)
    # Dans la zone visible, un point du logo (x, y) tombe en (x * SCALE + decalage) sur 72.
    unit = full / VISIBLE

    def place(point):
        return ((VISIBLE / 2 + (point[0] - LOGO_CENTER[0]) * scale) * unit,
                (VISIBLE / 2 + (point[1] - LOGO_CENTER[1]) * scale) * unit)

    stroke = stroke_width * scale * unit
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
    summit_radius = SUMMIT[2] * scale * unit
    draw.ellipse((summit_x - summit_radius, summit_y - summit_radius,
                  summit_x + summit_radius, summit_y + summit_radius), fill=FOREGROUND)
    return image.resize((size, size), Image.LANCZOS)


# Les favicons (32 px et moins) : logo a 80 % du carre, trait de 3,4 comme le logo du menu lateral.
FAVICON_FRACTION = 0.8
FAVICON_STROKE = 3.4


def favicon_svg():
    """Le favicon vectoriel : carre arrondi vert, logo clair, a la taille des petits favicons."""
    scale = FAVICON_FRACTION * VISIBLE / 47.0
    translate_x = VISIBLE / 2 - LOGO_CENTER[0] * scale
    translate_y = VISIBLE / 2 - LOGO_CENTER[1] * scale
    summit_x, summit_y, summit_radius = SUMMIT
    paths = "".join(f'<path d="{path_data(contour)}"/>' for contour in CONTOURS)
    return (f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 72 72">'
            f'<rect width="72" height="72" rx="16" fill="{BACKGROUND}"/>'
            f'<g transform="translate({translate_x:.4f} {translate_y:.4f}) scale({scale:.4f})">'
            f'<g fill="none" stroke="{FOREGROUND}" stroke-width="{FAVICON_STROKE:g}">{paths}</g>'
            f'<circle cx="{summit_x:g}" cy="{summit_y:g}" r="{summit_radius:g}" fill="{FOREGROUND}"/></g></svg>\n')


def web_icons(folder):
    """Les icones du site : favicons (petits, logo grossi), icones d'ecran d'accueil (logo de l'app)."""
    folder.mkdir(parents=True, exist_ok=True)
    (folder / "favicon.svg").write_text(favicon_svg())
    small = {size: legacy_icon(size, round_shape=False, logo_fraction=FAVICON_FRACTION, stroke_width=FAVICON_STROKE)
             for size in (16, 32, 48)}
    small[16].save(folder / "favicon-16x16.png", optimize=True)
    small[32].save(folder / "favicon-32x32.png", optimize=True)
    big = legacy_icon(256, round_shape=False, logo_fraction=FAVICON_FRACTION, stroke_width=FAVICON_STROKE)
    big.save(folder / "favicon.ico", sizes=[(16, 16), (32, 32), (48, 48)])
    # iOS arrondit lui-meme les coins : un carre plein, sans transparence.
    legacy_icon(180, round_shape=False, corner=0).convert("RGB").save(folder / "apple-touch-icon.png", optimize=True)
    for size in (192, 512):
        legacy_icon(size, round_shape=False).save(folder / f"android-chrome-{size}x{size}.png", optimize=True)


def main():
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--web", type=Path, help="dossier ou ecrire aussi les icones du site Trailmap")
    arguments = parser.parse_args()
    (RESOURCES / "drawable/ic_launcher_foreground.xml").write_text(vector_drawable(
        FOREGROUND, "Calque avant de l'icone Tunturo : logo B1 en esquisse, couleur primaryContainer de Mousse."))
    (RESOURCES / "drawable/ic_launcher_monochrome.xml").write_text(vector_drawable(
        "#FF000000", "Icone a theme (Android 13 et plus) : seule la forme compte, le systeme la colore."))
    for density, size in {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}.items():
        folder = RESOURCES / f"mipmap-{density}"
        legacy_icon(size, round_shape=False).save(folder / "ic_launcher.png", optimize=True)
        legacy_icon(size, round_shape=True).save(folder / "ic_launcher_round.png", optimize=True)
    if arguments.web:
        web_icons(arguments.web)


if __name__ == "__main__":
    main()
