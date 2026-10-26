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
    stroke = mark.get("stroke")
    width = mark.get("stroke-width")
    if not path or not stroke or not width:
        raise SystemExit("The source path must define its geometry and stroke.")

    # The standalone icon includes a fine keyline for use on white pages.
    # Adaptive launchers supply their own mask and use only the unframed symbol.
    ET.register_namespace("", SVG)
    icon = ET.Element("{%s}svg" % SVG, {
        "width": "108", "height": "108", "viewBox": "0 0 108 108",
    })
    ET.SubElement(icon, "{%s}title" % SVG).text = "Scriflow app icon"
    ET.SubElement(icon, "{%s}rect" % SVG, {
        "x": "2", "y": "2", "width": "104", "height": "104", "rx": "24",
        "fill": "#ffffff", "stroke": "#000000", "stroke-width": "0.8",
    })
    ET.SubElement(icon, "{%s}path" % SVG, dict(mark.attrib))
    icon_path = BRAND / "scriflow-icon.svg"
    ET.ElementTree(icon).write(icon_path, encoding="utf-8", xml_declaration=True)

    ET.register_namespace("android", ANDROID)
    vector = ET.Element("vector", {
        "xmlns:android": ANDROID,
        "android:width": "108dp", "android:height": "108dp",
        "android:viewportWidth": "108", "android:viewportHeight": "108",
    })
    ET.SubElement(vector, "path", {
        "android:fillColor": "#00000000", "android:strokeColor": stroke,
        "android:strokeWidth": width, "android:strokeLineCap": "round",
        "android:strokeLineJoin": "round", "android:pathData": path,
    })
    drawable = RES / "drawable/ic_launcher_foreground.xml"
    drawable.parent.mkdir(parents=True, exist_ok=True)
    ET.indent(vector, space="    ") if hasattr(ET, "indent") else None
    ET.ElementTree(vector).write(drawable, encoding="utf-8", xml_declaration=True)

    def render(svg, destination, size):
        destination.parent.mkdir(parents=True, exist_ok=True)
        subprocess.run([
            inkscape, str(svg), "--export-type=png", "--export-width=" + str(size),
            "--export-filename=" + str(destination),
        ], check=True)

    render(icon_path, BRAND / "scriflow-icon.png", 512)
    render(BRAND / "scriflow-symbol.svg", BRAND / "scriflow-symbol.png", 512)
    for density, size in [("mdpi", 48), ("hdpi", 72), ("xhdpi", 96),
                          ("xxhdpi", 144), ("xxxhdpi", 192)]:
        render(icon_path, RES / ("mipmap-" + density) / "ic_launcher.png", size)
    print("Updated Scriflow artwork and launcher resources from scriflow-symbol.svg.")


if __name__ == "__main__":
    main()
