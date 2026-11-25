"""Generate 16x16 pixel-art textures for the cable, connector, pipe, flange and copper wire.

Run:  python tools/gen_cable_textures.py
"""

from __future__ import annotations

import os

from PIL import Image

ROOT = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))),
                    "src", "main", "resources", "assets", "computera", "textures")


def lerp(a, b, t):
    return tuple(round(a[i] + (b[i] - a[i]) * t) for i in range(3))


def shade(color, amount):
    """amount in -1..1, negative darkens."""
    if amount < 0:
        return lerp(color, (0, 0, 0), -amount)
    return lerp(color, (255, 255, 255), amount)


# --------------------------------------------------------------------------- cable

CABLE_BASE = (58, 66, 80)
CABLE_DARK = (42, 48, 60)
CABLE_LIGHT = (76, 86, 103)
CABLE_RIM = (30, 34, 44)
CABLE_ACCENT = (74, 210, 202)
CABLE_ACCENT_DARK = (36, 138, 134)


def cable_texture() -> Image.Image:
    """Dark slate jacket with a braided weave and one cyan data strand.

    The strand runs down the middle of the texture so it reads as a continuous
    line along the whole cable, whichever face you look at."""
    img = Image.new("RGBA", (16, 16), CABLE_BASE + (255,))
    px = img.load()
    for y in range(16):
        for x in range(16):
            phase = (x + y) % 4
            if phase == 0:
                px[x, y] = CABLE_DARK + (255,)
            elif phase == 2:
                px[x, y] = CABLE_LIGHT + (255,)
    # cylindrical shading towards the two edges
    for y in range(16):
        px[0, y] = shade(CABLE_BASE, -0.35) + (255,)
        px[1, y] = shade(CABLE_BASE, -0.18) + (255,)
        px[14, y] = shade(CABLE_BASE, -0.18) + (255,)
        px[15, y] = shade(CABLE_BASE, -0.35) + (255,)
    # heat-shrink bands repeating once per block
    for band in (0, 8):
        for x in range(16):
            px[x, band] = shade(CABLE_BASE, 0.22) + (255,)
            px[x, band + 1] = CABLE_RIM + (255,)
    # inset data strand down the middle
    for y in range(16):
        px[6, y] = CABLE_RIM + (255,)
        px[7, y] = CABLE_ACCENT + (255,)
        px[8, y] = CABLE_ACCENT_DARK + (255,)
        px[9, y] = CABLE_RIM + (255,)
    # strand highlight glints
    for y in range(0, 16, 4):
        px[7, y] = shade(CABLE_ACCENT, 0.35) + (255,)
    return img


# ---------------------------------------------------------------------- connector

CONNECTOR_BODY = (72, 80, 92)
CONNECTOR_BODY_DARK = (44, 50, 60)
CONNECTOR_BODY_LIGHT = (108, 118, 132)
CONNECTOR_BOLT = (150, 158, 168)
CONNECTOR_BOLT_DARK = (86, 92, 102)
COPPER = (176, 116, 60)
COPPER_LIGHT = (214, 150, 86)
COPPER_DARK = (120, 74, 34)
LED = (86, 226, 214)


def connector_texture() -> Image.Image:
    """A cable collar. Only the 2px-deep side faces are normally visible, so the
    design is banded along the texture's X axis (which maps to the collar depth)."""
    img = Image.new("RGBA", (16, 16), CONNECTOR_BODY + (255,))
    px = img.load()
    # depth bands: dark rim -> copper contact -> machined body -> copper -> rim
    bands = {
        0: CONNECTOR_BODY_DARK, 1: shade(CONNECTOR_BODY, -0.20),
        2: COPPER, 3: COPPER_LIGHT,
        4: CONNECTOR_BODY_LIGHT, 5: CONNECTOR_BODY_LIGHT,
        6: CONNECTOR_BODY, 7: CONNECTOR_BODY,
        8: CONNECTOR_BODY, 9: CONNECTOR_BODY,
        10: CONNECTOR_BODY_LIGHT, 11: CONNECTOR_BODY_LIGHT,
        12: COPPER_LIGHT, 13: COPPER,
        14: shade(CONNECTOR_BODY, -0.20), 15: CONNECTOR_BODY_DARK,
    }
    for x, color in bands.items():
        for y in range(16):
            px[x, y] = color + (255,)
    # top/bottom bevel so the collar reads as a solid machined ring
    for x in range(16):
        px[x, 0] = shade(px[x, 0], -0.25) + (255,)
        px[x, 15] = shade(px[x, 15], -0.30) + (255,)
        px[x, 1] = shade(px[x, 1], 0.08) + (255,)
        px[x, 14] = shade(px[x, 14], -0.12) + (255,)
    # two rows of bolts on the collar body
    for bx in (6, 8):
        for by in (3, 12):
            px[bx, by] = CONNECTOR_BOLT + (255,)
            px[bx, by + 1] = CONNECTOR_BOLT_DARK + (255,)
    # activity led strip on the body
    for y in (6, 7, 8, 9):
        px[7, y] = LED + (255,)
    return img


# -------------------------------------------------------------------------- pipe

PIPE_BASE = (108, 114, 123)
PIPE_DARK = (72, 77, 85)
PIPE_LIGHT = (140, 147, 157)
PIPE_HOT = (168, 175, 184)


