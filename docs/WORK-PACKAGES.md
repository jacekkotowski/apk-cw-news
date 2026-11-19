# CW News: work packages

Precise specifications for the next steps, written so that each package can be built in its own session by the model named on it. `docs/PLAN.md` has the overview, the order and the reasons.

How to use this file: start a new session, pick the model, and say "Read `CLAUDE.md` and `docs/WORK-PACKAGES.md`, then build package P1 only." One package per session keeps the cost down.

## Rules for every package

These apply whichever model builds it.

**The code**
- No Gradle. The APK is built by `build.sh`; in a cloud session there is no Android SDK, so Android code (`MainActivity`, `CwService`, `Db`, `Refresher`, `RefreshJob`) is compiled only by GitHub Actions. Push to the working branch and check that the `android` workflow run succeeds before reporting a build.
- The classes listed in `CLAUDE.md` have no Android imports and are tested on a PC by `bash tests/run.sh`. Every new class with logic in it goes on that list, into the `javac` line of `tests/run.sh`, and gets tests.
- minSdk 26: no `List.of`, `Map.of`, `String.isBlank`, `strip`, `repeat`.
- Pure classes take a seeded `Dsp.Rng`. Never use `Math.random()` or the clock inside them, or the tests stop being repeatable.
- Audio thread (`Engine.render`, `Rx.process`, `Band.render`, `Bed.fill`): no allocation, no logging, no locks other than `Band`'s own `synchronized`, no file or network access.

**Settings pattern** (an integer setting needs four small edits)
1. UI: `<div class="seg" data-k="KEY"><button data-v="0">…</button>…</div>` in `app/assets/index.html`. The existing script wires every `.seg` to `A.set(KEY, value)` and paints the selected button from `S[KEY]`.
2. `MainActivity.Bridge.settings()`: add `o.put("KEY", prefs.getInt("KEY", DEFAULT))`.
3. `CwService.readSettings()`: read the pref and pass it to `engine` or `band`.
4. `tests/ui.py`: add the key to the fake `st` object and assert the click calls `['set','KEY',value]`.

**Tests**
- PC tests live in `tests/T.java`. Helpers: `check(name, condition)`, `eq(name, got, want)`, `near(name, got, want, tolerance)`. Add one static method per package and call it from `main`.
- UI: `tests/ui.py` needs the Python Playwright module, which the cloud session lacks. Update it anyway, and check the page with a short Node Playwright script (Chromium is installed; `require('playwright')` works). Use a fake `window.Android` object as `tests/ui.py` does.
- A package is done when `bash tests/run.sh` prints `0 failed`, the UI check passes, and the GitHub Actions run for the pushed commit succeeds.

**Delivery**
- Raise `versionCode` and `versionName` in `app/AndroidManifest.xml` once per release, not per package.
- Update `README.md` ("How it works", the version line, the permissions table if it changed) and the status table in `docs/PLAN.md`.
- State plainly what was not tested on a phone.

**When to stop and hand over to a stronger model**
- A test in the package's list cannot be made to pass in two honest attempts.
- The change needs edits in `Engine.render`, `Rx.process` or `Band.render` beyond what the package describes.
- The user reports a click, dropout or distortion after the package shipped.

Do not weaken a test to get it to pass; say what failed instead.

---

## Release 2.5: listen better

Four packages. P1 to P3 are independent of each other; P4 uses P3's noise blanker.

### P1. Follow the strongest signal, and a sleep timer

**Model:** Sonnet. **Size:** small.

**What the user gets**
- Band tab: "When it goes quiet: Stay / Strongest". With Strongest, when the contact you are listening to ends, the receiver jumps to the strongest station that can be heard. It works with the screen off.
- Settings: "Stop playing after: Off / 15 / 30 / 60 min". The sound fades out over the last 20 seconds and playback stops.

**Design**

`Band.java`
- New fields: `private long quietFrames; private boolean busyHere;`.
- In `render`, per station after its sample loop, decide once per block:
  - `inPass`: `Rx.gain(loHz, hiHz, pitch) > 0.25`, where `pitch` is the pitch of the station's current operator as `render` already computes it.
  - the station counts as a contact here if `inPass && heard(s)`;
  - it counts as keyed here if `inPass`, the current operator's `strength >= UNHEARD`, and `s.onFrames` grew during this block (remember its value before the sample loop).
- After the station loop: `quietFrames = keyedHere ? 0 : quietFrames + frames; busyHere = contactHere;`.
- New method:
  ```java
  /** Where to go when this frequency has gone quiet, in kHz, or NaN to stay. */
  synchronized double follow(double rxKhz, double quietS, double deadS)
  ```
  - Stay (return `NaN`) unless `quietFrames >= quietS * rate && !busyHere`, or `quietFrames >= deadS * rate`.
  - Candidates: stations with `qso != null`, `heard(s)`, and more than 0.25 kHz from `rxKhz`.
  - Score: the strongest heard operator's `level(o)`, times 0.3 for `Qso.CQ` and `Qso.TUNE` kinds, times 0.5 if fewer than two transmissions remain (`s.qso.tx.size() - s.next < 2`).
  - No candidate: return `NaN`. Otherwise set `quietFrames = 0` and return the winner's `khz`.

`Engine.java`
- `boolean newsHeard()`: true when a band is set, a story is loaded and not finished, and the bulletin is inside the filter (the existing `off` flag is false).

`CwService.java`
- `static volatile long lastUserTune;` and `static volatile int follow;`.
- In the audio loop, every 50th block: if `follow == 1`, a band exists, playback is not paused, `!engine.newsHeard()`, and 20 s have passed since `lastUserTune`, call `band.follow(rxKhz(), 6, 75)`. On a number, set `dial = f - rit / 1000.0` and save it with `prefs.edit().putFloat("dial", …).apply()`.
- Sleep timer: `readSettings` caches `prefs.getLong("sleepAt", 0)`. In the loop, when `sleepAt > 0`: during the last 20 s scale `engine.volume` linearly to zero; at the time, clear `sleepAt` and `sleep` in prefs and stop as the STOP command does.

`MainActivity.java`
- `setDial`, `setRit` and `nextSignal` set `CwService.lastUserTune = System.currentTimeMillis()`.
- `set("sleep", minutes)` also stores `sleepAt` (`0` for Off, else now plus the minutes).
- `settings()` adds `follow` (default 0) and `sleep` (default 0); `state()` adds `sleepLeft` in whole minutes.

UI
- Band tab, under the RIT row: the `follow` seg.
- Settings, new heading "Sleep": the `sleep` seg. The Listen tab's `#msg` shows "Stops in N min" while a timer runs.

**Tests** (`follow()` in `tests/T.java`)
- A band with a seed, tuned with `next()` onto a station: `follow` returns `NaN` while that contact is in progress.
- Keep rendering until that station's slot is free: within 7 s of audio `follow` returns a frequency.
- The returned frequency is one of the stations in `activityJson()`, and it is not the frequency just left.
- With nothing else on the band (activity 0 and all slots idle) `follow` returns `NaN`.
- After a jump, an immediate second call returns `NaN`.

**Done when** the tests pass, the two segs call the bridge, and a note in the report says the screen-off behaviour is untested on a phone.

**Watch out**
- A one-sided contact has long silences while the unheard station sends. That is why "quiet" alone is not enough and `busyHere` exists.
- Do not jump while the user is turning the knob: honour `lastUserTune`.

### P2. CW and CW-R (which side of the signal you listen on)

**Model:** Sonnet. **Size:** small.

**Background for the user's question.** The rule "lower sideband below 10 MHz" is for voice. A CW signal has no sideband; the receiver chooses which side to listen from. Rigs differ in their default (Yaesu's manuals describe CW Reverse as the lower-side setting, so its normal CW is the upper side) and all offer CW-R to swap. Swapping reverses which way the pitch moves as you tune and puts an interfering station on the other side of yours, where the filter may remove it. Today the app behaves like the upper side.

