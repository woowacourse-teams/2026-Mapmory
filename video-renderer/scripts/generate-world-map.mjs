import fs from "node:fs";
import path from "node:path";
import {fileURLToPath} from "node:url";

const scriptDir = path.dirname(fileURLToPath(import.meta.url));
const projectDir = path.resolve(scriptDir, "..");
const sourceDir = path.resolve(
  projectDir,
  "../client/shared/src/commonMain/kotlin/com/mapmory/shared/presentation/map/data",
);
const outputPath = path.resolve(projectDir, "src/data/world-map.json");

const files = fs
  .readdirSync(sourceDir)
  .filter((name) => /^GeneratedWorldMapDataPart\d+\.kt$/.test(name))
  .sort();

const countries = [];

for (const file of files) {
  const source = fs.readFileSync(path.join(sourceDir, file), "utf8");
  const starts = [...source.matchAll(/country\("([A-Z]{2})",\s*"([^"]+)"/g)];

  starts.forEach((match, index) => {
    const section = source.slice(
      match.index,
      starts[index + 1]?.index ?? source.length,
    );
    const rings = [];
    const ringPattern = /listOf\(((?:\s*p\([^)]*\)\s*,?)+)\)/g;

    for (const ringMatch of section.matchAll(ringPattern)) {
      const points = [
        ...ringMatch[1].matchAll(
          /p\((-?[\d.]+)f,\s*(-?[\d.]+)f\)/g,
        ),
      ].map((point) => [Number(point[1]), Number(point[2])]);

      if (points.length >= 3) rings.push(points);
    }

    if (rings.length > 0) {
      countries.push({code: match[1], name: match[2], rings});
    }
  });
}

fs.mkdirSync(path.dirname(outputPath), {recursive: true});
fs.writeFileSync(outputPath, `${JSON.stringify(countries)}\n`);
console.log(`Generated ${countries.length} countries at ${outputPath}`);
