import {Composition, type CalculateMetadataFunction} from "remotion";
import {sampleTravelTimeline} from "./travel-timeline/sample-data";
import {travelTimelineSchema} from "./travel-timeline/schema";
import {getTravelTimelineDuration} from "./travel-timeline/timing";
import {TravelTimeline, type TravelTimelinePreviewProps} from "./travel-timeline/TravelTimeline";
import {ReferenceTimeline, type ReferenceTimelineProps} from "./travel-timeline/ReferenceTimeline";

const calculateMetadata: CalculateMetadataFunction<TravelTimelinePreviewProps> = ({props}) => {
  const parsed = travelTimelineSchema.parse(props);
  return {
    durationInFrames: getTravelTimelineDuration(parsed.stops.length),
    props: {
      ...parsed,
      variant: props.variant ?? "classic",
      palette: props.palette ?? "midnight",
      scene: props.scene ?? "map",
    },
  };
};

const calculateReferenceMetadata: CalculateMetadataFunction<ReferenceTimelineProps> = ({props}) => {
  const parsed = travelTimelineSchema.parse(props);
  return {
    durationInFrames: getTravelTimelineDuration(parsed.stops.length),
    props: {...parsed, referenceStyle: props.referenceStyle},
  };
};

export const RemotionRoot = () => {
  return (
    <>
    <Composition
      id="TravelTimeline"
      component={TravelTimeline}
      width={1080}
      height={1920}
      fps={30}
      durationInFrames={getTravelTimelineDuration(sampleTravelTimeline.stops.length)}
      defaultProps={{...sampleTravelTimeline, variant: "classic"}}
      calculateMetadata={calculateMetadata}
    />
    <Composition
      id="TravelTimelineZoom"
      component={TravelTimeline}
      width={1080}
      height={1920}
      fps={30}
      durationInFrames={getTravelTimelineDuration(sampleTravelTimeline.stops.length)}
      defaultProps={{...sampleTravelTimeline, variant: "zoom"}}
      calculateMetadata={calculateMetadata}
    />
    <Composition
      id="TravelTimelineOrbit"
      component={TravelTimeline}
      width={1080}
      height={1920}
      fps={30}
      durationInFrames={getTravelTimelineDuration(sampleTravelTimeline.stops.length)}
      defaultProps={{...sampleTravelTimeline, variant: "classic", palette: "orbit", scene: "orbit"}}
      calculateMetadata={calculateMetadata}
    />
    <Composition
      id="TravelTimelineHorizon"
      component={ReferenceTimeline}
      width={1080}
      height={1920}
      fps={30}
      durationInFrames={getTravelTimelineDuration(sampleTravelTimeline.stops.length)}
      defaultProps={{...sampleTravelTimeline, referenceStyle: "horizon"}}
      calculateMetadata={calculateReferenceMetadata}
    />
    </>
  );
};
