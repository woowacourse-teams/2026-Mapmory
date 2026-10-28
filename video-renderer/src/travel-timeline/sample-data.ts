import type {TravelTimelineProps} from "./schema";

export const sampleTravelTimeline: TravelTimelineProps = {
  version: 1,
  stops: [
    {
      countryCode: "KR",
      countryName: "대한민국",
      visits: [
        {visitId: 1, startDate: "2024-03-12", endDate: "2024-03-14"},
        {visitId: 2, startDate: "2024-04-20", endDate: null},
      ],
      longitude: 126.978,
      latitude: 37.5665,
    },
    {countryCode: "TW", countryName: "대만", visits: [{visitId: 3, startDate: "2024-06-08", endDate: null}], longitude: 121.5654, latitude: 25.033},
    {countryCode: "PH", countryName: "필리핀", visits: [{visitId: 4, startDate: "2024-11-21", endDate: null}], longitude: 120.9842, latitude: 14.5995},
    {countryCode: "NP", countryName: "네팔", visits: [{visitId: 5, startDate: "2025-04-17", endDate: "2025-04-22"}], longitude: 85.324, latitude: 27.7172},
    {countryCode: "MN", countryName: "몽골", visits: [{visitId: 6, startDate: "2025-09-03", endDate: null}], longitude: 106.9057, latitude: 47.8864},
    {countryCode: "GB", countryName: "영국", visits: [{visitId: 7, startDate: "2026-05-14", endDate: "2026-05-20"}], longitude: -0.1276, latitude: 51.5072},
    {countryCode: "IS", countryName: "아이슬란드", visits: [{visitId: 8, startDate: "2026-08-26", endDate: null}], longitude: -21.9426, latitude: 64.1466},
  ],
};
