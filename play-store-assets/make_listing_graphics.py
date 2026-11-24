"""
Builds the Play Store listing graphics (captioned phone screenshots and the feature graphic) from the
renders StoreAssetsTest writes to app/src/test/snapshots/images/.

    python3 play-store-assets/make_listing_graphics.py

Needs Pillow and macOS's Avenir Next font. Output goes to play-store-assets/listing/ as 24-bit PNGs
(no alpha channel, which Play Console requires). See play-store-assets/listing.md for the whole process.
"""
from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter, ImageFont

ROOT = Path(__file__).resolve().parent.parent
RENDERS = ROOT / "app/src/test/snapshots/images"
OUT = ROOT / "play-store-assets/listing"
FONT = "/System/Library/Fonts/Avenir Next.ttc"
BOLD, DEMI, MEDIUM = 0, 2, 5

# Launcher icon colours (icon-source.svg): teal gradient, white and amber half rings.
TEAL_LIGHT = (33, 199, 214)  # #21C7D6
TEAL_DARK = (0, 86, 92)      # #00565C
AMBER = (255, 200, 87)       # #FFC857
SUBTEXT = (222, 248, 250)

# (output name, render, headline, subline, pro). Words in *stars* are drawn in amber. Play shows the
# shots in this order and most people only see the first two or three, so the core promise comes first.
SHOTS = [
    ("1-today", "shot_today", "Always know your *overtime*",
     "Check in with one tap and watch today's progress", False),
    ("2-overview", "shot_overview", "Ahead or behind? *See it.*",
     "Your balance by week, month, year or any range", False),
    ("3-history", "shot_history", "Log only the days that *were different*",
     "Days you skip count as your usual hours", False),
    ("4-auto", "shot_autoCheckIn", "Checks you in *automatically*",
     "When your phone joins the office Wi-Fi or reaches work", True),
    ("5-timesheet", "shot_timesheet", "Timesheets in *one tap*",
     "PDF or CSV for any period, ready to share", True),
    ("6-day", "shot_day", "Fix any day in *seconds*",
     "Edit blocks and breaks, mark vacation or sick days", False),
    ("7-private", "shot_todayDark", "*Private* by design",
     "No account. No ads. No tracking. No Internet access.", False),
]

W, H = 1080, 1920


def font(index: int, size: int) -> ImageFont.FreeTypeFont:
    return ImageFont.truetype(FONT, size, index=index)


def render(name: str) -> Image.Image:
    return Image.open(RENDERS / f"com.github.guidohu.clockshift_StoreAssetsTest_{name}.png").convert("RGB")


