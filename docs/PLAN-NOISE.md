# Plan: band noise, version 2.12.1

## Context

Static used to be single-sample ticks plus a dense burst for a crash. Measured through the Normal filter (48 kHz), a tick peaks only about -10 to +5 dB re the band-noise RMS, so storm "crashes" were mostly the noise swelling, and the AGC then pumped the noise in every letter gap of a slowly sent station. This version builds the static from published lightning physics: flashes with return strokes and a continuous train of small discharges, and crackle made of small clusters of impulses. Level convention for every target: the peak or RMS **through the Normal filter** (`Rx.lo/hi(NORMAL, 600)`, 48 kHz), in dB relative to the band-noise RMS in that filter with no signal. An impulse of amplitude A (in units of the white-noise deviation) peaks at A * sqrt(2B/fs) re that RMS (B = 400 Hz), so amplitudes are derived from the dB targets and then checked by measurement.

## What changes

1. **Lightning flashes** (new class `Lightning`, Android-free, allocation-free, used by `Rx`; it replaces the dense 1500/s burst and the linear noise swell). Flash rate 1/crashEveryS. 50 % intra-cloud (train only). Otherwise 20 % single stroke, else 2 + geometric (p = 5/7), at most 12, so a return-stroke flash has 3.8 strokes on average. Inter-stroke interval log-normal, geometric mean 60 ms, clamped 10-300 ms; each stroke 3-8 dB weaker; each stroke 5-20 impulses over 1-5 ms. First-stroke peak median 18 dB (crashGain 1) rising to 32 dB (2.5), sigma 6 dB, cap 45 dB. A train of discharges runs under the flash (duration log-normal, GM 0.3 s, 0.15-0.6 s): spacing GM 2.5 ms (0.7-20 ms), 12-18 dB under the first-stroke median, sigma 4 dB. If the strokes of a flash reach past its drawn length the flash is extended to the last stroke, so no stroke is cut off.
2. **Crackle**: every tick is a cluster of 2-8 impulses over 2 ms, peak median -3 dB (calm) to +6 dB (storm) by storminess s = clamp((ticksPerS - 2.5) / 37.5, 0, 1), sigma 7 dB, cap 25 dB. `Sky`: storm nearby crackle 40 -> 80 per second, crash interval 1.5 s -> 3 s. `Sky.label` (ticks > 32) keeps working.
3. **AGC hang**: measured first (test `agcHang`). Only if letter gaps pump by more than 3 dB: `Rx.setHang(seconds)`, default 0.35 s, set by `Engine.setSpeed` to 1.1 x the letter gap of the current Timing; `CwService` calls it whenever the speed settings change.
4. **Audio-stage floor**: after filter and AGC, white noise band-limited to 200-2700 Hz (2nd-order high-pass, 4th-order low-pass), density 60 dB under the band-noise density at the pitch, scaled by the noise level (zero when the noise is zero).

Everything in 1 and 2 still enters on the antenna side, before the one filter and the one AGC (and the blanker).

## What the measurements changed

- **Train level.** With the train 12-18 dB under the first-stroke median the flash body measured +3 dB over the floor, short of the +5 to +9.5 dB target. It is now 7-13 dB under (spacing spread sigma 0.4, geometric mean still 2.5 ms); a static flash body then measures +7.6 dB, 56 % of its 10 ms windows inside the band. Because the train is relative to the first stroke, a storm nearby (stroke median 32 dB) has a body of about +21 dB: much louder, as intended, and not held to the band.
- **Cluster level.** An impulse cluster's peak through the filter came out 1 dB off the intended dB, so amplitudes carry a calibration factor of 1.07 (tests keep them within 1.5 dB).
- **Flash rate.** A flash cannot start during another, so the wait is shortened by 0.33 s to keep one flash per crashEveryS.
- **Hang.** The measurement showed pumping (4.1 dB across a letter gap without it), so it is built. It holds only for the slow AGC (the fast AGC is meant to release quickly), and is refreshed only while the signal is within 3 % of the held peak, so the start-up overshoot of a tone is not held for ever.
- **Noise blanker.** Flashes are single-sample impulses, which is what the blanker removes, so it now takes out most of a storm's extra loudness (the old swell, which it could not remove, is gone).

## Tests (tests/T.java, fixed seeds)

- Statistics of `Lightning`: strokes per return-stroke flash 3.8 +/- 0.3 over at least 2000 flashes; intra-cloud share 0.5; inter-stroke geometric mean 60 ms; train spacing geometric mean 2.5 ms; crackle and first-stroke medians per storminess and per crashGain; flash and tick rates per weather state.
- Through the receiver (AGC off, small noise so nothing hits the ceiling): isolated cluster peaks match the dB targets; during a flash the 10 ms RMS stays +5 to +9.5 dB over the floor for at least half of the flash (calm/static gain).
- AGC hang: keyed full-strength station, 20 WPM characters / 12 WPM effective, Normal, slow AGC, calm: noise in letter and word gaps before and after.
- Floor: with Narrow a 2 kHz probe region is no longer silent; the floor is at least 35 dB under the Narrow band noise; zero noise gives exact silence.
- Older assertions change only where the sound is meant to change (listed in the commit message). `bandUnchanged` stays as it is. CPU of the busy-band test stays under 3 % of real time (was 1.3 %).
