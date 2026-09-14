export const PHOTO_SELECTION_LIMITS = Object.freeze({
  count: 200,
  totalBytes: 500 * 1024 * 1024,
  fileBytes: 50 * 1024 * 1024,
  concurrency: 3,
});

/** Reject the whole selection before reading files; never silently omit photos. */
export function validatePhotoSelection(files) {
  if (files.length > PHOTO_SELECTION_LIMITS.count) {
    throw new Error("사진은 한 번에 최대 200장까지 선택해주세요.");
  }
  if (files.some((file) => file.size > PHOTO_SELECTION_LIMITS.fileBytes)) {
    throw new Error("사진 한 장의 크기는 50MB 이하여야 해요.");
  }
  if (files.reduce((total, file) => total + file.size, 0) > PHOTO_SELECTION_LIMITS.totalBytes) {
    throw new Error("선택한 파일의 총 크기가 500MB를 넘어요. 사진을 줄여 다시 선택해주세요.");
  }
}

/** Bound in-flight reads, preserve order, and settle a batch before propagating failure. */
export async function parsePhotoBatches(files, parsePhoto) {
  const parsed = [];
  for (let start = 0; start < files.length; start += PHOTO_SELECTION_LIMITS.concurrency) {
    const batch = await Promise.allSettled(files.slice(start, start + PHOTO_SELECTION_LIMITS.concurrency)
      .map((file, offset) => Promise.resolve().then(() => parsePhoto(file, start + offset))));
    const failure = batch.find((result) => result.status === "rejected");
    if (failure) throw failure.reason;
    parsed.push(...batch.map((result) => result.value));
    if (start + PHOTO_SELECTION_LIMITS.concurrency < files.length) {
      await new Promise((resolve) => setTimeout(resolve, 0));
    }
  }
  return parsed;
}
