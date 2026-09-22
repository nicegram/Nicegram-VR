#!/usr/bin/env python3
"""Render the store assets from the app's own vector mark.

Why a script and not a folder of files somebody exported once: every asset here is derived from
`TMessagesProj_AppQuest/src/main/res/drawable/nicegram_mark.xml`, which is what the app actually
draws. Re-run this and the store art and the running app cannot drift apart. The blank-white mark
that shipped for three days (finding A-34) is exactly what a hand-exported folder lets happen.

Android `pathData` is SVG path syntax, so the vector is rendered directly rather than upscaled
from a 192px launcher icon.

    python3 store/make-assets.py

Outputs into store/assets/. Sizes are the common Meta Horizon Store shapes; **verify the current
requirements before uploading** — asset specs change, and a stale spec is a rejection.
"""

import pathlib
import re
import sys

try:
    import cairosvg
except ImportError:  # pragma: no cover - a clear message beats a traceback
    sys.exit("cairosvg is required: pip install cairosvg")

from PIL import Image, ImageDraw, ImageFont

ROOT = pathlib.Path(__file__).resolve().parent.parent
VECTOR = ROOT / "TMessagesProj_AppQuest/src/main/res/drawable/nicegram_mark.xml"
OUT = ROOT / "store/assets"

# The brand's own colours, the same two VrTheme installs as the accent and the outgoing bubble.
VIOLET = (0x8B, 0x5F, 0xF2)
VIOLET_DEEP = (0x6F, 0x4B, 0xD8)
BLACK = (0x00, 0x00, 0x00)


def glyph_paths() -> list[str]:
    """The white paths of the mark, in the vector's own 600x600 viewport."""
    xml = VECTOR.read_text(encoding="utf-8")
    paths = re.findall(r'android:pathData="([^"]+)"', xml, re.S)
    if not paths:
        sys.exit(f"no paths in {VECTOR} — has the mark moved?")
    return [p.strip() for p in paths]


def render_glyph(size: int, colour: str = "#FFFFFF") -> Image.Image:
    """The mark alone, on transparency, at any size — rendered, never upscaled."""
    # The vector shifts a 1024 viewport onto the glyph's bounds with translate(-212,-212).
    body = "\n".join(f'<path d="{d}" fill="{colour}"/>' for d in glyph_paths())
    svg = (
        f'<svg xmlns="http://www.w3.org/2000/svg" width="{size}" height="{size}" '
        f'viewBox="0 0 600 600"><g transform="translate(-212,-212)">{body}</g></svg>'
    )
    png = cairosvg.svg2png(bytestring=svg.encode(), output_width=size, output_height=size)
    return Image.open(__import__("io").BytesIO(png)).convert("RGBA")


def rounded(size: int, radius_ratio: float, colour: tuple) -> Image.Image:
    """A rounded square, the launcher icon's shape."""
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    ImageDraw.Draw(img).rounded_rectangle(
        [(0, 0), (size - 1, size - 1)], radius=int(size * radius_ratio), fill=colour + (255,)
    )
    return img


def vertical_gradient(w: int, h: int, top: tuple, bottom: tuple) -> Image.Image:
    img = Image.new("RGB", (w, h))
    draw = ImageDraw.Draw(img)
    for y in range(h):
        t = y / max(h - 1, 1)
        draw.line(
            [(0, y), (w, y)],
            fill=tuple(int(top[i] + (bottom[i] - top[i]) * t) for i in range(3)),
        )
    return img.convert("RGBA")


def font(size: int, bold: bool = True) -> ImageFont.FreeTypeFont:
    """A system face, chosen by what exists rather than by name alone."""
    candidates = [
        "/System/Library/Fonts/SFNSDisplay.ttf",
        "/System/Library/Fonts/Helvetica.ttc",
        "/System/Library/Fonts/Supplemental/Arial Bold.ttf" if bold else
        "/System/Library/Fonts/Supplemental/Arial.ttf",
        "/Library/Fonts/Arial.ttf",
    ]
    for path in candidates:
        if pathlib.Path(path).is_file():
            try:
                return ImageFont.truetype(path, size)
            except OSError:
                continue
    return ImageFont.load_default()


def centred(draw: ImageDraw.ImageDraw, text: str, f, y: int, w: int, fill) -> int:
    box = draw.textbbox((0, 0), text, font=f)
    draw.text(((w - (box[2] - box[0])) / 2 - box[0], y), text, font=f, fill=fill)
    return box[3] - box[1]


def main() -> None:
    OUT.mkdir(parents=True, exist_ok=True)
    made = []

    # 1. The logo on transparency. The store's logo asset requires it, and a white glyph is what
    #    survives being placed on whatever background the store puts behind it.
    logo = render_glyph(1024)
    logo.save(OUT / "logo-transparent-1024.png")
    made.append("logo-transparent-1024.png")

    # 2. The app icon: the launcher's own shape and colours, at store resolution.
    for size in (512, 1024):
        icon = rounded(size, 0.22, BLACK)
        mark = render_glyph(int(size * 0.52))
        icon.alpha_composite(mark, ((size - mark.width) // 2, (size - mark.height) // 2))
        icon.save(OUT / f"icon-{size}.png")
        made.append(f"icon-{size}.png")

    # 3. Cover art. The hard rule: NO TEXT in the top or bottom 20%, because the cover is cropped
    #    differently in different placements. Everything below is laid out inside the middle 60%.
    w, h = 2560, 1440
    cover = vertical_gradient(w, h, VIOLET, VIOLET_DEEP)
    mark = render_glyph(420)
    safe_top, safe_bottom = int(h * 0.20), int(h * 0.80)
    block_h = mark.height + 210
    y = safe_top + (safe_bottom - safe_top - block_h) // 2
    cover.alpha_composite(mark, ((w - mark.width) // 2, y))
    draw = ImageDraw.Draw(cover)
    ty = y + mark.height + 40
    centred(draw, "Nicegram VR", font(132), ty, w, (255, 255, 255, 255))
    centred(draw, "Telegram, quiet, in your headset", font(58, bold=False),
            ty + 170, w, (255, 255, 255, 220))
    cover.convert("RGB").save(OUT / "cover-2560x1440.png", quality=95)
    made.append("cover-2560x1440.png")

    # A square crop of the same, for placements that want one.
    square = vertical_gradient(1440, 1440, VIOLET, VIOLET_DEEP)
    m2 = render_glyph(520)
    square.alpha_composite(m2, ((1440 - m2.width) // 2, 380))
    d2 = ImageDraw.Draw(square)
    centred(d2, "Nicegram VR", font(120), 960, 1440, (255, 255, 255, 255))
    square.convert("RGB").save(OUT / "cover-square-1440.png", quality=95)
    made.append("cover-square-1440.png")

    print(f"wrote {len(made)} files into {OUT.relative_to(ROOT)}:")
    for name in made:
        size = (OUT / name).stat().st_size
        with Image.open(OUT / name) as im:
            print(f"  {name:<30} {im.size[0]}x{im.size[1]:<6} {size // 1024} KB")


if __name__ == "__main__":
    main()
