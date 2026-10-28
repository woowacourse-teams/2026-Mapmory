// Bundles entry.mjs into globe-lib.js (not committed) next to index.html, so the folder can be opened or published as is.
import { build } from "esbuild";
import { fileURLToPath } from "node:url";
const dir = fileURLToPath(new URL(".", import.meta.url));
await build({ entryPoints: [`${dir}entry.mjs`], bundle: true, format: "iife", minify: true, outfile: `${dir}globe-lib.js`, logLevel: "info" });
