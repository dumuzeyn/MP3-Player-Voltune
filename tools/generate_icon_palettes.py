from pathlib import Path
from xml.etree import ElementTree

from PIL import Image, ImageChops, ImageDraw


ROOT = Path(__file__).resolve().parents[1]
DRAWABLES = ROOT / "app/src/main/res/drawable-nodpi"
PREVIEW = ROOT / "docs/brand/voltune-icon-palettes.png"
SOURCE = DRAWABLES / "voltune_icon_foreground.png"
COLORS = ROOT / "app/src/main/res/values/colors.xml"
MANIFEST = ROOT / "app/src/main/AndroidManifest.xml"
STYLES = ROOT / "app/src/main/res/values/styles.xml"
STYLES_V31 = ROOT / "app/src/main/res/values-v31/styles.xml"
ACTIVITIES = ROOT / "app/src/main/java/com/dumuzeyn/mp3player/LauncherThemeActivities.java"
PALETTES = ("blue", "red", "green", "pink", "orange")


def resource_colors() -> dict[str, tuple[int, int, int]]:
    colors = {}
    for item in ElementTree.parse(COLORS).getroot().findall("color"):
        value = (item.text or "").strip()
        if value.startswith("#") and len(value) == 7:
            colors[item.attrib["name"]] = tuple(
                int(value[index:index + 2], 16) for index in (1, 3, 5))
    return colors


def title(value: str) -> str:
    return value[0].upper() + value[1:]


def component_suffix(background: str, mode: str, foreground: str) -> str:
    base = f"Custom{title(background)}{title(mode)}"
    return base if background == foreground else f"{base}Foreground{title(foreground)}"


def icon_name(background: str, mode: str, foreground: str) -> str:
    base = f"ic_launcher_custom_{background}_{mode}"
    return base if background == foreground else f"{base}_foreground_{foreground}"


def legacy_name(background: str, mode: str, foreground: str) -> str:
    base = f"voltune_icon_legacy_{background}_{mode}"
    return base if background == foreground else f"{base}_foreground_{foreground}"


def replace_generated_block(path: Path, start: str, end: str, content: str) -> None:
    text = path.read_text(encoding="utf-8")
    if text.count(start) != 1 or text.count(end) != 1:
        raise RuntimeError(f"Generated block markers are invalid in {path}")
    before, remainder = text.split(start)
    _, after = remainder.split(end)
    path.write_text(f"{before}{start}\n{content}\n{end}{after}", encoding="utf-8")


def adaptive_xml(background: str, mode: str, foreground: str,
                 monochrome: bool) -> str:
    monochrome_line = (
        '\n    <monochrome android:drawable="@drawable/voltune_icon_monochrome" />'
        if monochrome else ""
    )
    return (
        '<?xml version="1.0" encoding="utf-8"?>\n'
        '<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">\n'
        f'    <background android:drawable="@color/launcher_icon_{background}_{mode}_bg" />\n'
        f'    <foreground android:drawable="@drawable/voltune_icon_foreground_{foreground}_layer" />'
        f'{monochrome_line}\n</adaptive-icon>\n'
    )


