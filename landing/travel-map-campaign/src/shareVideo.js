import { downloadBlob } from "./videoRenderer.js";

// Keep the rendered result usable when native sharing is denied or unavailable.
export async function shareVideo(blob, {
  shareNavigator = globalThis.navigator,
  createFile = (parts, name, options) => new File(parts, name, options),
  download = downloadBlob,
} = {}) {
  const extension = blob.type.includes("mp4") ? "mp4" : "webm";
  const filename = `mapmory-2026-travel-map.${extension}`;
  try {
    if (shareNavigator?.share && shareNavigator?.canShare) {
      const file = createFile([blob], filename, { type: blob.type });
      if (shareNavigator.canShare({ files: [file] })) {
        await shareNavigator.share({
          title: "2026 지금까지의 여행",
          text: "사진으로 완성한 나의 2026 여행 지도 · Mapmory",
          files: [file],
        });
        return "shared";
      }
    }
  } catch (error) {
    if (error?.name === "AbortError") return "cancelled";
  }
  // Outside the share catch: a failed download must reach the retryable UI error.
  await download(blob, filename);
  return "downloaded";
}
