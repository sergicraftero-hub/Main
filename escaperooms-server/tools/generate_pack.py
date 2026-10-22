#!/usr/bin/env python3
"""
Genera el paquete de texturas del servidor en ../resourcepack (Java 1.21.4+).

  python3 tools/generate_pack.py

- Fondos pintados para los menús (fuente escape:gui, glifos U+E100...) por tema y número de filas.
- Iconos 16x16 para cada objeto con "model: escape:<nombre>" en las salas y menús. Se aplican con
  custom_model_data sobre el objeto vanilla de "material:", así quien no tenga el pack ve el objeto normal
  (nunca una textura rota). El número sale del nombre del modelo (igual que ItemSpec.modelData en Java).
- Sombreros 3D (chistera, gorra de detective, corona) con texturas vanilla.

Todo se dibuja por código: para cambiar el arte, sustituye los PNG de resourcepack/ por los tuyos
con el mismo nombre (o edita este script y vuelve a ejecutarlo). Bedrock (Geyser) no usa este pack:
allí se ven los objetos vanilla de "material:".
"""
import glob
import json
import math
import os
import shutil

import yaml
from PIL import Image, ImageDraw

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "resourcepack")
NS = "escape"

THEMES = ["main", "rooms", "room", "shop", "profile", "keypad", "dial", "notebook", "generic"]
# (title band, body, border, slot, slot shadow)
PALETTES = {
    "main": ("#F1DFA8", "#4A3520", "#C9A227", "#2E2114", "#1E150C"),
    "rooms": ("#F3C9C3", "#3A1A1F", "#B8323C", "#24100F", "#160909"),
    "room": ("#D6D3FF", "#25234A", "#6C63FF", "#17162F", "#0E0D1F"),
    "shop": ("#EBD3FF", "#33193F", "#B86BFF", "#200F29", "#15091B"),
    "profile": ("#C8F1F3", "#13343A", "#3FC1C9", "#0C2125", "#071518"),
    "keypad": ("#DDE3E8", "#2A3138", "#8A9BA8", "#1B2025", "#111417"),
    "dial": ("#F0D9BC", "#3A2A1A", "#CD7F32", "#261B10", "#18110A"),
    "notebook": ("#F7EED4", "#E8D9B0", "#6B4F2A", "#CDB98A", "#A99568"),
    "generic": ("#DCE1EA", "#232833", "#5A6475", "#171A21", "#0E1015"),
}


def hexc(h, a=255):
    h = h.lstrip("#")
    return (int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16), a)


def mix(c1, c2, t):
    return tuple(int(c1[i] + (c2[i] - c1[i]) * t) for i in range(4))


