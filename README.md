# CW News

Offline-friendly Android app that fetches RSS and Atom news and sends the headlines, optionally with summaries, as audible Morse code. A synthesized Ionosphere or Cosmic background can play underneath. No account, server, telemetry or ads.

Current version: 1.0. Android 8.0 (API 26) or newer.

## Install

Copy `CWNews.apk` to the phone, open it and allow installs from that source.

Try it: Feeds > tap Hacker News > Add. Settings > 20 WPM, effective 12 WPM, 600 Hz. Listen > Play.

## Build

Needs: Android SDK with build-tools 36.0.0 and platforms;android-36, JDK 17 or newer, zip. Tested on Linux only. There is no Gradle.

1. Set `ANDROID_SDK_ROOT`.
2. Put a keystore and `signing.env` in the project folder. `signing.env` holds `KS_FILE=...` and `KS_PASS=...`. Neither is in git.
3. Run `bash build.sh`.

A different signing key means a new version can't install over the old one.

GitHub Actions (`.github/workflows/android.yml`) runs the tests and uploads an APK. Without the `KEYSTORE_B64` and `KS_PASS` secrets it signs with a throwaway key.

## Tests

- `bash tests/run.sh`: Morse, timing, text cleaning, RSS/Atom parsing, queue and audio mix, on a PC.
- `python3 tests/ui.py`: the screen in Chromium with a fake Android bridge (Playwright).

## How it works

- `Morse`, `Timing`, `Keying`: text to sample-exact on/off runs. Farnsworth stretches only the letter and word gaps.
- `Engine`: CW plus background. Morse position depends only on the CW sample count.
- `Bed`: the two backgrounds, generated in code with no audio files.
- `TextNorm`, `FeedParser`, `Refresher`, `Db`: feeds, cleaning and the offline cache.
- `CwService`: foreground media service, notification, headset controls and audio focus.
- `RefreshJob`: periodic refresh when idle. Android decides the exact times.

## Permissions

| Permission | Why |
|---|---|
| INTERNET, ACCESS_NETWORK_STATE | Fetch feeds and tell offline from a failed fetch |
| FOREGROUND_SERVICE, FOREGROUND_SERVICE_MEDIA_PLAYBACK, WAKE_LOCK | Keep playing with the screen off |
| POST_NOTIFICATIONS | Show the playback controls |
| RECEIVE_BOOT_COMPLETED | Keep the periodic refresh after a restart |

## Troubleshooting

- No notification controls: allow notifications for CW News in Android settings.
- Stops after a while with the screen off: set the app's battery use to Unrestricted.
- A feed shows an error: only https:// feeds are accepted, and the reason is shown under the feed.
- Summaries are short: feeds carry what the publisher provides, and the app doesn't scrape articles.

## Sources

See `THIRD_PARTY.md`. MIT licensed, see `LICENSE`.
