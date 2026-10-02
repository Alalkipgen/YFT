#!/usr/bin/env python3
"""Render the legacy (API 24-25) launcher PNGs for YFT from the adaptive icon geometry.

The adaptive icon itself is the vector pair in app/src/main/res (drawable/ic_launcher_foreground.xml
and the ic_launcher_background colour). Android 7.x launchers cannot use adaptive icons, so this
script draws the same mark on a rounded square and on a circle at the five legacy densities.

Requires pycairo. Run from the repository root:  python3 scripts/generate-launcher-icons.py
"""

import math
import os
import sys

import cairo

BACKGROUND = (0x00 / 255, 0x69 / 255, 0x6B / 255)  # ic_launcher_background, YFT primary
MARK = (1.0, 1.0, 1.0)  # triangle, white
BAR = (0xFF / 255, 0xDB / 255, 0xCA / 255)  # bar, YFT tertiary container

# Geometry in the 108-unit adaptive-icon viewport; keep in sync with ic_launcher_foreground.xml.
TRIANGLE = [(36.0, 36.0), (72.0, 36.0), (54.0, 60.0)]
TRIANGLE_STROKE = 6.0
BAR_LEFT, BAR_TOP, BAR_RIGHT, BAR_BOTTOM = 34.0, 68.0, 74.0, 75.0

# Launchers show roughly the central 72 units of an adaptive icon; a legacy 48 dp icon draws its
# shape inside a 44 dp square (2 dp margin), so 18..90 maps onto 2..46.
VISIBLE_START, VISIBLE_SIZE = 18.0, 72.0
LEGACY_MARGIN, LEGACY_SHAPE = 2.0, 44.0

DENSITIES = {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}


def draw(size: int, round_shape: bool) -> cairo.ImageSurface:
    surface = cairo.ImageSurface(cairo.FORMAT_ARGB32, size, size)
    ctx = cairo.Context(surface)
    dp = size / 48.0
    ctx.scale(dp, dp)

    ctx.set_source_rgb(*BACKGROUND)
    if round_shape:
        ctx.arc(24.0, 24.0, LEGACY_SHAPE / 2, 0, 2 * math.pi)
    else:
        rounded_rect(ctx, LEGACY_MARGIN, LEGACY_MARGIN, LEGACY_SHAPE, LEGACY_SHAPE, 8.0)
    ctx.fill()

    # Map adaptive viewport units onto the legacy shape.
    scale = LEGACY_SHAPE / VISIBLE_SIZE
    ctx.translate(LEGACY_MARGIN - VISIBLE_START * scale, LEGACY_MARGIN - VISIBLE_START * scale)
    ctx.scale(scale, scale)

    ctx.set_source_rgb(*MARK)
    ctx.move_to(*TRIANGLE[0])
    for point in TRIANGLE[1:]:
        ctx.line_to(*point)
    ctx.close_path()
    ctx.set_line_join(cairo.LINE_JOIN_ROUND)
    ctx.set_line_width(TRIANGLE_STROKE)
    ctx.fill_preserve()
    ctx.stroke()

    ctx.set_source_rgb(*BAR)
    radius = (BAR_BOTTOM - BAR_TOP) / 2
    rounded_rect(ctx, BAR_LEFT, BAR_TOP, BAR_RIGHT - BAR_LEFT, BAR_BOTTOM - BAR_TOP, radius)
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
    res = os.path.join("app", "src", "main", "res")
    if not os.path.isdir(res):
        print("Run from the repository root.", file=sys.stderr)
        return 1
    for density, size in DENSITIES.items():
        folder = os.path.join(res, f"mipmap-{density}")
        os.makedirs(folder, exist_ok=True)
        draw(size, round_shape=False).write_to_png(os.path.join(folder, "ic_launcher.png"))
        draw(size, round_shape=True).write_to_png(os.path.join(folder, "ic_launcher_round.png"))
        print(f"{folder}: {size}px")
    return 0


if __name__ == "__main__":
    sys.exit(main())
