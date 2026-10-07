import { useEffect, useState } from "react";
import { createCachedAsyncLoader } from "./cachedAsyncLoader.js";

const loadWorldCountries = createCachedAsyncLoader(() => Promise.all([
  import("topojson-client"),
  import("world-atlas/countries-110m.json"),
]).then(([{ feature }, { default: topology }]) => feature(topology, topology.objects.countries).features));

export function useWorldCountries(enabled = true) {
  const [countries, setCountries] = useState([]);

  useEffect(() => {
    if (!enabled) return undefined;
    let active = true;
    loadWorldCountries().then((features) => {
      if (active) setCountries(features);
    }).catch(() => {
      // Keep the fallback visible; a later mount can retry the cleared cache.
      if (active) setCountries([]);
    });
    return () => { active = false; };
  }, [enabled]);

  return countries;
}

export { loadWorldCountries };
