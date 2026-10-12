import { useEffect, useRef, useState } from "react";
import { latestEntry } from "../howStoryAnalytics.js";
import { HOW_STORY_BAND_COUNT_MS, bandCountAt, formatHowStoryBandValue } from "../howStory.js";

// One measured value. In scroll mode it counts up once, the first time half of it is on screen (requestAnimationFrame,
// EASE_OUT); the static list and reduced motion show the final value. The live text is a box over an invisible copy
// of the final value, so the count never shifts the line, and screen readers get only the final value.
function BandValue({ count, animate }) {
  const ref = useRef(null);
  const [shown, setShown] = useState(null);
  const final = formatHowStoryBandValue(count, count.to);

  useEffect(() => {
    const element = ref.current;
    if (!animate || !element || typeof IntersectionObserver === "undefined") return undefined;
    if (window.matchMedia?.("(prefers-reduced-motion: reduce)").matches) return undefined;
    let frame = 0;
    setShown(formatHowStoryBandValue(count, 0));
    const observer = new IntersectionObserver((entries) => {
      if (!latestEntry(entries).isIntersecting) return;
      observer.disconnect();
      const start = performance.now();
      const step = (now) => {
        const u = Math.min(1, (now - start) / HOW_STORY_BAND_COUNT_MS);
        setShown(formatHowStoryBandValue(count, bandCountAt(count, u)));
        frame = u < 1 ? window.requestAnimationFrame(step) : 0;
      };
      frame = window.requestAnimationFrame(step);
    }, { threshold: 0.5 });
    observer.observe(element);
    return () => {
      observer.disconnect();
      if (frame) window.cancelAnimationFrame(frame);
      setShown(null);
    };
  }, [animate, count]);

  return (
    <dd className="how-band-value" ref={ref}>
      <span className="how-band-sizer" aria-hidden="true">{final}</span>
      <span className="how-band-live" aria-hidden="true">{shown ?? final}</span>
      <span className="sr-only">{final}</span>
    </dd>
  );
}

// The measured stats band after the pin. HowStory renders it only when getHowStoryBand() resolves a stat.
export function HowStoryBand({ band, animate }) {
  return (
    <div className="how-band">
      <p className="how-band-eyebrow">{band.eyebrow}</p>
      <dl className="how-band-stats">
        {band.stats.map((stat) => (
          <div className="how-band-stat" key={stat.id}>
            <dt className="how-band-label">{stat.label}</dt>
            <BandValue count={stat.count} animate={animate} />
            <dd className="how-band-footnote">{stat.footnote}</dd>
          </div>
        ))}
      </dl>
    </div>
  );
}
