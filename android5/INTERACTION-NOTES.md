# Android 5.0 interaction update (1.1.9-a5.2)

The software H.264 player and next-video cache are unchanged from a5.1. Minimum SDK remains 21. Package and release signing identity are unchanged, allowing an in-place update of the community Android5 app.

## Remote control

- MENU opens a right panel with five large text rows: 喜欢、关注、收藏、主页、分享.
- MENU again opens the existing comment panel. MENU while comments are open closes it.
- BACK closes a panel. BACK during normal feed playback opens playback/settings. Returning from an author's video list keeps the existing back navigation.
- All ModernMenuHelper menus ignore icon arrays; selected picker values use the Chinese suffix （当前） instead of a checkmark glyph.
- Danmaku, speed, quality, personalization, Cookie and other settings live in the BACK menu.

## Account features and limits

Import your own Cookie with the existing Settings Cookie workflow. This release never includes development/test credentials. New account features do not use the upstream shared default account.

- Reads liked, followed and collected state from video detail. Like/collect can toggle; already-followed authors display 已关注 and cannot be accidentally unfollowed from this quick panel. Follow requests with pending status display 关注待确认.
- Mutations are submitted once per click and followed by a state read. An unconfirmed or rejected response does not display success. Real like/follow/collect mutations have **not** been executed against the validation account during development; server-side acceptance still needs device verification.
- Home opens the current author's existing profile screen.
- Followed live uses `/webcast/web/feed/follow/` with the followed-feed scene, filtering recommended entries and explicitly unfollowed owners. It distinguishes an empty list from login/API errors and offers refresh/retry. Stream selection prefers SD1 for older CPUs. List availability was checked against an authenticated account; playback availability also depends on the stream and region.
- Clock adds ` | +N` for positive unread **notification** counts, polling at most once per minute while the app has focus. It fetches no message contents, sends no read receipts, and hides stale counts on errors/account changes. Private-chat unread totals are **not yet included**.
- Friend video sharing is **not implemented**. The fifth row clearly explains this. The current Lite app has no IM client, and a reliable Cookie-based friend-video sending interface has not been validated. No text composer or test messages are added.

## Verification

`InteractionSelfTestActivity` uses synthetic data only to test menu ABI, five-row layout, key dispatch, notification parsing, and followed-only live filtering. CI additionally exercises the real main activity's MENU → MENU → BACK → BACK route and captures the panels on API 21, alongside the existing software playback/cache tests.

Read-only development probes confirmed authenticated profile, following list, video interaction state, notification count and followed-live responses. These probes and their private responses are not in this repository, APK, CI, or release assets.
