// Pick 이/가, 을/를 from the last syllable's final consonant, so labels like "제주" read "제주를", not "제주을".
function hasFinalConsonant(word) {
  const code = word.charCodeAt(word.length - 1) - 0xac00;
  return code >= 0 && code <= 11171 && code % 28 !== 0;
}

export function withSubjectParticle(word) {
  return `${word}${hasFinalConsonant(word) ? "이" : "가"}`;
}

export function withObjectParticle(word) {
  return `${word}${hasFinalConsonant(word) ? "을" : "를"}`;
}
