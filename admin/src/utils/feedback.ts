export function formatFeedbackDate(value: string) {
  // 운영 API의 LocalDateTime은 UTC 저장 계약이며 시간대 접미사가 없을 수 있다.
  const normalized = /(?:Z|[+-]\d{2}:\d{2})$/i.test(value) ? value : `${value}Z`;
  const date = new Date(normalized);
  if (Number.isNaN(date.getTime())) return "—";
  const parts = new Intl.DateTimeFormat("en-CA", {
    timeZone: "Asia/Seoul", year: "numeric", month: "2-digit", day: "2-digit",
    hour: "2-digit", minute: "2-digit", hourCycle: "h23",
  }).formatToParts(date);
  const part = (type: Intl.DateTimeFormatPartTypes) => parts.find((item) => item.type === type)?.value;
  return `${part("year")}.${part("month")}.${part("day")} ${part("hour")}:${part("minute")}`;
}

export function validateFeedbackDateRange(from: string, to: string) {
  return from && to && from > to ? "시작일은 종료일보다 늦을 수 없습니다." : "";
}