def write_json(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        json.dump(data, f, indent=2, ensure_ascii=False)


# ───────────────────────── menu backgrounds ─────────────────────────

def menu_background(theme, rows):
    band, body, border, slot, shadow = (hexc(c) for c in PALETTES[theme])
    w = 176
    h = 17 + rows * 18 + 4
    im = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    # body with a soft vertical gradient
    for y in range(h):
        c = mix(body, mix(body, (0, 0, 0, 255), 0.25), y / h)
        d.line([(0, y), (w - 1, y)], fill=c)
    # title band
    d.rectangle([3, 3, w - 4, 14], fill=band)
    d.line([(3, 15), (w - 4, 15)], fill=mix(band, body, 0.6))
    # border (2 px, rounded corners)
    for i in range(2):
        d.rectangle([i, i, w - 1 - i, h - 1 - i], outline=border if i == 0 else mix(border, body, 0.4))
    for (x, y) in [(0, 0), (w - 1, 0), (0, h - 1), (w - 1, h - 1)]:
        im.putpixel((x, y), (0, 0, 0, 0))
    # corner ornaments
    for (x, y) in [(4, 4), (w - 7, 4)]:
        d.rectangle([x, y, x + 2, y + 2], fill=border)
    # slots exactly where the chest slots are (7 + 18c, 17 + 18r)
    for r in range(rows):
        for c in range(9):
            x = 7 + 18 * c
            y = 17 + 18 * r
            d.rectangle([x, y, x + 17, y + 17], fill=shadow)
            d.rectangle([x + 1, y + 1, x + 17, y + 17], fill=mix(slot, body, 0.2))
            d.rectangle([x + 1, y + 1, x + 16, y + 16], fill=slot)
            d.line([(x + 17, y + 1), (x + 17, y + 17)], fill=mix(border, slot, 0.75))
            d.line([(x + 1, y + 17), (x + 17, y + 17)], fill=mix(border, slot, 0.75))
    return im


def build_font():
    providers = [{
        "type": "space",
        "advances": {
            "": -1, "": -2, "": -4, "": -8,
            "": -16, "": -32, "": -64, "": -128,
            "": 1, "": 2, "": 4, "": 8,
        },
    }]
    for ti, theme in enumerate(THEMES):
        for rows in range(3, 7):
            name = f"{theme}_{rows}"
            menu_background(theme, rows).save(os.path.join(ROOT, f"assets/{NS}/textures/font/gui/{name}.png"))
            code = 0xE100 + ti * 4 + (rows - 3)
            providers.append({
                "type": "bitmap",
                "file": f"{NS}:font/gui/{name}.png",
                "ascent": 13,
                "height": 17 + rows * 18 + 4,
                "chars": [chr(code)],
            })
    write_json(os.path.join(ROOT, f"assets/{NS}/font/gui.json"), {"providers": providers})


# ───────────────────────── 16x16 icons ─────────────────────────

class Sprite:
    def __init__(self):
        self.im = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
        self.d = ImageDraw.Draw(self.im)

    def px(self, x, y, c):
        if 0 <= x < 16 and 0 <= y < 16:
            self.im.putpixel((x, y), hexc(c) if isinstance(c, str) else c)

    def rect(self, x1, y1, x2, y2, c):
        self.d.rectangle([x1, y1, x2, y2], fill=hexc(c))

    def line(self, x1, y1, x2, y2, c, w=1):
        self.d.line([(x1, y1), (x2, y2)], fill=hexc(c), width=w)

    def ellipse(self, x1, y1, x2, y2, c, outline=None):
        self.d.ellipse([x1, y1, x2, y2], fill=hexc(c) if c else None, outline=hexc(outline) if outline else None)

    def poly(self, pts, c):
        self.d.polygon(pts, fill=hexc(c))

    def outline(self, c="#1A1410"):
        """1 px dark outline around everything drawn (crisp Minecraft look)."""
        src = self.im.copy()
        col = hexc(c)
        for y in range(16):
            for x in range(16):
                if src.getpixel((x, y))[3] > 0:
                    continue
                for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                    nx, ny = x + dx, y + dy
                    if 0 <= nx < 16 and 0 <= ny < 16 and src.getpixel((nx, ny))[3] > 0:
                        self.im.putpixel((x, y), col)
                        break
        return self

    def shade(self, light=(-1, -1), amount=0.18):
        """Lighten the top-left edge of shapes and darken the bottom-right."""
        src = self.im.copy()
        for y in range(16):
            for x in range(16):
                p = src.getpixel((x, y))
                if p[3] == 0:
                    continue
                lx, ly = x + light[0], y + light[1]
                bx, by = x - light[0], y - light[1]
                edge_l = not (0 <= lx < 16 and 0 <= ly < 16) or src.getpixel((lx, ly))[3] == 0
                edge_d = not (0 <= bx < 16 and 0 <= by < 16) or src.getpixel((bx, by))[3] == 0
                if edge_l:
                    self.im.putpixel((x, y), mix(p, (255, 255, 255, 255), amount))
                elif edge_d:
                    self.im.putpixel((x, y), mix(p, (0, 0, 0, 255), amount))
        return self


def key(metal, dark):
    s = Sprite()
    s.ellipse(1, 1, 7, 7, metal)
    s.ellipse(3, 3, 5, 5, None)
    s.d.ellipse([3, 3, 5, 5], fill=(0, 0, 0, 0))
    s.line(6, 6, 13, 13, metal, 2)
    s.rect(11, 13, 12, 14, metal)
    s.rect(13, 10, 14, 11, metal)
    s.px(10, 12, dark)
    return s.shade().outline()


def bottle(liquid, label=None):
    s = Sprite()
    s.rect(7, 1, 8, 4, "#8FB7B5")
    s.rect(7, 0, 8, 0, "#6B4A2A")
    s.rect(5, 5, 10, 14, "#BFE3E0")
    s.rect(6, 8, 9, 13, liquid)
    if label:
        s.rect(5, 9, 10, 11, label)
    s.px(6, 6, "#FFFFFF")
    return s.shade().outline()


def gem(color, light):
    s = Sprite()
    s.poly([(8, 2), (13, 6), (11, 13), (5, 13), (3, 6)], color)
    s.poly([(8, 2), (10, 6), (6, 6)], light)
    s.px(6, 8, "#FFFFFF")
    return s.shade(amount=0.25).outline()


def paper(lines=4, color="#F2EEE1", ink="#6B6355"):
    s = Sprite()
    s.rect(3, 1, 12, 14, color)
    for i in range(lines):
        s.line(5, 4 + i * 2, 10, 4 + i * 2, ink)
    return s.shade().outline()


def medallion(color, light, symbol, up):
    s = Sprite()
    s.ellipse(2, 2, 13, 13, color)
    s.ellipse(4, 4, 11, 11, light)
    if up:
        s.poly([(8, 5), (11, 10), (5, 10)], symbol)
    else:
        s.poly([(5, 5), (11, 5), (8, 10)], symbol)
    return s.shade(amount=0.2).outline()


def pile(color, dark):
    s = Sprite()
    s.ellipse(2, 8, 13, 14, color)
    s.ellipse(4, 6, 11, 11, color)
    s.px(6, 8, "#FFFFFF")
    s.px(9, 11, dark)
    s.px(5, 12, dark)
    return s.shade().outline()


SPRITES = {}


def sprite(name):
    def deco(fn):
        SPRITES[name] = fn
        return fn
    return deco


@sprite("key_brass")
def _():
    return key("#D4A645", "#7A5A1E")


@sprite("key_iron")
def _():
    return key("#B8BCC4", "#5D626B")


@sprite("key_gold")
def _():
    return key("#F5CE38", "#9C7A10")


@sprite("uv_lamp")
def _():
    s = Sprite()
    s.rect(6, 0, 9, 1, "#3A3A44")
    s.rect(4, 2, 11, 3, "#2B2B33")
    s.rect(5, 4, 10, 12, "#7B3CFF")
    s.rect(6, 5, 9, 11, "#C7A6FF")
    s.rect(4, 13, 11, 14, "#2B2B33")
    s.line(5, 4, 5, 12, "#2B2B33")
    s.line(10, 4, 10, 12, "#2B2B33")
    return s.shade().outline()


@sprite("weight")
def _():
    s = Sprite()
    s.poly([(3, 14), (12, 14), (10, 6), (5, 6)], "#8C9096")
    s.rect(6, 3, 9, 5, "#5B5F66")
    s.d.rectangle([7, 4, 8, 4], fill=(0, 0, 0, 0))
    return s.shade(amount=0.25).outline()


@sprite("menu_compass")
def _():
    s = Sprite()
    s.ellipse(1, 1, 14, 14, "#C9A227")
    s.ellipse(3, 3, 12, 12, "#F4EBD0")
    s.poly([(8, 3), (10, 8), (8, 7), (6, 8)], "#D13B3B")
    s.poly([(8, 13), (6, 8), (8, 9), (10, 8)], "#3B3B46")
    return s.shade().outline()


@sprite("menu_rooms")
def _():
    s = Sprite()
    s.poly([(1, 3), (5, 1), (10, 3), (14, 1), (14, 12), (10, 14), (5, 12), (1, 14)], "#E9DDB5")
    s.line(5, 1, 5, 12, "#C9B98A")
    s.line(10, 3, 10, 14, "#C9B98A")
    s.line(3, 9, 7, 6, "#B03A2E")
    s.line(7, 6, 12, 8, "#B03A2E")
    s.px(12, 8, "#D13B3B")
    s.px(11, 7, "#D13B3B")
    s.px(12, 7, "#D13B3B")
    return s.shade().outline()


@sprite("menu_daily")
def _():
    s = Sprite()
    s.rect(2, 6, 13, 14, "#D33A4A")
    s.rect(1, 4, 14, 6, "#E8505F")
    s.rect(7, 4, 8, 14, "#F5CE38")
    s.line(4, 1, 7, 4, "#F5CE38")
    s.line(11, 1, 8, 4, "#F5CE38")
    return s.shade().outline()


@sprite("menu_shop")
def _():
    return gem("#2ECC71", "#A8F0C6")


@sprite("menu_party")
def _():
    s = Sprite()
    for ox, col in ((0, "#4A90D9"), (6, "#E0A040")):
        s.ellipse(2 + ox, 2, 7 + ox, 7, "#F1C27D")
        s.rect(1 + ox, 9, 8 + ox, 14, col)
    return s.shade().outline()


@sprite("notebook")
def _():
    s = Sprite()
    s.rect(3, 1, 13, 14, "#7A4A22")
    s.rect(4, 2, 12, 13, "#9B6334")
    s.rect(3, 1, 4, 14, "#4F2E14")
    s.rect(10, 0, 11, 6, "#D13B3B")
    s.rect(6, 5, 10, 6, "#E9DDB5")
    return s.shade().outline()


@sprite("room_tutorial")
def _():
    s = Sprite()
    s.rect(2, 3, 13, 14, "#2E6FD1")
    s.rect(3, 4, 12, 13, "#4C8BEA")
    s.poly([(8, 0), (14, 3), (8, 5), (2, 3)], "#26262E")
    s.line(13, 3, 13, 7, "#F5CE38")
    return s.shade().outline()


@sprite("room_alchemist")
def _():
    s = Sprite()
    s.rect(7, 1, 8, 5, "#BFE3E0")
    s.poly([(6, 5), (9, 5), (13, 13), (2, 13)], "#BFE3E0")
    s.poly([(5, 9), (10, 9), (13, 13), (2, 13)], "#38D16A")
    s.px(6, 11, "#C7FFD9")
    s.px(9, 10, "#C7FFD9")
    return s.shade().outline()


@sprite("room_express")
def _():
    s = Sprite()
    s.rect(2, 4, 13, 12, "#2B2B33")
    s.rect(3, 5, 12, 8, "#F5CE38")
    s.rect(5, 1, 7, 4, "#2B2B33")
    s.ellipse(2, 11, 6, 15, "#B03A2E")
    s.ellipse(9, 11, 13, 15, "#B03A2E")
    s.rect(9, 6, 11, 7, "#FFF5B0")
    return s.shade().outline()


@sprite("wing_cine")
def _():
    s = Sprite()
    s.ellipse(1, 1, 14, 14, "#3A3A44")
    for (x, y) in ((7, 3), (11, 7), (7, 11), (3, 7)):
        s.ellipse(x - 1, y - 1, x + 2, y + 2, "#15151A")
    s.ellipse(6, 6, 9, 9, "#B8BCC4")
    return s.shade().outline()


@sprite("wing_anime")
def _():
    s = Sprite()
    for a in range(5):
        ang = math.radians(a * 72 - 90)
        cx, cy = 8 + math.cos(ang) * 4, 8 + math.sin(ang) * 4
        s.ellipse(int(cx) - 3, int(cy) - 3, int(cx) + 2, int(cy) + 2, "#FFB7D5")
    s.ellipse(6, 6, 9, 9, "#F5CE38")
    return s.shade().outline("#7A2E4E")


@sprite("wing_games")
def _():
    s = Sprite()
    s.rect(1, 5, 14, 11, "#4A4A57")
    s.ellipse(0, 5, 5, 13, "#4A4A57")
    s.ellipse(10, 5, 15, 13, "#4A4A57")
    s.rect(3, 7, 5, 7, "#E0E0E0")
    s.rect(4, 6, 4, 8, "#E0E0E0")
    s.px(11, 6, "#E8505F")
    s.px(12, 8, "#4A90D9")
    return s.shade().outline()


@sprite("wing_originals")
def _():
    s = Sprite()
    s.ellipse(2, 1, 13, 12, "#8A5CF6")
    s.ellipse(4, 3, 8, 7, "#D7C6FF")
    s.rect(3, 12, 12, 14, "#6B4A2A")
    return s.shade().outline()


@sprite("rope")
def _():
    s = Sprite()
    for r, c in ((6, "#C8A26B"), (4, "#A8824B"), (2, "#C8A26B")):
        s.ellipse(8 - r, 8 - r, 7 + r, 7 + r, None, outline=c)
    s.line(13, 9, 15, 15, "#C8A26B")
    return s.outline()


@sprite("hook")
def _():
    s = Sprite()
    s.line(8, 1, 8, 10, "#8C8F96", 2)
    s.d.arc([4, 7, 12, 15], 0, 180, fill=hexc("#8C8F96"), width=2)
    s.px(4, 10, "#8C8F96")
    s.px(4, 9, "#8C8F96")
    return s.shade().outline()


@sprite("grapple")
def _():
    s = Sprite()
    s.line(1, 1, 7, 7, "#C8A26B", 2)
    s.line(7, 7, 12, 12, "#8C8F96", 2)
    s.line(12, 12, 14, 9, "#8C8F96", 2)
    s.line(12, 12, 9, 14, "#8C8F96", 2)
    return s.shade().outline()


@sprite("seal_water")
def _():
    return medallion("#2266AA", "#5FB3F5", "#E8F6FF", False)


@sprite("seal_earth")
def _():
    return medallion("#2E7D32", "#7CCB80", "#F1FFE8", False)


@sprite("seal_air")
def _():
    return medallion("#9AA4B2", "#E9EEF4", "#5A6475", True)


@sprite("violet_crystal")
def _():
    s = Sprite()
    s.poly([(8, 0), (12, 5), (10, 15), (6, 15), (4, 5)], "#9B59F6")
    s.poly([(8, 0), (9, 6), (7, 6)], "#E2CCFF")
    return s.shade(amount=0.25).outline()


@sprite("lens")
def _():
    s = Sprite()
    s.ellipse(2, 2, 13, 13, "#B08A3A")
    s.ellipse(4, 4, 11, 11, "#BDEBFF")
    s.px(5, 5, "#FFFFFF")
    s.px(6, 5, "#FFFFFF")
    return s.outline()


@sprite("tongs")
def _():
    s = Sprite()
    s.line(2, 14, 9, 3, "#5D626B", 2)
    s.line(6, 14, 11, 4, "#8C8F96", 2)
    s.ellipse(4, 11, 7, 14, None, outline="#3A3A44")
    return s.outline()


@sprite("mercury")
def _():
    s = Sprite()
    s.ellipse(4, 6, 12, 14, "#D7DCE3")
    s.poly([(8, 1), (11, 8), (5, 8)], "#D7DCE3")
    s.px(6, 9, "#FFFFFF")
    return s.shade(amount=0.25).outline()


@sprite("sulfur")
def _():
    return pile("#F2D53C", "#B89A10")


@sprite("salt")
def _():
    return pile("#F4F4F4", "#BFC4CA")


@sprite("stone_black")
def _():
    return gem("#2B2B33", "#6E6E7E")


@sprite("stone_white")
def _():
    return gem("#E9EEF4", "#FFFFFF")


@sprite("stone_yellow")
def _():
    return gem("#F2C12E", "#FFF1A8")


@sprite("stone_red")
def _():
    return gem("#C62828", "#FF8A80")


@sprite("ticket")
def _():
    s = Sprite()
    s.rect(1, 4, 14, 11, "#F5D76E")
    s.rect(4, 6, 11, 6, "#8A6D1C")
    s.rect(4, 9, 8, 9, "#8A6D1C")
    for y in (5, 7, 9):
        s.d.point((1, y), fill=(0, 0, 0, 0))
        s.d.point((14, y), fill=(0, 0, 0, 0))
    return s.shade().outline()


@sprite("fragment")
def _():
    s = Sprite()
    s.poly([(3, 2), (12, 1), (13, 9), (9, 14), (4, 12), (2, 7)], "#EFE6CC")
    s.line(5, 5, 10, 4, "#6B6355")
    s.line(4, 8, 10, 7, "#6B6355")
    return s.shade().outline()


@sprite("letter")
def _():
    s = paper(4, "#F2EEE1")
    s.ellipse(9, 10, 13, 14, "#B71C1C")
    return s.outline()


@sprite("document")
def _():
    return paper(5, "#FBF7EC", "#55606B")


@sprite("newspaper")
def _():
    s = Sprite()
    s.rect(2, 2, 13, 13, "#E2E2DA")
    s.rect(3, 3, 12, 4, "#3A3A44")
    for x0 in (3, 8):
        for i in range(4):
            s.line(x0, 6 + i * 2, x0 + 3, 6 + i * 2, "#7A7A86")
    return s.shade().outline()


@sprite("place_card")
def _():
    s = Sprite()
    s.poly([(1, 12), (8, 5), (15, 12)], "#FBF7EC")
    s.line(5, 10, 11, 10, "#C9A227")
    return s.shade().outline()


@sprite("sheet_music")
def _():
    s = paper(0, "#FBF7EC")
    for i in range(4):
        s.line(4, 4 + i * 2, 11, 4 + i * 2, "#8C8F96")
    s.ellipse(5, 8, 7, 10, "#15151A")
    s.line(7, 4, 7, 9, "#15151A")
    s.ellipse(9, 6, 11, 8, "#15151A")
    s.line(11, 2, 11, 7, "#15151A")
    return s.outline()


@sprite("bottle_gin")
def _():
    return bottle("#E8F6FF", "#2E6FD1")


@sprite("bottle_vermouth")
def _():
    return bottle("#A0182B", "#F5D76E")


@sprite("bottle_bitters")
def _():
    return bottle("#6B3410", "#F2EEE1")


@sprite("envelope")
def _():
    s = Sprite()
    s.rect(1, 4, 14, 12, "#EFE6CC")
    s.line(1, 4, 8, 9, "#B9AE8E")
    s.line(14, 4, 8, 9, "#B9AE8E")
    s.rect(11, 5, 13, 7, "#C62828")
    return s.shade().outline()


@sprite("scalpel")
def _():
    s = Sprite()
    s.line(2, 14, 8, 8, "#4A4A57", 2)
    s.poly([(8, 8), (14, 1), (12, 7), (10, 9)], "#D7DCE3")
    s.px(12, 5, "#B71C1C")
    s.px(11, 7, "#B71C1C")
    return s.shade().outline()


# ───────────────────────── 3D hats ─────────────────────────

def cube(frm, to, tex):
    return {"from": frm, "to": to, "faces": {f: {"texture": "#t", "uv": [0, 0, 16, 16]} for f in
                                              ("north", "south", "east", "west", "up", "down")}}


HAT_DISPLAY = {"head": {"translation": [0, 6.6, 0], "scale": [1.0, 1.0, 1.0]},
               "gui": {"rotation": [30, 225, 0], "scale": [0.625, 0.625, 0.625]},
               "ground": {"translation": [0, 3, 0], "scale": [0.5, 0.5, 0.5]}}

HATS = {
    "hat_top": ("minecraft:block/black_wool", [cube([2, 0, 2], [14, 1, 14], "#t"), cube([4, 1, 4], [12, 9, 12], "#t"),
                                                cube([3.9, 1.5, 3.9], [12.1, 2.5, 12.1], "#t")]),
    "hat_detective": ("minecraft:block/brown_wool", [cube([3, 0, 3], [13, 4, 13], "#t"), cube([3, 0, 1], [13, 1, 3], "#t"),
                                                      cube([3, 0, 13], [13, 1, 15], "#t")]),
    "hat_crown": ("minecraft:block/gold_block", [cube([3, 0, 3], [13, 2, 13], "#t")] + [
        cube([x, 2, z], [x + 2, 5, z + 2], "#t") for (x, z) in ((3, 3), (11, 3), (3, 11), (11, 11), (7, 3), (7, 11))]),
}


# ───────────────────────── pack ─────────────────────────

RESOURCES = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "src", "main", "resources")

