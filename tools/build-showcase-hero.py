#!/usr/bin/env python3
"""Build the README banner from checked-in screenshots (requires Pillow and DejaVu)."""
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont, ImageOps

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / 'assets' / 'showcase'
FONTS = Path('/usr/share/fonts/truetype/dejavu')

def font(name, size):
    return ImageFont.truetype(str(FONTS / name), size)

def main():
    canvas = Image.new('RGB', (2000, 920), '#f2f2f0')
    draw = ImageDraw.Draw(canvas)
    symbol = Image.open(ROOT / 'assets' / 'branding' / 'scriflow-symbol.png').convert('RGBA')
    symbol.thumbnail((120, 120), Image.Resampling.LANCZOS)
    canvas.paste(symbol, (84, 65), symbol)
    draw.text((206, 100), 'SCRIFLOW', font=font('DejaVuSans-Bold.ttf', 34), fill='#181818')
    draw.line((106, 214, 850, 214), fill='#222222', width=2)
    headline = font('DejaVuSerif.ttf', 119)
    draw.text((96, 268), 'Make room', font=headline, fill='#111111')
    draw.text((96, 412), 'for thought.', font=headline, fill='#111111')
    body = font('DejaVuSans.ttf', 30)
    draw.text((108, 604), 'Write by hand. Read deeply.', font=body, fill='#303030')
    draw.text((108, 655), 'Keep your ideas together.', font=body, fill='#303030')
    draw.line((108, 760, 158, 760), fill='#222222', width=3)
    draw.text((176, 747), 'MADE FOR MATEPAD PAPER', font=font('DejaVuSans-Bold.ttf', 21), fill='#303030')
    for x, title, path in ((1000, '01  /  WRITE', '01-writing.png'), (1480, '02  /  READ', '02-pdf.png')):
        draw.text((x, 100), title, font=font('DejaVuSans-Bold.ttf', 20), fill='#333333')
        shot = Image.open(ASSETS / path).convert('RGB')
        shot = ImageOps.contain(shot, (426, 568), Image.Resampling.LANCZOS)
        draw.rounded_rectangle((x-8, 153, x+434, 737), radius=15, fill='#ffffff', outline='#222222', width=2)
        canvas.paste(shot, (x, 161))
    canvas.save(ASSETS / 'hero.png', optimize=True)

if __name__ == '__main__':
    main()
