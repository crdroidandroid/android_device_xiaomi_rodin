#!/usr/bin/env python3
"""Generate XiaomiParts Android resources for every rodin product language."""

from concurrent.futures import ThreadPoolExecutor, as_completed
from html import unescape
from pathlib import Path
import re
import sys
import time
import urllib.parse
import urllib.request
import xml.etree.ElementTree as ET


ROOT = Path(__file__).resolve().parents[1]
ANDROID_ROOT = ROOT.parents[3]
SOURCE = ROOT / "res" / "values" / "strings.xml"
ENDPOINT = "https://translate.google.com/m"
RESULT_RE = re.compile(r'<div class="result-container">(.*?)</div>', re.DOTALL)
ENTRY_RE = re.compile(r"__(\d{3})__\s*(.*?)(?=\n__\d{3}__|\Z)", re.DOTALL)
PLACEHOLDER_RE = re.compile(r"%(\d+)\$([a-zA-Z])")

# Resource qualifier, translation-service language code. English variants and
# Android pseudo-locales intentionally use the default resources.
LOCALES = [
    ("af", "af"), ("am", "am"), ("ar", "ar"), ("as", "as"),
    ("az", "az"), ("be", "be"), ("bg", "bg"), ("bn", "bn"),
    ("bs", "bs"), ("ca", "ca"), ("cs", "cs"), ("da", "da"),
    ("de", "de"), ("el", "el"), ("es", "es"), ("et", "et"),
    ("eu", "eu"), ("fa", "fa"), ("fi", "fi"), ("fr", "fr"),
    ("gl", "gl"), ("gu", "gu"), ("hi", "hi"), ("hr", "hr"),
    ("hu", "hu"), ("hy", "hy"), ("in", "id"), ("is", "is"),
    ("iw", "he"), ("ja", "ja"), ("ka", "ka"), ("kk", "kk"),
    ("km", "km"), ("kn", "kn"), ("ko", "ko"), ("ky", "ky"),
    ("lo", "lo"), ("lt", "lt"), ("lv", "lv"), ("mk", "mk"),
    ("ml", "ml"), ("mn", "mn"), ("mr", "mr"), ("ms", "ms"),
    ("my", "my"), ("nb", "no"), ("ne", "ne"), ("nl", "nl"),
    ("or", "or"), ("pa", "pa"), ("pl", "pl"),
    ("pt-rBR", "pt"), ("pt-rPT", "pt"), ("ro", "ro"),
    ("ru", "ru"), ("si", "si"), ("sk", "sk"), ("sl", "sl"),
    ("sq", "sq"), ("sr", "sr"), ("b+sr+Latn", "sr-Latn"),
    ("sv", "sv"), ("sw", "sw"), ("ta", "ta"), ("te", "te"),
    ("th", "th"), ("tl", "tl"), ("tr", "tr"), ("uk", "uk"),
    ("ur", "ur"), ("uz", "uz"), ("vi", "vi"),
    ("zh-rCN", "zh-CN"), ("zh-rHK", "zh-TW"), ("zh-rTW", "zh-TW"),
    ("zu", "zu"), ("ast-rES", "ast"), ("ckb", "ckb"),
    ("gd", "gd"), ("cy", "cy"), ("fur-rIT", "fur"),
    ("nn-rNO", "no"),
]


def source_strings() -> list[tuple[str, str]]:
    root = ET.parse(SOURCE).getroot()
    return [
        (node.attrib["name"], "".join(node.itertext()))
        for node in root.findall("string")
    ]


def protect_placeholders(text: str) -> str:
    return PLACEHOLDER_RE.sub(lambda match: f"__ARG{match.group(1)}{match.group(2)}__", text)


def restore_placeholders(text: str) -> str:
    return re.sub(r"__ARG(\d+)([a-zA-Z])__", r"%\1$\2", text)


