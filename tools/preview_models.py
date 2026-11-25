"""Offline isometric preview of the cable / pipe block models.

Renders each face as a flat-shaded quad using the average colour of its texture.
Good enough to check geometry (gaps, overlaps, flush joints) without launching
the game.

Run:  python tools/preview_models.py
"""

from __future__ import annotations

import json
import math
import os

from PIL import Image, ImageDraw

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ASSETS = os.path.join(ROOT, "src", "main", "resources", "assets", "computera")
MODELS = os.path.join(ASSETS, "models", "block")
TEXTURES = os.path.join(ASSETS, "textures")

SCALE = 6          # screen pixels per model pixel
SIZE = 16 * SCALE * 4

# camera basis: looking from the +x/+y/+z octant, slightly above
YAW = math.radians(35)
PITCH = math.radians(28)


def avg_color(texture_ref: str):
    path = os.path.join(TEXTURES, texture_ref.split(":")[1] + ".png")
    img = Image.open(path).convert("RGBA")
    pixels = [p for p in img.getdata() if p[3] > 40]
    if not pixels:
        return (255, 0, 255)
    n = len(pixels)
    return tuple(sum(p[i] for p in pixels) // n for i in range(3))


_cache: dict[str, tuple] = {}


def color_of(texture_ref: str):
    if texture_ref not in _cache:
        _cache[texture_ref] = avg_color(texture_ref)
    return _cache[texture_ref]


def load(name: str) -> dict:
    with open(os.path.join(MODELS, name), encoding="utf-8") as handle:
        return json.load(handle)


def project(point, cam):
    """World point (model coords, 0..16) -> screen point (vector projection)."""
    x, y, z = point
    x -= 8
    y -= 8
    z -= 8
    # yaw around Y, then pitch around X
    rx = x * math.cos(YAW) - z * math.sin(YAW)
    rz = x * math.sin(YAW) + z * math.cos(YAW)
    ry = y * math.cos(PITCH) - rz * math.sin(PITCH)
    depth = y * math.sin(PITCH) + rz * math.cos(PITCH)
    sx = SIZE / 2 + rx * SCALE
    sy = SIZE / 2 - ry * SCALE
    return sx, sy, depth


FACE_DIRS = {
    "north": (0, 0, -1),
    "south": (0, 0, 1),
    "east": (1, 0, 0),
    "west": (-1, 0, 0),
    "up": (0, 1, 0),
    "down": (0, -1, 0),
}

FACE_CORNERS = {
    "north": ((0, 0, 0), (0, 1, 0), (1, 1, 0), (1, 0, 0)),
    "south": ((1, 0, 1), (1, 1, 1), (0, 1, 1), (0, 0, 1)),
    "west": ((0, 0, 1), (0, 1, 1), (0, 1, 0), (0, 0, 0)),
    "east": ((1, 0, 0), (1, 1, 0), (1, 1, 1), (1, 0, 1)),
    "up": ((0, 1, 0), (0, 1, 1), (1, 1, 1), (1, 1, 0)),
    "down": ((0, 0, 1), (0, 0, 0), (1, 0, 0), (1, 0, 1)),
}

LIGHT = {
    "up": 1.00, "down": 0.45, "north": 0.72, "south": 0.82, "east": 0.90, "west": 0.66,
}


def shade(color, factor):
    return tuple(min(255, round(c * factor)) for c in color)


def render(parts, name: str) -> None:
    """parts: list of (model json, block offset (bx,by,bz))."""
    image = Image.new("RGBA", (SIZE, SIZE), (16, 18, 22, 255))
    draw = ImageDraw.Draw(image)

    quads = []
    for model, offset in parts:
        textures = model.get("textures", {})
        for el in model.get("elements", []):
            x0, y0, z0 = el["from"]
            x1, y1, z1 = el["to"]
            for face, data in el.get("faces", {}).items():
                tex = data["texture"]
                if tex.startswith("#"):
                    tex = textures.get(tex[1:], tex)
                color = color_of(tex)
                corners = []
                for cx, cy, cz in FACE_CORNERS[face]:
                    px = (x0 + (x1 - x0) * cx) + offset[0] * 16
                    py = (y0 + (y1 - y0) * cy) + offset[1] * 16
                    pz = (z0 + (z1 - z0) * cz) + offset[2] * 16
                    sx, sy, depth = project((px, py, pz), None)
                    corners.append((sx, sy, depth))
                depth = sum(c[2] for c in corners) / 4
                quads.append((depth, corners, shade(color, LIGHT[face])))

    quads.sort(key=lambda q: q[0])
    for _, corners, color in quads:
        draw.polygon([(c[0], c[1]) for c in corners], fill=color + (255,),
                     outline=shade(color, 0.55) + (255,))

    out = os.path.join(ROOT, ".preview", name)
    image.save(out)
    print("wrote", out)


def cord_run(prefix: str, name: str) -> None:
    core = load(f"{prefix}_core.json")
    parts = [(core, (0, 0, 0))]
    for direction in ("north", "south", "east", "up"):
        parts.append((load(f"{prefix}_arm_{direction}.json"), (0, 0, 0)))
    # neighbour cable one block to the south, showing the double collar joint
    parts.append((core, (0, 0, 1)))
    parts.append((load(f"{prefix}_arm_south.json"), (0, 0, 1)))
    render(parts, name)


def main() -> None:
    os.makedirs(os.path.join(ROOT, ".preview"), exist_ok=True)
    render([(load("cable_core.json"), (0, 0, 0))], "cable_core.png")
    render([(load("cable_item.json"), (0, 0, 0))] if os.path.exists(
        os.path.join(MODELS, "cable_item.json")) else [(load("cable.json"), (0, 0, 0))],
        "cable_item.png")
    cord_run("cable", "cable_run.png")
    cord_run("pipe", "pipe_run.png")


if __name__ == "__main__":
    main()
