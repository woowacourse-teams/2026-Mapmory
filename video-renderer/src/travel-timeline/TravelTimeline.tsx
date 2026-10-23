import React from "react";
import {AbsoluteFill, interpolate, useCurrentFrame, useVideoConfig} from "remotion";
import {clamp, easeInOutCubic} from "./globe-math";
import type {TravelStop, TravelTimelineProps} from "./schema";
import {HOLD_FRAMES, LEG_FRAMES, MOVE_FRAMES, START_FRAMES} from "./timing";
import {WorldGlobe} from "./WorldGlobe";

const formatDate = (date: string) => {
  const [year, month, day] = date.split("-");
  return `${year}. ${month}. ${day}`;
};

const dateLabelOf = (stop: TravelStop) => {
  const firstVisit = stop.visits[0];

  if (stop.visits.length > 1) {
    return `${formatDate(firstVisit.startDate)} 외 ${stop.visits.length - 1}회`;
  }

  if (!firstVisit.endDate || firstVisit.endDate === firstVisit.startDate) {
    return formatDate(firstVisit.startDate);
  }

  return `${formatDate(firstVisit.startDate)} – ${formatDate(firstVisit.endDate)}`;
};

const yearOf = (stop: TravelStop) => stop.visits[0].startDate.slice(0, 4);

export const TravelTimeline: React.FC<TravelTimelineProps> = ({stops}) => {
  const frame = useCurrentFrame();
  const {durationInFrames} = useVideoConfig();
  const elapsed = Math.max(0, frame - START_FRAMES);
  const segmentIndex = clamp(
    Math.floor(elapsed / LEG_FRAMES),
    0,
    stops.length - 2,
  );
  const localFrame = elapsed - segmentIndex * LEG_FRAMES;
  const progress = frame < START_FRAMES ? 0 : easeInOutCubic(localFrame / MOVE_FRAMES);
  const arrival = easeInOutCubic((localFrame - MOVE_FRAMES) / HOLD_FRAMES);
  const incoming = easeInOutCubic((progress - 0.76) / 0.24);
  const outgoing = 1 - easeInOutCubic(progress / 0.16);
  const activeStop = progress < 0.76 ? stops[segmentIndex] : stops[segmentIndex + 1];
  const labelOpacity = Math.max(outgoing, incoming);
  const labelOffset = interpolate(labelOpacity, [0, 1], [24, 0]);
  const years = [...new Set(stops.map(yearOf))];
  const activeYear = yearOf(activeStop);
  const fadeIn = clamp(frame / 15, 0, 1);
  const fadeOut = clamp((durationInFrames - frame) / 18, 0, 1);

  return (
    <AbsoluteFill
      style={{
        backgroundColor: "#0F141C",
        color: "#F2F4F6",
        fontFamily: "Pretendard, Apple SD Gothic Neo, sans-serif",
        opacity: fadeIn * fadeOut,
        overflow: "hidden",
      }}
    >
      <WorldGlobe
        stops={stops}
        segmentIndex={segmentIndex}
        progress={progress}
        arrival={arrival}
      />

      <div
        style={{
          position: "absolute",
          top: 1210,
          left: 0,
          right: 0,
          height: 710,
          background: "linear-gradient(180deg, rgba(15,20,28,0) 0%, #0F141C 24%, #0F141C 100%)",
        }}
      />

      <div
        style={{
          position: "absolute",
          top: 1425,
          left: 80,
          right: 80,
          textAlign: "center",
          opacity: labelOpacity,
          transform: `translateY(${labelOffset}px)`,
        }}
      >
        <div style={{fontSize: 66, fontWeight: 800, letterSpacing: -2.5}}>
          {activeStop.countryName}
        </div>
        <div style={{marginTop: 18, color: "#8B95A1", fontSize: 30, fontWeight: 600}}>
          {dateLabelOf(activeStop)}
        </div>
      </div>

      <div style={{position: "absolute", top: 1680, left: 114, right: 114}}>
        <div style={{position: "absolute", top: 54, left: 0, right: 0, height: 3, borderRadius: 99, background: "#333D4B"}} />
        <div style={{display: "flex", justifyContent: "space-between"}}>
          {years.map((year) => {
            const active = year === activeYear;
            const passed = Number(year) < Number(activeYear);
            return (
              <div key={year} style={{width: 100, textAlign: "center", color: active ? "#D6E8FF" : "#6B7684", fontSize: 26, fontWeight: active ? 750 : 600}}>
                {year}
                <div
                  style={{
                    position: "relative",
                    zIndex: 1,
                    width: active ? 17 : 12,
                    height: active ? 17 : 12,
                    margin: `${active ? 34 : 37}px auto 0`,
                    borderRadius: "50%",
                    background: active ? "#3182F6" : passed ? "#255CA0" : "#333D4B",
                    boxShadow: active ? "0 0 0 8px rgba(49,130,246,0.14)" : "none",
                  }}
                />
              </div>
            );
          })}
        </div>
      </div>
    </AbsoluteFill>
  );
};
