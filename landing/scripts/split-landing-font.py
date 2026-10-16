"""Split LINE Seed Sans KR into a small core file and the rest, for unicode-range loading on the landing.

core = Latin, punctuation, symbols, jamo and the 2,350 common Hangul syllables (KS X 1001), which cover the landing's
copy; ext = the other 8,822 syllables. src/styles.css lists ext as a second family after core, so a browser downloads it
only when the page shows a syllable core lacks. The original files stay for /recap/.
Run: python3 scripts/split-landing-font.py  (needs fontTools + brotli)
"""
from pathlib import Path
from fontTools import subset
from fontTools.ttLib import TTFont

FONTS = Path(__file__).resolve().parent.parent / "public/assets/fonts"


def ks_x_1001(cp):
    # Python's euc-kr spells syllables outside KS X 1001 as 8-byte jamo sequences; the 2,350 are 2 bytes.
    return len(chr(cp).encode("euc-kr")) == 2


def split(weight):
    source = FONTS / f"line-seed-kr-{weight}.woff2"
    cmap = TTFont(source).getBestCmap()
    core = {cp for cp in cmap if not (0xAC00 <= cp <= 0xD7A3) or ks_x_1001(cp)}
    ext = set(cmap) - core
    for name, cps in (("core", core), ("ext", ext)):
        options = subset.Options()
        options.flavor = "woff2"
        options.layout_features = ["*"]
        options.name_IDs = ["*"]
        options.notdef_outline = True
        font = TTFont(source)
        subsetter = subset.Subsetter(options)
        subsetter.populate(unicodes=cps)
        subsetter.subset(font)
        target = FONTS / f"line-seed-kr-{weight}-{name}.woff2"
        font.flavor = "woff2"
        font.save(target)
        print(target.name, target.stat().st_size)
    return len(core), len(ext)


if __name__ == "__main__":
    assert split("regular") == split("bold"), "regular and bold must cover the same characters"
