// The globe drawing primitives the prototype page needs, bundled into one script (globe-lib.js) so the page can be
// published as a standalone artifact without the Vite build. Build: node prototypes/scroll-globe/build.mjs
import { geoGraticule10, geoInterpolate, geoOrthographic, geoPath } from "d3-geo";
import { feature } from "topojson-client";
import land110 from "world-atlas/land-110m.json";

window.GlobeLib = { geoGraticule10, geoInterpolate, geoOrthographic, geoPath, LAND: feature(land110, land110.objects.land), GRATICULE: geoGraticule10() };
