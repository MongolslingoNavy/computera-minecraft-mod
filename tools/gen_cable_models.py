"""Generate the multipart cable / pipe block models and their held-item models.

Arm boxes are written out per direction so every arm ends flush with the block
face and the joint ring sits exactly on that face.

Run:  python tools/gen_cable_models.py
"""

from __future__ import annotations

import json
import os

ROOT = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))),
                    "src", "main", "resources", "assets", "computera", "models", "block")

ALL_FACES = ("north", "south", "east", "west", "up", "down")

# direction -> (body box, joint box); boxes are x0,y0,z0,x1,y1,z1
CABLE_ARMS = {
    "north": ((5, 5, 0, 11, 11, 6), None),
    "south": ((5, 5, 10, 11, 11, 16), None),
    "west": ((0, 5, 5, 6, 11, 11), None),
    "east": ((10, 5, 5, 16, 11, 11), None),
    "down": ((5, 0, 5, 11, 6, 11), None),
    "up": ((5, 10, 5, 11, 16, 11), None),
}

PIPE_ARMS = {
    "north": ((4, 4, 0, 12, 12, 5), None),
    "south": ((4, 4, 11, 12, 12, 16), None),
    "west": ((0, 4, 4, 5, 12, 12), None),
    "east": ((11, 4, 4, 16, 12, 12), None),
    "down": ((4, 0, 4, 12, 5, 12), None),
    "up": ((4, 11, 4, 12, 16, 12), None),
}

# held-item models show a straight horizontal segment along X
CABLE_ITEM = ((5, 5, 5, 11, 11, 11), (3, 3, 3, 13, 13, 13))
PIPE_ITEM = ((4, 4, 4, 12, 12, 12), (2, 2, 2, 14, 14, 14))


def element(box, texture: str, faces=ALL_FACES) -> dict:
    x0, y0, z0, x1, y1, z1 = box
    return {
        "from": [x0, y0, z0],
        "to": [x1, y1, z1],
        "faces": {face: {"texture": texture} for face in faces},
    }


def save(name: str, data: dict) -> None:
    path = os.path.join(ROOT, name)
    with open(path, "w", encoding="utf-8", newline="\n") as handle:
        json.dump(data, handle, indent=2)
        handle.write("\n")
    print("wrote", path)


def cord_models(prefix: str, body_texture: str, joint_texture: str,
                arms: dict, item: tuple, item_model: str) -> None:
    textures = {"all": f"computera:block/{body_texture}",
                "joint": f"computera:block/{joint_texture}",
                "particle": f"computera:block/{body_texture}"}
    core, _ = item
    save(f"{prefix}_core.json", {
        "textures": {"all": textures["all"], "particle": textures["particle"]},
        "elements": [element(core, "#all")],
    })

    for direction, (body, joint) in arms.items():
        elements = [element(body, "#all")]
        if joint is not None:
            elements.append(element(joint, "#joint"))
        save(f"{prefix}_arm_{direction}.json", {
            "textures": textures,
            "elements": elements,
        })

    # straight segment: core + east/west arms + the two joints that close them
    straight = [element(core, "#all"),
                element(arms["east"][0], "#all"),
                element(arms["west"][0], "#all")]
    save(f"{item_model}.json", {"textures": textures, "elements": straight})


def main() -> None:
    cord_models("cable", "cable", "cable_connector", CABLE_ARMS, CABLE_ITEM, "cable")
    cord_models("pipe", "pipe", "pipe_flange", PIPE_ARMS, PIPE_ITEM, "logistics_pipe")


if __name__ == "__main__":
    main()
