# Notice

Leanview
Copyright (c) 2026 Leanview contributors

Leanview is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License, version 3 of
the License (GPL-3.0-only). Leanview is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the
implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the file LICENSE for the full text.

## Source code

The complete corresponding source code of each release is published in this repository: the commit tagged with the version of the
release (for example `v1.0.0`) holds exactly the source the published APKs were built from, with the instructions to build them
(README_BUILD.md).

## What Leanview is based on

Leanview is based on SmartTube, https://github.com/yuliskov/SmartTube, which is released under the MIT license. Its license notice
is kept here as that license requires:

---

MIT License

Copyright (c) 2020-present yuliskov

Permission is hereby granted, free of charge, to any person obtaining
a copy of this software and associated documentation files (the
"Software"), to deal in the Software without restriction, including
without limitation the rights to use, copy, modify, merge, publish,
distribute, sublicense, and/or sell copies of the Software, and to
permit persons to whom the Software is furnished to do so, subject to
the following conditions:

The above copyright notice and this permission notice shall be
included in all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE
LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION
OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION
WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.

---

## GPL-3.0 code inside Leanview

Leanview contains code derived from these two GPL-3.0 projects, which is why Leanview as a whole is distributed under the GPL-3.0:

- NewPipe, https://github.com/TeamNewPipe/NewPipe (the proof-of-origin token generator in
  `MediaServiceCore/youtubeapi/src/main/java/com/liskovsoft/youtubeapi/app/potokennp2/generators/PoTokenWebView.kt`).
- bgutil-ytdlp-pot-provider, https://github.com/Brainicism/bgutil-ytdlp-pot-provider (the other generators in the same folder and
  `MediaServiceCore/youtubeapi/src/main/assets/potokennp2/`).

## Other components

The licenses and notices of every other component bundled in the app are in THIRD_PARTY_NOTICES.md.

The JavaScript solver of yt-dlp/ejs (Unlicense) is bundled and is modified by Leanview when it is loaded: one call that builds a large
output string is replaced by a call that collects the same output in an array, because the QuickJS engine Leanview uses where the system
JavaScript engine is not available copies a string on every append (the change is applied on both engines). The Unlicense requires nothing more;
this is stated for transparency (details in THIRD_PARTY_NOTICES.md).

## Not affiliated

Leanview is an independent project. It is not affiliated with, endorsed by or connected to YouTube, Google, SmartTube, NewPipe or
bgutil-ytdlp-pot-provider. YouTube is a trademark of Google LLC.