# vanilla items whose own definition is not a plain model (compass needle, map tint, trims, heads...):
# a custom model on them would break their vanilla look, so the generator refuses them
COMPLEX = {"compass", "recovery_compass", "clock", "filled_map", "crossbow", "bow", "shield", "player_head",
           "decorated_pot", "potion", "splash_potion", "lingering_potion", "tipped_arrow", "bundle", "fishing_rod",
           "elytra", "trident", "spyglass", "firework_star", "light", "goat_horn"}
COMPLEX_SUFFIX = ("_helmet", "_chestplate", "_leggings", "_boots", "_banner", "_spawn_egg", "_head", "_skull",
                  "_bed", "_shulker_box", "chest")
# block items whose vanilla item model lives in block/ instead of item/
BLOCK_MODEL_SUFFIX = ("_carpet", "_wool", "_concrete", "_planks", "_log", "_terracotta", "_glass")
BLOCK_MODEL = {"target", "crafter", "bookshelf", "stone", "dirt", "glowstone", "sea_lantern"}


def java_hash(text):
    h = 0
    for ch in text:
        h = (31 * h + ord(ch)) & 0xFFFFFFFF
    return h - 2 ** 32 if h >= 2 ** 31 else h


def model_data(model):
    key = model.lower() if ":" in model else NS + ":" + model.lower()
    h = java_hash(key)
    return abs(int(math.fmod(h, 9_000_000))) + 1_000_000


