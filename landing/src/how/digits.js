// LINE Seed Sans KR has no tabular digits (`tnum` changes nothing), so a number that runs every frame is drawn in
// fixed-width cells, one character per cell: d = digit, p = separator, t = text (the counter word), "" = unused.
export const DIGIT_CELLS = 7;
export const digitKind = (char) => (char === undefined ? "" : /\d/.test(char) ? "d" : /[,.]/.test(char) ? "p" : "t");
