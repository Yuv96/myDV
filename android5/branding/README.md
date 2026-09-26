# Launcher identity

`mark.svg` is the editable vector master. A white television surrounds an upward
play symbol, with short cyan and pink edge offsets on black. The upward symbol
echoes “抬头”; the television shape stays recognizable at small launcher sizes.
The square icon contains no text. This is an original geometric mark, not the
official Douyin logo.

`../branding.py` installs the following during the GitHub Actions APK build:

- Opaque PNG launcher icons at mdpi through xxxhdpi (48–192 px), usable on API 21.
- A 320 × 180 px xhdpi Android TV banner with the complete name “抖音抬头版”.
- API 26 adaptive foreground/background and API 33 monochrome icon layers.
- Application and every launcher activity/alias icon, round icon, label and banner
  references. Existing explicit icons on other activities use the same identity.
- A required Leanback feature declaration and optional touchscreen declaration.

The adaptive foreground fits within the central 66 px safe circle of the 108 px
viewport; the legacy square uses a closer crop for legibility. The banner leaves
at least 24 px horizontal padding and uses Noto Sans CJK for its real Chinese text.
Pillow reads the packaged font directly, so SVG text rendering and host font
fallback cannot silently replace the app name with missing-glyph boxes.

CI installs `CairoSVG`, `Pillow` and Ubuntu's `fonts-noto-cjk` package. Override
`BRANDING_CJK_FONT` only with a Noto Sans CJK font containing Simplified Chinese.
The build also writes `build/branding-preview/icon.png` (512 px) and
`build/branding-preview/banner.png` (1280 × 720 px) for visual review. The generated
previews are build artifacts; the SVG and Python implementation are the source.

Per the repository policy, rendering, APK resource checks and launcher smoke tests
run in GitHub Actions only. Check the packaged manifest and both API 21 launcher
appearance and modern masked icons there.
