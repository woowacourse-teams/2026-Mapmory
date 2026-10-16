export const dashboardDays = [1, 7, 30, 45, 60] as const;
export type DashboardDays = typeof dashboardDays[number];

export function getDashboardRange(days: DashboardDays, now = new Date()) {
  const parts = new Intl.DateTimeFormat("en-CA", {
    timeZone: "Asia/Seoul",
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
  }).formatToParts(now);
  const value = (type: string) => parts.find((part) => part.type === type)?.value ?? "0";
  const year = Number(value("year"));
  const month = Number(value("month"));
  const day = Number(value("day"));
  const to = `${year}-${String(month).padStart(2, "0")}-${String(day).padStart(2, "0")}`;
  const fromDate = new Date(Date.UTC(year, month - 1, day));
  fromDate.setUTCDate(fromDate.getUTCDate() - (days - 1));
  return { from: fromDate.toISOString().slice(0, 10), to };
}

