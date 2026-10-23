import {z} from "zod";
import {travelVisitSchema} from "../travel-timeline/schema";

export const travelTimelineSnapshotSchema = z.object({
  version: z.literal(1),
  stops: z.array(
    z.object({
      countryCode: z.string().length(2),
      countryName: z.string().min(1),
      visits: z.array(travelVisitSchema).min(1),
    }),
  ).min(2),
});

export type TravelTimelineSnapshot = z.infer<typeof travelTimelineSnapshotSchema>;
