import {Composition, type CalculateMetadataFunction} from "remotion";
import {sampleTravelTimeline} from "./travel-timeline/sample-data";
import {travelTimelineSchema, type TravelTimelineProps} from "./travel-timeline/schema";
import {getTravelTimelineDuration} from "./travel-timeline/timing";
import {TravelTimeline} from "./travel-timeline/TravelTimeline";

const calculateMetadata: CalculateMetadataFunction<TravelTimelineProps> = ({props}) => {
  const parsed = travelTimelineSchema.parse(props);
  return {
    durationInFrames: getTravelTimelineDuration(parsed.stops.length),
    props: parsed,
  };
};

export const RemotionRoot = () => {
  return (
    <Composition
      id="TravelTimeline"
      component={TravelTimeline}
      width={1080}
      height={1920}
      fps={30}
      durationInFrames={getTravelTimelineDuration(sampleTravelTimeline.stops.length)}
      defaultProps={sampleTravelTimeline}
      calculateMetadata={calculateMetadata}
    />
  );
};
