#!/usr/bin/env python3
"""Render the legacy (API 24-25) launcher PNGs for YFT from the adaptive icon resources.

The adaptive icon is drawable/ic_launcher_background.xml (a Mint Teal to Deep Teal gradient)
under drawable/ic_launcher_foreground.xml (the white play-and-download mark). Android 7.x
launchers cannot use adaptive icons, so this script reads both vectors and draws the same icon
on a rounded square and on a circle at the five legacy densities. Edit the vectors, then rerun.

Requires pycairo. Run from the repository root:  python3 scripts/generate-launcher-icons.py
"""

import math
import os
import re
import sys
import xml.etree.ElementTree as ET

import cairo

ANDROID = "{http://schemas.android.com/apk/res/android}"
RES = os.path.join("app", "src", "main", "res")

# Launchers show roughly the central 72 units of an adaptive icon; a legacy 48 dp icon draws its
# shape inside a 44 dp square (2 dp margin), so 18..90 maps onto 2..46.
VISIBLE_START, VISIBLE_SIZE = 18.0, 72.0
LEGACY_MARGIN, LEGACY_SHAPE, LEGACY_CORNER = 2.0, 44.0, 9.75

DENSITIES = {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}


def argb(value: str):
    """#RRGGBB or #AARRGGBB to an (r, g, b, a) tuple for cairo."""
    digits = value.lstrip("#")
    if len(digits) == 6:
        digits = "FF" + digits
    a, r, g, b = (int(digits[i:i + 2], 16) / 255 for i in range(0, 8, 2))
    return r, g, b, a


def background_gradient():
    """The linear gradient of ic_launcher_background.xml, in adaptive viewport units."""
    root = ET.parse(os.path.join(RES, "drawable", "ic_launcher_background.xml")).getroot()
    gradient = root.find(".//gradient")
    if gradient is None or gradient.get(ANDROID + "type") != "linear":
        raise SystemExit("ic_launcher_background.xml must hold one linear gradient")
    coords = [float(gradient.get(ANDROID + key)) for key in ("startX", "startY", "endX", "endY")]
    return coords, argb(gradient.get(ANDROID + "startColor")), argb(gradient.get(ANDROID + "endColor"))


def foreground_paths():
    """(fill colour, polygons) for each path of ic_launcher_foreground.xml (M, L and Z only)."""
    root = ET.parse(os.path.join(RES, "drawable", "ic_launcher_foreground.xml")).getroot()
    paths = []
    for path in root.iter("path"):
        data = path.get(ANDROID + "pathData")
        tokens = re.findall(r"[MLZ]|-?\d+(?:\.\d+)?", data)
        if any(t.isalpha() and t not in "MLZ" for t in tokens):
            raise SystemExit("ic_launcher_foreground.xml may only use M, L and Z commands")
        polygons, current, command, numbers = [], [], None, []
        for token in tokens:
            if token in "MLZ":
                command = token
                if token == "M" and current:
                    polygons.append(current)
                    current = []
                if token == "Z":
                    polygons.append(current)
                    current = []
                continue
            numbers.append(float(token))
            if len(numbers) == 2:
                current.append(tuple(numbers))
                numbers = []
        if current:
            polygons.append(current)
        paths.append((argb(path.get(ANDROID + "fillColor")), polygons))
    return paths


def draw(size: int, round_shape: bool) -> cairo.ImageSurface:
    surface = cairo.ImageSurface(cairo.FORMAT_ARGB32, size, size)
    ctx = cairo.Context(surface)
    ctx.scale(size / 48.0, size / 48.0)

    if round_shape:
        ctx.arc(24.0, 24.0, LEGACY_SHAPE / 2, 0, 2 * math.pi)
    else:
        rounded_rect(ctx, LEGACY_MARGIN, LEGACY_MARGIN, LEGACY_SHAPE, LEGACY_SHAPE, LEGACY_CORNER)
    ctx.clip()

    # Map adaptive viewport units onto the legacy shape.
    scale = LEGACY_SHAPE / VISIBLE_SIZE
    ctx.translate(LEGACY_MARGIN - VISIBLE_START * scale, LEGACY_MARGIN - VISIBLE_START * scale)
    ctx.scale(scale, scale)

    (x0, y0, x1, y1), start, end = background_gradient()
    gradient = cairo.LinearGradient(x0, y0, x1, y1)
    gradient.add_color_stop_rgba(0, *start)
    gradient.add_color_stop_rgba(1, *end)
    ctx.rectangle(0, 0, 108, 108)
    ctx.set_source(gradient)
    ctx.fill()

    for colour, polygons in foreground_paths():
        ctx.set_source_rgba(*colour)
        for polygon in polygons:
            ctx.move_to(*polygon[0])
            for point in polygon[1:]:
                ctx.line_to(*point)
            ctx.close_path()
        ctx.set_fill_rule(cairo.FILL_RULE_WINDING)
        ctx.fill()
    return surface


def rounded_rect(ctx, x, y, width, height, radius):
    ctx.new_sub_path()
    ctx.arc(x + width - radius, y + radius, radius, -math.pi / 2, 0)
    ctx.arc(x + width - radius, y + height - radius, radius, 0, math.pi / 2)
    ctx.arc(x + radius, y + height - radius, radius, math.pi / 2, math.pi)
    ctx.arc(x + radius, y + radius, radius, math.pi, 3 * math.pi / 2)
    ctx.close_path()


def main() -> int:
    if not os.path.isdir(RES):
        print("Run from the repository root.", file=sys.stderr)
        return 1
    out = os.environ.get("YFT_ICON_OUT", RES)
    for density, size in DENSITIES.items():
        folder = os.path.join(out, f"mipmap-{density}")
        os.makedirs(folder, exist_ok=True)
        draw(size, round_shape=False).write_to_png(os.path.join(folder, "ic_launcher.png"))
        draw(size, round_shape=True).write_to_png(os.path.join(folder, "ic_launcher_round.png"))
        print(f"{folder}: {size}px")
    return 0


if __name__ == "__main__":
    sys.exit(main())
