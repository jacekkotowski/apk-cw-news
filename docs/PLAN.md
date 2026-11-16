# CW News: plan

Working plan for the Band tab (the made-up 40 m CW band) and the contacts on it. Status is updated when each step ships.

## Done

- **2.0:** Band tab with a tuning dial, signals that sweep in and out, generated contacts, news as the bulletin station at 7020 kHz.
- **2.1:** Modular contacts with operator personalities (casual, chatty, terse, beginner, outdoor), skeds, park/summit/contest stations with several callers, repeat requests, QRS requests that are honoured, interruptions and error signs. Reports follow the signal the listener hears; greetings follow the other station's local time; weather follows season and time of day; POTA references use the current country prefixes.

## Next

### 1. One receiver chain and individual fists (audio only)

Makes everything else sound more like a real radio.

- Noise and signals pass through the same filter and AGC. A strong station pushes the noise down, and it rushes back in the gaps.
- Each operator gets a fist: bug-style long dahs, straight-key jitter, hesitations, and errors that are corrected with eight dots.
- Signal character: a little chirp, key clicks, slow drift, rough notes from homebrew rigs, faster and deeper fading on polar paths.
- Measure before tuning: the renderer must stay within the existing bounds, and the soak test must still pass for hours of audio.

### 2. Answer a CQ with typed sending and a reactive partner

The first step where you take part in a contact.

- Settings hold your callsign, name, QTH and rig.
- Tap a CQ, or type a reply. The app keys what you type as sidetone; the partner answers in real time.
- A rule-based partner matches your speed, sends `AGN` or `?` when it misses you, slows down on `QRS`, and answers `NAME?`, `QTH?` and `RIG?`. No AI is needed.
- Open questions: sidetone latency on Android (measure first); whether a missed call costs the contact.

### 3. Later, roughly in order

- Propagation: nearby Europe by day, DX at night, busier evenings and contest weekends.
- Band geography: fast DX ops near 7000 to 7010, QRP around 7030, slower ops higher.
- Recurring stations: the same operator returns with the same rig and QTH and remembers you once you have worked them.
- A logbook you fill in after listening, scored against what was sent.
- Pile-up mode: you are the POTA activator and stations call you, scored on rate and accuracy.
- Receiver controls: filter width, RIT, and finer tuning (the dial now steps 50 Hz, coarse for a 320 Hz filter).
- Display: a scrolling waterfall and an S-meter. The UI polls every 500 ms, so this needs a faster path.

## Known gaps

- Not yet tested on a phone. The 2.1 APK was built by GitHub Actions and has not been installed or listened to.
- `tests/ui.py` needs the Python Playwright module, which is not installed in this environment.
- SOTA region codes are plausible but unchecked against the real summit lists.
- Other countries' POTA prefixes follow the ISO scheme by inference; only the US change is confirmed.
- No propagation model: a Danish station can still chat with Argentina at noon.

## How to check a step

Each step ends with `bash tests/run.sh` passing and a CI build producing `CWNews-apk`. Install and listen on a phone before calling a step done.
