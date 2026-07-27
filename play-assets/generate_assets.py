"""
Generates Google Play graphic assets for Skyline Glider.

Everything is drawn from the same palette and visual language as the game
itself (core/Palette.kt, render/Backdrop.kt) so the store page and the app
look like the same product. Re-run after changing the palette:

    python3 generate_assets.py
"""
from PIL import Image, ImageDraw, ImageFilter, ImageFont
import math, random

INK        = (11, 10, 31)
DEEP       = (20, 16, 51)
NEON       = (59, 232, 255)
MAGENTA    = (255, 61, 154)
GOLD       = (255, 201, 60)
SUIT       = (47, 107, 255)
SKIN       = (232, 181, 138)

SKY = [(0.00, (7, 6, 26)), (0.30, (27, 17, 71)), (0.55, (74, 28, 99)),
       (0.78, (156, 47, 99)), (0.93, (226, 96, 63)), (1.00, (255, 162, 75))]


def lerp(a, b, t):
    return tuple(round(a[i] + (b[i] - a[i]) * t) for i in range(3))


def sky_colour(t):
    t = max(0.0, min(1.0, t))
    for i in range(len(SKY) - 1):
        p0, c0 = SKY[i]
        p1, c1 = SKY[i + 1]
        if p0 <= t <= p1:
            return lerp(c0, c1, (t - p0) / (p1 - p0))
    return SKY[-1][1]


def sky(draw, w, h, horizon):
    for y in range(int(horizon)):
        draw.line([(0, y), (w, y)], fill=sky_colour(y / horizon))
    draw.rectangle([0, horizon, w, h], fill=INK)


def sun(img, cx, cy, r):
    glow = Image.new("RGBA", img.size, (0, 0, 0, 0))
    g = ImageDraw.Draw(glow)
    g.ellipse([cx - r * 3, cy - r * 3, cx + r * 3, cy + r * 3], fill=(255, 179, 103, 70))
    glow = glow.filter(ImageFilter.GaussianBlur(r * 0.7))
    img.alpha_composite(glow)
    d = ImageDraw.Draw(img)
    d.ellipse([cx - r, cy - r, cx + r, cy + r], fill=(255, 208, 138, 255))
    d.ellipse([cx - r * .72, cy - r * .72, cx + r * .72, cy + r * .72], fill=(255, 240, 196, 255))


