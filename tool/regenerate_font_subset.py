#!/usr/bin/env python3
"""
Regenerates assets/fonts/NotoSansSC-Subset.ttf so it covers every character
currently used in lib/**/*.dart and assets/data/*.json.

Run this after adding new words/grammar topics that introduce characters
(Chinese or otherwise) not already in the app, otherwise those new
characters will render as empty boxes.

How it works:
1. Collects every unique character used in the app's source and data files.
2. Asks the Google Fonts API to build a "Noto Sans SC" TTF containing only
   those characters (server-side subsetting).
3. Noto Sans SC's Latin coverage excludes a handful of Turkish letters
   (ğ Ğ ş Ş ı İ) used in this app's Turkish UI text, so a second tiny font
   is fetched from Google Fonts for just those letters and their glyphs are
   grafted onto the main font.

Requires: fontTools (`pip install fonttools`) and internet access.
"""
import glob
import os
import re
import subprocess
import sys
import urllib.parse
import urllib.request

from fontTools.ttLib import TTFont

ROOT = os.path.normpath(os.path.join(os.path.dirname(__file__), ".."))
OUT_PATH = os.path.join(ROOT, "assets", "fonts", "NotoSansSC-Subset.ttf")
TURKISH_EXTRA_CODEPOINTS = [0x11E, 0x11F, 0x15E, 0x15F, 0x130, 0x131]  # Ğğ Şş İı

HEADERS = {"User-Agent": "Mozilla/5.0"}


# Characters Flutter inserts at runtime that never appear literally in the
# source/data files, e.g. the ellipsis used by TextOverflow.ellipsis.
RUNTIME_EXTRA_CHARS = "…"


def collect_chars() -> str:
    chars = set(RUNTIME_EXTRA_CHARS)
    for pattern in ("lib/**/*.dart", "assets/data/*.json"):
        for path in glob.glob(os.path.join(ROOT, pattern), recursive=True):
            with open(path, encoding="utf-8") as f:
                chars.update(f.read())
    return "".join(sorted(c for c in chars if c.isprintable()))


def fetch_google_font_ttf(family: str, text: str) -> bytes:
    query = urllib.parse.quote(text, safe="")
    css_url = f"https://fonts.googleapis.com/css2?family={family}&text={query}"
    req = urllib.request.Request(css_url, headers=HEADERS)
    css = urllib.request.urlopen(req).read().decode()
    match = re.search(r"url\((https://fonts\.gstatic\.com/[^)]+)\)", css)
    if not match:
        raise RuntimeError(f"No font URL found in Google Fonts response for {family!r}")
    font_req = urllib.request.Request(match.group(1), headers=HEADERS)
    return urllib.request.urlopen(font_req).read()


def graft_missing_glyphs(target: TTFont, source: TTFont, codepoints: list[int]) -> None:
    s_cmap = source.getBestCmap()
    t_glyf, s_glyf = target["glyf"], source["glyf"]
    t_hmtx, s_hmtx = target["hmtx"], source["hmtx"]
    copied: dict[str, str] = {}

    def copy_glyph(src_name: str) -> str:
        if src_name in copied:
            return copied[src_name]
        new_name = src_name
        while new_name in target.getGlyphOrder() and new_name not in copied.values():
            new_name = f"tk_{new_name}"
        glyph = s_glyf[src_name]
        if glyph.isComposite():
            for component in glyph.components:
                copy_glyph(component.glyphName)
        t_glyf[new_name] = glyph
        if new_name not in target.getGlyphOrder():
            target.setGlyphOrder(target.getGlyphOrder() + [new_name])
        t_hmtx[new_name] = s_hmtx[src_name]
        copied[src_name] = new_name
        if glyph.isComposite():
            for component in t_glyf[new_name].components:
                component.glyphName = copied.get(component.glyphName, component.glyphName)
        return new_name

    for cp in codepoints:
        if cp not in s_cmap:
            continue
        new_name = copy_glyph(s_cmap[cp])
        for table in target["cmap"].tables:
            table.cmap[cp] = new_name

    target["maxp"].numGlyphs = len(target.getGlyphOrder())
    target["glyf"].compile(target)


def build_from_scratch(chars: str) -> "TTFont":
    """Fetch the full Noto Sans SC and subset it locally to `chars`.

    Used only when no existing subset is present. The CSS2 `text=` param is
    unreliable for very large CJK character sets (the URL can exceed the
    server's length limit and 400), so a small fixed request is used just to
    obtain the font URL, then it is subset locally with pyftsubset.
    """
    seed = "".join(sorted(set(chars)))[:200]
    main_bytes = fetch_google_font_ttf("Noto+Sans+SC", seed)
    tmp_downloaded = OUT_PATH + ".downloaded.tmp"
    with open(tmp_downloaded, "wb") as f:
        f.write(main_bytes)
    tmp_main = OUT_PATH + ".tmp"
    unicodes = ",".join(f"U+{ord(c):04X}" for c in chars)
    subprocess.run(
        ["pyftsubset", tmp_downloaded, f"--unicodes={unicodes}",
         f"--output-file={tmp_main}", "--layout-features=*"],
        check=True,
    )
    os.remove(tmp_downloaded)
    target = TTFont(tmp_main)
    os.remove(tmp_main)
    return target


def main() -> None:
    chars = collect_chars()
    print(f"{len(chars)} unique characters in use")

    # Incremental design: the Google Fonts CSS2 `text=` endpoint 400s once the
    # requested character set grows past a few hundred CJK glyphs (the encoded
    # URL gets too long). So instead of re-downloading and re-subsetting every
    # character on each run, start from the existing shipped subset and only
    # fetch + graft the handful of glyphs newly introduced since last time.
    # Each request therefore stays tiny and the pipeline scales indefinitely.
    if os.path.exists(OUT_PATH):
        target = TTFont(OUT_PATH)
        cmap = target.getBestCmap()
        missing = [ord(c) for c in chars if ord(c) not in cmap]
    else:
        target = build_from_scratch(chars)
        cmap = target.getBestCmap()
        missing = [ord(c) for c in chars if ord(c) not in cmap]

    if not missing:
        print("Font already covers every character; nothing to do.")
        return

    print(f"Grafting {len(missing)} new glyph(s): {''.join(chr(c) for c in missing)}")

    # Turkish letters live in Noto Sans (Latin), everything else in Noto Sans SC.
    needed_turkish = [cp for cp in missing if cp in TURKISH_EXTRA_CODEPOINTS]
    needed_cjk = [cp for cp in missing if cp not in TURKISH_EXTRA_CODEPOINTS]

    for family, cps in (("Noto+Sans+SC", needed_cjk), ("Noto+Sans", needed_turkish)):
        if not cps:
            continue
        extra_text = "".join(chr(cp) for cp in cps)
        extra_bytes = fetch_google_font_ttf(family, extra_text)
        tmp_extra = OUT_PATH + ".extra.tmp"
        with open(tmp_extra, "wb") as f:
            f.write(extra_bytes)
        graft_missing_glyphs(target, TTFont(tmp_extra), cps)
        os.remove(tmp_extra)

    still = [c for c in missing if ord(chr(c)) not in target.getBestCmap()]
    if still:
        print("WARNING: still missing glyphs for:", [chr(c) for c in still])

    target.save(OUT_PATH)
    print(f"Saved {OUT_PATH} ({os.path.getsize(OUT_PATH)} bytes)")


if __name__ == "__main__":
    main()
