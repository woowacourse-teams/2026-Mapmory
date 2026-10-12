// The photo library seen from inside the phone: one sprite of open-licence crops (never a Gyeongju photo), a pins
// layer revealed by the scan wave, and the four Gyeongju photos at their cells with their green pins and rings.
// Sizes come from --tile and the window rect, both set by the scroll hook.
import mosaic from "../data/how-mosaic.json";

const PIN_TILE = 16;
const noLocation = new Set(mosaic.noLocation);
// One <use> per located cell, built once; the pattern repeats with the sprite's 16 x 20 block.
const pinUses = mosaic.sources
  .map((_, index) => index)
  .filter((index) => !noLocation.has(index))
  .map((index) => `<use href="#how-pin" x="${(index % mosaic.cols) * PIN_TILE + 2}" y="${Math.floor(index / mosaic.cols) * PIN_TILE + 2}"/>`)
  .join("");
// A 6x8 white teardrop with a 1px darker rim and a baked 1px drop shadow.
const PIN_PATH = "M3 7.6C2.2 6.7 0.5 4.8 0.5 3.1a2.5 2.5 0 0 1 5 0c0 1.7-1.7 3.6-2.5 4.5z";
export const HOW_PINS_SVG = `<defs><g id="how-pin"><path d="${PIN_PATH}" transform="translate(0 1)" fill="rgba(0,0,0,0.28)"/><path d="${PIN_PATH}" fill="rgba(255,255,255,0.92)" stroke="rgba(0,0,0,0.25)" stroke-width="1"/></g><pattern id="how-pins-pattern" width="${mosaic.cols * PIN_TILE}" height="${mosaic.rows * PIN_TILE}" patternUnits="userSpaceOnUse">${pinUses}</pattern></defs><rect width="100%" height="100%" fill="url(#how-pins-pattern)"/>`;

// The green match pin (9x12), a little larger than the white ones.
function MatchPin() {
  return (
    <svg className="how-match-pin" viewBox="0 0 9 12" focusable="false">
      <path d="M4.5 11.4C3.3 10.1 0.6 7.3 0.6 4.6a3.9 3.9 0 0 1 7.8 0c0 2.7-2.7 5.5-3.9 6.8z" />
      <circle cx="4.5" cy="4.5" r="1.5" />
    </svg>
  );
}

export function LibraryMosaic({ matches }) {
  return (
    <div className="how-mosaic-window" data-node="mosaicWindow">
      <div className="how-mosaic-sprite" data-node="mosaicSprite" />
      <div className="how-pins-layer" data-node="pinsLayer">
        <div className="how-pins-reveal" data-node="pinsReveal">
          <div className="how-pins-inner" data-node="pinsInner">
            <svg className="how-pins" focusable="false" dangerouslySetInnerHTML={{ __html: HOW_PINS_SVG }} />
          </div>
        </div>
      </div>
      {matches.map((match, index) => (
        <div className="how-mosaic-match" data-cell={index} key={match.id}>
          <img src={match.src} alt="" width="18" height="18" loading="lazy" decoding="async" data-node={`matchCell${index}`} />
          <span className="how-mosaic-mark" data-node={`matchMark${index}`}>
            <span className="how-mosaic-mark-pop" data-i={index}>
              <span className="how-mosaic-ring" />
              <MatchPin />
            </span>
          </span>
        </div>
      ))}
    </div>
  );
}

// A still of the library for the static list: the dimmed sprite only.
export function LibraryStill() {
  return <div className="how-mosaic-still" />;
}
