// The photo library seen from inside the phone: one sprite of open-licence crops (never a Gyeongju photo), the white
// location pins (a raster of the same block) revealed by the scan wave, and the four Gyeongju photos at their cells
// with their green pins and rings.
// Sizes come from --tile and the window rect, both set by the scroll hook.
//
// The sprite is one 16 x 20 block. Tiled as one wallpaper it repeats every 16 cells and reads as a lattice, so
// the window is covered by 8 x 10 panels, each showing a different part of the block (offsets from
// scripts/build-how-story-assets.py, which keeps a photo from touching itself across a seam). The pins raster
// follows the same panels, so a photo without a stored location never gets a pin anywhere.
import mosaic from "../data/how-mosaic.json";

const { cols: PANEL_COLS, rows: PANEL_ROWS, across: PANELS_ACROSS, offsets: PANEL_OFFSETS } = mosaic.panels;
const PANELS = PANEL_OFFSETS.map(([ox, oy], index) => ({
  index,
  col: (index % PANELS_ACROSS) * PANEL_COLS,
  row: Math.floor(index / PANELS_ACROSS) * PANEL_ROWS,
  ox,
  oy,
}));
const tiles = (count) => `calc(${count} * var(--tile, 16px))`;
const panelStyle = ({ col, row, ox, oy }) => ({
  left: tiles(col),
  top: tiles(row),
  width: tiles(PANEL_COLS),
  height: tiles(PANEL_ROWS),
  backgroundPosition: `${tiles(-ox)} ${tiles(-oy)}`,
});

// The sprite's panels, or the pins' (the same cells and offsets, from library-pins.webp).
function Panels({ className }) {
  return PANELS.map((panel) => <span className={className} key={panel.index} style={panelStyle(panel)} />);
}

// The green match pin (9x12), a little larger than the white ones. The flying photos carry it too (HowStory.jsx).
export function MatchPin() {
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
      <div className="how-mosaic-sprite"><Panels className="how-mosaic-panel" /></div>
      {/* The library dims under a cover of the ground colour (settling in beat 02, set aside in beat 03). */}
      <span className="how-mosaic-dim" data-node="mosaicDim" />
      {/* The pin wave: the reveal (which also carries the pins' opacity) and its counter-moving child. */}
      <div className="how-pins-reveal" data-node="pinsReveal">
        <div className="how-pins-inner" data-node="pinsInner"><Panels className="how-pins-panel" /></div>
      </div>
      {/* One wrapper, so the four cells share one layer rather than taking one each where they overlap the pins. */}
      <div className="how-mosaic-matches">
        {matches.map((match, index) => (
          <div className="how-mosaic-match" data-cell={index} key={match.id}>
            {/* Grows about its centre once its pin pops (data-pins), so the four found photos stand out. */}
            <span className="how-mosaic-lift" data-i={index}>
              <img src={match.src} alt="" width="18" height="18" loading="lazy" decoding="async" data-node={`matchCell${index}`} />
              <span className="how-mosaic-mark" data-node={`matchMark${index}`}>
                <span className="how-mosaic-mark-pop" data-i={index}>
                  <span className="how-mosaic-ring" />
                  <MatchPin />
                </span>
              </span>
            </span>
          </div>
        ))}
      </div>
    </div>
  );
}

// A still of the library for the static list: the same panels, dimmed.
export function LibraryStill() {
  return <div className="how-mosaic-still"><Panels className="how-mosaic-panel" /></div>;
}