def background(size: tuple[int, int]) -> Image.Image:
    """Diagonal teal gradient like the icon, with a soft light glow in the upper left."""
    w, h = size
    small = Image.new("RGB", (64, 64))
    for y in range(64):
        for x in range(64):
            t = x / 63 * 0.35 + y / 63 * 0.65
            small.putpixel((x, y), tuple(round(a + (b - a) * t) for a, b in zip(TEAL_LIGHT, TEAL_DARK)))
    canvas = small.resize(size, Image.BICUBIC).convert("RGBA")
    glow = Image.new("RGBA", size, (0, 0, 0, 0))
    r = round(max(w, h) * 0.45)
    ImageDraw.Draw(glow).ellipse((-r // 2, -r // 2, r, r), fill=(255, 255, 255, 46))
    canvas.alpha_composite(glow.filter(ImageFilter.GaussianBlur(r // 3)))
    return canvas


def words_with_colour(text: str) -> list[tuple[str, tuple]]:
    """Splits 'Always know your *overtime*' into words, amber for the starred ones."""
    result, highlight = [], False
    for word in text.split():
        start = word.startswith("*")
        if start:
            highlight = True
        clean = word.replace("*", "")
        result.append((clean, AMBER if highlight else (255, 255, 255)))
        if word.endswith("*") and (not start or len(word) > 1):
            highlight = False
    return result


def wrap_words(draw, words, fnt, max_width):
    """Wraps into as few lines as fit [max_width], then evens them out so no word is left alone."""
    lines = greedy_wrap(draw, words, fnt, max_width)
    width = max_width
    while width > 200:
        narrower = greedy_wrap(draw, words, fnt, width - 10)
        if len(narrower) != len(lines):
            break
        lines, width = narrower, width - 10
    return lines


def greedy_wrap(draw, words, fnt, max_width):
    lines, line = [], []
    for word in words:
        candidate = line + [word]
        if line and draw.textlength(" ".join(w for w, _ in candidate), font=fnt) > max_width:
            lines.append(line)
            line = [word]
        else:
            line = candidate
    return lines + [line]


def draw_headline(draw, text, fnt, y, width, max_width, line_gap) -> int:
    space = draw.textlength(" ", font=fnt)
    for line in wrap_words(draw, words_with_colour(text), fnt, max_width):
        total = sum(draw.textlength(w, font=fnt) for w, _ in line) + space * (len(line) - 1)
        x = (width - total) / 2
        for word, colour in line:
            draw.text((x, y), word, font=fnt, fill=colour)
            x += draw.textlength(word, font=fnt) + space
        y += fnt.size + line_gap
    return y


def draw_centered(draw, text, fnt, y, width, max_width, fill, line_gap) -> int:
    lines = wrap_words(draw, [(w, fill) for w in text.split()], fnt, max_width)
    for line in lines:
        s = " ".join(w for w, _ in line)
        draw.text(((width - draw.textlength(s, font=fnt)) / 2, y), s, font=fnt, fill=fill)
        y += fnt.size + line_gap
    return y


def with_status_bar(screen: Image.Image) -> Image.Image:
    """Adds an Android status bar (time, Wi-Fi, battery) in the screen's own background colour."""
    bar_h = 84
    bg = screen.getpixel((6, 6))
    dark = sum(bg) / 3 < 128
    ink = (230, 235, 235) if dark else (28, 32, 33)
    out = Image.new("RGB", (screen.width, screen.height + bar_h), bg)
    out.paste(screen, (0, bar_h))
    d = ImageDraw.Draw(out)
    d.text((56, 22), "3:12", font=font(DEMI, 38), fill=ink)
    # Battery
    bx, by = screen.width - 100, 30
    d.rounded_rectangle((bx, by, bx + 44, by + 24), 5, outline=ink, width=3)
    d.rectangle((bx + 46, by + 7, bx + 50, by + 17), fill=ink)
    d.rounded_rectangle((bx + 5, by + 5, bx + 32, by + 19), 2, fill=ink)
    # Wi-Fi: a filled quarter-circle wedge
    wx, wy = screen.width - 170, 26
    d.pieslice((wx, wy, wx + 46, wy + 46), 225, 315, fill=ink)
    return out


def phone(screen: Image.Image, width: int) -> Image.Image:
    """The screen inside a slim dark device frame, with transparent rounded corners."""
    screen = with_status_bar(screen)
    bezel = round(width * 0.024)
    screen_w = width - 2 * bezel
    screen = screen.resize((screen_w, round(screen.height * screen_w / screen.width)), Image.LANCZOS)
    height = screen.height + 2 * bezel
    outer_r, inner_r = round(width * 0.09), round(width * 0.07)

    frame = Image.new("RGBA", (width, height), (0, 0, 0, 0))
    d = ImageDraw.Draw(frame)
    d.rounded_rectangle((0, 0, width - 1, height - 1), outer_r, fill=(22, 27, 30, 255))
    d.rounded_rectangle((2, 2, width - 3, height - 3), outer_r - 2, outline=(70, 80, 84, 255), width=2)
    mask = Image.new("L", screen.size, 0)
    ImageDraw.Draw(mask).rounded_rectangle((0, 0, screen.width - 1, screen.height - 1), inner_r, fill=255)
    frame.paste(screen, (bezel, bezel), mask)
    # Punch-hole camera
    cx, cy, r = width // 2, bezel + round(screen.width * 0.035), round(width * 0.016)
    d.ellipse((cx - r, cy - r, cx + r, cy + r), fill=(12, 14, 16, 255))
    return frame


def paste_with_shadow(canvas: Image.Image, item: Image.Image, pos: tuple[int, int], blur: int, opacity: float = 0.4) -> None:
    shadow = Image.new("RGBA", canvas.size, (0, 0, 0, 0))
    alpha = item.split()[3].point(lambda a: a * opacity)
    shadow.paste((0, 30, 34, 255), (pos[0], pos[1] + blur // 2), alpha)
    canvas.alpha_composite(shadow.filter(ImageFilter.GaussianBlur(blur)))
    canvas.alpha_composite(item, pos)


def pro_pill(draw: ImageDraw.ImageDraw, y: int) -> int:
    fnt = font(BOLD, 30)
    text = "PRO"
    tw = draw.textlength(text, font=fnt)
    pw, ph = tw + 44, 50
    x = (W - pw) / 2
    draw.rounded_rectangle((x, y, x + pw, y + ph), ph // 2, fill=AMBER)
    draw.text((x + 22, y + 7), text, font=fnt, fill=TEAL_DARK)
    return y + ph + 26


def paper(page: Image.Image, width: int) -> Image.Image:
    """A PDF page as a sheet of paper with rounded corners."""
    page = page.resize((width, round(page.height * width / page.width)), Image.LANCZOS)
    sheet = Image.new("RGBA", page.size, (0, 0, 0, 0))
    mask = Image.new("L", page.size, 0)
    ImageDraw.Draw(mask).rounded_rectangle((0, 0, page.width - 1, page.height - 1), 14, fill=255)
    sheet.paste(page, (0, 0), mask)
    return sheet


def screenshot(render_name: str, headline: str, subline: str, pro: bool) -> Image.Image:
    canvas = background((W, H))
    draw = ImageDraw.Draw(canvas)
    y = 110
    if pro:
        y = pro_pill(draw, y - 20)
    y = draw_headline(draw, headline, font(BOLD, 86), y, W, 960, 4)
    y = draw_centered(draw, subline, font(MEDIUM, 40), y + 18, W, 900, SUBTEXT, 10)
    top = 560  # the same in every shot, so the phone does not jump when swiping through the listing
    assert y + 40 <= top, f"caption too long: {headline}"

    if render_name == "shot_timesheet":
        # The Overview with its share button, and the PDF it exports laid over it.
        device = phone(render("shot_overview"), 640)
        paste_with_shadow(canvas, device, (60, top + 40), 36)
        sheet = paper(render(render_name), 720).rotate(-4, resample=Image.BICUBIC, expand=True)
        paste_with_shadow(canvas, sheet, (W - sheet.width - 30, top + 150), 40, 0.5)
    else:
        device = phone(render(render_name), 780)
        paste_with_shadow(canvas, device, ((W - device.width) // 2, top), 40)  # runs off the bottom edge
    return canvas.convert("RGB")


def feature_graphic() -> Image.Image:
    w, h = 1024, 500
    canvas = background((w, h))
    draw = ImageDraw.Draw(canvas)

    icon = Image.open(ROOT / "play-store-assets/icon-rounded.png").convert("RGBA").resize((112, 112), Image.LANCZOS)
    paste_with_shadow(canvas, icon, (64, 70), 14)
    draw.text((64, 200), "ClockShift", font=font(BOLD, 66), fill="white")
    draw.text((66, 290), "Work hours and", font=font(DEMI, 34), fill=SUBTEXT)
    x = 66 + draw.textlength("Work hours and ", font=font(DEMI, 34))
    draw.text((x, 290), "overtime", font=font(DEMI, 34), fill=AMBER)
    draw.text((66, 334), "at a glance", font=font(DEMI, 34), fill=SUBTEXT)
    draw.text((66, 404), "No ads  ·  No account  ·  Private", font=font(MEDIUM, 24), fill=SUBTEXT)

    device = phone(render("shot_today"), 300)
    paste_with_shadow(canvas, device, (w - device.width - 90, 50), 24)
    return canvas.convert("RGB")


def main() -> None:
    OUT.mkdir(exist_ok=True)
    for out_name, render_name, headline, subline, pro in SHOTS:
        screenshot(render_name, headline, subline, pro).save(OUT / f"screenshot-{out_name}.png", optimize=True)
    feature_graphic().save(OUT / "feature-graphic.png", optimize=True)
    print(f"Wrote {len(SHOTS)} screenshots and the feature graphic to {OUT.relative_to(ROOT)}")


if __name__ == "__main__":
    main()
