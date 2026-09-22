import json
import re
import time
import urllib.parse
import urllib.request
from concurrent.futures import ThreadPoolExecutor, as_completed
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "app/src/main/java/com/dumuzeyn/mp3player"
OUTPUT = SOURCE / "localization"
LANGUAGES = {
    "es": "Es",
    "pt": "PtBr",
    "zh-CN": "ZhCn",
    "de": "De",
    "fr": "Fr",
    "hi": "Hi",
    "id": "Id",
    "ja": "Ja",
    "ko": "Ko",
    "ar": "Ar",
}
APP_CODES = {"pt": "pt-BR"}
SEPARATOR = "ZXQVVLTSEPZXQ"
PATTERN = re.compile(r'\btr\(\s*"((?:\\.|[^"\\])*)"\s*,', re.DOTALL)


def source_phrases() -> list[str]:
    phrases = set()
    for path in SOURCE.glob("*.*"):
        if path.suffix not in {".kt", ".java"}:
            continue
        for match in PATTERN.finditer(path.read_text(encoding="utf-8")):
            phrase = json.loads('"' + match.group(1) + '"')
            if "${" not in phrase:
                phrases.add(phrase)
    return sorted(phrases)


def translate_batch(phrases: list[str], target: str) -> list[str]:
    payload = f"\n{SEPARATOR}\n".join(phrase.replace("\n", " ZXQVNLINEZXQV ") for phrase in phrases)
    query = urllib.parse.urlencode(
        {"client": "gtx", "sl": "en", "tl": target, "dt": "t", "q": payload}
    )
    request = urllib.request.Request(
        "https://translate.googleapis.com/translate_a/single?" + query,
        headers={"User-Agent": "Voltunizator-localization/4.1.1"},
    )
    for attempt in range(4):
        try:
            with urllib.request.urlopen(request, timeout=45) as response:
                data = json.loads(response.read().decode("utf-8"))
            translated = "".join(part[0] for part in data[0] if part[0])
            results = [value.strip().replace(" ZXQVNLINEZXQV ", "\n") for value in translated.split(SEPARATOR)]
            if len(results) == len(phrases):
                return results
        except Exception:
            if attempt == 3:
                raise
            time.sleep(2 ** attempt)
    raise RuntimeError(f"Translation batch failed for {target}")


def kotlin_string(value: str) -> str:
    return json.dumps(value, ensure_ascii=False).replace("$", "\\$")


def write_catalog(target: str, suffix: str, phrases: list[str]) -> None:
    batches = [phrases[offset : offset + 20] for offset in range(0, len(phrases), 20)]
    translated_batches: list[list[str] | None] = [None] * len(batches)
    with ThreadPoolExecutor(max_workers=6) as pool:
        futures = {
            pool.submit(translate_batch, batch, target): index
            for index, batch in enumerate(batches)
        }
        for future in as_completed(futures):
            translated_batches[futures[future]] = future.result()
    translated = [value for batch in translated_batches for value in (batch or [])]
    entries = "\n".join(
        f"        {kotlin_string(source)} to {kotlin_string(result)},"
        for source, result in zip(phrases, translated)
    )
    content = (
        "package com.dumuzeyn.mp3player.localization\n\n"
        f"internal object Translations{suffix} {{\n"
        "    val entries: Map<String, String> = mapOf(\n"
        f"{entries}\n"
        "    )\n"
        "}\n"
    )
    (OUTPUT / f"Translations{suffix}.kt").write_text(content, encoding="utf-8")


def write_dispatcher() -> None:
    branches = "\n".join(
        f'        "{APP_CODES.get(code, code)}" -> Translations{suffix}.entries[english]'
        for code, suffix in LANGUAGES.items()
    )
    content = (
        "package com.dumuzeyn.mp3player\n\n"
        "import com.dumuzeyn.mp3player.localization.*\n\n"
        "internal object TranslationCatalog {\n"
        "    fun text(language: String, english: String): String = when (language) {\n"
        f"{branches}\n"
        "        else -> null\n"
        "    } ?: english\n"
        "}\n"
    )
    (SOURCE / "TranslationCatalog.kt").write_text(content, encoding="utf-8")


def main() -> None:
    OUTPUT.mkdir(parents=True, exist_ok=True)
    phrases = source_phrases()
    for target, suffix in LANGUAGES.items():
        write_catalog(target, suffix, phrases)
        print(f"{target}: {len(phrases)} phrases", flush=True)
    write_dispatcher()


if __name__ == "__main__":
    main()
