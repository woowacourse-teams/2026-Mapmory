import assert from "node:assert/strict";
import { access, readFile } from "node:fs/promises";
import test from "node:test";
import { CAMPAIGN_PATH, CAMPAIGN_URL, CAMPAIGN_LANDING_URL } from "../src/campaignConfig.js";
import { publicAssetUrl } from "../src/publicAssets.js";
import { demoJourney } from "../src/journeyData.js";

test("campaign lives under /recap/ while the introduction still goes to the landing root", () => {
  assert.equal(CAMPAIGN_PATH, "/recap/");
  assert.equal(CAMPAIGN_URL, "https://map-mory.com/recap/");
  assert.equal(new URL(CAMPAIGN_LANDING_URL).pathname, "/");
});

test("runtime public photos honor both root and recap builds", () => {
  assert.equal(publicAssetUrl("photo.jpg", "/"), "/assets/photo.jpg");
  assert.equal(publicAssetUrl("photo.jpg", "/recap/"), "/recap/assets/photo.jpg");
  assert.equal(publicAssetUrl("photo.jpg"), "/assets/photo.jpg");
  assert.equal(demoJourney.points.length, 5);
  for (const point of demoJourney.points) assert.match(point.image, /^\/assets\/team-/);
});

for (const [directory, base] of [["client", "/"], ["recap", "/recap/"]]) {
  test(`${directory} build keeps scripts, CSS, fonts and team photos in its own asset directory`, async () => {
    const output = new URL(`../dist/${directory}/`, import.meta.url);
    const html = await readFile(new URL("index.html", output), "utf8");
    const links = [...html.matchAll(/(?:src|href)="([^"]+)"/g)].map((match) => match[1]);
    assert.ok(links.some((value) => value.endsWith(".js")));
    assert.ok(links.some((value) => value.endsWith(".css")));
    for (const link of links) {
      assert.ok(link.startsWith(`${base}assets/`), `Unexpected asset path: ${link}`);
      const file = new URL(link.slice(base.length), output);
      await access(file);
      if (link.endsWith(".css")) {
        const css = await readFile(file, "utf8");
        const fonts = [...css.matchAll(/url\(["']?([^)"']+\.woff2)["']?\)/g)].map((match) => match[1]);
        assert.equal(fonts.length, 2);
        for (const font of fonts) {
          assert.ok(font.startsWith(`${base}assets/fonts/`), font);
          await access(new URL(font.slice(base.length), output));
        }
      }
    }
    for (const point of demoJourney.points) {
      await access(new URL(point.image.slice(1), output));
    }
  });
}
