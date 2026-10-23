import {z} from "zod";

const localDateSchema = z.string().regex(/^\d{4}-\d{2}-\d{2}$/);

export const travelVisitSchema = z.object({
  visitId: z.number().int().positive(),
  startDate: localDateSchema,
  endDate: localDateSchema.nullable(),
});

export const travelStopSchema = z.object({
  countryCode: z.string().length(2),
  countryName: z.string().min(1),
  visits: z.array(travelVisitSchema).min(1),
  longitude: z.number().min(-180).max(180),
  latitude: z.number().min(-90).max(90),
});

export const travelTimelineSchema = z.object({
  version: z.literal(1),
  stops: z.array(travelStopSchema).min(2),
});

export type TravelVisit = z.infer<typeof travelVisitSchema>;
export type TravelStop = z.infer<typeof travelStopSchema>;
export type TravelTimelineProps = z.infer<typeof travelTimelineSchema>;
