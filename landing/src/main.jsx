import React from "react";
import { createRoot } from "react-dom/client";
import { App } from "./App.jsx";
import { initializeAnalytics } from "./analytics.js";
import "./styles.css";
import "./photo-finder-hero.css";
import "./how-play.css";

initializeAnalytics();

createRoot(document.getElementById("root")).render(
  <React.StrictMode>
    <App />
  </React.StrictMode>,
);
