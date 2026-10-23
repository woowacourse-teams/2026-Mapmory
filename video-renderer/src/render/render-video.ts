import {mkdir, readFile} from "node:fs/promises";
import path from "node:path";
import {fileURLToPath} from "node:url";
import {bundle} from "@remotion/bundler";
import {renderMedia, selectComposition} from "@remotion/renderer";
import {createRenderInput} from "./create-render-input";

const rendererDir = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "../..");
const defaultOutputLocation = path.join(rendererDir, "out", "travel-timeline.mp4");

const resolveArguments = () => {
  const [inputArgument, outputArgument] = process.argv.slice(2);

  if (!inputArgument) {
    throw new Error(
      "Usage: npm run render:worker -- <snapshot.json> [output.mp4]",
    );
  }

  return {
    inputLocation: path.resolve(process.cwd(), inputArgument),
    outputLocation: outputArgument
      ? path.resolve(process.cwd(), outputArgument)
      : defaultOutputLocation,
  };
};

const main = async () => {
  const {inputLocation, outputLocation} = resolveArguments();
  const snapshotJson = await readFile(inputLocation, "utf8");
  const inputProps = createRenderInput(JSON.parse(snapshotJson));

  await mkdir(path.dirname(outputLocation), {recursive: true});

  const serveUrl = await bundle({
    entryPoint: path.join(rendererDir, "src/index.ts"),
  });

  const composition = await selectComposition({
    serveUrl,
    id: "TravelTimeline",
    inputProps,
    port: 3001,
  });

  await renderMedia({
    composition,
    serveUrl,
    codec: "h264",
    outputLocation,
    inputProps,
    port: 3001,
  });

  console.log(`Rendered ${outputLocation}`);
};

main().catch((error: unknown) => {
  console.error(error);
  process.exitCode = 1;
});