def normalize_placeholders(text: str, source: str) -> str:
    expected = PLACEHOLDER_RE.findall(source)
    normalized = text
    for number, kind in expected:
        # Some languages cause the service to drop the type suffix or closing
        # underscores from the protected token. Recover it from the source.
        normalized = re.sub(
            rf"__ARG{number}(?:{kind})?_*",
            f"%{number}${kind}",
            normalized,
            flags=re.IGNORECASE,
        )
        # Serbian Cyrillic transliterates the protection token itself.
        cyrillic_kind = {"s": "с", "d": "д"}.get(kind.lower(), kind)
        normalized = re.sub(
            rf"__АРГ{number}(?:{cyrillic_kind})?_*",
            f"%{number}${kind}",
            normalized,
            flags=re.IGNORECASE,
        )
    if sorted(PLACEHOLDER_RE.findall(normalized)) != sorted(expected):
        raise RuntimeError(
            f"placeholder mismatch: source={source!r}, translation={normalized!r}",
        )
    return normalized


def make_batches(entries: list[tuple[str, str]]) -> list[list[tuple[int, str, str]]]:
    batches: list[list[tuple[int, str, str]]] = []
    current: list[tuple[int, str, str]] = []
    size = 0
    for index, (name, value) in enumerate(entries):
        line = f"__{index:03d}__ {protect_placeholders(value)}"
        if current and size + len(line) + 1 > 1750:
            batches.append(current)
            current = []
            size = 0
        current.append((index, name, line))
        size += len(line) + 1
    if current:
        batches.append(current)
    return batches


def translate_batch(language: str, batch: list[tuple[int, str, str]]) -> dict[int, str]:
    query = "\n".join(line for _, _, line in batch)
    url = ENDPOINT + "?" + urllib.parse.urlencode({"sl": "en", "tl": language, "q": query})
    request = urllib.request.Request(url, headers={"User-Agent": "Mozilla/5.0"})
    last_error: Exception | None = None
    for attempt in range(4):
        try:
            with urllib.request.urlopen(request, timeout=30) as response:
                page = response.read().decode("utf-8")
            match = RESULT_RE.search(page)
            if not match:
                raise RuntimeError("translation result missing")
            translated = unescape(match.group(1)).replace("<br>", "\n")
            results = {
                int(index): restore_placeholders(value.strip())
                for index, value in ENTRY_RE.findall(translated)
            }
            expected = {index for index, _, _ in batch}
            if results.keys() != expected:
                raise RuntimeError(f"marker mismatch: expected {expected}, got {results.keys()}")
            return results
        except Exception as error:  # Network services may transiently throttle.
            last_error = error
            time.sleep(1.5 * (attempt + 1))
    raise RuntimeError(f"translation failed for {language}: {last_error}")


def translate_text(language: str, text: str) -> str:
    url = ENDPOINT + "?" + urllib.parse.urlencode(
        {"sl": "en", "tl": language, "q": protect_placeholders(text)},
    )
    request = urllib.request.Request(url, headers={"User-Agent": "Mozilla/5.0"})
    last_error: Exception | None = None
    for attempt in range(4):
        try:
            with urllib.request.urlopen(request, timeout=30) as response:
                page = response.read().decode("utf-8")
            match = RESULT_RE.search(page)
            if not match:
                raise RuntimeError("translation result missing")
            return restore_placeholders(unescape(match.group(1)).strip())
        except Exception as error:
            last_error = error
            time.sleep(1.5 * (attempt + 1))
    raise RuntimeError(f"single translation failed for {language}: {last_error}")


def xml_escape(value: str) -> str:
    return (
        value.replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace('"', "&quot;")
        .replace("'", "\\'")
    )


OFFICIAL_SETTINGS_STRINGS = {
    "cancel": "cancel",
    "dismiss": "dismiss",
    "cpu_apply": "apply",
    "screen_resolution_dialog_confirm": "apply",
    "screen_resolution_dialog_cancel": "cancel",
    "thermal_apply": "apply",
    "thermal_reset_confirm": "reset",
    "cpu_profile_economy": "battery_saver",
}


