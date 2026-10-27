export type PaletteName = "midnight" | "horizon" | "orbit";

export type TravelPalette = {
  background: string;
  text: string;
  muted: string;
  active: string;
  route: string;
  reached: string;
  unvisited: string;
  oceanCenter: string;
  oceanMiddle: string;
  oceanEdge: string;
  divider: string;
};

export const palettes: Record<PaletteName, TravelPalette> = {
  midnight: {
    background: "#0F141C",
    text: "#F2F4F6",
    muted: "#8B95A1",
    active: "#4593FC",
    route: "#3182F6",
    reached: "#255CA0",
    unvisited: "#273447",
    oceanCenter: "#1D2A3C",
    oceanMiddle: "#152033",
    oceanEdge: "#0E1624",
    divider: "#333D4B",
  },
  horizon: {
    background: "#050B13",
    text: "#F4F5F2",
    muted: "#B0BDC2",
    active: "#E9D6AD",
    route: "#DFC589",
    reached: "#8D957D",
    unvisited: "#627068",
    oceanCenter: "#4A849D",
    oceanMiddle: "#1D5873",
    oceanEdge: "#071827",
    divider: "#344650",
  },
  orbit: {
    background: "#050712",
    text: "#F0F0FF",
    muted: "#A5ACCA",
    active: "#C6B8FF",
    route: "#9AA6FF",
    reached: "#4B5FB5",
    unvisited: "#222B4B",
    oceanCenter: "#101B3B",
    oceanMiddle: "#0A1228",
    oceanEdge: "#040611",
    divider: "#252847",
  },
};

export type TravelScene = "map" | "orbit";
