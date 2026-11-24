# Story station: film and book nets as CW exchanges (2.12.0)

## Context

The owner made five stories as radio nets, with a prompt in the local `ham_radio` project (not on GitHub): LV-426 Net, Hail Mary Net, Terminus Net, Endurance Net and Mars Net - pro. The app should play them as stations talking to each other, each station slightly different, at least about 100 Hz apart in pitch. It should also play plain `.txt` books, with a setting for which story plays and a frequency of its own. The prompt goes into the docs, so new nets can be made. The app is lean and good as it is: add one feature, no clutter.

## The source files (measured)

The owner uploaded them in this session: `/root/.claude/uploads/2b604a20-30e5-50fe-9c00-c80aca48bc96/*.txt`, with the matching `*.srt`. The `.txt` files are the source; the SRTs are word-by-word subtitles rendered from them.

```
Hail Mary Net                                   <- title, line 1

  0.13 min  === EP 1  WAKING UP ===             <- episode marker
  0.13 min  MRY -> ALL  VVV VVV DE MRY <BT> HAIL MARY SHIP COMPUTER <BT> ...
  1.23 min  MRY -> HMY  BED 3 DE MRY <BT> ... WHAT IS 2 PLUS 2 ?
Music: Eternity by Stellardrone ...             <- credit, last line: ignore
```

- 464–668 messages per net, 14–30 episodes, 7–24 calls. Overs have a median of 14–40 words and at most 58. Each net runs 6–12 hours. The text files total 380 KB, about 90 KB in the APK.
- Prosigns are written `<BT> <KN> <AR> <SK> <AS>`, and `Morse.parse` already sends `<XX>` as a prosign. **But `TextNorm.forMorse` drops `<` and `>`**, so `Script` must clean only the text between prosign tokens.
- A few lowercase letters appear; they are upper-cased.
- The `t min` timestamps are the original render at a fixed speed. Here the keying follows the user's WPM, so only the order and the episode breaks are kept: about 1.5–3 s between overs, 10 s at an episode marker.
- The SRT headers give each call a full name ("RKY" = "Rocky, Eridian engineer on the Blip-A"). Step 0 adds these once as legend lines at the top of each shipped file.

## Decisions

- **No News/Story switch.** The story is a second fixed station on the band, and you tune to it (as in 2.10.4). The Listen tab stays the news player.
- **7010 kHz.** Near the start of the scale, as asked, but not at the edge, where the Wide filter would hang outside the band. It is 10 kHz from the news and the Signal buttons reach it.
- **Pitch.** There are six offsets from 7010: −75, −45, −15, +15, +45 and +75 Hz (30 Hz steps: the ear tells 20 Hz apart, and this only has to tell stations apart). All of them stay inside the Narrow ±125 Hz filter. Calls are assigned in order of how much they talk, each to the free offset farthest from the partners it talks to most, so the usual pairs (HMY/RKY, HAB/HOU) are at least 60 Hz apart, and three mutual partners fit. More than 160 Hz in total would lose the edge speakers in Narrow. Each call also keeps its own fist (keyer, bug, straight key, sideswiper in turn), a strength between S7 and S9, and gentle fading, with no chirp or drift. All of this is seeded from the title, so a character always sounds the same.
- **The story waits for you.** It does not run while you are tuned away. If you leave in the middle of an over, that over restarts when you come back. The position is saved, because a net runs 6–12 hours. At the end it waits 60 s and starts over.
- **Ship the five nets** in `app/assets/stories/`, because the owner asked for them. Risk, stated once: the repo and the releases are public, and these retell copyrighted books and films. Leaving them out of git and using **Add a file** on the phone would avoid that.
- **No SRT support.** The `.txt` is the source and the prompt makes `.txt`. Supported formats: **net** (the format above) and **plain book**.
- **Settings > Band**, one block, "Story station, 7010 kHz": a story menu (Off, the five, added files), **Add a .txt file**, and **Remove** for added files. That is all; the position is not set in Settings.
- **Position without clutter.**
  - By default the story **resumes where you stopped**, saved automatically. Most of the time nothing needs setting.
  - To move, tap the "Story: …" status line on the Band tab. It is shown only while you are tuned to 7010, and it gets a small ▸ to show that it opens.
  - That opens a panel, closed by default and closed again when you tune away, with three controls:
    - **Episode** menu: start of episode n.
    - **Position** slider over the whole story, with the label `EP 3 · over 41 of 668`. It takes effect on release.
    - **Again**: repeat the over just sent. This is the one practice button, for copying a missed over.
  - No new permanent buttons, and nothing on the Listen tab.
  - A jump takes effect at the next over boundary. The current over stops at the end of its word, so it does not click.

