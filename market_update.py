import os
import io
import math
import datetime as dt
import xml.etree.ElementTree as ET
from pathlib import Path

import requests
from PIL import Image, ImageDraw, ImageFont


# =========================
# Settings
# =========================

COINS = {
    "BTC": ["bitcoin"],
    "ETH": ["ethereum"],
    "SOL": ["solana"],
    "ETHW": ["ethereumpow", "ethereum-pow-iou"],
}

FX_CODES = ["USD", "EUR", "CNY"]

SITE_URL = "alilienasic.ru"
TELEGRAM_CHANNEL_URL = "t.me/AliLienASIC"

OUTPUT_IMAGE = "market_update.png"


# =========================
# Helpers
# =========================

def get_env_required(name: str) -> str:
    value = os.environ.get(name)
    if not value:
        raise RuntimeError(f"Missing environment variable: {name}")
    return value


def format_usd(value: float) -> str:
    if value >= 1000:
        return f"${value:,.0f}"
    if value >= 10:
        return f"${value:,.2f}".rstrip("0").rstrip(".")
    if value >= 1:
        return f"${value:,.3f}".rstrip("0").rstrip(".")
    return f"${value:,.4f}".rstrip("0").rstrip(".")


def format_change(value: float | None) -> tuple[str, tuple[int, int, int]]:
    if value is None:
        return "—", (165, 170, 178)
    arrow = "▲" if value >= 0 else "▼"
    color = (64, 210, 126) if value >= 0 else (240, 80, 92)
    sign = "+" if value >= 0 else ""
    return f"{arrow} {sign}{value:.2f}%", color


def format_rub(value: float) -> str:
    return f"{value:.2f} ₽"


# =========================
# Data sources
# =========================

def fetch_crypto_prices() -> dict:
    all_ids = []
    for ids in COINS.values():
        all_ids.extend(ids)

    url = "https://api.coingecko.com/api/v3/simple/price"
    params = {
        "ids": ",".join(all_ids),
        "vs_currencies": "usd",
        "include_24hr_change": "true",
    }

    r = requests.get(url, params=params, timeout=30)
    r.raise_for_status()
    raw = r.json()

    result = {}

    for ticker, ids in COINS.items():
        selected = None

        for coin_id in ids:
            item = raw.get(coin_id)
            if item and item.get("usd") is not None:
                selected = item
                break

        if selected:
            result[ticker] = {
                "price": selected.get("usd"),
                "change_24h": selected.get("usd_24h_change"),
            }
        else:
            result[ticker] = {
                "price": None,
                "change_24h": None,
            }

    return result


def fetch_cbr_fx() -> dict:
    url = "https://www.cbr.ru/scripts/XML_daily.asp"
    r = requests.get(url, timeout=30)
    r.raise_for_status()

    root = ET.fromstring(r.content)
    result = {}

    for valute in root.findall("Valute"):
        char_code = valute.findtext("CharCode")
        if char_code in FX_CODES:
            nominal = float(valute.findtext("Nominal").replace(",", "."))
            value = float(valute.findtext("Value").replace(",", "."))
            result[char_code] = value / nominal

    missing = [c for c in FX_CODES if c not in result]
    if missing:
        raise RuntimeError(f"Missing FX rates from CBR: {', '.join(missing)}")

    return result


# =========================
# Image rendering
# =========================

def load_font(size: int, bold: bool = False):
    candidates = [
        "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf" if bold else "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf",
        "/usr/share/fonts/truetype/liberation2/LiberationSans-Bold.ttf" if bold else "/usr/share/fonts/truetype/liberation2/LiberationSans-Regular.ttf",
    ]
    for p in candidates:
        if Path(p).exists():
            return ImageFont.truetype(p, size)
    return ImageFont.load_default()


