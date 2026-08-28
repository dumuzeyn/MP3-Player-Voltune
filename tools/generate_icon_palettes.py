from pathlib import Path
from xml.etree import ElementTree

from PIL import Image, ImageChops, ImageDraw


ROOT = Path(__file__).resolve().parents[1]
DRAWABLES = ROOT / "app/src/main/res/drawable-nodpi"
PREVIEW = ROOT / "docs/brand/voltune-icon-palettes.png"
SOURCE = DRAWABLES / "voltune_icon_foreground.png"
COLORS = ROOT / "app/src/main/res/values/colors.xml"
PALETTES = {
    "blue": ((52, 120, 246), (64, 215, 255)),
    "red": ((255, 77, 103), (255, 178, 62)),
    "green": ((37, 184, 107), (200, 240, 75)),
    "pink": ((233, 75, 170), (54, 216, 208)),
    "orange": ((255, 53, 69), (208, 232, 255)),
}


def resource_colors() -> dict[str, tuple[int, int, int]]:
    colors = {}
    for item in ElementTree.parse(COLORS).getroot().findall("color"):
        value = (item.text or "").strip()
        if value.startswith("#") and len(value) == 7:
            colors[item.attrib["name"]] = tuple(
                int(value[index:index + 2], 16) for index in (1, 3, 5))
    return colors


def horizontal_gradient(size: tuple[int, int], first: tuple[int, int, int],
                        second: tuple[int, int, int]) -> Image.Image:
    width, height = size
    strip = Image.new("RGB", (width, 1))
    pixels = strip.load()
    for x in range(width):
        amount = x / max(1, width - 1)
        pixels[x, 0] = tuple(round(a + (b - a) * amount) for a, b in zip(first, second))
    return strip.resize((width, height))


def recolor(source: Image.Image, first: tuple[int, int, int],
            second: tuple[int, int, int]) -> Image.Image:
    alpha = source.getchannel("A")
    luminance = source.convert("L")
    shading = luminance.point(lambda value: min(255, round(184 + value * 0.48)))
    shaded = ImageChops.multiply(horizontal_gradient(source.size, first, second),
                                 Image.merge("RGB", (shading, shading, shading)))
    highlights = luminance.point(lambda value: max(0, min(150, (value - 205) * 3)))
    result = Image.composite(Image.new("RGB", source.size, "white"), shaded, highlights)
    result.putalpha(alpha)
    return result


def legacy_tile(foreground: Image.Image, background: tuple[int, int, int]) -> Image.Image:
    size = foreground.width
    inset = round(size * 0.12)
    logo = foreground.resize((size - inset * 2, size - inset * 2), Image.Resampling.LANCZOS)
    tile = Image.new("RGBA", foreground.size, (*background, 255))
    tile.alpha_composite(logo, (inset, inset))
    mask = Image.new("L", foreground.size, 0)
    ImageDraw.Draw(mask).rounded_rectangle((0, 0, size - 1, size - 1),
                                           radius=round(size * 0.18), fill=255)
    tile.putalpha(mask)
    return tile


def main() -> None:
    source = Image.open(SOURCE).convert("RGBA")
    colors = resource_colors()
    for mode in ("light", "dark"):
        legacy_tile(source, colors[f"voltune_background_{mode}"]).save(
            DRAWABLES / f"voltune_icon_legacy_{mode}.png", optimize=True)
    previews = []
    for name, (primary, secondary) in PALETTES.items():
        foreground = recolor(source, primary, secondary)
        foreground.save(DRAWABLES / f"voltune_icon_foreground_{name}.png", optimize=True)
        for mode in ("light", "dark"):
            background = colors[f"launcher_icon_{name}_{mode}_bg"]
            tile = legacy_tile(foreground, background)
            tile.save(DRAWABLES / f"voltune_icon_legacy_{name}_{mode}.png", optimize=True)
            previews.append(tile.resize((256, 256), Image.Resampling.LANCZOS))

    sheet = Image.new("RGBA", (256 * 5, 256 * 2), (238, 237, 242, 255))
    for index, preview in enumerate(previews):
        sheet.alpha_composite(preview, ((index // 2) * 256, (index % 2) * 256))
    PREVIEW.parent.mkdir(parents=True, exist_ok=True)
    sheet.convert("RGB").save(PREVIEW, quality=95)
    print(f"Generated {len(PALETTES)} foregrounds and {len(previews) + 2} legacy icons")


if __name__ == "__main__":
    main()
