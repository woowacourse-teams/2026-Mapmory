import photoCredits from "./data/photo-credits.json";

const LICENSE_LABELS = { cc0: "CC0", pdm: "Public Domain" };

function licenseLabel({ license, licenseVersion }) {
  return LICENSE_LABELS[license] ?? `CC ${license.toUpperCase()} ${licenseVersion}`.trim();
}

// Demo photos come from openly licensed collections; CC BY asks for creator, source and license.
function PhotoCredits() {
  return (
    <details className="photo-credits">
      <summary>사진 출처</summary>
      <p>데모 사진은 공개 라이선스 사진을 크기 조정·자르기·WebP 변환해 사용했어요.</p>
      <ul>
        {photoCredits.map((credit) => (
          <li key={credit.file}>
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
