// Public files are not imported modules: honor Vite's base for runtime image URLs.
export function publicAssetUrl(filename, base = import.meta.env ? import.meta.env.BASE_URL : "/") {
  return `${base}assets/${filename}`;
}