def pipe_texture() -> Image.Image:
    img = Image.new("RGBA", (16, 16), PIPE_BASE + (255,))
    px = img.load()
    # brushed metal: vertical streaks with a cylindrical light falloff
    for x in range(16):
        t = abs(x - 5.5) / 7.5
        tone = lerp(PIPE_HOT, PIPE_DARK, min(1.0, t * 1.15))
        for y in range(16):
            jitter = ((x * 7 + y * 13) % 5 - 2) * 2
            px[x, y] = shade(tone, jitter / 255.0) + (255,)
    # weld seam along one edge
    for y in range(16):
        px[0, y] = PIPE_DARK + (255,)
        px[1, y] = shade(PIPE_BASE, -0.10) + (255,)
    # specular highlight
    for y in range(16):
        px[5, y] = PIPE_HOT + (255,)
    # banded rivets: one coupling band per block length
    for band in (0, 8):
        for x in range(16):
            px[x, band] = shade(PIPE_BASE, -0.28) + (255,)
            px[x, band + 1] = shade(PIPE_BASE, 0.18) + (255,)
        for rx in (3, 11):
            px[rx, band + 2] = shade(PIPE_BASE, 0.45) + (255,)
            px[rx, band + 3] = shade(PIPE_BASE, -0.35) + (255,)
    return img


PIPE_FLANGE_BODY = (126, 134, 144)
PIPE_FLANGE_DARK = (74, 80, 89)
PIPE_FLANGE_LIGHT = (166, 174, 184)
BRASS = (168, 128, 66)
BRASS_LIGHT = (208, 166, 96)
BRASS_DARK = (110, 80, 36)
RIVET = (196, 204, 214)


def pipe_flange_texture() -> Image.Image:
    """A bolted pipe flange. Banded along X (the flange depth) so its 2px side
    faces still read as a metal coupling."""
    img = Image.new("RGBA", (16, 16), PIPE_FLANGE_BODY + (255,))
    px = img.load()
    bands = {
        0: PIPE_FLANGE_DARK, 1: shade(PIPE_FLANGE_BODY, -0.22),
        2: BRASS_DARK, 3: BRASS,
        4: PIPE_FLANGE_LIGHT, 5: PIPE_FLANGE_LIGHT,
        6: PIPE_FLANGE_BODY, 7: PIPE_FLANGE_BODY,
        8: PIPE_FLANGE_BODY, 9: PIPE_FLANGE_BODY,
        10: PIPE_FLANGE_LIGHT, 11: PIPE_FLANGE_LIGHT,
        12: BRASS, 13: BRASS_DARK,
        14: shade(PIPE_FLANGE_BODY, -0.22), 15: PIPE_FLANGE_DARK,
    }
    for x, color in bands.items():
        for y in range(16):
            px[x, y] = color + (255,)
    # top/bottom bevel
    for x in range(16):
        px[x, 0] = shade(px[x, 0], -0.22) + (255,)
        px[x, 15] = shade(px[x, 15], -0.28) + (255,)
        px[x, 1] = shade(px[x, 1], 0.10) + (255,)
        px[x, 14] = shade(px[x, 14], -0.10) + (255,)
    # collar rivets, kept large enough to survive the depth squash
    for bx in (6, 8):
        for by in (3, 12):
            px[bx, by] = RIVET + (255,)
            px[bx, by + 1] = shade(RIVET, -0.45) + (255,)
    # two barcode-ish stamp marks on the body so it does not look blank
    for y in (6, 7, 8, 9):
        px[7, y] = shade(PIPE_FLANGE_DARK, 0.14) + (255,)
    return img


# ------------------------------------------------------------------- copper wire

WIRE_HI = (246, 168, 94)
WIRE_MID = (198, 118, 52)
WIRE_LOW = (138, 74, 26)
WIRE_DARK = (92, 46, 14)


def copper_wire_texture() -> Image.Image:
    """A coil of bare copper wire seen from above: two turns plus a loose lead."""
    from PIL import ImageDraw

    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    draw = ImageDraw.Draw(img)

    def turn(box, lead_in):
        draw.ellipse(box, outline=WIRE_MID + (255,), width=1)
        draw.arc(box, 165, 300, fill=WIRE_HI + (255,), width=1)
        draw.arc(box, 15, 120, fill=WIRE_LOW + (255,), width=1)
        if lead_in:
            draw.point(lead_in, fill=WIRE_HI + (255,))

    turn((1, 3, 14, 11), (1, 3))
    turn((4, 6, 11, 9), None)

    # loose lead leaving the coil at the lower right
    for x, y in ((13, 9), (14, 10), (14, 11), (15, 12), (15, 13), (15, 14), (15, 15)):
        draw.point((x, y), fill=WIRE_MID + (255,))
        if y + 1 < 16:
            draw.point((x, y + 1), fill=WIRE_LOW + (255,))
    draw.point((15, 12), fill=WIRE_HI + (255,))
    draw.point((13, 9), fill=WIRE_HI + (255,))
    # cut end catching the light
    draw.point((0, 3), fill=WIRE_HI + (255,))
    draw.point((0, 4), fill=WIRE_DARK + (255,))
    draw.point((15, 15), fill=WIRE_DARK + (255,))
    return img


def save(img: Image.Image, *parts: str) -> None:
    path = os.path.join(ROOT, *parts)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    img.save(path)
    print("wrote", path)


def main() -> None:
    save(cable_texture(), "block", "cable.png")
    save(connector_texture(), "block", "cable_connector.png")
    save(pipe_texture(), "block", "pipe.png")
    save(pipe_flange_texture(), "block", "pipe_flange.png")
    save(copper_wire_texture(), "item", "copper_wire.png")


if __name__ == "__main__":
    main()