def official_settings_string(qualifier: str, name: str) -> str | None:
    resource_name = OFFICIAL_SETTINGS_STRINGS.get(name)
    if not resource_name:
        return None
    candidates = [
        ANDROID_ROOT / "packages/apps/Settings/res" / f"values-{qualifier}" / "strings.xml",
        ANDROID_ROOT / "vendor/crowdin/overlay/packages/apps/Settings/res" /
            f"values-{qualifier}" / "strings.xml",
    ]
    for candidate in candidates:
        if not candidate.exists():
            continue
        node = ET.parse(candidate).getroot().find(f"string[@name='{resource_name}']")
        if node is None:
            continue
        value = "".join(node.itertext()).strip()
        if len(value) >= 2 and value[0] == value[-1] == '"':
            value = value[1:-1]
        return value
    return None


def translate_locale(
    qualifier: str,
    language: str,
    entries: list[tuple[str, str]],
    single: bool = False,
) -> str:
    if single:
        translated = {
            index: translate_text(language, value)
            for index, (_, value) in enumerate(entries)
        }
    else:
        translated: dict[int, str] = {}
        for batch in make_batches(entries):
            translated.update(translate_batch(language, batch))

    output_dir = ROOT / "res" / f"values-{qualifier}"
    output_dir.mkdir(parents=True, exist_ok=True)
    output = output_dir / "strings.xml"
    lines = [
        '<?xml version="1.0" encoding="utf-8"?>',
        "<!-- Machine-translated baseline with official AOSP terminology where available. -->",
        "<resources>",
    ]
    for index, (name, source) in enumerate(entries):
        value = official_settings_string(qualifier, name) or translated[index]
        value = normalize_placeholders(value, source)
        lines.append(f'    <string name="{name}">{xml_escape(value)}</string>')
    lines.extend(["</resources>", ""])
    output.write_text("\n".join(lines), encoding="utf-8")
    old_output = output_dir / "cpu_strings.xml"
    if old_output.exists():
        old_output.unlink()
    return qualifier


def validate_catalogs() -> None:
    entries = source_strings()
    source = dict(entries)
    failures: list[str] = []
    catalogs = sorted((ROOT / "res").glob("values-*/strings.xml"))
    for catalog in catalogs:
        nodes = ET.parse(catalog).getroot().findall("string")
        translated = {
            node.attrib["name"]: "".join(node.itertext())
            for node in nodes
        }
        missing = source.keys() - translated.keys()
        extra = translated.keys() - source.keys()
        placeholders = [
            name
            for name in source.keys() & translated.keys()
            if sorted(PLACEHOLDER_RE.findall(source[name])) !=
                sorted(PLACEHOLDER_RE.findall(translated[name]))
        ]
        if len(nodes) != len(entries) or missing or extra or placeholders:
            failures.append(
                f"{catalog.parent.name}: count={len(nodes)}, "
                f"missing={sorted(missing)}, extra={sorted(extra)}, "
                f"placeholders={placeholders}",
            )
    if failures:
        raise SystemExit("\n".join(failures))
    print(
        f"{len(catalogs)} localized catalogs validated; "
        f"{len(entries)} strings per catalog",
    )


def main() -> None:
    if sys.argv[1:] == ["--validate"]:
        validate_catalogs()
        return
    entries = source_strings()
    requested = set(sys.argv[1:])
    locales = [
        (qualifier, language)
        for qualifier, language in LOCALES
        if qualifier != "it" and (not requested or qualifier in requested)
    ]
    failures: list[str] = []
    with ThreadPoolExecutor(max_workers=4) as executor:
        futures = {
            executor.submit(
                translate_locale,
                qualifier,
                language,
                entries,
                bool(requested),
            ): qualifier
            for qualifier, language in locales
        }
        for future in as_completed(futures):
            qualifier = futures[future]
            try:
                print(f"translated {future.result()}", flush=True)
            except Exception as error:
                failures.append(f"{qualifier}: {error}")
                print(f"FAILED {qualifier}: {error}", flush=True)
    if failures:
        raise SystemExit("\n".join(failures))


if __name__ == "__main__":
    main()
