import {mkdir} from "node:fs/promises";
import path from "node:path";
import {fileURLToPath} from "node:url";
import {bundle} from "@remotion/bundler";
import {renderMedia, selectComposition} from "@remotion/renderer";
import {sampleTravelTimeline} from "../travel-timeline/sample-data";

const rendererDir = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "../..");
const outputDir = path.join(rendererDir, "out");
const outputLocation = path.join(outputDir, "travel-timeline.mp4");

const main = async () => {
  await mkdir(outputDir, {recursive: true});

  const serveUrl = await bundle({
    entryPoint: path.join(rendererDir, "src/index.ts"),
  });

  const composition = await selectComposition({
    serveUrl,
    id: "TravelTimeline",
    inputProps: sampleTravelTimeline,
    port: 3001,
  });

  await renderMedia({
    composition,
    serveUrl,
    codec: "h264",
    outputLocation,
    inputProps: sampleTravelTimeline,
    port: 3001,
  });

  console.log(`Rendered ${outputLocation}`);
};

main().catch((error: unknown) => {
  console.error(error);
  process.exitCode = 1;
});
