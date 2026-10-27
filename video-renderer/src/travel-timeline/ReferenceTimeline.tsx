import React from "react";
import {AbsoluteFill, useCurrentFrame, useVideoConfig} from "remotion";
import {clamp, easeInOutCubic} from "./globe-math";
import {palettes} from "./palette";
import type {TravelStop, TravelTimelineProps} from "./schema";
import {END_FRAMES, HOLD_FRAMES, LEG_FRAMES, MOVE_FRAMES, START_FRAMES} from "./timing";
import {WorldGlobe} from "./WorldGlobe";

export type ReferenceTimelineProps = TravelTimelineProps & {referenceStyle: "horizon"};

const dateLabelOf = (stop: TravelStop) => {
  const firstVisit = stop.visits[0];
  const format = (date: string) => {
    const [year, month, day] = date.split("-");
    return `${year}. ${month}. ${day}`;
  };

  if (stop.visits.length > 1) return `${format(firstVisit.startDate)} 외 ${stop.visits.length - 1}회`;
  if (!firstVisit.endDate || firstVisit.endDate === firstVisit.startDate) return format(firstVisit.startDate);
  return `${format(firstVisit.startDate)} – ${format(firstVisit.endDate)}`;
};

const yearOf = (stop: TravelStop) => stop.visits[0].startDate.slice(0, 4);

const StarField: React.FC<{frame: number}> = ({frame}) => (
  <AbsoluteFill style={{background: "radial-gradient(ellipse at 50% 43%, rgba(40,73,120,0.16), transparent 55%), #050811"}}>
    {Array.from({length: 88}, (_, index) => {
      const x = (index * 137.508 + 7) % 100;
      const y = (index * 79.37 + 3) % 100;
      const size = index % 17 === 0 ? 3 : index % 5 === 0 ? 2 : 1;
      const opacity = 0.17 + ((Math.sin(frame * 0.04 + index * 8) + 1) / 2) * 0.45;

      return (
        <div
          key={index}
          style={{
            position: "absolute",
            left: `${x}%`,
            top: `${y}%`,
            width: size,
            height: size,
            borderRadius: "50%",
            background: "#DCE7EE",
            opacity,
          }}
        />
      );
    })}
  </AbsoluteFill>
);

const YearRail: React.FC<{
  years: string[];
  activeYear: string;
  color: string;
  muted: string;
  divider: string;
  background: string;
}> = ({years, activeYear, color, muted, divider, background}) => {
  const activeIndex = years.indexOf(activeYear);
  const progress = activeIndex / Math.max(1, years.length - 1);

  return (
    <div style={{position: "absolute", top: 1680, left: 114, right: 114, zIndex: 8}}>
      <div
        style={{
          position: "absolute",
          top: 54,
          left: 0,
          right: 0,
          height: 3,
          borderRadius: 99,
          background: `linear-gradient(90deg, ${color} 0%, ${color} ${progress * 100}%, ${divider} ${progress * 100}%, ${divider} 100%)`,
        }}
      />
      <div style={{display: "flex", justifyContent: "space-between"}}>
        {years.map((year) => {
          const active = year === activeYear;
          const passed = Number(year) < Number(activeYear);

          return (
            <div key={year} style={{width: 100, textAlign: "center", color: active ? "#F4F7FB" : muted, fontSize: 26, fontWeight: active ? 750 : 600}}>
              {year}
              <div
                style={{
                  position: "relative",
                  zIndex: 1,
                  width: active ? 17 : 12,
                  height: active ? 17 : 12,
                  margin: `${active ? 34 : 37}px auto 0`,
                  borderRadius: "50%",
                  background: active ? color : passed ? color : divider,
                  opacity: active || passed ? 1 : 0.74,
                  boxShadow: active ? `0 0 0 8px ${color}24` : `0 0 0 7px ${background}88`,
                }}
              />
            </div>
          );
        })}
      </div>
    </div>
  );
};

export const ReferenceTimeline: React.FC<ReferenceTimelineProps> = ({stops}) => {
  const frame = useCurrentFrame();
  const {durationInFrames} = useVideoConfig();
  const elapsed = Math.max(0, frame - START_FRAMES);
  const segmentIndex = clamp(Math.floor(elapsed / LEG_FRAMES), 0, stops.length - 2);
  const localFrame = elapsed - segmentIndex * LEG_FRAMES;
  const progress = frame < START_FRAMES ? 0 : easeInOutCubic(localFrame / MOVE_FRAMES);
  const arrival = easeInOutCubic((localFrame - MOVE_FRAMES) / HOLD_FRAMES);
  const activeStop = stops[progress < 0.76 ? segmentIndex : segmentIndex + 1];
  const activeYear = yearOf(activeStop);
  const years = [...new Set(stops.map(yearOf))];
  const palette = palettes.horizon;
  const fadeIn = clamp(frame / 15, 0, 1);
  const fadeOut = clamp((durationInFrames - frame) / END_FRAMES, 0, 1);

  return (
    <AbsoluteFill style={{background: palette.background, color: palette.text, fontFamily: "Pretendard, Apple SD Gothic Neo, sans-serif", overflow: "hidden", opacity: fadeIn * fadeOut}}>
      <StarField frame={frame} />
      <WorldGlobe stops={stops} segmentIndex={segmentIndex} progress={progress} arrival={arrival} horizon palette="horizon" />
      <div style={{position: "absolute", top: 280, left: 80, right: 80, zIndex: 4, textAlign: "center", textShadow: "0 4px 30px rgba(0,0,0,0.55)"}}>
        <div style={{fontSize: 76, lineHeight: 1.08, fontWeight: 800, letterSpacing: -2.5}}>{activeStop.countryName}</div>
        <div style={{marginTop: 12, fontSize: 22, color: palette.muted, fontWeight: 550}}>{dateLabelOf(activeStop)}</div>
      </div>
      <div style={{position: "absolute", top: 1370, left: 0, right: 0, height: 550, zIndex: 5, background: "linear-gradient(180deg, transparent, rgba(5,11,19,0.9) 46%, #050B13 90%)"}} />
      <YearRail years={years} activeYear={activeYear} color={palette.route} muted={palette.muted} divider={palette.divider} background={palette.background} />
    </AbsoluteFill>
  );
};