**What the user gets:** Band tab, Receiver: "Sideband: CW / CW-R".

**Design**
- A sign `s`: `+1` for CW (today's behaviour), `-1` for CW-R. Pref `side` (0 or 1).
- `Engine`: `volatile int side;`. In `render`: `pitch = toneHz + sign * (Band.NEWS - dialKhz) * 1000` (keep the existing clamp), and pass the sign to `Band.render`.
- `Band.render` gets a seventh parameter `int sign` and uses `hz = toneHz + sign * (s.khz + o.offsetKhz + o.drifted - dialKhz) * 1000`. The four- and six-argument overloads pass `+1`. Store the sign in a field so `tuned()` uses the same formula.
- New pure helper in `Rx`:
  ```java
  /** The range of station frequencies heard, in Hz relative to where the receiver listens. */
  static double[] heard(int width, double tone, int sign)
  ```
  For `+1` it is `{lo - tone, hi - tone}`; for `-1` it is `{tone - hi, tone - lo}`.
- `MainActivity.band()` uses `Rx.heard` for `passLo` and `passHi`. The page needs no change: it already draws the passband from those two numbers.

**Tests**
- With sign `-1`, a station 300 Hz above the dial is rendered at `tone - 300` (measure with the existing `goertzel` helper), and at `tone + 300` with `+1`.
- The bulletin still waits when tuned outside the filter, in both settings.
- `Rx.heard` for both signs and all three widths.
- A station just above the wanted one that is audible in CW with the Wide filter is at least 20 dB weaker in CW-R, because Wide extends 1200 Hz above the pitch and only 400 Hz below.

**Watch out:** the waterfall must not flip. Only the shaded passband moves to the other side of the needle.

### P3. Filter controls: shift, peak filter, noise blanker, AGC speed

**Model:** Sonnet, with care. **Size:** medium. This edits `Rx.process`; follow the formulas and stop if a test will not pass.

**What the user gets** (Band tab, Receiver)
- "Shift" slider, minus 300 to plus 300 Hz in steps of 50: moves the passband up or down without changing the pitch, to push an interfering station out.
- "Peak filter: Off / On" (APF): lifts the station exactly at your pitch above the rest.
- "Noise blanker: Off / On": removes static ticks.
- "AGC: Off / Slow / Fast": Fast recovers quickly after a static crash; Slow is steadier on a clean signal.

**Design**

Shift
- Pref `shift` (Hz). `Engine.shift` volatile int. Where `Engine.render` computes `lo` and `hi`, add the shift to both, keeping `lo >= 100`.
- `Rx.heard` (from P2) takes the shift as well; if P2 is not built yet, add the helper here.

Peak filter
- Pref `apf` (0 or 1). In `Rx`: one `Dsp.Biquad` per channel set with `bandpass(tone, tone / 80.0, rate)` (0 dB at the centre, about 80 Hz wide).
- In `process`, after the band-pass cascade and before the envelope is measured: `l += apf.step(l)` (same for `r`). A tone at the pitch doubles (+6 dB); a tone 200 Hz away gains under 1 dB.
- `Rx` needs the pitch: add it to `tune(loHz, hiHz, toneHz)` and retune the peak filter with the others.

Noise blanker
- Pref `nb` (0 or 1). It works on the signal before the filter, where a static tick is a single-sample spike.
- Keep one sample of look-ahead per channel. For each sample compute `d = |x[n] - 0.5 * (x[n-1] + x[n+1])|` and a slow average `e` of `d` (time constant 50 ms). If `d > 4.5 * e`, replace `x[n]` with `0.5 * (x[n-1] + x[n+1])` and do not feed that `d` into `e`.
- The one-sample delay applies whether the blanker is on or off, so switching it does not shift the audio.

AGC speed
- Pref `agc`: 0 Off, 1 Slow, 2 Fast. Existing installs have 1, which stays "Slow".
- Release time: Slow 0.5 s, Fast 0.12 s. Replace the constant `RELEASE_S` with a field; recompute `releaseKeep` when it changes.
- `Engine.agc` becomes an `int`. `Rx.process` takes the mode instead of a boolean.

**Tests**
- Shift +200: `Rx.gain` with the shifted edges is 1 at `tone + 200` and below 0.1 at `tone - 250` (Normal width).
- Peak filter on, AGC off, tone of amplitude 0.3 at the pitch: output within 5% of 0.6. Same tone 200 Hz above: under 0.34.
- Blanker: noise only, AGC off, 60 s. Count output samples above four times the RMS; with the blanker on the count falls by at least 70%. A clean 600 Hz tone passes within 1% of its level with the blanker on.
- AGC: after a 1 s tone of amplitude 1 over noise, the time for the noise to return to within 3 dB of its setting is under 0.25 s in Fast and over 0.4 s in Slow.
- Every existing `receiver()` test still passes, and "busy band stays clear of full scale" passes for all AGC modes.

**Watch out**
- Changing the peak filter or the shift while audio plays must use the existing fade-out, swap, settle, fade-in in `Rx.apply`, or it will click.
- The blanker must be skipped for the delay line's first sample after `apply()` resets state.

### P4. Weather on the band

**Model:** Sonnet for `Sky`, the settings and the wording; the `Rx` noise change is small but follows P3's rules. **Size:** medium. **Needs:** P3 (the blanker) to be worth hearing.

**What the user gets**
- The band's noise is no longer the same every day. Summer afternoons and evenings bring thunderstorm static; a storm can come close for a while; rarely the band flutters under an aurora or fades out after a solar flare.
- Settings, Band: "Band weather: Auto / Calm / Summer static / Storm nearby / Aurora / Solar flare". Auto follows season, time and chance; the others hold one state so each can be heard.
- The Band tab says what is going on in one line, and stations mention it.

**What each kind sounds like** (from operators' reports; see the sources in `docs/PLAN.md`)

| Kind | Sound | Where it bites |
|---|---|---|
| Distant thunderstorms | Steady crackle of small ticks, a crash every few seconds | Summer, afternoon to late evening; all year in the tropics |
| Storm nearby | Loud crashes that swamp signals, with rumbling bursts; the AGC pumps | Short spells of 5 to 25 minutes |
| Aurora (geomagnetic storm) | Signals on northern paths flutter quickly and get weaker | Night, high latitudes, a few days a year |
| Solar flare fade-out | Every signal on the sunlit side sinks within minutes and returns over half an hour | Daytime only |

**Design**

New pure class `Sky.java`
```java
final class Sky {
    static final int AUTO = 0, CALM = 1, STATIC = 2, STORM = 3, AURORA = 4, FLARE = 5;
    Sky(long seed);
    /** Moves time on. month 0-11, localHour 0-24 at the listener, latitude in degrees. */
    void advance(double seconds, int month, double localHour, double latitude, int mode);
    double ticksPerSecond();   // 2.5 calm, up to 25 in static, 40 in a storm
    double crashEverySeconds();// 40 calm, 6 static, 1.5 storm
    double crashGain();        // 1 calm and static, 2.5 storm
    double aurora();           // 0 to 1
    double flare();            // 0 to 1
    String label();            // "" when calm, else a short sentence for the Band tab
}
```
- States for thunderstorms: CALM, STATIC, STORM. In Auto, the chance per hour of leaving CALM is `0.05 + 0.5 * season * daytime`, where `season` is 1 in the local summer months, 0.5 in spring and autumn, 0.1 in winter, and 1 all year below 25 degrees latitude; `daytime` is 1 from 13 to 23 local, else 0.3. STATIC lasts 20 to 90 minutes; from it, a STORM starts with chance 0.3 per hour and lasts 5 to 25 minutes, then falls back to STATIC.
- Aurora in Auto: starts with chance 0.004 per hour, lasts 2 to 6 hours, level rises and falls over 30 minutes.
- Flare in Auto: only when `localHour` is between 7 and 17; chance 0.003 per hour; rises over 3 minutes, holds 5 to 15, recovers over 30.
- All outputs change smoothly: move each toward its target by a small step per call, never jump.

`Rx.java`
- The four static constants (`TICKS_PER_S`, `CRASH_EVERY_S`, and a new crash gain) become fields set by `void weather(double ticksPerS, double crashEveryS, double crashGain)`.
- A crash gains a second part that the blanker cannot remove: for the length of the crash, multiply the noise deviation by `1 + 3 * crashGain * envelope`, where the envelope rises in 10 ms and decays over the crash.

`Band.java`
- Owns a `Sky`; `render` calls `sky.advance(frames / (double) rate, month, hourNow() + lon / 15, lat, mode)`.
- `double[] weather()` returns the three noise numbers for `Engine` to pass to `rx.weather(...)` each block.
- Per operator, at spawn: `northern` = true when the listener, the operator, or the path's midpoint is above 55 degrees north or south (add a small pure helper to `Prop`). In `gain(o)`: when `northern`, multiply by `1 - 0.5 * aurora * (0.5 + 0.5 * sin(o.pf))` using a flutter rate of 12 to 20 Hz, and apply `-12 * aurora` dB.
- Flare: every operator whose path is in daylight (`Prop.sunElevation` above 0 at the midpoint) gets `-30 * flare` dB.
- `String sky()` for the bridge.

`Qso.java`
- `Qso.Scene` gains `double qrn` (0 to 1) and `boolean aurora`. In the `CONDX` topic: with `qrn > 0.4` say one of `QRN VY BAD HR`, `LOTS OF STATIC TODAY`, `QRN FROM TSTORMS HR`; with `aurora` say `AURORA TONITE SIGS FLUTTERY` or `CONDX POOR K INDEX HIGH`.

Bridge and UI
- Pref `weather` (0 to 5). `MainActivity.band()` adds `"sky": "<label>"`. The Band tab shows it in a new `#sky` message line.

**Tests**
- `Sky` in each fixed mode returns that mode's numbers after 60 s of `advance`.
- Auto over 2000 simulated summer evenings at 50 degrees north is in STATIC or STORM for 25 to 60% of the time; over winter nights under 10%.
- No output changes by more than 5% of its range between two calls 10 ms apart.
- Flare never rises when the local hour is 22.
- `Rx` with storm numbers: output noise RMS is at least 6 dB above calm, and the blanker (P3) brings ticks down but leaves at least half of that rise.
- Aurora at level 1: an operator at 62 degrees north is at least 10 dB weaker than the same operator with aurora 0; one at 40 degrees is unchanged.
- "busy band stays clear of full scale" still passes in STORM mode.

**Not in this package:** real space-weather data (P5), rain static (no good description of its sound was found).

### P5. Real space weather (optional)

**Model:** Sonnet. **Size:** small. **Needs:** P4.

- Settings: "Use real space weather: Off / On" (default Off, so the app stays quiet on the network unless asked).
- When On and online, `Refresher` fetches NOAA's planetary K index once per refresh and stores the latest value in prefs. Candidate address, to be verified by fetching a sample before coding: `https://services.swpc.noaa.gov/products/noaa-planetary-k-index.json`.
- `Sky` in Auto: with a stored Kp of 5 or more, aurora level is `(Kp - 4) / 5`, capped at 1, in place of the random aurora.
- The fetch must use the same HTTPS-only path as the feeds, fail silently, and never block playback. Add the host to the README's privacy notes.
- Test the parser on a saved sample file, including an empty and a malformed one.

---

> **Superseded:** P6 to P11 below are replaced by `docs/PLAN-KEY.md` (K1 to K8), approved on 2026-11-19. They are kept for reference; K1 to K8 reuse their rules and tests.

## Release 2.6 and 2.7: on the air with your own key

Eight packages, K1 to K8, replace the old P6 to P11. The overview, the reasons and the order are in `docs/PLAN-KEY.md`; this section is the exact specification, and where the two differ, this section wins. Build one package per session, in the order K1, K2, K3, K4 (ships as 2.6.0), K5, K6 (ships as 2.7.0), K8. K7 is a contingency, built only if the go/no-go check in it fails.

Everything is Sonnet work. K2, K3 and K5 are "with care": if a listed test cannot be made to pass in two honest attempts, stop and report, as the rules at the top say.

### What "forgiving" means

These rules are the heart of the release. They come from what learners say stops them: fear of making mistakes on the air ("key fright"), and partners who are too fast.

1. **Nothing the user sends ends the contact.** There is no failure state and no score during a contact.
2. **One request at a time.** The partner asks only for the most important missing thing (callsign, then report, then name, then QTH), in the shortest form: `CALL?`, `UR NAME?`.
3. **Two misses, then move on.** If the same item is still unclear after two requests, the partner says `NO PROBLEM` and continues without it. The callsign gets three.
4. **Close is good enough.** A callsign within two letters of the user's own is taken as the user's. The partner sends it back for confirmation once (`SP9ABC?`) and accepts `R`, `C`, `YES`, or a repeat.
5. **The partner slows down, never the user.** It answers at the user's measured speed or slower, widening the gaps between letters before slowing the letters themselves. `QRS` slows it by a quarter each time.
6. **Patience.** It waits 25 seconds of silence before a gentle prompt (the user's call and `?`), and prompts at most three times. Then it signs off kindly (`NIL HR 73 CUL`) and the contact is logged as incomplete, not failed.
7. **Encouragement, in moderation.** At most one short phrase per over, chosen by what just happened:
   - after the user corrects an error: `NO PROBLEM`, `NP`
   - after a repeat that worked: `GUD COPY NW`, `TNX FER RPT`
   - to a user with fewer than five logged contacts: `UR CW IS FB`, `NICE FIST`, `TAKE UR TIME`
   - at the end: `TNX FER QSO UR DOING FB`, `HPE CU AGN`
8. **Unreadable sending gets help, not silence.** First `SRI NO COPY PSE AGN`; second `PSE AGN SLOWLY`, and the partner itself slows down; third, the partner asks for exactly one thing (`PSE UR CALL?`).
9. **A minimal contact counts.** Callsigns both ways and one report make a logged contact. Name, QTH and chat are extra.
10. **Important things are sent twice.** The partner repeats its name, QTH and the report, as real operators do for a weak or new station.

Three levels, chosen in Settings as "Partners are: Patient / Normal / Realistic" (`manners` 0, 1, 2). Patient is the default and follows every rule above. Normal waits less (15 s before a prompt) and asks for repeats once. Realistic adds real conditions: the partner may not hear you if you are off its frequency, weak or covered by noise, and may copy letters wrong.

Three kinds of contact, offered easiest first: a park or summit activator (four short overs: call, report, thanks), a standard contact (report, name, QTH), and a chat (standard, then 3 to 6 chat overs on weather, rig, antenna, hobby, job, age, key).

### K1. Your station and the logbook

**Model:** Sonnet. **Size:** small to medium. **Needs:** nothing. No audio code.

**What the user gets**
- Settings, new heading "Your station": callsign, name, QTH, rig, antenna (text fields), power (5, 10, 50, 100 W), sending speed for typed text, "Partners are: Patient / Normal / Realistic", and "Send with: Typing / My key (microphone)". "My key" does nothing until K6; until then the choice is only stored.
- A fifth tab, "Log", newest first, with date, call, name, QTH and reports. Incomplete contacts are shown plainly, not as errors. A line at the top: "N contacts, M countries".

**Design**

`Profile.java` (new, pure; add it to `CLAUDE.md` and to the `javac` line in `tests/run.sh`)
- Fields: `call, name, qth, rig, ant` (strings), `power` (int W), `sendWpm` (int), `manners` (int 0 to 2), `sendWith` (int 0 or 1). A made-up valid default so that contacts work before anything is entered: call `SP0CWN`, name `JACEK`, QTH `KRAKOW`, rig `FT891`, ant `DIPOLE`, power 10, sendWpm 15, manners 0, sendWith 0.
- `static String clean(String key, String raw)`: upper-cases, keeps only characters for which `Morse.supported(c)` is true plus the space, collapses runs of spaces, trims, and cuts to the limit (call 10, everything else 20). Keys: `mycall`, `myname`, `myqth`, `myrig`, `myant`.
- `static final java.util.regex.Pattern CALL = Pattern.compile("[A-Z0-9]{1,3}[0-9][A-Z0-9]{0,3}[A-Z](/[A-Z0-9]+)?")`.
- `static String check(String key, String clean)`: returns `""` when the value is acceptable and otherwise a message for the page, for example "That is not a callsign. A call looks like SP9ABC." For `mycall`, an empty or non-matching value is refused; for the other keys, the value may be empty (the default is then used).
- `static Profile of(String call, String name, …)` or a plain constructor with those fields, and `Profile defaults()`.
- Country count for the Log line: `static String country(String call)` returns the prefix group of a callsign (the characters up to and including the first digit, after removing a leading portable prefix such as `SP/`). It is a rough count of distinct prefixes, not a DXCC lookup; the Log line says "countries" and the README notes the approximation.

`MainActivity.Bridge`
- `String setText(String key, String value)`: `clean`, then `check`; on success `prefs.edit().putString(key, clean).apply()` and return `""`, otherwise return the message and store nothing. Then `service(CwService.APPLY, -1)` as `set` does.
- `settings()` adds `mycall, myname, myqth, myrig, myant` (defaults from `Profile.defaults()`), `power` (10), `sendwpm` (15), `manners` (0) and `sendwith` (0).
- `String log()` returns `db.logJson(200)`; `String logSummary()` returns `{"contacts":N,"countries":M}`.
- The integer settings use the existing four-edit pattern: seg in `index.html`; `settings()`; `CwService.readSettings()` (read them into a static `Profile` named `CwService.profile`, rebuilt on every `APPLY`); and `tests/ui.py`.

`Db.java`
- Version 2. `onUpgrade(d, o, n)`: `if (o < 2) createLog(d)`; `onCreate` also calls `createLog`. `feeds` and `articles` must not be touched.
- `private static void createLog(SQLiteDatabase d)`: `CREATE TABLE log(id INTEGER PRIMARY KEY, at INTEGER NOT NULL, call TEXT NOT NULL, name TEXT NOT NULL DEFAULT '', qth TEXT NOT NULL DEFAULT '', rst_sent TEXT NOT NULL DEFAULT '', rst_rcvd TEXT NOT NULL DEFAULT '', khz REAL NOT NULL DEFAULT 0, complete INTEGER NOT NULL DEFAULT 0, overs INTEGER NOT NULL DEFAULT 0)`.
- `long addLog(long at, String call, String name, String qth, String rstSent, String rstRcvd, double khz, boolean complete, int overs)`, `String logJson(int limit)` (newest first, with `id`, `at`, `call`, `name`, `qth`, `sent`, `rcvd`, `khz`, `complete`, `overs`), `int logCount()`, `int logCountries()` (distinct `Profile.country(call)` over all rows; read the calls and count in Java).

`index.html`
- Settings: heading "Your station", five text inputs (a call that fails `check` shows the returned message under the field in the `bad` style), the power and manners and send-with `seg`s, and a sending-speed slider (5 to 30 WPM).
- A fifth nav button "Log" and `<section id="log">`: the summary line, then one row per contact. The `tick()` loop refreshes it only when the Log tab is open and the JSON changed (use the `feedSig` idea).
- Interface text stays English, with no exclamation marks and no scores.

**Tests**
- `profile()` in `tests/T.java`: `clean` upper-cases, drops unsupported characters (`é`, `#`, `{`), collapses spaces and honours the limits; `check` accepts `SP9ABC`, `DL1XYZ`, `W1AW`, `SP9ABC/P`, `OH2BH`, `9A1A` and refuses `HELLO`, `123`, `SP9`, empty; the default call passes `check`; `country` gives the same value for `SP9ABC` and `SP1XYZ` and a different one for `DL1XYZ`.
- UI: a Node Playwright script with a fake `window.Android` (as `tests/ui.py` does). It checks that typing a call calls `setText('mycall', …)` and shows the returned message, that the Log tab renders rows and the summary line, and that an incomplete contact has no error styling. Add the same checks to `tests/ui.py`.
- Upgrade path: it cannot run on a PC. In the report, describe how `onUpgrade(…, 1, 2)` was reasoned (the new table is created by the same method, feeds and articles are untouched) and say it was compiled by CI only.

**Done when** `bash tests/run.sh` prints `0 failed`, the UI check passes, the CI run is green, and the report says that the Log tab and the upgrade of an existing version-1 database were not tried on a phone.

**Watch out**
- Do not raise the version in this package; K4 ships 2.6.0.
- `Profile` must stay free of Android imports.

### K2. `Heard`: reading one over leniently

**Model:** Sonnet, with care. **Size:** medium. **Needs:** K1 (`Profile`). Pure Java.

**What the user gets:** nothing directly. It is the reader that makes the partner forgiving: it works out what an over of yours contained, even when the letters are wrong.

**Design**

`Morse.java`: add a reverse table, `static String letter(String code)`, returning the character as a string or the prosign name (`BT KN SK AR AS HH`) for a code of dots and dashes, or null. Build it from the existing `CODE` and `PROSIGN` maps.

`Heard.java` (new, pure; add to `CLAUDE.md` and `tests/run.sh`)
- `static Heard parse(String text, Expect expect)` and `static Heard parse(java.util.List<KeyDecoder.Letter> letters, Expect expect)`. The second form exists from K5 on; in K2 define the `Letter` type inside `Heard` as `static final class Letter { String code; String ch; java.util.List<String> alternatives; long startMs, endMs; }` and let K5 reuse it. `text` is the decoded over: letters, spaces, `<BT>`-style prosigns and `*` for an unknown letter.
- `Expect`: the words the partner expects now: its own call, the user's call (if known), its name and city, the user's profile values, and the fixed vocabulary. Build it with `Expect.of(String partnerCall, String partnerName, String partnerQth, Profile user)`.
- Fixed vocabulary: `CQ CQ DE K KN BK SK AR BT R RR OK TU TNX FB UR RST NAME OP QTH RIG ANT WX PWR HR ES GM GA GE GN 73 88 CUL HPE AGN PSE QRS QRQ QSB QRM QRN QSL HW CPY SRI NIL NW AGE HOBBY HOBBIES JOB WORK OCC OCCUPATION KEY CONDX YES NO 5NN 599`, plus every abbreviation in `Abbrev`.

**Steps, in this order**
1. **Normalise.** Upper-case. Map letter pairs to prosigns when they stand alone as a token: `BT`→`<BT>`, `KN`→`<KN>`, `SK` or `VA`→`<SK>`, `AR`→`<AR>`. A run of 6 or more characters from `E I S H 5` is the error sign `<HH>`.
2. **Split run-on words.** A token longer than 4 letters that is not in the vocabulary is split by dynamic programming (word break), minimising the total cost, and only when the best split costs at most 1 (nearly everything explained; otherwise leave the token whole): a vocabulary word costs 0, a callsign-shaped piece (`Profile.CALL`, 4 or more characters) costs 0.5 plus 0.02 per character, which prefers shorter calls followed by vocabulary words, any other letter costs 1. Pieces must be at least 2 characters. `73AE0QDE` becomes `73 AE0Q DE`.
3. **Morse-aware fuzzy match** of each token against `Expect`, by weighted edit distance. A substitution costs 0.5 when the two letters' codes differ by exactly one element added, removed or flipped (E/T, I/S, S/H, A/N, A/W, U/V, D/B, R/L, K/C, M/O, and so on; compute it from `Morse.code`, do not list pairs by hand), otherwise 1. Insertion and deletion cost 1. Accept a match when the cost is 0, or at most 0.5 for words of 3 letters (one Morse-near slip), or at most 1 for 4 or more letters, or at most 2 for 6 or more. (The first version allowed cost 1 for 3 letters; random letters then passed as words too often, so 3-letter words are stricter.) A callsign: accept the user's call at cost at most 2 and set `callUnsure` if the cost is above 0.
4. **Re-segmentation from timing** (only with `List<Letter>`). For a token that failed step 3, try the alternatives K5 recorded: each ambiguous gap is an inner gap or a letter gap, each ambiguous mark a dit or a dah. Cap this at 10 ambiguous items (1024 candidates). Keep the candidate with the lowest step-3 cost, if it is accepted. In K2, test this with hand-made `Letter` lists.
5. **Extract fields.** Callsigns (`X DE Y`, or alone), a report (`[1-5][1-9N][1-9N]C?`, reading `N` as 9 and `T` as 0, and `5NN`; a trailing `C`, the chirp mark, is kept: `579C`), and the value after `NAME` or `OP`, `QTH`, `RIG`, `ANT`, `WX`, `PWR` (up to the next `<BT>` or keyword, at most 4 words). Questions: a keyword followed by `?`, or `UR`, `WHAT` or `HW` before the keyword, for `NAME QTH RST RIG PWR ANT WX AGE HOBBY JOB KEY QSL CONDX` (`HOBBIES` is `HOBBY`, `WORK` and `OCC` are `JOB`). Requests: `QRS QRQ AGN RPT ?` (a lone `?` is a request to repeat). Closings: `73 SK TU GB CUL`. Turn markers: `K KN BK`. Also the error sign and greetings (`GM GA GE`).
6. **Readability.** `known = accepted tokens / all tokens`. The over is `unreadable` when `known < 0.3` and no call or report was found.

Public fields (all plain): `String text; java.util.List<String> tokens; String myCallHeard, theirCallHeard; boolean callUnsure; String report; java.util.Map<String,String> values` (keys `NAME QTH RIG ANT WX PWR`); `java.util.Set<String> questions, requests, closings`; `String turn; boolean errorSign; String greeting; double known; boolean unreadable`.

**Tests** (`heard()` in `tests/T.java`)
- `SP9ABX DE DL1ZZ` with user call `SP9ABC` is accepted with `callUnsure`.
- `UR RST 5NN 5NN` gives report 599.
- `73AE0QDE` splits as `73`, `AE0Q`, `DE`.
- `NAME JACEK JACEK` and `NAMR JACEK` both give the name `JACEK`.
- `WX?`, `UR ANT?`, `WHAT JOB?`, `HOBBIES?` each give the right question.
- `EEEEEEE` gives the error sign.
- Random letters are `unreadable`.
- For each of these pairs, the Morse-aware cost is lower than the plain cost (assert both numbers).
- A hand-made `Letter` list with one ambiguous gap is re-segmented into the expected word.
- `Morse.letter(".-")` is `A`, `Morse.letter("-...-")` is `BT`, and every letter A to Z round-trips. Where a prosign and a punctuation mark share a code (BT and `=`, AR and `+`, KN and `(`), `Morse.letter` returns the prosign.

**Done when** the tests pass and `Heard` has no Android imports.

**Watch out:** the weights are a guess. Do not tune them until a test passes by accident; say so if a test needs a different weight.

### K3. `Partner`: the friendly operator

**Model:** Sonnet, with care. **Size:** large. **Needs:** K1, K2. Pure Java.

**What the user gets:** nothing directly; it is the conversation logic that K4 puts on the band.

**Design**

`Qso.java`
- Make `Qso.Op` and `Qso.Gen` package-private (remove `private`) with no logic change. **Guard first:** before the change, add a test that builds the text of the `Tx` list of the contacts from seeds 1 to 500 and records a hash (a 64-bit polynomial hash over the concatenated strings); put the expected number into the test as a literal. Run it before and after, and it must not change.
- `static Op userOp(Profile p, Scene s)`: an `Op` built from the user's profile. Unknown fields stay null or 0. `react` and `say` must skip a topic whose field is null: add the null guards there.

`Partner.java` (new, pure; add to `CLAUDE.md` and `tests/run.sh`)
- It reuses the phrase code in `Qso.Gen`: `Gen g` (with its own `List<Tx>` sink), `Op self = g.op(style)`, `Op you` from `Qso.userOp`.
- API:
  ```java
  static Partner calling(Dsp.Rng r, Qso.Scene scene, Profile me, int manners, int kind);   // the partner calls CQ
  static Partner answering(Dsp.Rng r, Qso.Scene scene, Profile me, int manners, int kind); // answers the user's CQ
  static Partner from(Qso contact, int operator, Profile me, int manners);                  // takes over a station already on the band
  Qso.Tx first();
  Qso.Tx hear(Heard h, double seconds, double offsetHz, double wpm);  // the user's over ended
  Qso.Tx silence(double seconds);                                     // null means keep waiting
  boolean done();
  String hint();      // what would be natural to send now, in plain words
  Heard.Expect expect();
  Contact result();   // call, name, QTH, reports, complete, overs, for the log
  ```
  Each reply is a `Qso.Tx` with `who = 0`. `Contact` is a small static class inside `Partner`.
- State: stage `CALL → EXCHANGE → CHAT → BYE → DONE`; the known user fields; `missCount[item]`; `unreadableRun`; `prompts`; `speed`; `askedTopics`; `pendingAnswers` (questions the user asked and the partner has not yet answered); `overs`.

**Reply builder.** Segments are joined with `<BT>`, as `Gen.join` does, in this order:
1. **Acknowledge** something real, never a bare "wrong": `R R {name}`, `FB {name}`, or `TNX FER RPT` when a report has just come. On the first over after the call, use `Gen.basics(self, you, true)`: it gives greeting, thanks, the report twice, name and QTH, and repeats the important things.
2. **Callsign confirm** when `callUnsure`: `{call}?` once. Then accept `R`, `C`, `YES`, `OK` or a repeat of the call.
3. **Answers** to the user's questions, at most 2 per over (the rest wait for the next). `WX`→`say(WX)`, `ANT`→`say(ANT)`, `RIG` and `PWR`→`say(RIG)`, `HOBBY`→`say(HOBBY)`, `JOB`→`say(WORK)`, `AGE`→`say(AGE)`, `KEY`→`say(KEY)`, `QSL`→`say(QSL)`, `CONDX`→`say(CONDX)`, `NAME`→`g.name(self)`, `QTH`→`g.qth(self)`, `RST`→`g.report(self, you)` (sent twice).
4. **Reactions** to what the user told: `react(topic, self, you)` for topics the user just sent (WX, RIG, ANT, HOBBY, KEY, AGE). At most 1, or 2 for a CHATTY partner.
5. **Own content** for the stage. In CHAT: 1 or 2 topics from `self.plan`, through `say`.
6. **At most one request:** the most important missing item, in the order call, report, name, QTH, in the shortest form (`CALL?`, `UR RST?`, `UR NAME?`, `UR QTH?`). After 2 misses (3 for the call) send `NO PROBLEM` and drop it.
7. **At most one question** to keep the chat going, in CHAT only and only when no request was made. Pick a topic not yet known: `UR WX?`, `WHAT RIG ES ANT?`, `ANY OTHER HOBBIES?`, `WHAT DO U DO FER WORK?`, `HW LONG HAM?`.
8. **At most one encouragement** (rule 7 above), chosen by what just happened.
9. **Turn ending:** `{you} DE {self} KN`. In BYE use the `Gen.goodbye` style: `TNX FER QSO {name} … 73 … <SK>`, then `E E`.

The ten forgiving rules above all apply. Unreadable overs: `SRI NO COPY PSE AGN`, then `PSE AGN SLOWLY` (and the partner slows by a quarter), then `PSE UR CALL?`. Silence: a gentle prompt `{you}?` at 25 s (15 s in Normal), at most three, then `NIL HR 73 CUL` and the result is "incomplete". Speed: never above `min(user's measured wpm, Profile.sendWpm)`; widen the Farnsworth gap first. `QRS` gives ×0.75 each time and the partner never speeds up again. A minimal contact is both calls plus one report. Contact kinds: activator (call, report, TU 73), standard (report, name, QTH), chat (standard plus 3 to 6 chat overs).

**Tests** (`partner()` in `tests/T.java`, scripted with `Heard.parse` on text)
- A minimal contact in four overs is logged as complete. A full standard contact. A chat of at least eight overs.
- The user's call sent with one wrong letter is confirmed and then accepted.
- Three unreadable overs in a row: the partner's three replies follow rule 8, its speed drops, and the contact continues.
- Silence: prompts at 25 s three times, then a kind sign-off; the result is "incomplete".
- `QRS` twice: the speed falls to about 56% and never rises again.
- The error sign in the user's over is followed by `NO PROBLEM` or `NP` at most once.
- Never more than one encouragement, one request and one question in a single over, across 2000 random scripted contacts.
- Realistic manners, 300 Hz off frequency: no answer. Patient manners: an answer.
- The partner's effective speed never exceeds the user's measured speed in Patient manners.
- The generated-contacts hash from seeds 1 to 500 is unchanged.
- `WX? ANT? HOBBY? JOB?` sent in one over: two are answered now and two in the next over, each with text from `say`.
- The user sends `WX HR SUNNY 25C`: the partner's next over may react and never asks `UR WX?` afterwards.
- No reply ever contains `WRONG`, `ERROR` or `FAIL`.

**Done when** the tests pass and the hash test passed before and after the `Qso` change.

### K4. A live station, TX muting and the contact card (typing)

**Model:** Sonnet. **Size:** large. **Needs:** K1 to K3. Ships as **2.6.0**.

**What the user gets:** on the Band tab, a Contact card. You answer a station that is calling CQ, or call CQ yourself, and hold a contact by typing. The partner is the one from K3.

**Design**

`Qso.java`: `static Qso live(Op self, …)`, a one-operator `Qso` with the arrays filled, kind `RAGCHEW`. Partner replies are appended to `qso.tx`.

`Band.java`
- `Partner live` on `Station`. `boolean takeOver(double rxKhz, Partner p)` turns the CQ station being listened to into a live one, keeping its frequency, strength and fist. `boolean answerMe(double txKhz, Partner p)` makes a new live station on the user's frequency, with a strength from `Prop` and a fist from `Fist.of`.
- `endTx`: when `live != null` and the transmissions have run out, do not clear the `qso`; wait.
- `synchronized void userOver(Heard h, double seconds, double wpm, double txKhz)`: runs on the caller's thread, never the audio thread. It calls `live.hear`, appends the reply with its natural pause and sets `s.wait`.
- `void tickLive(double nowS)`: called once a second from `MainActivity`, passes silence to `live.silence`.
- A live station is not replaced or timed out while the contact runs. `follow()` returns NaN while a live station exists.
- `String liveJson()`: stage, the partner's call, the hint, the transcript, and `sending` (whether the partner is keying now).

`Engine.java`
- `volatile boolean txKey` and `void key(boolean down)`. In `render`, a duck gain ramps to −20 dB over 5 ms while the key is down, holds for 0.4 s after the last key-up (counted in samples), then ramps back over 5 ms in the style of the existing `GATE_S` ramp. Apply it to `sig` after `rx.process` and before the volume. No allocation, no logging.
- `void send(Keying k)`: plays a sidetone at `toneHz` for typed text, mixed after the duck.
- The transmit frequency is the dial; the receiver listens at dial plus RIT.

`CwService.java`: expose `engine` as `static volatile Engine engine`, like `band`.

UI and bridge (Band tab, a "Contact" card below the tuning controls)
- **Answer** (enabled when the station in the filter is calling CQ) and **Call CQ** (enabled on a clear frequency).
- During a contact: the partner's call, a hint line (from `Partner.hint()`, off by a Settings switch), the transcript under "Show their text: After each over / Never / As they send" (default: after each over, because copying by ear is the point and the text is the check).
- A typing line with buttons that fill it in from the profile and can be edited before sending: **Call** (`{their} DE {my} {my} K`), **Report** (`{their} DE {my} <BT> TNX FER CALL <BT> UR RST 579 579 <BT> NAME {name} {name} <BT> QTH {qth} {qth} <BT> HW? {their} DE {my} <KN>`), **R**, **AGN?**, **QRS**, **73** (`{their} DE {my} TNX FER QSO 73 <SK>`), and **Send**.
- A zero-beat line: how far the station is from your transmit frequency, and **Net**, which puts the dial on it and clears RIT.
- **End** closes the card. The contact is logged with `Db.addLog` when the partner is done, or on End.
- When a contact finishes, a short summary: who, what was exchanged, one kind sentence, no score.
- Bridge: `liveState()`, `answer()`, `callCq()`, `sendText(String)`, `net()`, `endContact()`. `sendText` runs `Heard.parse` with the partner's `expect()`, then `Band.userOver(…, wpm = Profile.sendWpm, …)`, and plays the typed text with `Engine.send`.

**Tests**
- Band: `answerMe` and 4 scripted overs reach DONE, and the station is never replaced in between. `follow()` returns NaN while a live station exists.
- Engine: a key-down step stays under the existing no-click limit, the duck is −20 dB within 5 ms, and it is released 0.4 s after the key-up.
- UI: a Node Playwright check with a fake bridge (Answer enables, the fill buttons fill, Send calls `sendText`, the transcript honours the show-text setting, Net calls the bridge), and `tests/ui.py` updated.

**Delivery:** raise `versionCode` and `versionName` to 2.6.0, update the README and `docs/PLAN.md`, and say that a typed contact has not been held on a phone.

### K5. `KeyEar` and `KeyDecoder` (pure DSP)

**Model:** Sonnet, with care. **Size:** large. **Needs:** K2 (`Heard.Letter`). Pure Java, tested on a PC with synthetic buzzer sound.

**What the user gets:** nothing directly. It is the ear that reads your key, built to cope with a poor fist.

**Design**

`Dsp.java`: a small radix-2 FFT (`static void fft(float[] re, float[] im)`), used only for calibration.

`KeyEar.java` (new, pure). Input: mono PCM at `rate` (16000 by default; 48000 must work too).
- **Calibration.** `findPitch(float[] keyDown2s)`: a 4096-point Hann FFT averaged over the frames; the peak between 300 and 4000 Hz, refined by parabolic interpolation. Also measure `levelOn` (key held), `levelOff` (2 s of quiet) and `leak` (2 s with the band playing) as median envelopes. Report `snrDb = 20 log10(levelOn / levelOff)` and `leakOk = leak < 0.5 · thrOn`.
- **Running.** Quadrature mix to baseband: `I = x · cos(2π f_b t)`, `Q = −x · sin(2π f_b t)`, each through two cascaded `Dsp.Biquad.lowpass(200 Hz, 0.707)`. Envelope `e = sqrt(I² + Q²)`, with a rise of about 2 to 3 ms.
- **Hysteresis.** `thrOn = sqrt(levelOn · levelOff)`, `thrOff = 0.7 · thrOn`. Debounce: drop marks shorter than 12 ms and spaces shorter than 8 ms (merge them).
- **Events:** `onKey(boolean down, long sampleIndex)`.
- **AFC.** During key-down runs longer than 40 ms, `df = atan2(Q·I₋₁ − I·Q₋₁, I·I₋₁ + Q·Q₋₁) · rate / 2π` averaged over the run, then `f_b += 0.1 · df`.
- **Shifted output for K7:** decimate I/Q to 3200 Hz (every 5th sample at 16 kHz, every 15th at 48 kHz) and upconvert: `y = I · cos(2π · 800 t) − Q · sin(2π · 800 t)`. The buzzer is then exactly at 800 Hz whatever its pitch. Write `y` to a 12 s ring buffer (`float[38400]`), allocated once.

`KeyDecoder.java` (new, pure). Input: mark and space durations in milliseconds.
- **Fist profile.** `KeyDecoder.Fist5` holds five numbers `dot, dash, inner, letter, word` in ms, with `toString` and `parse` for prefs. When one exists it seeds the clusters. `static Fist5 learn(List<Long> marks, List<Long> gaps, String knownText)` aligns your timings to the known text: it walks `Morse.parse(knownText)` element by element and, if the mark count differs, aligns by dynamic programming on dit/dah labels. It returns the mean of each class. K6 uses it for "Learn my fist".
- **Initial dot** without a profile: `1200 / Profile.sendWpm`, then a 2-cluster k-means on the log durations of the first 8 marks.
- **Adaptive clusters.** Marks go into `dotMean` and `dashMean`, gaps into `innerMean`, `letterMean` and `wordMean`. Classify against the geometric-mean boundaries, then update the chosen mean by an exponential average with α = 0.15. Constraints: `dash ≥ 2·dot`, `letter ≥ 2·inner`, `word ≥ 1.6·letter`.
- **Ambiguity.** An item within ±20% (in log) of its boundary is marked ambiguous, and both readings go into `Letter.alternatives`.
- **Straight-key rules**, each with its own test:
  1. A mark longer than `1.6 · dashMean` is still a dash, not an error.
  2. A gap shorter than `0.5 · innerMean` is merged into the mark (bounce).
  3. A letter gap below the boundary, with an unknown code on both sides: also try it as a letter gap (the alternatives mechanism, forced on).
  4. A word gap is never inferred from a single long gap shorter than `1.3 · letterMean` (a hesitation inside a word).
  5. A single isolated `E` or `T` between two letter gaps that makes the code unknown when joined: keep both readings.
- **Output:** `List<Heard.Letter>` (code, char or prosign or `*`, alternatives, start and end in ms), a live string, and `wpm() = 1200 / dotMean`.
- **Saved overs:** `static String timingsJson(...)` and `fromJson(...)` (marks and gaps in ms plus the text you meant), so that real overs become test data in `tests/data/fist/*.json`.
- **End of over:** the last token is `K KN BK SK AR` or `?` and the silence is at least 1.5 s; or the silence is at least `max(4 s, 3 · wordMean)` in Patient manners (2.5 s in Normal).
- Keep the raw timings for K8.

**Test sound:** `tests/BuzzerSynth.java`, a helper that is not shipped (add it to the `javac` line of `tests/run.sh` with the tests). It renders `Keying` with a `Fist` as a buzzer: a square-ish wave (the fundamental plus 2nd and 3rd harmonics at −10 and −16 dB), 3 ms ramps, a room echo (a comb at 23 ms, gain 0.3), white noise at a set SNR, optionally the band leaking in at 600 Hz, and a door-slam click. A "learner" fist is made directly with the `Fist` constructor: `dah 0.75` (dashes of 2.25 dots), `ditJitter 0.25`, `gapJitter 0.35`, `charGap 0.6`, `hesitate 0.2`.

**Tests** (`keyear()` and `keydecoder()` in `tests/T.java`)
- Keyer, 5 to 30 WPM, 20 dB SNR: at least 99% of letters right. Straight key and bug from `Fist.of`, 12 to 22 WPM, 10 dB: at least 92%. A beginner's fist at 20/8 Farnsworth: at least 90%, with word breaks in the right places.
- Noise alone, a voice-like burst and a single loud click: no letters.
- The pitch is found within 3% at 500, 800, 2400 and 3200 Hz. With the leak 12 dB below the buzzer, nothing is decoded while the key is idle. Key-down is reported within 15 ms.
- The end of an over is found for `K`, `KN`, `BK` and `SK`, and never in the middle of a slow sender's word.
- The learner fist read through `KeyDecoder` and `Heard` against a fixed QSO script recovers call, report, name and QTH in at least 90% of 200 seeded runs.
- With a severe learner fist (`dah 0.67`, i.e. 2-dot dashes, `gapJitter 0.45`, `charGap 0.5`): `Fist5.learn` from 3 sends of the warm-up text, then `KeyDecoder` and `Heard` recover call, report and name in at least 85% of 200 runs. Without the profile the rate is at least 10 points lower (this shows the profile helps).
- Each straight-key rule has a test that fails if the rule is removed.
- Every file in `tests/data/fist/` (empty at first) is decoded and checked against its meant text with `Heard`; callsign and report must match, and the test prints the character error rate.
- The shifted output's spectral peak is within ±3 Hz of 800 Hz for buzzer pitches from 500 to 3500 Hz.

**Done when** all tests pass. Do not loosen a threshold; say what failed.

### K6. Microphone, calibration and the key in the contact card

**Model:** Sonnet. **Size:** large. **Needs:** K4, K5. Android and UI. Ships as **2.7.0**.

**What the user gets:** "Send with: My key". The phone listens to your buzzer, reads your Morse, and the partner answers it. A set-up that learns your pitch and your fist, a warm-up and a line that shows what was read.

**Design**
- **`Mic`**, an inner class of `MainActivity`, runs `AudioRecord` on its own thread: 16000 Hz, mono, PCM16, 20 ms blocks. The source is `UNPROCESSED` when `AudioManager.getProperty(PROPERTY_SUPPORT_AUDIO_SOURCE_UNPROCESSED)` is `"true"`, else `VOICE_RECOGNITION`.
- It runs only while the Band tab is visible, a contact card is open (or a warm-up runs) and "Send with: My key" is set, and is stopped in `onPause`. `FLAG_KEEP_SCREEN_ON` is set while it runs. Because capture happens only while the app is on screen, no microphone foreground-service type is needed; confirm this against the current Android documentation and write down what you found.
- **Wiring**
  - `KeyEar` events go to `CwService.engine.key(down)` and to `KeyDecoder`.
  - At the end of an over, `Heard.parse(letters, partner.expect())` goes to `Band.userOver(...)`.
  - `Band.tickLive` runs every second.
  - While `liveJson().sending` is true, decoded letters are ignored unless 3 or more marks arrive with an envelope of at least 2 × `thrOn` (break-in). Then the partner stops at the end of its word: add `Band.breakIn()`, which truncates the current `Keying` at the next `wordStart`.
- **Permission:** `RECORD_AUDIO` in the manifest, asked for the first time "My key" is chosen, with the sentence "The sound is analysed on the phone and never stored or sent."
- **UI**
  - A set-up in four steps: hold the key 2 s; stay quiet 2 s; the band plays 3 s; then "Learn my fist". It shows the pitch found, the SNR and the leak warning, and the advice "Use headphones, a different buzzer pitch, or a cable".
  - **Learn my fist:** "Send this three times at your own pace": `CQ CQ DE {mycall} {mycall} K`, then `UR RST 599 NAME {myname}`, then `PARIS PARIS`. The result is `Fist5.learn`, saved in prefs, plus one friendly line ("Your dashes are about 2.4 dots; I will read them that way"). Repeatable from Settings.
  - In the card: a key light that follows the buzzer, the line "You sent: …" filling in live, and the typing line kept as a fallback.
  - **Warm up:** 60 s with no partner, showing what is read and a score against a suggested text.
  - **"That's not what I sent"** under "You sent": you type what you meant, and the over's timings and the meant text are kept locally (up to 50, the oldest dropped), with an **Export** action that shares the JSON. Timings only, never audio. Exported files go into `tests/data/fist/`.
  - Help text "It does not hear my key": move the phone closer, recalibrate, use headphones, or connect the buzzer by cable.
- README: add `RECORD_AUDIO` and its reason to the permissions table.

**Tests:** a Playwright check with a fake bridge for the four set-up steps and both leak outcomes, and `tests/ui.py` updated. The Android parts are compiled only by CI.

**Delivery:** 2.7.0. State plainly that nothing was heard through a real microphone or keyed on a phone.

### K7. Contingency: neural decoding (DeepCW narrow)

**Model:** Sonnet, with care and stop rules. **Size:** large. Build it only if the go/no-go check fails. Nothing in K1 to K6 depends on it; `KeyEar` already writes the 800 Hz stream it needs.

**Go/no-go check.** After K6 the owner holds at least 5 key contacts and does one 60 s warm-up after each, with the fist profile learned. Build K7 only if one of these holds:
- the warm-up score stays below 85% of characters;
- the partner had to ask again (a request or "SRI NO COPY") in more than 1 over in 4 because of decoding, not because something was missing;
- the owner judges it frustrating.

Before K7, try two cheap fixes, each a short session: add the exported overs to `tests/data/fist/` and tune the `KeyDecoder` constants until they read; relax the `Heard` thresholds for the Patient level.

If K7 is built, add Settings "Neural decoding: Off / On" (`neural`, off by default). Everything below applies only then.

**The model** (checked against `e04/deepcw-engine`, MIT): `model_narrow.onnx` is 1.2 MB and covers 3 frequency bins at 787.5 to 812.5 Hz, so the tone must sit at 800 Hz within about ±10 Hz. That is why `KeyEar` shifts the buzzer to 800 Hz first. Input `spectrogram` `[1,1,T,F]`; output `log_probs` `[1,T/2,42]` (`output_time_stride` 2). There are 42 classes (`,./0-9?A-Z` and space); the CTC blank is index 41. The vocabulary has no prosigns and no error sign: `<BT>`, `<KN>`, `<SK>` come out as letter pairs, and run-on words such as `73AE0QDE` occur, which `Heard` already handles.

**Files**
- `app/assets/deepcw_narrow.onnx` and `deepcw_narrow.json`, committed, with the MIT notices in `THIRD_PARTY.md`.
- Pin **ONNX Runtime 1.24.3**. Check on Maven Central that `com.microsoft.onnxruntime:onnxruntime-android:1.24.3` and `com.microsoft.onnxruntime:onnxruntime:1.24.3` (desktop) exist; if not, use the nearest later patch.
- The AAR is not committed. A new `tools/fetch-ort.sh` downloads it to `third_party/` (gitignored) with a pinned SHA-256, and unpacks `classes.jar`, `jni/arm64-v8a/libonnxruntime.so` and `libonnxruntime4j_jni.so` (both libraries are needed).

**Pure Java parts** (in `tests/run.sh`), ported function by function from `examples/python/decode_morse.py`
- `DeepCwPre`: reflect-pad 128, periodic Hann 256, hop 48. The DFT is taken only at bins `ceil(787.5/12.5) = 63` to `floor(812.5/12.5) = 65` (three sums per hop), as magnitudes. Then the noise normalisation exactly as in the reference: divide the magnitude by `noise = max(q90 / sqrt(-2 ln 0.1), peak · 1e-3, 1e-5)`, and only then `log1p`.
- `DeepCwText`: log-softmax, the 1-frame dilation, greedy CTC with blank 41, the space and word-boundary rule, and `PosteriorStitcher` (12 s window, 1 s step, left context 4 s, freeze guard 2 s, ramps). Copy the constants verbatim.

**Android part:** `NeuralReader` (the only class that imports `ai.onnxruntime`). It loads the model from assets as a `byte[]` (1.2 MB: no mmap and no uncompressed asset needed), then `OrtEnvironment.getEnvironment().createSession(bytes, opts)` with `intraOpNumThreads = 1`. A worker thread, every 1 s and only if `KeyEar` saw key activity in the last 12 s, takes the ring buffer, runs `DeepCwPre`, the session and `DeepCwText`, and publishes the text. It is enabled only when the `neural` setting is on, the device ABI includes arm64-v8a and loading succeeded (catch `UnsatisfiedLinkError` and `OrtException`; on failure show "Neural decoding not available on this phone").

**Combining:** at the end of an over, `Heard` gets both the `KeyDecoder` letters and the neural text, scores each against `Expect` (the sum of its step-3 costs) and uses the lower one; ties go to the neural text. Over-end detection always comes from `KeyDecoder` timing.

**`build.sh`**
- `javac -classpath $J:third_party/ort/classes.jar`, and `d8` adds `third_party/ort/classes.jar`.
- After `aapt2 link`, copy the two `.so` files to `$O/lib/arm64-v8a/` and add them with `(cd $O && zip -q u.apk classes.dex lib/arm64-v8a/*.so)` before `zipalign -p 4`.
- `build.sh` stops with a clear message if `third_party/ort` is missing. CI runs `tools/fetch-ort.sh` first.

**Golden tests (CI only):** a new workflow step runs `pip install onnxruntime==1.24.3 numpy`, writes 10 WAVs (3200 Hz) from `BuzzerSynth` through `KeyEar`'s shifted output, and runs `decode_morse.py --model model_narrow.onnx` into `tests/data/deepcw-golden/*.txt`. Then `tests/deepcw.sh` runs the Java pipeline with the desktop ORT jar and asserts the same text. It also reports the character error rate of narrow against wide on learner-fist audio; if narrow is more than 3 points worse, say so and let the owner decide about the 15 MB wide model.

**Stop rules:** if the Java spectrogram or posteriors differ from Python by more than 1e-4 after two honest attempts, stop and report. Report the APK size before and after, measured from CI.

**Delivery:** the next version after K8. State plainly that CPU load and battery were not measured on a phone.

### K8. Feedback on sending

**Model:** Sonnet. **Size:** small. **Needs:** K5, K6. Ships as 2.8.0.

- After a contact or a warm-up, a card with three numbers from `KeyDecoder`'s raw timings: speed in WPM, dash length in dots (3 is the standard), and evenness of the gaps between letters.
- A small timing strip: the standard timing on the top row and the user's on the bottom, for the last word sent.
- One sentence, always phrased as the next thing to try, never as a fault: "Your dashes are a little short; try holding them for a count of three."
- A pure class (`SendFeedback`) computes the numbers and picks the sentence, so both are tested on a PC. Add it to `CLAUDE.md` and `tests/run.sh`.
- Tests: a decoder run on a fist with short dashes gives the "dashes" sentence; an even keyer gives an encouraging one; no sentence contains `WRONG`, `ERROR` or `FAIL`.

---

## Housekeeping

**Model:** Haiku. Version bumps, the README, release notes, and the status table in `docs/PLAN.md` after each release. Opening the pull request and merging when asked.
