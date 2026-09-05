import assert from "node:assert/strict";
import test from "node:test";
import { pickOriginalPhotoFiles, supportsOriginalPhotoPicker } from "../src/photoPicker.js";

test("uses the original file picker only in a secure supported context", () => {
  assert.equal(supportsOriginalPhotoPicker({ isSecureContext: true, showOpenFilePicker() {} }), true);
  assert.equal(supportsOriginalPhotoPicker({ isSecureContext: false, showOpenFilePicker() {} }), false);
  assert.equal(supportsOriginalPhotoPicker({ isSecureContext: true }), false);
});

test("reads every selected original file from file system handles", async () => {
  const files = [{ name: "first.jpg" }, { name: "second.heic" }];
  let pickerOptions;
  const result = await pickOriginalPhotoFiles({
    isSecureContext: true,
    async showOpenFilePicker(options) {
      pickerOptions = options;
      return files.map((file) => ({ getFile: async () => file }));
    },
  });

  assert.equal(result.status, "selected");
  assert.deepEqual(result.files, files);
  assert.equal(pickerOptions.multiple, true);
  assert.equal(pickerOptions.startIn, "pictures");
  assert.ok(pickerOptions.types[0].accept["image/*"].includes(".jpg"));
});

test("treats picker cancellation separately from an unsupported browser", async () => {
  const result = await pickOriginalPhotoFiles({
    isSecureContext: true,
    async showOpenFilePicker() {
      throw new DOMException("cancelled", "AbortError");
    },
  });

  assert.equal(result.status, "cancelled");
  assert.deepEqual(result.files, []);
});
