import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";

const read = (file) => readFileSync(new URL(`../${file}`, import.meta.url), "utf8");

test("Android app links trust only the Mapmory packages", () => {
  const entries = JSON.parse(read("public/.well-known/assetlinks.json"));
  assert.deepEqual(
    entries.map((entry) => entry.target.package_name).sort(),
    ["com.mapmory.android", "com.mapmory.android.internal"],
  );
  for (const entry of entries) {
    assert.deepEqual(entry.relation, ["delegate_permission/common.handle_all_urls"]);
    assert.equal(entry.target.namespace, "android_app");
    // Play Console > 앱 무결성 > 앱 서명 키 인증서의 SHA-256. 자리표시자가 남아 있으면 앱 링크 검증이 실패한다.
    assert.ok(entry.target.sha256_cert_fingerprints.length > 0);
    for (const fingerprint of entry.target.sha256_cert_fingerprints) {
      assert.match(fingerprint, /^([0-9A-F]{2}:){31}[0-9A-F]{2}$/);
    }
  }
});

test("iOS universal links open only invite paths in the Mapmory apps", () => {
  const aasa = JSON.parse(read("public/.well-known/apple-app-site-association"));
  const [detail] = aasa.applinks.details;
  assert.ok(detail.appIDs.includes("22YUT65LMV.com.mapmory.ios"));
  assert.deepEqual(detail.components.map((component) => component["/"]), ["/invite/*"]);
});

test("the invite page never shows the token or group details and points to both stores", () => {
  const page = read("public/invite/index.html");
  assert.match(page, /noindex/);
  assert.match(page, /<meta name="referrer" content="no-referrer">/);
  assert.match(page, /apps\.apple\.com\/kr\/app\/.*id6807056166/);
  assert.match(page, /play\.google\.com\/store\/apps\/details\?id=com\.mapmory\.android/);
  assert.match(page, /코드로 참여/);
  // The token is in the path. The page must not read the URL, call out, or load analytics.
  assert.doesNotMatch(page, /location|document\.URL|fetch\(|XMLHttpRequest|sendBeacon|posthog|gtag|<script[^>]+src=/i);
});

test("nginx example serves the association files as JSON and every invite path as one page", () => {
  const conf = read("nginx/invite.conf");
  assert.match(conf, /location = \/\.well-known\/apple-app-site-association \{[^}]*default_type application\/json;/);
  assert.match(conf, /location = \/\.well-known\/assetlinks\.json \{[^}]*default_type application\/json;/);
  assert.match(conf, /location \^~ \/invite\/ \{[^}]*access_log off;[^}]*try_files \/invite\/index\.html =404;/);
});
