"""Generates the Skaldoria app icon (PNG + multi-size ICO) in a Nord palette."""
import os
from PIL import Image, ImageDraw, ImageFont

OUT_DIR = os.path.join(os.path.dirname(__file__), "..", "src", "desktopMain", "resources", "icons")
OUT_DIR = os.path.abspath(OUT_DIR)
os.makedirs(OUT_DIR, exist_ok=True)

S = 1024  # master render size

# Nord palette
BG_TOP = (129, 161, 193)      # nord9  #81A1C1
BG_BOT = (94, 129, 172)       # nord10 #5E81AC
FRAME = (46, 52, 64)          # nord0  #2E3440
SLIDE = (236, 239, 244)       # nord6  #ECEFF4
ACCENT = (136, 192, 208)      # nord8  #88C0D0
GOLD = (235, 203, 139)        # nord13 #EBCB8B


def rounded_mask(size, radius):
    m = Image.new("L", (size, size), 0)
    d = ImageDraw.Draw(m)
    d.rounded_rectangle([0, 0, size - 1, size - 1], radius=radius, fill=255)
    return m


def vertical_gradient(size, top, bot):
    grad = Image.new("RGB", (1, size))
    for y in range(size):
        t = y / (size - 1)
        grad.putpixel((0, y), tuple(int(top[i] + (bot[i] - top[i]) * t) for i in range(3)))
    return grad.resize((size, size))


def load_font(px):
    for name in ("segoeuib.ttf", "arialbd.ttf", "seguisb.ttf", "arial.ttf"):
        p = os.path.join("C:\\Windows\\Fonts", name)
        if os.path.exists(p):
            return ImageFont.truetype(p, px)
    return ImageFont.load_default()


def build():
    img = Image.new("RGBA", (S, S), (0, 0, 0, 0))

    # Gradient rounded-square background with a dark frame ring.
    radius = int(S * 0.22)
    grad = vertical_gradient(S, BG_TOP, BG_BOT).convert("RGBA")
    mask = rounded_mask(S, radius)
    img.paste(grad, (0, 0), mask)

    draw = ImageDraw.Draw(img)
    inset = int(S * 0.055)
    draw.rounded_rectangle(
        [inset, inset, S - inset, S - inset],
        radius=int(radius * 0.82), outline=FRAME, width=int(S * 0.03),
    )

    # A stylised presentation slide behind the monogram.
    sx0, sy0 = int(S * 0.24), int(S * 0.30)
    sx1, sy1 = int(S * 0.76), int(S * 0.64)
    draw.rounded_rectangle([sx0, sy0, sx1, sy1], radius=int(S * 0.035),
                           fill=SLIDE + (36,))

    # Monogram "S" for Skaldoria.
    font = load_font(int(S * 0.60))
    text = "S"
    bbox = draw.textbbox((0, 0), text, font=font)
    tw, th = bbox[2] - bbox[0], bbox[3] - bbox[1]
    tx = (S - tw) / 2 - bbox[0]
    ty = (S - th) / 2 - bbox[1] - int(S * 0.02)
    # soft shadow
    draw.text((tx + int(S * 0.012), ty + int(S * 0.012)), text, font=font, fill=(46, 52, 64, 120))
    draw.text((tx, ty), text, font=font, fill=SLIDE)

    # Presentation "screen" underline bar + nav dots.
    bar_y = int(S * 0.78)
    draw.rounded_rectangle([int(S * 0.30), bar_y, int(S * 0.70), bar_y + int(S * 0.028)],
                           radius=int(S * 0.014), fill=GOLD)
    dot_r = int(S * 0.018)
    cy = bar_y + int(S * 0.075)
    for i, cx in enumerate((0.42, 0.50, 0.58)):
        fill = ACCENT if i == 0 else SLIDE + (150,)
        x = int(S * cx)
        draw.ellipse([x - dot_r, cy - dot_r, x + dot_r, cy + dot_r], fill=fill)

    # Re-apply rounded mask so corners stay clean.
    out = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    out.paste(img, (0, 0), mask)

    png_path = os.path.join(OUT_DIR, "app.png")
    out.resize((512, 512), Image.LANCZOS).save(png_path)

    ico_path = os.path.join(OUT_DIR, "app.ico")
    sizes = [16, 24, 32, 48, 64, 128, 256]
    out.save(ico_path, format="ICO", sizes=[(s, s) for s in sizes])

    print("wrote", png_path)
    print("wrote", ico_path)


if __name__ == "__main__":
    build()