def collect_items(node, out):
    """(material, model) pairs anywhere in a YAML tree."""
    if isinstance(node, dict):
        if "model" in node and "material" in node:
            out.add((str(node["material"]).lower().replace("minecraft:", ""), str(node["model"])))
        for v in node.values():
            collect_items(v, out)
    elif isinstance(node, list):
        for v in node:
            collect_items(v, out)


def vanilla_model(material):
    if material in BLOCK_MODEL or material.endswith(BLOCK_MODEL_SUFFIX):
        return f"minecraft:block/{material}"
    return f"minecraft:item/{material}"


def build_overrides():
    pairs = set()
    files = glob.glob(os.path.join(RESOURCES, "rooms", "*.yml"))
    files += [os.path.join(RESOURCES, f) for f in ("menus.yml", "cosmetics.yml", "config.yml")]
    for f in files:
        with open(f, encoding="utf-8") as fh:
            collect_items(yaml.safe_load(fh), pairs)
    by_material = {}
    seen = {}
    for material, model in sorted(pairs):
        name = model.split(":", 1)[1]
        if material in COMPLEX or material.endswith(COMPLEX_SUFFIX):
            raise SystemExit(f"model {model} on '{material}': pick a simpler material (its vanilla item is special)")
        if name not in SPRITES and name not in HATS:
            raise SystemExit(f"model {model} is used but has no sprite in generate_pack.py")
        cmd = model_data(model)
        if cmd in seen and seen[cmd] != model:
            raise SystemExit(f"custom model data clash: {model} and {seen[cmd]}")
        seen[cmd] = model
        by_material.setdefault(material, {})[cmd] = name
    for material, entries in by_material.items():
        write_json(os.path.join(ROOT, f"assets/minecraft/items/{material}.json"), {"model": {
            "type": "minecraft:range_dispatch",
            "property": "minecraft:custom_model_data",
            "index": 0,
            "entries": [{"threshold": cmd, "model": {"type": "minecraft:model", "model": f"{NS}:item/{name}"}}
                        for cmd, name in sorted(entries.items())],
            "fallback": {"type": "minecraft:model", "model": vanilla_model(material)},
        }})
    return sum(len(e) for e in by_material.values()), len(by_material)