def draw_market_card(crypto: dict, fx: dict, output_path: str = OUTPUT_IMAGE) -> str:
    W, H = 1080, 1350

    BG = (4, 5, 7)
    PANEL2 = (16, 18, 24)
    GOLD = (242, 169, 33)
    GOLD2 = (255, 199, 70)
    WHITE = (245, 245, 245)
    MUTED = (165, 170, 178)
    RED = (240, 80, 92)

    F_TITLE = load_font(54, True)
    F_SUB = load_font(28)
    F_DATE = load_font(26)
    F_TICKER = load_font(58, True)
    F_PRICE = load_font(48, True)
    F_CHANGE = load_font(34, True)
    F_SMALL = load_font(24)
    F_FOOT = load_font(28, True)

    img = Image.new("RGB", (W, H), BG)
    d = ImageDraw.Draw(img)

    # Background glow
    for r in range(560, 20, -18):
        a = int(38 * (1 - r / 560))
        col = (min(255, BG[0] + a), min(255, BG[1] + int(a * 0.55)), BG[2])
        d.ellipse((W // 2 - r, 180 - r, W // 2 + r, 180 + r), outline=col, width=3)

    # Main border
    margin = 46
    d.rounded_rectangle((margin, margin, W - margin, H - margin), radius=46, outline=GOLD, width=3, fill=(6, 7, 10))
    d.rounded_rectangle((margin + 8, margin + 8, W - margin - 8, H - margin - 8), radius=40, outline=(75, 50, 12), width=1)

    # Circuit lines
    for side in [0, 1]:
        x0 = 95 if side == 0 else W - 95
        sign = 1 if side == 0 else -1
        for i in range(7):
            y = 260 + i * 58
            pts = [
                (x0, y),
                (x0 + sign * (30 + i * 5), y),
                (x0 + sign * (30 + i * 5), y + 22),
                (x0 + sign * (75 + i * 4), y + 22),
            ]
            d.line(pts, fill=(165, 105, 18), width=3)
            ex, ey = pts[-1]
            d.ellipse((ex - 6, ey - 6, ex + 6, ey + 6), outline=GOLD, width=2)

    # Logo mark: stylized A in circle
    cx, cy = 160, 150
    d.ellipse((cx - 75, cy - 75, cx + 75, cy + 75), fill=(10, 11, 15), outline=GOLD, width=4)
    d.polygon([(cx - 42, cy + 47), (cx - 5, cy - 55), (cx + 43, cy + 47), (cx + 16, cy + 47), (cx - 5, cy - 12), (cx - 27, cy + 47)], fill=GOLD2)
    d.polygon([(cx - 8, cy + 22), (cx + 43, cy + 6), (cx + 28, cy + 25)], fill=WHITE)

    # Header
    today = dt.datetime.utcnow().strftime("%d %B %Y")
    d.text((260, 98), "ALI_LIEN", font=F_TITLE, fill=WHITE)
    d.text((260, 158), "MARKET UPDATE", font=F_SUB, fill=GOLD2)
    d.text((260, 205), f"{today} · Daily crypto & FX rates", font=F_DATE, fill=MUTED)

    d.line((80, 285, W - 80, 285), fill=(190, 123, 20), width=2)

    # Crypto rows
    row_y = 330
    row_h = 160
    for i, ticker in enumerate(["BTC", "ETH", "SOL", "ETHW"]):
        y = row_y + i * row_h
        data = crypto.get(ticker, {})
        price = data.get("price")
        change = data.get("change_24h")
        price_text = format_usd(price) if isinstance(price, (int, float)) else "—"
        change_text, change_color = format_change(change if isinstance(change, (int, float)) else None)

        d.rounded_rectangle((90, y, W - 90, y + 118), radius=24, fill=PANEL2, outline=(55, 37, 10), width=1)

        ccx, ccy = 150, y + 59
        d.ellipse((ccx - 38, ccy - 38, ccx + 38, ccy + 38), fill=(30, 32, 38), outline=GOLD, width=3)

        mini = ticker[0] if ticker != "ETHW" else "E"
        mini_font = load_font(34, True)
        bbox = d.textbbox((0, 0), mini, font=mini_font)
        d.text((ccx - (bbox[2] - bbox[0]) // 2, ccy - (bbox[3] - bbox[1]) // 2 - 3), mini, font=mini_font, fill=GOLD2)

        d.text((215, y + 31), ticker, font=F_TICKER, fill=WHITE)

        pb = d.textbbox((0, 0), price_text, font=F_PRICE)
        d.text((690 - (pb[2] - pb[0]), y + 39), price_text, font=F_PRICE, fill=WHITE)
        d.text((740, y + 44), change_text, font=F_CHANGE, fill=change_color)

    # FX block
    fx_y = 1015
    d.rounded_rectangle((90, fx_y, W - 90, fx_y + 120), radius=24, fill=(10, 11, 15), outline=(95, 63, 14), width=1)

    fx_items = [
        ("USD", format_rub(fx["USD"]), 120),
        ("EUR", format_rub(fx["EUR"]), 410),
        ("CNY", format_rub(fx["CNY"]), 700),
    ]
    for code, value, x in fx_items:
        d.text((x, fx_y + 28), code, font=F_FOOT, fill=WHITE)
        d.text((x + 85, fx_y + 28), value, font=F_FOOT, fill=MUTED)

    # Chip
    chip_x, chip_y = W // 2, 1210
    chip_w = 58
    d.rounded_rectangle((chip_x - chip_w // 2, chip_y - chip_w // 2, chip_x + chip_w // 2, chip_y + chip_w // 2), radius=10, outline=GOLD2, width=4, fill=(20, 16, 7))
    for k in range(-3, 4):
        x = chip_x + k * 13
        d.line((x, chip_y - chip_w // 2 - 18, x, chip_y - chip_w // 2 - 5), fill=GOLD, width=3)
        d.line((x, chip_y + chip_w // 2 + 5, x, chip_y + chip_w // 2 + 18), fill=GOLD, width=3)

    for k in range(-2, 3):
        y = chip_y + k * 13
        d.line((chip_x - chip_w // 2 - 45, y, chip_x - chip_w // 2 - 8, y), fill=GOLD, width=3)
        d.line((chip_x + chip_w // 2 + 8, y, chip_x + chip_w // 2 + 45, y), fill=GOLD, width=3)

    footer = f"{SITE_URL}  ·  {TELEGRAM_CHANNEL_URL}"
    fb = d.textbbox((0, 0), footer, font=F_SMALL)
    d.text(((W - (fb[2] - fb[0])) // 2, 1266), footer, font=F_SMALL, fill=MUTED)

    img.save(output_path, quality=95)
    return output_path


# =========================
# Telegram
# =========================

def send_to_telegram(image_path: str) -> None:
    bot_token = get_env_required("TELEGRAM_BOT_TOKEN")
    chat_id = os.environ.get("TELEGRAM_CHAT_ID", "@AliLienASIC")

    caption = (
        "📊 Ali_Lien Market Update\n\n"
        "BTC / ETH / SOL / ETHW\n"
        "USD / EUR / CNY\n\n"
        "Ali_Lien ASIC Firmware\n"
        "https://alilienasic.ru"
    )

    url = f"https://api.telegram.org/bot{bot_token}/sendPhoto"

    with open(image_path, "rb") as img_file:
        files = {"photo": img_file}
        data = {
            "chat_id": chat_id,
            "caption": caption,
        }
        r = requests.post(url, data=data, files=files, timeout=60)
        r.raise_for_status()


def main() -> None:
    crypto = fetch_crypto_prices()
    fx = fetch_cbr_fx()
    image_path = draw_market_card(crypto, fx, OUTPUT_IMAGE)

    if os.environ.get("DRY_RUN") == "1":
        print(f"DRY_RUN enabled. Image generated: {image_path}")
        return

    send_to_telegram(image_path)
    print("Market update sent to Telegram.")


if __name__ == "__main__":
    main()