## Step 0 — files (Sonnet)

**Done in the planning session (2.12.0 branch):** the five nets are in `app/assets/stories/`, byte for byte, with `@ CALL = name` legend lines after the title (taken from the SRT headers; every call is named). Nothing left to do here.

## Step 1 — `Script` (new pure class, Sonnet)

`app/src/pl/cwnews/Script.java`, with no Android imports. Add it to `CLAUDE.md`'s list and to `tests/run.sh`.

- `static Script parse(String fileName, String text)` returns `title`, `List<Line>` (with `speaker`, `to`, `text`, `pauseS` and `episode`), `calls[]`, `names[]` (from the `@` lines, otherwise the call) and `episodes` (the titles with their first line index).
- **Net**: most lines match `^\s*[\d.]+ min  (.+)$`, and the body is `FROM -> TO  TEXT` or `=== EP n TITLE ===`. Other lines are ignored. Text is upper-cased and split on `<XX>`; each piece goes through `TextNorm.forMorse`, and the prosign tokens are rejoined. A line that ends up empty is dropped.
- **Plain book** (anything else): strip Project Gutenberg's `*** START OF` and `*** END OF` sections. One station, called by the short title. Paragraphs are cut into overs of at most about 40 words at sentence ends, each ending `<BT>`. Chapter headings (`CHAPTER …`, a short all-caps line) become episodes.
- Handle a byte order mark (BOM), CRLF line ends and empty files.
- `static int[] offsetsHz(Script s)`: the pitch rule.

## Step 2 — the story station in `Band` (**Opus**: audio thread, timing and thread-safety)

`app/src/pl/cwnews/Band.java`, plus a `Qso.story(...)` factory and a new kind `Qso.STORY` in `Qso.java`.

