#!/usr/bin/env python3
"""Render Scriflow's SVG source into Android launcher resources and PNG artwork.

Requires Python 3 and Inkscape. Normal APK builds use the checked-in outputs
and do not need Inkscape. Run this script after editing the source symbol.
"""
from pathlib import Path
import shutil
import subprocess
import xml.etree.ElementTree as ET


ROOT = Path(__file__).resolve().parent.parent
BRAND = ROOT / "assets/branding"
RES = ROOT / "app/src/main/res"
ANDROID = "http://schemas.android.com/apk/res/android"
SVG = "http://www.w3.org/2000/svg"


def main():
    inkscape = shutil.which("inkscape")
    if inkscape is None:
        raise SystemExit("Install Inkscape to regenerate the branding assets.")
    source = ET.parse(BRAND / "scriflow-symbol.svg").getroot()
    paths = source.findall("{%s}path" % SVG)
    if source.get("viewBox") != "0 0 108 108" or len(paths) != 1:
        raise SystemExit("Expected one symbol path in a 108 x 108 viewBox.")
    mark = paths[0]
    path = mark.get("d")
    if not path or mark.get("fill") != "#000000" or mark.get("fill-rule") != "evenodd":
        raise SystemExit("The source must be one black, even-odd filled path.")
    # The launcher needs a compact path: extremely fine chalk grains disappear
    # at icon sizes and a very long Android path can exceed resource limits.
    launcher = ET.parse(BRAND / "scriflow-symbol-android.svg").getroot()
    launcher_paths = launcher.findall("{%s}path" % SVG)
    if launcher.get("viewBox") != "0 0 108 108" or len(launcher_paths) != 1:
        raise SystemExit("Expected one compact launcher path in a 108 x 108 viewBox.")
    launcher_mark = launcher_paths[0]
    if launcher_mark.get("fill") != "#000000" or launcher_mark.get("fill-rule") != "evenodd":
        raise SystemExit("The launcher must be one black, even-odd filled path.")
    launcher_path = launcher_mark.get("d")
    if not launcher_path or len(launcher_path.encode("utf-8")) > 30000:
        raise SystemExit("The launcher path is missing or too long for an Android resource.")

    # The standalone icon includes a fine keyline for use on white pages.
    # Adaptive launchers supply their own mask and use only the unframed symbol.
    ET.register_namespace("", SVG)
    for suffix, background, foreground in (("", "#ffffff", "#000000"),
                                            ("-dark", "#000000", "#ffffff")):
        icon = ET.Element("{%s}svg" % SVG, {
            "width": "108", "height": "108", "viewBox": "0 0 108 108",
        })
        ET.SubElement(icon, "{%s}title" % SVG).text = "Scriflow app icon"
        ET.SubElement(icon, "{%s}desc" % SVG).text = "Copyright 2026 Xing. See LICENSE-ICON.md."
        ET.SubElement(icon, "{%s}rect" % SVG, {
            "x": "2", "y": "2", "width": "104", "height": "104", "rx": "24",
            "fill": background, "stroke": foreground, "stroke-width": "0.8",
        })
        attrs = dict(mark.attrib); attrs["fill"] = foreground
        ET.SubElement(icon, "{%s}path" % SVG, attrs)
        ET.ElementTree(icon).write(BRAND / ("scriflow-icon" + suffix + ".svg"),
                                   encoding="utf-8", xml_declaration=True)
        if suffix:
            dark = ET.Element("{%s}svg" % SVG, dict(source.attrib))
            ET.SubElement(dark, "{%s}title" % SVG).text = "Scriflow symbol, light on dark"
            ET.SubElement(dark, "{%s}desc" % SVG).text = "Copyright 2026 Xing. See LICENSE-ICON.md."
            ET.SubElement(dark, "{%s}path" % SVG, attrs)
            ET.ElementTree(dark).write(BRAND / "scriflow-symbol-dark.svg",
                                       encoding="utf-8", xml_declaration=True)

    ET.register_namespace("android", ANDROID)
    for suffix, color in (("", "#ff000000"), ("_dark", "#ffffffff")):
        vector = ET.Element("vector", {
            "xmlns:android": ANDROID,
            "android:width": "108dp", "android:height": "108dp",
            "android:viewportWidth": "108", "android:viewportHeight": "108",
        })
        ET.SubElement(vector, "path", {
            "android:fillColor": color, "android:fillType": "evenOdd", "android:pathData": launcher_path,
        })
        drawable = RES / ("drawable/ic_launcher_foreground" + suffix + ".xml")
        drawable.parent.mkdir(parents=True, exist_ok=True)
        ET.indent(vector, space="    ") if hasattr(ET, "indent") else None
        ET.ElementTree(vector).write(drawable, encoding="utf-8", xml_declaration=True)

    def render(svg, destination, size):
        destination.parent.mkdir(parents=True, exist_ok=True)
        subprocess.run([
            inkscape, str(svg), "--export-type=png", "--export-width=" + str(size),
            "--export-filename=" + str(destination),
        ], check=True)

    for name in ("scriflow-icon", "scriflow-icon-dark", "scriflow-symbol", "scriflow-symbol-dark"):
        render(BRAND / (name + ".svg"), BRAND / (name + ".png"), 512)
    for density, size in [("mdpi", 48), ("hdpi", 72), ("xhdpi", 96),
                          ("xxhdpi", 144), ("xxxhdpi", 192)]:
        render(BRAND / "scriflow-icon.svg", RES / ("mipmap-" + density) / "ic_launcher.png", size)
        render(BRAND / "scriflow-icon-dark.svg", RES / ("mipmap-night-" + density) / "ic_launcher.png", size)
    print("Updated Scriflow artwork and launcher resources from scriflow-symbol.svg.")


if __name__ == "__main__":
    main()
