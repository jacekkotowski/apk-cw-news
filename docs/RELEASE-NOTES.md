# CW News {{VERSION}}

An Android app that turns the news into Morse code, and a practice band where you can make Morse contacts with made-up stations, by typing, by tapping buttons, or with your own key and buzzer.

## Install

1. Download **CWNews-{{VERSION}}.apk** below (use Chrome on the phone, or download on a computer and copy it over).
2. Open it and allow installs from that source when Android asks. Updates install over an older version and keep your feeds, settings and log.
3. Needs Android 8.0 or newer. No account, no ads, no tracking.

## First five minutes

**News as Morse**
1. Feeds tab: open *Built-in catalogue*, open a group and tap *Add* on a feed or two.
2. Listen tab: tap the big Play button. Headlines are sent as Morse. On the Band tab the news is the station at 7 020 kHz. Settings sets the speed and the tone.

**The practice band**
1. Settings > *Your station*: enter your callsign (a made-up one works), name and QTH. Settings > *Band*: choose your region.
2. Band tab: tap *Play*. Turn the knob, tap the waterfall, or use the *Signal* buttons to find a station. The button under the left Signal button switches the filter between Wide and Narrow. The text window under the knob shows what the station you are tuned to sends, to check what you copied by ear (Settings > Show their text turns it off).
3. When a station calls CQ, tap **Answer**. Or find a clear spot and tap **Call CQ**.
4. Build your over with the coloured buttons: **Tell** (RST, Name, QTH, Weather and temperature, Rig, Ant), **Ask** (Name?, QTH?, Weather?, Rig?) and **Say** (Call, R, AGN?, QRS, 73), then **Send**. Or tap **Send this** to send the suggested text. **Net** puts the dial on the other station. **End contact** logs it in the Log tab.

The other station is patient. It asks again kindly, slows down when you ask, and never fails you. A contact needs only both callsigns and one report.

**With your own key and buzzer**
1. Settings > *Your station* > *Send with* > **My key (microphone)**. Allow the microphone when asked. The sound is analysed on the phone while the Band tab is on screen and is never stored or sent.
2. Band tab > **Set up my key**: it takes about eight seconds (hold the key, stay quiet, let the band play). Repeat it whenever the noise changes, for example when a generator starts.
3. Key the suggested text. The line *You sent* shows what the phone read; if it is wrong, type what you meant and tap *That's not what I sent*.

**Stories**
1. Settings > *Story station, 7010 kHz*: choose one of the five nets, or **Add a .txt file** of your own.
2. Band tab: tune to 7010 kHz. The stations talk to each other, each at its own pitch. The story waits while you are tuned away and resumes where you stopped.
3. Tap the *Story:* line to pick an episode, move along the story, or hear the last over again. How the files work, and a prompt for making new nets: docs/STORIES.md.

## New in {{VERSION}}: the static

- **Crashes are lightning now.** In summer static and in a storm nearby you hear separate flashes: a sharp first stroke, a few weaker strokes about 60 ms apart, and a rattle that lasts a third of a second. About half of the flashes are the soft cloud-to-cloud kind, with a rattle but no strokes. A storm nearby is much louder than before, and the noise blanker now takes out most of it (it did not before).
- **Distant thunderstorms crackle** instead of ticking: each tick is a small cluster of pops, quiet in calm weather and sharp in a storm. A storm nearby has twice as many (80 a second).
- **The noise no longer pumps in the gaps between letters.** With the slow AGC and a slow effective speed, the hiss used to swell back in every letter gap. The AGC now holds its gain a little longer than the letter gap, so it only recovers in the longer gaps between words.
- **A very quiet hiss across the whole audio range**, also where the filter cuts everything, so the Narrow filter no longer sounds like a dead line.
- Not tried by ear on a phone yet: how loud a storm nearby feels with the fast AGC and the Narrow filter, and how the flashes sound on headphones.

## Good to know

- The band is made up: every station and contact is generated on the spot, and follows the time of day and your region.
- A very sloppy hand can be misread by the key reader; typing and the buttons always work.
- Tested on a PC and on one phone by its author. Please report problems at https://github.com/jacekkotowski/apk-cw-news/issues.
- Code and the plan: README.md and docs/PLAN.md in the repository.