def skyline(draw, w, base, seed, colour, win_colour, min_h, max_h, win_chance, lit=True):
    rng = random.Random(seed)
    x = -20
    while x < w + 20:
        bw = rng.randint(int(w * 0.045), int(w * 0.11))
        bh = rng.randint(int(min_h), int(max_h))
        draw.rectangle([x, base - bh, x + bw, base], fill=colour)
        if rng.random() < 0.18:
            draw.polygon([(x + bw / 2, base - bh - bh * 0.22),
                          (x + bw * 0.15, base - bh),
                          (x + bw * 0.85, base - bh)], fill=colour)
        if lit:
            cols = max(1, bw // max(6, int(w * 0.011)))
            rows = max(1, bh // max(8, int(w * 0.014)))
            cw, rh = bw / (cols + 1), bh / (rows + 1)
            for c in range(cols):
                for r in range(rows):
                    if rng.random() > win_chance:
                        continue
                    wx = x + cw * (c + 0.7)
                    wy = base - bh + rh * (r + 0.7)
                    draw.rectangle([wx, wy, wx + cw * 0.45, wy + rh * 0.4], fill=win_colour)
        x += bw + rng.randint(2, int(w * 0.02))


def stars(draw, w, horizon, seed, n):
    rng = random.Random(seed)
    for _ in range(n):
        sx = rng.uniform(0, w)
        sy = rng.uniform(0, horizon * 0.55)
        r = rng.uniform(0.6, 1.9)
        fade = 1 - sy / (horizon * 0.55)
        a = int(200 * fade * rng.uniform(0.4, 1.0))
        draw.ellipse([sx - r, sy - r, sx + r, sy + r], fill=(255, 255, 255, a))


FONT_BLACK = "/usr/share/fonts/truetype/lato/Lato-Black.ttf"


def glider(img, cx, cy, unit, wing=NEON, suit=SUIT):
    """The wingsuit hero — same skeleton as render/Hero.kt, tuned for stills.

    `unit` is one 'metre'; the figure spans about 1.5 units nose to heel.
    """
    layer = Image.new("RGBA", img.size, (0, 0, 0, 0))
    d = ImageDraw.Draw(layer)

    def P(mx, my):
        return (cx + mx * unit, cy - my * unit)

    sho, hip = P(0.20, 0.14), P(-0.30, -0.04)
    hand_t, hand_b = P(0.56, 0.46), P(0.54, -0.22)
    foot_t, foot_b = P(-0.86, 0.20), P(-0.82, -0.34)

    # Wing membranes first, so the limbs read as ribs across them.
    d.polygon([sho, hand_t, foot_t, hip], fill=wing + (80,))
    d.polygon([sho, hand_b, foot_b, hip], fill=wing + (140,))
    edge = max(1, int(unit * 0.022))
    d.line([sho, hand_t, foot_t, hip, sho], fill=wing + (230,), width=edge)
    d.line([sho, hand_b, foot_b, hip, sho], fill=wing + (230,), width=edge)

    limb = max(2, int(unit * 0.075))
    for a, b in ((hip, foot_t), (sho, hand_t), (hip, foot_b), (sho, hand_b)):
        d.line([a, b], fill=suit + (255,), width=limb)
    d.line([hip, sho], fill=suit + (255,), width=max(3, int(unit * 0.17)))

    # Chest accent, as in the game.
    mid = ((hip[0] + sho[0]) / 2, (hip[1] + sho[1]) / 2)
    d.line([mid, sho], fill=wing + (255,), width=max(2, int(unit * 0.06)))

    hx, hy = P(0.36, 0.20)
    hr = unit * 0.13
    d.ellipse([hx - hr, hy - hr, hx + hr, hy + hr], fill=SKIN + (255,))
    d.pieslice([hx - hr * 1.18, hy - hr * 1.18, hx + hr * 1.18, hy + hr * 1.18],
               180, 360, fill=suit + (255,))
    d.line([(hx - hr * 1.1, hy - hr * .12), (hx + hr * 1.1, hy - hr * .12)],
           fill=wing + (255,), width=max(2, int(unit * 0.05)))

    img.alpha_composite(layer.filter(ImageFilter.GaussianBlur(unit * 0.10)))
    img.alpha_composite(layer)


def tracked_text(draw, xy, text, font, fill, tracking):
    """PIL has no letter-spacing, so place glyphs one at a time."""
    x, y = xy
    for ch in text:
        draw.text((x, y), ch, font=font, fill=fill)
        x += draw.textlength(ch, font=font) + tracking
    return x - tracking


def text_width(draw, text, font, tracking):
    return sum(draw.textlength(c, font=font) for c in text) + tracking * (len(text) - 1)


# ---------------------------------------------------------------- app icon
def make_icon(path, size=512):
    img = Image.new("RGBA", (size, size), INK + (255,))
    d = ImageDraw.Draw(img)
    horizon = size * 0.70
    sky(d, size, size, horizon)

    sd = ImageDraw.Draw(img, "RGBA")
    stars(sd, size, horizon, 7, 45)
    sun(img, size * 0.74, horizon - size * 0.12, size * 0.095)

    d = ImageDraw.Draw(img)
    skyline(d, size, horizon + size * 0.02, 11, (39, 32, 96), (150, 130, 220),
            size * 0.09, size * 0.26, 0.05)
    skyline(d, size, horizon + size * 0.08, 3, (23, 18, 58), GOLD,
            size * 0.13, size * 0.36, 0.14)

    # Hero sits slightly above centre; the icon is cropped to a circle on many
    # launchers, so nothing important goes near the corners.
    glider(img, size * 0.50, size * 0.44, size * 0.30)
    img.convert("RGB").save(path, "PNG", optimize=True)
    return path


# --------------------------------------------------------- feature graphic
def make_feature(path, w=1024, h=500):
    img = Image.new("RGBA", (w, h), INK + (255,))
    d = ImageDraw.Draw(img)
    horizon = h * 0.74
    sky(d, w, h, horizon)

    sd = ImageDraw.Draw(img, "RGBA")
    stars(sd, w, horizon, 21, 120)
    sun(img, w * 0.62, horizon - h * 0.16, h * 0.10)

    d = ImageDraw.Draw(img)
    skyline(d, w, horizon + 4, 5, (43, 35, 104), (154, 134, 224), h * 0.09, h * 0.30, 0.05)
    skyline(d, w, horizon + 18, 8, (30, 24, 74), GOLD, h * 0.14, h * 0.42, 0.12)
    skyline(d, w, horizon + 38, 13, (18, 14, 46), (255, 217, 138), h * 0.18, h * 0.52, 0.18)

    # Rooftop plane running off the bottom edge.
    d.polygon([(0, h), (w, h), (w, horizon + h * 0.19), (0, horizon + h * 0.25)],
              fill=(31, 26, 74))
    d.line([(0, horizon + h * 0.25), (w, horizon + h * 0.19)], fill=(108, 95, 184), width=3)

    # Coin arc sweeping in from the left, behind the hero.
    cd = ImageDraw.Draw(img, "RGBA")
    for i in range(8):
        t = i / 7
        cx = w * (0.545 + t * 0.20)
        cy = h * (0.60 - math.sin(t * math.pi) * 0.16)
        r = h * 0.018 * (0.75 + t * 0.45)
        cd.ellipse([cx - r * 2.6, cy - r * 2.6, cx + r * 2.6, cy + r * 2.6], fill=GOLD + (28,))
        cd.ellipse([cx - r, cy - r, cx + r, cy + r], fill=GOLD + (255,))
        cd.ellipse([cx - r * .62, cy - r * .62, cx + r * .62, cy + r * .62], fill=(255, 240, 190, 255))

    glider(img, w * 0.795, h * 0.40, h * 0.30)

    # Title block, kept clear of the 5% edge margin Play may crop.
    td = ImageDraw.Draw(img, "RGBA")
    f_big = ImageFont.truetype(FONT_BLACK, int(h * 0.155))
    f_sub = ImageFont.truetype(FONT_BLACK, int(h * 0.072))
    f_tag = ImageFont.truetype(FONT_BLACK, int(h * 0.048))
    x0 = int(w * 0.065)
    tracked_text(td, (x0, h * 0.30), "SKYLINE", f_big, (237, 234, 255, 255), h * 0.020)
    tracked_text(td, (x0 + 3, h * 0.475), "GLIDER", f_sub, NEON + (255,), h * 0.055)
    tracked_text(td, (x0 + 3, h * 0.60), "ENDLESS ROOFTOP RUNNER", f_tag,
                 (158, 151, 201, 255), h * 0.012)

    img.convert("RGB").save(path, "PNG", optimize=True)
    return path


if __name__ == "__main__":
    make_icon("play-icon-512.png")
    make_feature("play-feature-graphic-1024x500.png")
    print("written")
