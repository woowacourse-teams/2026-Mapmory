import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import vm from "node:vm";
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
  assert.match(page, /apps\.apple\.com\/app\/id6807056166/);
  assert.match(page, /play\.google\.com\/store\/apps\/details\?id=com\.mapmory\.android/);
  assert.match(page, /코드로 참여/);
  // The token stays on the page: no network calls, analytics or external scripts, and no automatic navigation.
  assert.doesNotMatch(page, /document\.URL|fetch\(|XMLHttpRequest|sendBeacon|posthog|gtag|<script[^>]+src=/i);
  assert.doesNotMatch(page, /location(\.href)?\s*=[^=]|location\.(assign|replace)\(|window\.open\(|http-equiv="refresh"|\.src\s*=/i);
  // .store sets display:flex, which would otherwise override the hidden attribute and show a dead "#" button.
  assert.match(page, /\.store\[hidden\]\s*\{\s*display:\s*none;\s*\}/);
});

const PLAY = "https://play.google.com/store/apps/details?id=com.mapmory.android";

function runInvitePage({ pathname, userAgent }) {
  const page = read("public/invite/index.html");
  const script = /<script>([\s\S]*?)<\/script>/.exec(page)[1];
  const container = { children: [], insertBefore(node, ref) { this.remove(node); this.children.splice(this.children.indexOf(ref), 0, node); }, appendChild(node) { this.remove(node); this.children.push(node); }, remove(node) { const i = this.children.indexOf(node); if (i >= 0) this.children.splice(i, 1); } };
  const element = (id, href, hidden, classes) => {
    const set = new Set(classes);
    const node = { id, href, hidden, parentNode: container, classList: { add: (c) => set.add(c), remove: (c) => set.delete(c), contains: (c) => set.has(c) } };
    container.children.push(node);
    return node;
  };
  const nodes = {
    "open-app": element("open-app", "#", true, ["store"]),
    "store-primary": element("store-primary", "https://apps.apple.com/app/id6807056166", false, ["store"]),
    "store-secondary": element("store-secondary", PLAY, false, ["store", "secondary"]),
  };
  vm.runInNewContext(script, {
    navigator: { userAgent },
    window: { location: { pathname } },
    document: { getElementById: (id) => nodes[id] },
    URL,
    URLSearchParams,
  });
  return { nodes, order: container.children.map((node) => node.id) };
}

const ANDROID = "Mozilla/5.0 (Linux; Android 14; SM-S918N) AppleWebKit/537.36 KAKAOTALK";

test("on Android the page offers an intent button that keeps the invite and a Play link with the token", () => {
  const token = "q9Xw2_-abcDEF0123456789abcdefghijklmnopqrstu";
  const { nodes, order } = runInvitePage({ pathname: `/invite/${token}`, userAgent: ANDROID });
  const store = `${PLAY}&referrer=invite_token%3D${token}`;
  assert.equal(nodes["store-secondary"].href, store);
  assert.equal(nodes["open-app"].hidden, false);
  assert.equal(
    nodes["open-app"].href,
    `intent://map-mory.com/invite/${token}#Intent;scheme=https;package=com.mapmory.android;S.browser_fallback_url=${encodeURIComponent(store)};end`,
  );
  assert.deepEqual(order, ["open-app", "store-secondary", "store-primary"]);
});

test("a path that is not a plain token gets no intent button and an unchanged Play link", () => {
  for (const pathname of ["/invite/", "/invite/abc", "/invite/a%22b;end", "/invite/abc/def", `/invite/${"a".repeat(513)}`]) {
    const { nodes } = runInvitePage({ pathname, userAgent: ANDROID });
    assert.equal(nodes["open-app"].hidden, true, pathname);
    assert.equal(nodes["store-secondary"].href, PLAY, pathname);
  }
});

test("iPhone keeps the plain store links and no intent button", () => {
  const { nodes, order } = runInvitePage({
    pathname: "/invite/q9Xw2abcdef",
    userAgent: "Mozilla/5.0 (iPhone; CPU iPhone OS 18_0 like Mac OS X) KAKAOTALK",
  });
  assert.equal(nodes["open-app"].hidden, true);
  assert.equal(nodes["store-secondary"].href, PLAY);
  assert.deepEqual(order, ["open-app", "store-primary", "store-secondary"]);
});

test("nginx example serves the association files as JSON and every invite path as one page", () => {
  const conf = read("nginx/invite.conf");
  assert.match(conf, /location = \/\.well-known\/apple-app-site-association \{[^}]*default_type application\/json;/);
  assert.match(conf, /location = \/\.well-known\/assetlinks\.json \{[^}]*default_type application\/json;/);
  assert.match(conf, /location \^~ \/invite\/ \{[^}]*access_log off;[^}]*try_files \/invite\/index\.html =404;/);
});
