# README showcase

The four screenshots were captured from Scriflow 0.49.0-rc.1's real UI in an
Android 10 emulator, using disposable sample data rather than a person's notes.
The notebook lettering and PDF annotations are synthetic stroke paths derived
from the QTHandwriting font (Copyright 1992 QualiType, available under the SIL
Open Font License 1.1 or GPL); the emulator uses a software pen approximation
because the Huawei firmware brush is unavailable. These images do not measure
or represent physical stylus pressure, appearance, or latency. The PDF and
browser page are original sample documents; the browser page was served locally.

`hero.png` combines the unaltered writing and PDF screenshots with the existing
Scriflow symbol. Regenerate it with `python3 tools/build-showcase-hero.py` using
Pillow and DejaVu fonts installed. The gallery uses the original emulator
screenshots directly. The Scriflow symbol embedded in the banner has a separate
[rights notice](../../LICENSE-ICON.md).
