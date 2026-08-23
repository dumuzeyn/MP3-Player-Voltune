from pathlib import Path
from PIL import Image, ImageDraw, ImageFont


ROOT = Path(__file__).resolve().parents[1]
MASTER = ROOT / "app/src/main/res/drawable-nodpi/voltune_icon_master.png"
FOREGROUND = ROOT / "app/src/main/res/drawable-nodpi/voltune_icon_foreground.png"
OUTPUT = ROOT / "docs/brand/voltune-icon-preview.png"
SIZE = 360
BRAND = (9, 2, 24, 255)


def fit(image: Image.Image, inset_ratio: float, background=(0, 0, 0, 0)) -> Image.Image:
    canvas = Image.new("RGBA", (SIZE, SIZE), background)
    inset = round(SIZE * inset_ratio)
    resized = image.resize((SIZE - inset * 2, SIZE - inset * 2), Image.Resampling.LANCZOS)
    canvas.alpha_composite(resized, (inset, inset))
    return canvas


def mask_tile(image: Image.Image, kind: str) -> Image.Image:
    mask = Image.new("L", (SIZE, SIZE), 0)
    draw = ImageDraw.Draw(mask)
    if kind == "circle":
        draw.ellipse((0, 0, SIZE - 1, SIZE - 1), fill=255)
    elif kind == "squircle":
        draw.rounded_rectangle((0, 0, SIZE - 1, SIZE - 1), radius=120, fill=255)
    else:
        draw.rounded_rectangle((0, 0, SIZE - 1, SIZE - 1), radius=72, fill=255)
    result = image.copy()
    result.putalpha(mask)
    return result


def labeled(tile: Image.Image, label: str) -> Image.Image:
    result = Image.new("RGBA", (SIZE, SIZE + 46), (242, 241, 246, 255))
    result.alpha_composite(tile, (0, 0))
    draw = ImageDraw.Draw(result)
    font = ImageFont.load_default(size=18)
    box = draw.textbbox((0, 0), label, font=font)
    draw.text(((SIZE - (box[2] - box[0])) / 2, SIZE + 13), label,
              fill=(27, 20, 38, 255), font=font)
    return result


def main() -> None:
    master = Image.open(MASTER).convert("RGBA")
    foreground = Image.open(FOREGROUND).convert("RGBA")
    if foreground.getbbox() is None:
        raise SystemExit("Foreground is fully transparent")

    adaptive = fit(foreground, 0.18, BRAND)
    alpha_bounds = fit(foreground, 0.18).getchannel("A").getbbox()
    content_ratio = (alpha_bounds[2] - alpha_bounds[0]) / SIZE
    if not 0.50 <= content_ratio <= 0.70:
        raise SystemExit(f"Adaptive content ratio {content_ratio:.3f} is outside 0.50..0.70")

    splash = fit(foreground, 0.17, BRAND)
    header = fit(foreground, 0.12, (20, 17, 28, 255))
    legacy = master.resize((SIZE, SIZE), Image.Resampling.LANCZOS)
    tiles = [
        labeled(mask_tile(adaptive, "circle"), "Launcher: circle"),
        labeled(mask_tile(adaptive, "rounded"), "Launcher: rounded square"),
        labeled(mask_tile(adaptive, "squircle"), "Launcher: squircle"),
        labeled(legacy, "Legacy launcher"),
        labeled(splash, "Android 12+ splash"),
        labeled(header, "Header and theme menu"),
    ]
    sheet = Image.new("RGBA", (SIZE * 3, (SIZE + 46) * 2), (242, 241, 246, 255))
    for index, tile in enumerate(tiles):
        sheet.alpha_composite(tile, ((index % 3) * SIZE, (index // 3) * (SIZE + 46)))
    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    sheet.convert("RGB").save(OUTPUT, quality=95)
    print(f"PASS adaptive content ratio={content_ratio:.3f}; preview={OUTPUT}")


if __name__ == "__main__":
    main()
