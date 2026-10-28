import React from "react";
import {AbsoluteFill, interpolate, useCurrentFrame, useVideoConfig} from "remotion";
import {clamp, easeInOutCubic} from "./globe-math";
import type {TravelStop, TravelTimelineProps} from "./schema";
import {HOLD_FRAMES, LEG_FRAMES, MOVE_FRAMES, START_FRAMES} from "./timing";
import {WorldGlobe} from "./WorldGlobe";
import {palettes, type PaletteName, type TravelScene} from "./palette";

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
type TravelTimelineVariant = "classic" | "zoom";

const hexWithAlpha = (hex: string, alpha: number) => {
  const channels = hex.replace("#", "").match(/.{2}/g)?.map((value) => Number.parseInt(value, 16)) ?? [0, 0, 0];
  return `rgba(${channels[0]}, ${channels[1]}, ${channels[2]}, ${alpha})`;
};

const SpaceField: React.FC<{frame: number}> = ({frame}) => (
  <AbsoluteFill
    style={{
      background:
        "radial-gradient(ellipse at 24% 29%, rgba(66,73,155,0.35), transparent 43%), radial-gradient(ellipse at 80% 54%, rgba(76,42,117,0.25), transparent 46%), #050712",
    }}
  >
    {Array.from({length: 200}, (_, index) => {
      const left = (index * 137.508) % 100;
      const top = (index * 79.37 + 9) % 100;
      const size = index % 10 === 0 ? 3 : index % 3 === 0 ? 2 : 1;
      const opacity = 0.27 + ((Math.sin(frame * 0.037 + index * 9) + 1) / 2) * 0.55;
      return (
        <div
          key={index}
          style={{
            position: "absolute",
            left: `${left}%`,
            top: `${top}%`,
            width: size,
            height: size,
            borderRadius: "50%",
            background: index % 9 === 0 ? "#C9C4FF" : "#FFFFFF",
            opacity,
            boxShadow: size === 3 ? "0 0 10px rgba(201,196,255,0.8)" : undefined,
          }}
        />
      );
    })}
  </AbsoluteFill>
);


export type TravelTimelinePreviewProps = TravelTimelineProps & {
  variant?: TravelTimelineVariant;
  palette?: PaletteName;
  scene?: TravelScene;
};

export const TravelTimeline: React.FC<TravelTimelinePreviewProps> = ({
  stops,
  variant = "classic",
  palette: paletteName = "midnight",
  scene = "map",
}) => {
  const frame = useCurrentFrame();
  const {durationInFrames} = useVideoConfig();
  const elapsed = Math.max(0, frame - START_FRAMES);
  const segmentIndex = clamp(Math.floor(elapsed / LEG_FRAMES), 0, stops.length - 2);
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
  const zoomed = variant === "zoom";
  const selectedPalette = palettes[paletteName];
  const fadeIn = clamp(frame / 15, 0, 1);
  const fadeOut = clamp((durationInFrames - frame) / 18, 0, 1);
  const activeYearIndex = years.indexOf(activeYear);
  const lineProgress = activeYearIndex / Math.max(1, years.length - 1);

  return (
    <AbsoluteFill
      style={{
        backgroundColor: selectedPalette.background,
        color: selectedPalette.text,
        fontFamily: "Pretendard, Apple SD Gothic Neo, sans-serif",
        opacity: fadeIn * fadeOut,
        overflow: "hidden",
      }}
    >
      {scene === "orbit" ? <SpaceField frame={frame} /> : null}
      <WorldGlobe
        stops={stops}
        segmentIndex={segmentIndex}
        progress={progress}
        arrival={arrival}
        zoomed={zoomed}
        palette={paletteName}
      />

      <div
        style={{
          position: "absolute",
          top: zoomed ? 850 : 1210,
          left: 0,
          right: 0,
          height: zoomed ? 1070 : 710,
          background: zoomed
            ? `linear-gradient(180deg, ${hexWithAlpha(selectedPalette.background, 0)} 0%, ${hexWithAlpha(selectedPalette.background, 0.35)} 20%, ${hexWithAlpha(selectedPalette.background, 0.91)} 46%, ${selectedPalette.background} 68%, ${selectedPalette.background} 100%)`
            : `linear-gradient(180deg, ${hexWithAlpha(selectedPalette.background, 0)} 0%, ${selectedPalette.background} 25%, ${selectedPalette.background} 100%)`,
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
        <div style={{fontSize: 66, fontWeight: 800, letterSpacing: -2.5}}>{activeStop.countryName}</div>
        <div style={{marginTop: 18, color: selectedPalette.muted, fontSize: 30, fontWeight: 600}}>{dateLabelOf(activeStop)}</div>
      </div>

      <div style={{position: "absolute", top: 1680, left: 114, right: 114}}>
        <div
          style={{
            position: "absolute",
            top: 54,
            left: 0,
            right: 0,
            height: 3,
            borderRadius: 99,
            background: `linear-gradient(90deg, ${selectedPalette.route} 0%, ${selectedPalette.route} ${lineProgress * 100}%, ${selectedPalette.divider} ${lineProgress * 100}%, ${selectedPalette.divider} 100%)`,
          }}
        />
        <div style={{display: "flex", justifyContent: "space-between"}}>
          {years.map((year) => {
            const active = year === activeYear;
            const passed = Number(year) < Number(activeYear);
            return (
              <div key={year} style={{width: 100, textAlign: "center", color: active ? selectedPalette.text : selectedPalette.muted, fontSize: 26, fontWeight: active ? 750 : 600}}>
                {year}
                <div
                  style={{
                    position: "relative",
                    zIndex: 1,
                    width: active ? 17 : 12,
                    height: active ? 17 : 12,
                    margin: `${active ? 34 : 37}px auto 0`,
                    borderRadius: "50%",
                    background: active ? selectedPalette.route : passed ? selectedPalette.reached : selectedPalette.divider,
                    boxShadow: active ? `0 0 0 8px ${hexWithAlpha(selectedPalette.route, 0.14)}` : "none",
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
