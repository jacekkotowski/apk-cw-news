# Story station

The story station is a second fixed station on the band, at **7010 kHz**. It plays a story as stations talking to each other, each with its own pitch (at least 60 Hz apart for partners, six slots 30 Hz apart in all), fist, strength and fading. Tune the Band tab to 7010 kHz to hear it. The story waits while you are tuned away, and resumes where you stopped (the position is saved per story).

Choose the story in Settings > Story station. Five nets ship with the app (LV-426 Net, Hail Mary Net, Terminus Net, Endurance Net, Mars Net - pro). **Add a .txt file** copies your own file (UTF-8, at most 5 MB) into the app; **Remove** deletes an added file.

On the Band tab, near 7010 kHz, tap the line `Story: <title>, EP n · <who> ▸` to open the panel: **Episode** menu, **Position** slider (applied on release) and **Again**, which repeats the over just sent. A jump takes effect at the end of the word being sent.

## The net format

```
Hail Mary Net                                   <- line 1: the title
@ HMY = Hail Mary, Dr Ryland Grace              <- legend: CALL = full name
@ RKY = Rocky, Eridian engineer on the Blip-A

  0.13 min  === EP 1  WAKING UP ===             <- episode marker
  0.13 min  MRY -> ALL  VVV VVV DE MRY <BT> ... <- t min  FROM -> TO  TEXT
  1.23 min  MRY -> HMY  BED 3 DE MRY <BT> ... WHAT IS 2 PLUS 2 ?
Music: Eternity by Stellardrone                 <- any other line is ignored
```

- A line is `t min` (two spaces), then either `=== EP n TITLE ===` or `FROM -> TO  TEXT`. The timestamps are ignored: the keying follows your WPM, so only the order and the episode breaks count (about 1.5 to 3 s between overs, 10 s at an episode).
- Calls are 3 letters; `ALL` is a broadcast. The `@ CALL = name` legend after the title gives each call a name for the status line; without it the call is shown.
- Text is upper-cased. Prosigns are written `<BT> <KN> <AR> <SK> <AS>` and are sent as one character; everything between them is cleaned for Morse (`A-Z 0-9 . / ? ( )` and a few others).
- At the end the story waits 60 s and starts again.

## A plain book

Anything that is not a net is read as a book: Project Gutenberg's `*** START OF` and `*** END OF` sections are stripped, there is one station named after the short title, paragraphs are cut at sentence ends into overs of about 40 words at most, each ending `<BT>`, and headings such as `CHAPTER ...` or a short all-caps line become episodes.

## Adding a story

- On the phone: Settings > Story station > **Add a .txt file**.
- In the source: put the file in `app/assets/stories/` and rebuild. It appears under Built-in.

Both are plain UTF-8 text. A BOM and CRLF line ends are accepted.

## The prompt for making a new net

**Reconstructed from the five nets.** The original prompt was not available, so this one was written from the conventions in the shipped files and in the plan. Give it to a model with the book or film script as input.

```
Turn the attached story (book or screenplay) into a radio net: the characters
talk to each other in Morse code. Output only the text file, in exactly this
format, with no commentary.

LINE 1: the title, for example "Hail Mary Net".

LEGEND: next, one line per station: "@ CALL = full name and role", for example
"@ RKY = Rocky, Eridian engineer on the Blip-A". Every call that speaks must
have one. Use a 3-letter call for each character, ship, computer or group, made
from its name (HMY, RKY, MRY). Use ALL for a broadcast to everybody.

BODY: every line after that is
  <t> min  <FROM> -> <TO>  <TEXT>
where <t> is the elapsed minutes with two decimals, then the word min, two
spaces, FROM and TO are calls, two spaces, then the text of one over.
Episodes are marked by a line
  <t> min  === EP <n>  <TITLE> ===
Make 14 to 30 episodes that follow the plot of the source in order. Time only
has to increase.

PROCEDURE, as an amateur radio operator would send it:
- A call opens an over: "X DE Y <BT> ... <KN>", where X is the station called
  and Y the sender. Use <AR> at the end of a message and <SK> at the end of a
  contact. Separate thoughts inside an over with <BT>.
- Open a broadcast with "VVV VVV DE Y". Use QST for bulletins and announcements.
- Use "NR n ... CK n" for formal traffic (messages with a number and a check).
- Write numbers as digits.

TEXT RULES:
- Capital letters, and only the characters A-Z 0-9 . / ? ( ) and the prosigns
  <BT> <KN> <AR> <SK> <AS>. No quotes, commas, colons, hyphens or accents.
- An over is 10 to 50 words. Split a longer speech into several overs, with the
  other station answering between them, so it sounds like a conversation.
- Keep each character's voice and what they know. Retell the story through what
  they say to each other. Do not copy long passages of the source word for word.
- Keep to the plot of the source, including its important numbers and names.
```
