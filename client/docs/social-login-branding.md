# Social login button artwork

Checked against the provider documentation on 2026-10-28. These assets are used only for the corresponding authentication action. Keep their geometry and brand colors; do not replace them with emoji, text glyphs, or generic icons.

| Provider | Source | Implementation |
| --- | --- | --- |
| Google | [Branding guidelines](https://developers.google.com/identity/branding-guidelines), [official gradient G](https://developers.google.com/static/identity/images/g-logo.png) | Original PNG, aspect ratio preserved; white fill, #747775 border, #1F1F1F text; Google Sans Medium 14/20, localized Continue with Google label. White is retained in both app themes. |
| Kakao | [Design guide](https://developers.kakao.com/docs/ko/kakaologin/design-guide), [official Korean button SVG](https://developers.kakao.com/tool/images/resource/preview/login-complete-ko.svg) | First SVG path is the official speech bubble. Converted to Android vector format with translation only; black symbol, #FEE500 background, black text at 85% opacity, system font and 카카오 로그인 label. |
| Apple | [Human Interface Guidelines](https://developer.apple.com/design/human-interface-guidelines/sign-in-with-apple), [official artwork](https://devimages-cdn.apple.com/design/resources/download/Logo-Sign-in-with-Apple.dmg) | Left-aligned medium black/white SVG paths converted to Android vectors. Preserve the complete 31×44 canvas and built-in padding; canvas height matches button height. System-font localized Sign in with Apple label, font size 43% of button height; black/light and white/dark variants. |

Buttons have equal full-width placement and a minimum 48 dp touch target. Decorative logos have no separate accessibility description; the actionable text labels the button. Existing enabled state, provider availability, session recovery and analytics callbacks stay in SocialLoginPanel.

Google Sans comes from [google/fonts](https://github.com/google/fonts/tree/main/ofl/googlesans). The bundled static font was instantiated from GoogleSans[GRAD,opsz,wght].ttf at wght=500, opsz=14, GRAD=0 with fontTools, then subset to U+0020–007E for the Google label; Korean glyphs use the platform fallback. See [OFL](licenses/GoogleSans-OFL.txt). Apple artwork terms are retained in [Apple Design Resources License](licenses/Apple-Design-Resources-License.rtf). Logos remain trademarks of their respective owners.
