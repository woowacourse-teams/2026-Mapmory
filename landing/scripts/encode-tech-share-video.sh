#!/usr/bin/env bash
# Encodes an app timeline share video (1080x1920) for the #how tech section's share scene (TECH_SHARE.media in
# src/techStory.js): crops it to the globe and the country name, drops audio, scales to 540 wide, and writes an mp4
# (H.264, for Safari) and a webm (VP9, for browsers built without H.264) plus a webp poster into public/assets/tech/.
# The crop drops the video's own year bar and date line: at phone-mockup size their text is about 6px, so the scene
# draws its own year buttons instead. CROP is ffmpeg's w:h:x:y in the source's pixels; the default fits the "classic"
# style (globe from y 270, name 1435-1500, date 1525-1560, years 1680-1765). Pick another for a style laid out differently.
#   scripts/encode-tech-share-video.sh <input.mp4> [name=share-classic] [crop=1080:1290:0:220] [poster-second=13.2]
set -euo pipefail
in="$1"; name="${2:-share-classic}"; crop="${3:-1080:1290:0:220}"; at="${4:-13.2}"
out="$(dirname "$0")/../public/assets/tech"
mkdir -p "$out"
ffmpeg -v error -y -i "$in" -an -vf "crop=$crop,scale=540:-2:flags=lanczos,format=yuv420p" \
  -c:v libx264 -profile:v high -preset slow -crf 27 -movflags +faststart "$out/$name.mp4"
ffmpeg -v error -y -i "$in" -an -vf "crop=$crop,scale=540:-2:flags=lanczos,format=yuv420p" \
  -c:v libvpx-vp9 -crf 38 -b:v 0 -row-mt 1 -deadline good -cpu-used 2 "$out/$name.webm"
ffmpeg -v error -y -ss "$at" -i "$in" -frames:v 1 -vf "crop=$crop,scale=540:-2:flags=lanczos" -c:v libwebp -quality 78 "$out/$name.webp"
ls -l "$out/$name.mp4" "$out/$name.webm" "$out/$name.webp"