def generate_component_blocks() -> None:
    activity_lines = []
    alias_lines = []
    java_lines = []
    fallback_styles = []
    splash_styles = []
    for background in PALETTES:
        for mode in ("light", "dark"):
            for foreground in PALETTES:
                if background == foreground:
                    continue
                suffix = component_suffix(background, mode, foreground)
                icon = icon_name(background, mode, foreground)
                style = f"AppTheme{suffix}"
                parent = "AppTheme" if mode == "light" else "AppTheme.Dark"
                color = f"launcher_icon_{background}_{mode}_bg"
                activity_lines.extend([
                    "        <activity",
                    f'            android:name=".LauncherThemeActivities${suffix}"',
                    '            android:configChanges="keyboard|keyboardHidden|orientation|screenSize"',
                    '            android:exported="false"',
                    f'            android:theme="@style/{style}" />',
                ])
                alias_lines.extend([
                    "        <activity-alias",
                    f'            android:name=".Launcher{suffix}"',
                    '            android:enabled="false"',
                    '            android:exported="true"',
                    f'            android:icon="@mipmap/{icon}"',
                    '            android:label="@string/app_name"',
                    f'            android:theme="@style/{style}"',
                    f'            android:targetActivity=".LauncherThemeActivities${suffix}">',
                    "            <intent-filter>",
                    '                <action android:name="android.intent.action.MAIN" />',
                    '                <category android:name="android.intent.category.LAUNCHER" />',
                    "            </intent-filter>",
                    "        </activity-alias>",
                ])
                java_lines.extend([
                    f"    public static final class {suffix} extends MainActivity {{",
                    "    }",
                    "",
                ])
                fallback_styles.extend([
                    f'    <style name="{style}" parent="{parent}">',
                    f'        <item name="android:statusBarColor">@color/{color}</item>',
                    f'        <item name="android:navigationBarColor">@color/{color}</item>',
                    f'        <item name="android:windowBackground">@color/{color}</item>',
                    "    </style>",
                ])
                splash_styles.extend([
                    f'    <style name="{style}" parent="{parent}">',
                    f'        <item name="android:statusBarColor">@color/{color}</item>',
                    f'        <item name="android:navigationBarColor">@color/{color}</item>',
                    f'        <item name="android:windowBackground">@color/{color}</item>',
                    f'        <item name="android:windowSplashScreenBackground">@color/{color}</item>',
                    f'        <item name="android:windowSplashScreenAnimatedIcon">@drawable/voltune_icon_splash_{foreground}</item>',
                    "    </style>",
                ])
    replace_generated_block(MANIFEST, "<!-- GENERATED CUSTOM FOREGROUND ACTIVITIES START -->",
                            "<!-- GENERATED CUSTOM FOREGROUND ACTIVITIES END -->",
                            "\n".join(activity_lines))
    replace_generated_block(MANIFEST, "<!-- GENERATED CUSTOM FOREGROUND ALIASES START -->",
                            "<!-- GENERATED CUSTOM FOREGROUND ALIASES END -->",
                            "\n".join(alias_lines))
    replace_generated_block(ACTIVITIES, "// GENERATED CUSTOM FOREGROUND ACTIVITIES START",
                            "// GENERATED CUSTOM FOREGROUND ACTIVITIES END",
                            "\n".join(java_lines).rstrip())
    replace_generated_block(STYLES, "<!-- GENERATED CUSTOM FOREGROUND STYLES START -->",
                            "<!-- GENERATED CUSTOM FOREGROUND STYLES END -->",
                            "\n".join(fallback_styles))
    replace_generated_block(STYLES_V31, "<!-- GENERATED CUSTOM FOREGROUND STYLES START -->",
                            "<!-- GENERATED CUSTOM FOREGROUND STYLES END -->",
                            "\n".join(splash_styles))


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
    foregrounds = {}
    for name in PALETTES:
        primary = colors[f"launcher_foreground_{name}_primary"]
        secondary = colors[f"launcher_foreground_{name}_secondary"]
        foreground = recolor(source, primary, secondary)
        foregrounds[name] = foreground
        foreground.save(DRAWABLES / f"voltune_icon_foreground_{name}.png", optimize=True)
    generated_icons = 0
    for background_name in PALETTES:
        for mode in ("light", "dark"):
            background = colors[f"launcher_icon_{background_name}_{mode}_bg"]
            for foreground_name, foreground in foregrounds.items():
                legacy = legacy_name(background_name, mode, foreground_name)
                tile = legacy_tile(foreground, background)
                tile.save(DRAWABLES / f"{legacy}.png", optimize=True)
                icon = icon_name(background_name, mode, foreground_name)
                (ROOT / f"app/src/main/res/mipmap-anydpi/{icon}.xml").write_text(
                    '<?xml version="1.0" encoding="utf-8"?>\n'
                    '<bitmap xmlns:android="http://schemas.android.com/apk/res/android"\n'
                    '    android:gravity="fill"\n'
                    f'    android:src="@drawable/{legacy}" />\n', encoding="utf-8")
                (ROOT / f"app/src/main/res/mipmap-anydpi-v26/{icon}.xml").write_text(
                    adaptive_xml(background_name, mode, foreground_name, False),
                    encoding="utf-8")
                (ROOT / f"app/src/main/res/mipmap-anydpi-v33/{icon}.xml").write_text(
                    adaptive_xml(background_name, mode, foreground_name, True),
                    encoding="utf-8")
                generated_icons += 1
                if background_name == foreground_name:
                    previews.append(tile.resize((256, 256), Image.Resampling.LANCZOS))

    sheet = Image.new("RGBA", (256 * 5, 256 * 2), (238, 237, 242, 255))
    for index, preview in enumerate(previews):
        sheet.alpha_composite(preview, ((index // 2) * 256, (index % 2) * 256))
    PREVIEW.parent.mkdir(parents=True, exist_ok=True)
    sheet.convert("RGB").save(PREVIEW, quality=95)
    generate_component_blocks()
    print(f"Generated {len(PALETTES)} foregrounds and {generated_icons + 2} legacy icons")


if __name__ == "__main__":
    main()
