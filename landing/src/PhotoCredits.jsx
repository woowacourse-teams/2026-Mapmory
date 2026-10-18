import { useState } from "react";

const LICENSE_LABELS = { cc0: "CC0", pdm: "Public Domain", pd: "Public domain" };

function licenseLabel({ license, licenseVersion }) {
  return LICENSE_LABELS[license] ?? `CC ${license.toUpperCase()} ${licenseVersion}`.trim();
}

// The landing's photos come from openly licensed collections; CC BY asks for creator, source and license.
// The list (src/data/photo-credits.json, one entry per sprite cell) loads only when the footer's 사진 출처 is opened.
function PhotoCredits() {
  const [credits, setCredits] = useState(null);
  const load = (event) => {
    if (!event.currentTarget.open || credits) return;
    import("./data/photo-credits.json").then(({ default: list }) => setCredits(list)).catch(() => setCredits(null));
  };
  return (
    <details className="photo-credits" onToggle={load}>
      <summary>사진 출처</summary>
      <p>사진은 공개 라이선스 사진을 크기 조정·자르기·WebP 변환해 사용했어요.</p>
      <ul>
        {(credits ?? []).map((credit) => (
          <li key={credit.file}>
            {credit.place && <b>{credit.place} · </b>}
            <a href={credit.source} target="_blank" rel="noreferrer">{credit.title || "제목 없음"}</a>
            {credit.creator && ` · ${credit.creator}`}
            {" · "}
            {credit.licenseUrl ? <a href={credit.licenseUrl} target="_blank" rel="noreferrer">{licenseLabel(credit)}</a> : licenseLabel(credit)}
          </li>
        ))}
      </ul>
    </details>
  );
}

export { PhotoCredits };
