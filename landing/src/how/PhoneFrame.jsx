// A clay phone: 9px bezel, a 360x780dp screen and an 8dp punch-hole camera. No clock, no status icons and no
// side buttons, so the frame stays brand-neutral and never shows a made-up time. --phone-w on an ancestor sets
// its size, and every in-screen size is a multiple of --dp.
export function PhoneFrame({ children, className = "" }) {
  return (
    <div className={`how-phone ${className}`.trim()}>
      <div className="how-phone-screen">
        {children}
        <span className="how-phone-punch" />
      </div>
    </div>
  );
}