def main():
    if os.path.isdir(ROOT):
        shutil.rmtree(ROOT)
    os.makedirs(os.path.join(ROOT, f"assets/{NS}/textures/font/gui"))
    os.makedirs(os.path.join(ROOT, f"assets/{NS}/textures/item"))
    write_json(os.path.join(ROOT, "pack.mcmeta"), {"pack": {
        "pack_format": 46,
        "supported_formats": [46, 99],
        "description": "EscapeVerse · salas de escape"}})
    icon = SPRITES["room_alchemist"]().im.resize((64, 64), Image.NEAREST)
    icon.save(os.path.join(ROOT, "pack.png"))
    build_font()
    for name, fn in sorted(SPRITES.items()):
        fn().im.save(os.path.join(ROOT, f"assets/{NS}/textures/item/{name}.png"))
        write_json(os.path.join(ROOT, f"assets/{NS}/models/item/{name}.json"),
                   {"parent": "minecraft:item/generated", "textures": {"layer0": f"{NS}:item/{name}"}})
    for name, (texture, elements) in HATS.items():
        write_json(os.path.join(ROOT, f"assets/{NS}/models/item/{name}.json"),
                   {"textures": {"t": texture, "particle": texture}, "elements": elements, "display": HAT_DISPLAY})
    used, materials = build_overrides()
    print(f"Pack generated: {len(SPRITES)} icons, {len(HATS)} hats, {len(THEMES) * 4} menu backgrounds, "
          f"{used} custom models on {materials} vanilla items -> {os.path.normpath(ROOT)}")


if __name__ == "__main__":
    main()
