# Sources and licences

The app has no third-party libraries. It uses only the Android framework.

| Project | Licence | What was used |
|---|---|---|
| [cwtool](https://github.com/ncw/cwtool) by Nick Craig-Wood | MIT | Code reviewed. The Morse table and the idea of RSS items separated by BT come from it, rewritten in Java. Its whole-dit Farnsworth rounding was replaced with exact timing. |
| [Morse News](https://morse-rss-news.sourceforge.net/) by Robert B. Denny | CPAL 1.0 | Behaviour only, from the published manual: the DE source preamble, short names in parentheses, oldest-first order, seen-story cache, story age and BT separators. None of its source or audio files are included. |
| Morse From RSS News Feeds (Android, 2017) | Unknown | Nothing used. No source code was found, and its APK was deliberately not downloaded. |
| [Morse Runner](https://github.com/VE3NEA/MorseRunner) by Alex Shovkoplyas VE3NEA | MPL-2.0 | The idea of Rayleigh fading and static only. No code was read or copied.
| [WebMorseRunner](https://github.com/fritzsche/WebMorseRunner) by Fritz Schmid | Unlicense | The receiver model of the Band background: noise and static pass together through a narrow filter at the CW pitch, then an AGC. Code reviewed and rewritten in Java. |
| CW abbreviation lists (CWops, amateurradio.com, giangrandi.org) | n/a | Which abbreviations are common. The table in `Abbrev.java` is our own. | |

## cwtool licence notice

Copyright (C) 2023 by Nick Craig-Wood http://www.craig-wood.com/nick/

Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
