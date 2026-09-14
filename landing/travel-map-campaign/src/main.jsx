import React from "react";
import { createRoot } from "react-dom/client";
import { initializeCampaignAnalytics } from "./analytics.js";
import { App } from "./App.jsx";
import "./styles.css";

initializeCampaignAnalytics();

createRoot(document.getElementById("root")).render(
  <React.StrictMode>
    <App />
  </React.StrictMode>,
);
