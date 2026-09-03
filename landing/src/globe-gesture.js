// A tap or the mobile page's vertical pan is not a globe exploration.
export function classifyGlobeGesture(start, point) {
  if (!start || start.pointerId !== point.pointerId) return "pending";
  const dx = Math.abs(point.clientX - start.clientX);
  const dy = Math.abs(point.clientY - start.clientY);
  if (Math.max(dx, dy) < 8) return "pending";
  return start.pointerType === "touch" && dy >= dx ? "page_scroll" : "globe_drag";
}
