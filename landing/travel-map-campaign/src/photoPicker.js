const PHOTO_EXTENSIONS = [".jpg", ".jpeg", ".heic", ".heif", ".png", ".tif", ".tiff", ".avif", ".webp"];

export function supportsOriginalPhotoPicker(environment = globalThis) {
  return environment?.isSecureContext === true && typeof environment?.showOpenFilePicker === "function";
}
export async function pickOriginalPhotoFiles(environment = globalThis) {
  if (!supportsOriginalPhotoPicker(environment)) {
    return { status: "unsupported", files: [] };
  }

  try {
    const handles = await environment.showOpenFilePicker({
      id: "mapmory-recap-original-photos",
      multiple: true,
      startIn: "pictures",
      excludeAcceptAllOption: false,
      types: [{
        description: "위치정보가 포함된 원본 사진",
        accept: { "image/*": PHOTO_EXTENSIONS },
      }],
    });
    const files = await Promise.all(handles.map((handle) => handle.getFile()));
    return { status: "selected", files };
  } catch (error) {
    if (error?.name === "AbortError") return { status: "cancelled", files: [] };
    return { status: "failed", files: [], error };
  }
}