- `static final double STORY = 7010`. Add `synchronized void setStory(Script s, int line)` (`null` means off), `int storyLine()`, and `String storyNow()` (the current sender's name and episode).
- One extra `Station` outside `slots`, fed chunks of about 20 lines as a `Qso`, with `Op`s from the offsets, fists and strengths above. **It has its own `Dsp.Rng`**, because the order of the band's random draws is part of its behaviour (see the `initOp` comment). With the story off, the band renders bit for bit as before.
- Render it in the same per-station loop: move the body into a method rather than copying it. At `startTx`, outside the passband (the `inPass` test), it waits. If it leaves the passband in the middle of an over, the over is dropped and `next` is kept, so it repeats.
- With a story on: `freeFrequency` and `clearAt` keep 0.4 kHz away from STORY; `next()` lists it; `follow()` does not move the dial away while the story is keyed in the filter. `cqNear` ignores the kind. The waterfall, `activityJson` and `copy()` work unchanged (`RKY: HMY DE RKY <BT> ...`); `copy()` shows prosigns as written.

## Step 3 — service and bridge (Sonnet)

- `CwService.readSettings`: read the `story` pref (`asset:<name>` or `file:<name>`), load and parse on a background thread, then `band.setStory(script, storyLine)`. Save `storyLine` next to `savePosition`.
- `MainActivity` bridge:
  - `stories()`: JSON of the titles and their episodes.
  - `pickStory()`: `ACTION_OPEN_DOCUMENT` with `text/*` and `*/*`, at most 5 MB, UTF-8, copied to `filesDir/stories/`.
  - `removeStory(name)`.
  - `storyFrom(line)`: used by the Episode menu, the slider, and Again (the line before the current one). It saves `storyLine` at once.
  - Add `story: {title, ep, line, total, now}` to the existing band-state JSON, and `episodes` to `stories()`.
  - `Band.setStoryLine(int)` drops the current over at the end of its word, the same fade as a detune.
- No new permissions.

## Step 4 — UI (Sonnet)

`app/assets/index.html`

- The Settings block, plus one dim line: "Tune to 7010 kHz. The story waits while you are away. Show their text applies to it."
- Band tab, `#tuned` (line 249): near 7010 it shows `Story: Hail Mary Net, EP 3 · Rocky, Eridian engineer on the Blip-A ▸`. A tap toggles `#spos`, the panel with the Episode menu, the Position slider and its label, and Again. The panel hides when you tune away. It reuses the existing `.row`, `.btn` and `select` styles, with no new CSS beyond the slider width.
- Add a short "Stories" paragraph to How to use.

## Step 5 — docs and version (Sonnet)

- `docs/STORIES.md`: the net format, the `@` legend, the plain-book rules, how to add a story (put a file in `app/assets/stories/` and rebuild, or **Add a .txt file** on the phone), and **the prompt**. If the owner sends the original from `ham_radio`, it goes in verbatim. Otherwise a reconstruction, labelled as such, built from the measured conventions:
  - a title line, then `=== EP n TITLE ===` markers and `t min  FROM -> TO  TEXT` lines;
  - 3-letter calls and `ALL` for broadcasts, plus an `@ CALL = full name` legend;
  - procedure: `X DE Y <BT> … <KN>` (`<AR>` at the end of a message, `<SK>` at the end of a contact);
  - `QST` bulletins, `VVV` tests, `NR … CK` traffic, numbers as digits;
  - characters `A–Z 0–9 . / ? ( )` only; overs of 10–50 words; 14–30 episodes following the source's plot;
  - with the source book or script as input.
- README: a Story station entry, plus a truth pass on stale lines. The intro names AGC, RIT, three filters and CW/CW-R; it says "Nothing writes to the log yet"; and it describes a "Use real space weather" setting that is no longer in the UI. Check `Refresher`: if the K-index fetch can no longer be switched on, remove the fetch call and its README and permission mentions (`SpaceWx` stays).
- `docs/RELEASE-NOTES.md` and `docs/PLAN.md`. `AndroidManifest.xml`: `versionCode 26`, `versionName 2.12.0`.

## Not doing (lean)

No mode switch, no SRT, no story controls on the Listen tab or in the notification, no per-story speed, no original timestamps.

## Verification

- `bash tests/run.sh`, with new tests in `tests/T.java`:
  - Parse all five shipped nets: message and episode counts 624/23, 668/14, 472/30, 464/14, 620/14; the credit line is ignored.
  - Prosigns survive: a line with `<BT>` keys `-...-`.
  - A plain-book excerpt with a Gutenberg header, and an empty file.
  - Offsets: the top pairs are at least 100 Hz apart, and `Rx.gain` in Narrow is above 0.5 at ±75 Hz.
  - **Regression**: a band rendered with the story off has the same hash as before for the same seed.
  - The story waits off-tune and repeats the over after a mid-over detune; `storyLine` advances and wraps; random stations stay out of 7010 ± 0.4 kHz.
- `python3 tests/ui.py`, with the fake bridge extended (`stories`, `pickStory`, `storyFrom`):
  - the Settings block renders;
  - "Story:" shows only at 7010, and a tap opens the panel;
  - the Episode menu and the slider call `storyFrom` with the right line;
  - the panel hides after tuning away.
- `tests/T.java` also covers `setStoryLine`: the next over is the chosen line, Again repeats the last one, and the position is kept after `setStory` with the same script.
- `bash build.sh` if the SDK is here, otherwise the GitHub Actions APK. Only a build from the current source is handed over.
- Not testable here, and stated in the hand-over: the file picker on a phone, the sound by ear, and the layout on a phone.
