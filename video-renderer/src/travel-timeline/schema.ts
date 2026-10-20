import {z} from "zod";

export const travelStopSchema = z.object({
  countryCode: z.string().length(2),
  countryName: z.string().min(1),
  date: z.string().min(1),
  longitude: z.number().min(-180).max(180),
  latitude: z.number().min(-90).max(90),
});

export const travelTimelineSchema = z.object({
  stops: z.array(travelStopSchema).min(2),
});

export type TravelStop = z.infer<typeof travelStopSchema>;
export type TravelTimelineProps = z.infer<typeof travelTimelineSchema>;
