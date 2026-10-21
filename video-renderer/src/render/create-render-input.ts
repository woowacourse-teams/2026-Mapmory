import {getCountryCoordinate} from "../travel-timeline/country-coordinates";
import {
  travelTimelineSchema,
  type TravelTimelineProps,
} from "../travel-timeline/schema";
import {travelTimelineSnapshotSchema} from "./snapshot-schema";

export const createRenderInput = (rawSnapshot: unknown): TravelTimelineProps => {
  const snapshot = travelTimelineSnapshotSchema.parse(rawSnapshot);

  return travelTimelineSchema.parse({
    version: snapshot.version,
    stops: snapshot.stops.map((stop) => ({
      ...stop,
      ...getCountryCoordinate(stop.countryCode),
    })),
  });
};
