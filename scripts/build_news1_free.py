#!/usr/bin/env python3
"""NEWS1 Free V2 collector.

100% free / no API key.
- Market prices: Yahoo Finance public chart endpoint (best effort).
- Calendar: Forex Factory/FairEconomy public JSON feed (best effort).
- News discovery: Google News RSS metadata limited to requested publishers.
- Trading Economics is intentionally prioritised in news discovery because the user
  wants a larger share of TE market/macro coverage.

No paywalled article is reconstructed. Analysis is headline/rule-based and is only
emitted when there is a specific rule; otherwise the analysis field is omitted.
"""
from __future__ import annotations

import email.utils
import html
import json
import math
import re
import urllib.parse
import urllib.request
import xml.etree.ElementTree as ET
from concurrent.futures import ThreadPoolExecutor, as_completed
from datetime import datetime, timedelta, timezone
from pathlib import Path
from typing import Any
from zoneinfo import ZoneInfo

ROOT = Path(__file__).resolve().parents[1]
DATA = ROOT / "data"
DATA.mkdir(exist_ok=True)
LATEST = DATA / "latest.json"
HISTORY = DATA / "history.json"
JKT = ZoneInfo("Asia/Jakarta")
UTC = timezone.utc
NOW_UTC = datetime.now(UTC)
NOW_WIB = NOW_UTC.astimezone(JKT)
UA = "Mozilla/5.0 (NEWS1-Free-V2; +https://github.com/)"

ASSETS = [
    ("XAUUSD", ["XAUUSD=X"], "Logam Mulia", "Emas Spot"),
    ("Silver", ["SI=F"], "Logam Mulia", "Perak"),
    ("Copper", ["HG=F"], "Logam Industri", "Tembaga"),
    ("DXY", ["DX-Y.NYB", "DX=F"], "Dollar", "Indeks dolar AS"),
    ("US10Y", ["^TNX"], "Yield", "Treasury AS 10Y"),
    ("USDJPY", ["JPY=X"], "FX", "Dolar AS / Yen"),
    ("EURUSD", ["EURUSD=X"], "FX", "Euro / Dolar AS"),
    ("GBPUSD", ["GBPUSD=X"], "FX", "Pound / Dolar AS"),
    ("Brent", ["BZ=F"], "Energi", "Minyak Brent"),
    ("WTI", ["CL=F"], "Energi", "Minyak WTI"),
    ("S&P 500", ["^GSPC"], "Saham AS", "S&P 500"),
    ("Dow", ["^DJI"], "Saham AS", "Dow Jones"),
    ("Nasdaq", ["^IXIC"], "Saham AS", "Nasdaq Composite"),
    ("Bitcoin", ["BTC-USD"], "Crypto", "Bitcoin"),
]

ASSET_META = {a: {"category": cat, "labelId": label} for a, _s, cat, label in ASSETS}
ALL_IMPACT_ASSETS = [
    "XAUUSD", "Silver", "Copper", "DXY", "US Treasury yields", "USDJPY",
    "EURUSD", "GBPUSD", "Brent", "WTI", "US equities", "Bitcoin"
]

ALLOWED_PUBLISHERS = {
    "reuters": "Reuters",
    "bloomberg": "Bloomberg",
    "yahoo finance": "Yahoo Finance",
    "trading economics": "Trading Economics",
    "forex factory": "Forex Factory",
}

TE_REFERENCE = {
    "XAUUSD": "https://tradingeconomics.com/commodity/gold",
    "Silver": "https://tradingeconomics.com/commodity/silver",
    "Copper": "https://tradingeconomics.com/commodity/copper",
    "DXY": "https://tradingeconomics.com/united-states/currency",
    "US10Y": "https://tradingeconomics.com/united-states/government-bond-yield",
    "USDJPY": "https://tradingeconomics.com/japan/currency",
    "EURUSD": "https://tradingeconomics.com/euro-area/currency",
    "GBPUSD": "https://tradingeconomics.com/united-kingdom/currency",
    "Brent": "https://tradingeconomics.com/commodity/brent-crude-oil",
    "WTI": "https://tradingeconomics.com/commodity/crude-oil",
    "S&P 500": "https://tradingeconomics.com/united-states/stock-market",
    "Dow": "https://tradingeconomics.com/united-states/stock-market",
    "Nasdaq": "https://tradingeconomics.com/united-states/stock-market",
    "Bitcoin": "https://tradingeconomics.com/commodity/bitcoin",
}


def fetch_text(url: str, timeout: int = 7) -> str:
    req = urllib.request.Request(url, headers={"User-Agent": UA, "Accept": "*/*"})
    with urllib.request.urlopen(req, timeout=timeout) as r:
        return r.read().decode("utf-8", errors="replace")


def safe_num(x: Any) -> float | None:
    try:
        v = float(x)
        return None if math.isnan(v) or math.isinf(v) else v
    except Exception:
        return None


def format_price(asset: str, value: float | None) -> str:
    if value is None:
        return "—"
    if asset == "US10Y":
        y = value / 10.0 if value > 20 else value
        return f"{y:.3f}%"
    if asset == "USDJPY":
        return f"{value:.3f}"
    if asset in {"EURUSD", "GBPUSD"}:
        return f"{value:.5f}"
    if asset == "DXY":
        return f"{value:.3f}"
    if asset in {"Copper"}:
        return f"{value:.4f}"
    return f"{value:,.2f}"


def yahoo_snapshot(asset: str, symbols: list[str]) -> dict[str, Any] | None:
    for symbol in symbols:
        try:
            enc = urllib.parse.quote(symbol, safe="")
            url = f"https://query1.finance.yahoo.com/v8/finance/chart/{enc}?range=2d&interval=15m&includePrePost=true"
            obj = json.loads(fetch_text(url))
            result = obj.get("chart", {}).get("result") or []
            if not result:
                raise ValueError("no result")
            r = result[0]
            meta = r.get("meta", {})
            timestamps = r.get("timestamp") or []
            closes = (((r.get("indicators") or {}).get("quote") or [{}])[0].get("close") or [])
            valid = [(int(ts), safe_num(cl)) for ts, cl in zip(timestamps, closes)]
            valid = [(ts, cl) for ts, cl in valid if cl is not None]
            last = safe_num(meta.get("regularMarketPrice"))
            if last is None and valid:
                last = valid[-1][1]
            if last is None:
                raise ValueError("no price")
            cutoff = int((NOW_UTC - timedelta(hours=24)).timestamp())
            older = [cl for ts, cl in valid if ts <= cutoff]
            first = older[-1] if older else safe_num(meta.get("chartPreviousClose"))
            if first is None and valid:
                first = valid[0][1]
            pct = ((last - first) / first * 100.0) if first not in (None, 0) else None
            src = "Yahoo Finance"
            is_future = symbol.endswith("=F")
            if is_future:
                src += " • futures"
            meta_asset = ASSET_META.get(asset, {})
            return {
                "asset": asset,
                "labelId": meta_asset.get("labelId", asset),
                "category": meta_asset.get("category", "Market"),
                "symbol": symbol,
                "last": format_price(asset, last),
                "lastNumeric": round(last, 8),
                "changePct": round(pct, 3) if pct is not None else None,
                "change": f"{pct:+.2f}%" if pct is not None else "—",
                "source": src,
                "priceType": "FUTURES" if is_future else "SPOT/INDEX/REFERENCE",
                "comment": f"{symbol} • {'futures' if is_future else 'reference'}",
                "sourceUrl": f"https://finance.yahoo.com/quote/{urllib.parse.quote(symbol, safe='')}",
                "referenceSource": "Trading Economics",
                "referenceUrl": TE_REFERENCE.get(asset, "https://tradingeconomics.com/markets"),
                "stale": False,
            }
        except Exception:
            continue
    return None


def load_previous() -> dict[str, Any]:
    try:
        return json.loads(LATEST.read_text(encoding="utf-8"))
    except Exception:
        return {}


def market_data(previous: dict[str, Any]) -> tuple[list[dict[str, Any]], list[str]]:
    prev_map = {x.get("asset"): x for x in previous.get("marketSnapshot", []) if isinstance(x, dict)}
    results: dict[str, dict[str, Any] | None] = {}
    warnings: list[str] = []
    with ThreadPoolExecutor(max_workers=8) as ex:
        futs = {ex.submit(yahoo_snapshot, asset, symbols): asset for asset, symbols, _cat, _label in ASSETS}
        for fut in as_completed(futs):
            asset = futs[fut]
            try:
                results[asset] = fut.result()
            except Exception:
                results[asset] = None
    out = []
    for asset, _symbols, _cat, _label in ASSETS:
        snap = results.get(asset)
        if snap:
            out.append(snap)
        elif asset in prev_map:
            old = dict(prev_map[asset])
            old["stale"] = True
            old["source"] = old.get("source", "Yahoo Finance") + " • cached"
            out.append(old)
            warnings.append(f"{asset}: harga live gagal; memakai cache terakhir.")
        else:
            warnings.append(f"{asset}: harga belum tersedia dari endpoint publik.")
    return out, warnings


def parse_dt_any(s: str) -> datetime | None:
    if not s:
        return None
    try:
        dt = datetime.fromisoformat(s.replace("Z", "+00:00"))
        if dt.tzinfo is None:
            dt = dt.replace(tzinfo=UTC)
        return dt
    except Exception:
        pass
    try:
        return email.utils.parsedate_to_datetime(s)
    except Exception:
        return None


def ff_calendar() -> tuple[list[dict[str, Any]], list[str]]:
    urls = [
        "https://nfs.faireconomy.media/ff_calendar_thisweek.json",
        "https://nfs.faireconomy.media/ff_calendar_nextweek.json",
    ]
    raw_items: list[dict[str, Any]] = []
    warnings: list[str] = []
    ok = 0
    for url in urls:
        try:
            data = json.loads(fetch_text(url))
            if isinstance(data, list):
                raw_items.extend(x for x in data if isinstance(x, dict))
                ok += 1
        except Exception:
            warnings.append(f"Kalender Forex Factory tidak tersedia pada run ini ({url.rsplit('/',1)[-1]}).")
    if not ok:
        return [], warnings
    end = NOW_UTC + timedelta(days=7)
    seen = set()
    out = []
    for x in raw_items:
        dt = parse_dt_any(str(x.get("date", "")))
        if not dt:
            continue
        dt_utc = dt.astimezone(UTC)
        if dt_utc < NOW_UTC - timedelta(hours=1) or dt_utc > end:
            continue
        title = str(x.get("title") or x.get("event") or "").strip()
        country = str(x.get("country") or x.get("currency") or "").strip()
        key = (dt_utc.isoformat(), title, country)
        if key in seen:
            continue
        seen.add(key)
        impact_raw = str(x.get("impact") or "Low").lower()
        impact = "HIGH" if "high" in impact_raw else "MEDIUM" if "medium" in impact_raw else "LOW"
        out.append({
            "datetimeWib": dt.astimezone(JKT).strftime("%d %b %Y %H:%M WIB"),
            "timestamp": int(dt_utc.timestamp()),
            "currency": country or "—",
            "event": title or "Economic event",
            "eventId": translate_lite(title),
            "impact": impact,
            "actual": str(x.get("actual") or "—"),
            "forecast": str(x.get("forecast") or "—"),
            "previous": str(x.get("previous") or "—"),
            "revision": str(x.get("revision") or "—"),
            "source": "Forex Factory / FairEconomy",
            "url": "https://www.forexfactory.com/calendar",
            "referenceSource": "Trading Economics",
            "referenceUrl": "https://tradingeconomics.com/calendar",
        })
    out.sort(key=lambda x: x["timestamp"])
    return out, warnings


def clean_title(s: str) -> str:
    return re.sub(r"\s+", " ", html.unescape(re.sub(r"<[^>]+>", "", s or ""))).strip()


def translate_lite(text: str) -> str:
    """Lightweight local market-language translation; no external translation API."""
    if not text:
        return ""
    replacements = [
        (r"\bGold\b", "Emas"), (r"\bSilver\b", "Perak"), (r"\bCopper\b", "Tembaga"),
        (r"\bCrude Oil\b", "Minyak Mentah"), (r"\bOil\b", "Minyak"), (r"\bBrent\b", "Brent"),
        (r"\bDollar\b", "Dolar"), (r"\bUS Dollar\b", "Dolar AS"),
        (r"\bTreasury yields\b", "Imbal hasil Treasury"), (r"\bTreasury yield\b", "Imbal hasil Treasury"),
        (r"\byields\b", "imbal hasil"), (r"\byield\b", "imbal hasil"),
        (r"\bstocks\b", "saham"), (r"\bstock market\b", "pasar saham"),
        (r"\binflation\b", "inflasi"), (r"\bconsumer prices\b", "harga konsumen"),
        (r"\binterest rates\b", "suku bunga"), (r"\binterest rate\b", "suku bunga"),
        (r"\brate cut\b", "pemangkasan suku bunga"), (r"\brate hike\b", "kenaikan suku bunga"),
        (r"\brises\b", "naik"), (r"\brise\b", "naik"), (r"\bgains\b", "menguat"),
        (r"\bsurges\b", "melonjak"), (r"\bjumps\b", "melonjak"),
        (r"\bfalls\b", "turun"), (r"\bfall\b", "turun"), (r"\bdrops\b", "turun"),
        (r"\bslides\b", "melemah"), (r"\beases\b", "mereda"), (r"\bweakens\b", "melemah"),
        (r"\bhigher\b", "lebih tinggi"), (r"\blower\b", "lebih rendah"),
        (r"\bahead of\b", "menjelang"), (r"\bafter\b", "setelah"), (r"\bamid\b", "di tengah"),
        (r"\bon\b", "setelah"), (r"\bas\b", "saat"),
        (r"\bwar\b", "perang"), (r"\battack\b", "serangan"), (r"\btalks\b", "perundingan"),
        (r"\btrade\b", "perdagangan"), (r"\beconomy\b", "ekonomi"),
    ]
    result = text
    for pattern, repl in replacements:
        result = re.sub(pattern, repl, result, flags=re.IGNORECASE)
    return re.sub(r"\s+", " ", result).strip()


def news_rss() -> tuple[list[dict[str, Any]], list[str]]:
    cutoff = NOW_UTC - timedelta(hours=24)
    base = "when:1d"
    # Multiple TE queries deliberately increase TE share without using the paid API.
    site_queries = [
        ("Trading Economics", f"site:tradingeconomics.com (gold OR silver OR copper OR oil OR Brent OR WTI OR commodities) {base}"),
        ("Trading Economics", f"site:tradingeconomics.com (DXY OR dollar OR EURUSD OR GBPUSD OR USDJPY OR currencies) {base}"),
        ("Trading Economics", f"site:tradingeconomics.com (Treasury OR bond OR yield OR stocks OR S&P OR Nasdaq) {base}"),
        ("Trading Economics", f"site:tradingeconomics.com (CPI OR inflation OR Fed OR payrolls OR GDP OR retail sales OR economy) {base}"),
        ("Trading Economics", f"site:tradingeconomics.com (Iran OR Hormuz OR geopolitics OR war OR sanctions) {base}"),
        ("Reuters", f"site:reuters.com (gold OR dollar OR Treasury OR oil OR stocks OR Bitcoin OR Fed OR Hormuz) {base}"),
        ("Bloomberg", f"site:bloomberg.com (gold OR dollar OR Treasury OR oil OR stocks OR Bitcoin OR Fed OR Hormuz) {base}"),
        ("Yahoo Finance", f"site:finance.yahoo.com (gold OR dollar OR Treasury OR oil OR stocks OR Bitcoin OR Fed OR Hormuz) {base}"),
        ("Forex Factory", f"site:forexfactory.com (Fed OR CPI OR payrolls OR forex OR dollar OR gold) {base}"),
    ]

    def one(expected_source: str, q: str):
        local = []
        url = "https://news.google.com/rss/search?" + urllib.parse.urlencode({"q": q, "hl": "en-US", "gl": "US", "ceid": "US:en"})
        root = ET.fromstring(fetch_text(url))
        for item in root.findall(".//item"):
            title = clean_title(item.findtext("title") or "")
            link = (item.findtext("link") or "").strip()
            pub = parse_dt_any(item.findtext("pubDate") or "")
            src_el = item.find("source")
            publisher = clean_title(src_el.text if src_el is not None and src_el.text else expected_source)
            source_url = src_el.attrib.get("url", "") if src_el is not None else ""
            if pub is None or pub.astimezone(UTC) < cutoff or pub.astimezone(UTC) > NOW_UTC + timedelta(minutes=10):
                continue
            normalized = None
            p_low = publisher.lower()
            for k, v in ALLOWED_PUBLISHERS.items():
                if k in p_low:
                    normalized = v
                    break
            if normalized is None and expected_source.lower() in p_low:
                normalized = expected_source
            if normalized is None:
                continue
            # Google News often appends publisher to the title; remove once.
            title = re.sub(r"\s+-\s+(Reuters|Bloomberg|Yahoo Finance|Trading Economics|Forex Factory)\s*$", "", title, flags=re.I)
            local.append({"title": title, "source": normalized, "published": pub.astimezone(UTC), "url": link, "publisherUrl": source_url})
        return local

    items: list[dict[str, Any]] = []
    warnings: list[str] = []
    with ThreadPoolExecutor(max_workers=9) as ex:
        futs = {ex.submit(one, src, q): f"{src}-{i}" for i, (src, q) in enumerate(site_queries)}
        for fut in as_completed(futs):
            label = futs[fut]
            try:
                items.extend(fut.result())
            except Exception:
                warnings.append(f"Headline {label} gagal diindeks pada run ini.")

    dedup: dict[str, dict[str, Any]] = {}
    for x in items:
        k = re.sub(r"\W+", " ", x["title"].lower()).strip()
        if not k:
            continue
        if k not in dedup or x["published"] > dedup[k]["published"]:
            dedup[k] = x

    all_items = list(dedup.values())
    te = sorted([x for x in all_items if x["source"] == "Trading Economics"], key=lambda x: x["published"], reverse=True)[:32]
    others = sorted([x for x in all_items if x["source"] != "Trading Economics"], key=lambda x: x["published"], reverse=True)[:24]
    # Interleave: 2 TE then 1 other, preserving freshness within each bucket.
    final = []
    while te or others:
        for _ in range(2):
            if te:
                final.append(te.pop(0))
        if others:
            final.append(others.pop(0))
        if len(final) >= 48:
            break
    return final, warnings


def direction_from_words(text: str) -> str | None:
    t = text.lower()
    up = ["rises", "rise", "jumps", "surges", "gains", "higher", "accelerates", "hotter", "hawkish", "hike", "tightens", "cuts supply", "output cut", "rebound"]
    down = ["falls", "fall", "drops", "slides", "lower", "eases", "cools", "slows", "dovish", "rate cut", "weakens", "output rises", "supply rises", "declines"]
    if any(w in t for w in up): return "UP"
    if any(w in t for w in down): return "DOWN"
    return None


def news_category(title: str) -> str:
    t = title.lower()
    if any(k in t for k in ["iran", "hormuz", "war", "missile", "attack", "sanction", "israel", "ukraine", "russia", "china tensions"]):
        return "Geopolitik"
    if any(k in t for k in ["fed", "federal reserve", "ecb", "boj", "boe", "central bank", "rate cut", "rate hike"]):
        return "Bank Sentral"
    if any(k in t for k in ["cpi", "ppi", "pce", "payroll", "jobs", "unemployment", "gdp", "retail sales", "inflation", "economy"]):
        return "Makro & Data"
    if any(k in t for k in ["oil", "brent", "wti", "opec", "gold", "silver", "copper", "commodity", "commodities"]):
        return "Komoditas"
    if any(k in t for k in ["treasury", "yield", "bond", "gilt", "bund"]):
        return "Yield & Obligasi"
    if any(k in t for k in ["eurusd", "gbpusd", "usdjpy", "dxy", "dollar", "yen", "euro", "sterling", "currency", "forex"]):
        return "FX"
    if any(k in t for k in ["bitcoin", "crypto", "ethereum"]):
        return "Crypto"
    if any(k in t for k in ["s&p", "nasdaq", "dow", "stocks", "shares", "wall street", "equities"]):
        return "Saham"
    return "Umum Trading"


def classify_headline(title: str) -> tuple[str, dict[str, str], str]:
    t = title.lower()
    impacts = {a: "MIXED" for a in ALL_IMPACT_ASSETS}
    impact = "LOW"
    note = ""  # empty means: do not show an analysis box

    def set_many(mapping: dict[str, str]):
        impacts.update(mapping)

    if any(k in t for k in ["hormuz", "iran", "war", "missile", "attack", "strait", "sanctions"]):
        impact = "HIGH"
        set_many({"XAUUSD":"UP", "Silver":"UP", "Brent":"UP", "WTI":"UP", "US equities":"DOWN", "Bitcoin":"DOWN"})
        note = "Eskalasi geopolitik/Hormuz cenderung menaikkan risk premium minyak dan permintaan safe haven. Efek akhir pada emas tetap perlu dikonfirmasi oleh DXY dan yield AS."
    elif any(k in t for k in ["cpi", "inflation", "ppi", "pce"]):
        impact = "HIGH"
        d = direction_from_words(t)
        if d == "DOWN":
            set_many({"XAUUSD":"UP", "Silver":"UP", "DXY":"DOWN", "US Treasury yields":"DOWN", "USDJPY":"DOWN", "EURUSD":"UP", "GBPUSD":"UP", "US equities":"UP", "Bitcoin":"UP"})
            note = "Inflasi yang lebih dingin biasanya menekan yield dan USD sehingga mendukung emas, perak, FX non-USD dan aset berisiko."
        elif d == "UP":
            set_many({"XAUUSD":"DOWN", "Silver":"DOWN", "DXY":"UP", "US Treasury yields":"UP", "USDJPY":"UP", "EURUSD":"DOWN", "GBPUSD":"DOWN", "US equities":"DOWN", "Bitcoin":"DOWN"})
            note = "Inflasi yang lebih panas biasanya mengangkat yield/USD dan menekan emas serta aset berisiko."
        else:
            note = "Data inflasi adalah katalis tinggi. Arah baru valid setelah membandingkan actual vs forecast dan melihat reaksi DXY/yield."
    elif any(k in t for k in ["federal reserve", " fed ", "powell", "rate cut", "rate hike", "interest rate"]):
        impact = "HIGH"
        d = direction_from_words(t)
        if "cut" in t or "dovish" in t or d == "DOWN":
            set_many({"XAUUSD":"UP", "Silver":"UP", "DXY":"DOWN", "US Treasury yields":"DOWN", "USDJPY":"DOWN", "EURUSD":"UP", "GBPUSD":"UP", "US equities":"UP", "Bitcoin":"UP"})
            note = "Nada Fed dovish/pemangkasan suku bunga cenderung mendukung emas dan menekan USD/yield."
        elif "hike" in t or "hawkish" in t or d == "UP":
            set_many({"XAUUSD":"DOWN", "Silver":"DOWN", "DXY":"UP", "US Treasury yields":"UP", "USDJPY":"UP", "EURUSD":"DOWN", "GBPUSD":"DOWN", "US equities":"DOWN", "Bitcoin":"DOWN"})
            note = "Nada Fed hawkish cenderung menguatkan USD/yield dan menekan emas serta aset berisiko."
        else:
            note = "Headline Fed berdampak tinggi, tetapi arah tidak aman disimpulkan tanpa konteks pernyataan dan reaksi yield."
    elif any(k in t for k in ["payroll", "jobs", "unemployment", "labor market"]):
        impact = "HIGH"
        note = "Data tenaga kerja AS langsung memengaruhi ekspektasi Fed. Fokus pada actual vs forecast, revisi, serta reaksi US2Y/US10Y dan DXY."
    elif any(k in t for k in ["opec", "oil output", "crude supply", "production", "inventory", "inventories"]):
        impact = "MEDIUM"
        d = direction_from_words(t)
        if "cut" in t or d == "DOWN":
            impacts["Brent"] = impacts["WTI"] = "UP"
            note = "Pengurangan supply/output atau inventori yang lebih ketat cenderung mendukung Brent dan WTI."
        elif ("output" in t or "supply" in t or "inventory" in t) and d == "UP":
            impacts["Brent"] = impacts["WTI"] = "DOWN"
            note = "Kenaikan supply/output/inventori cenderung menekan Brent dan WTI."
    elif any(k in t for k in ["boj", "bank of japan", "yen"]):
        impact = "MEDIUM"
        if "hike" in t or "hawkish" in t:
            impacts["USDJPY"] = "DOWN"
            note = "BOJ hawkish biasanya mendukung JPY dan menekan USDJPY."
        elif "cut" in t or "dovish" in t:
            impacts["USDJPY"] = "UP"
            note = "BOJ dovish biasanya melemahkan JPY dan mendukung USDJPY."
    elif any(k in t for k in ["ecb", "euro"]):
        impact = "MEDIUM"
        if "hawkish" in t or "hike" in t:
            impacts["EURUSD"] = "UP"; note = "ECB hawkish cenderung mendukung EURUSD."
        elif "dovish" in t or "cut" in t:
            impacts["EURUSD"] = "DOWN"; note = "ECB dovish cenderung menekan EURUSD."
    elif any(k in t for k in ["boe", "bank of england", "sterling", "pound"]):
        impact = "MEDIUM"
        if "hawkish" in t or "hike" in t:
            impacts["GBPUSD"] = "UP"; note = "BoE hawkish cenderung mendukung GBPUSD."
        elif "dovish" in t or "cut" in t:
            impacts["GBPUSD"] = "DOWN"; note = "BoE dovish cenderung menekan GBPUSD."
    elif any(k in t for k in ["treasury", "yield", "bond"]):
        impact = "MEDIUM"
        note = "Yield Treasury adalah driver penting untuk DXY, XAUUSD dan saham. Arah harga perlu dibaca bersama sebab pergerakan yield."
    elif any(k in t for k in ["copper", "china demand", "industrial metal"]):
        impact = "MEDIUM"
        note = "Tembaga sensitif terhadap ekspektasi pertumbuhan global dan permintaan China; konfirmasi dengan DXY dan risk sentiment."
    elif any(k in t for k in ["bitcoin", "crypto"]):
        impact = "MEDIUM"
        note = "Headline crypto terutama memengaruhi Bitcoin; spillover makro meningkat bila bersamaan dengan perubahan yield/likuiditas."
    return impact, impacts, note


def snapshot_map(market: list[dict[str, Any]]) -> dict[str, dict[str, Any]]:
    return {x.get("asset"): x for x in market if isinstance(x, dict)}


def price_response_for(direction: str, pct: float | None) -> str:
    if direction == "MIXED" or pct is None:
        return ""
    if abs(pct) < 0.10:
        return "TERTAHAN"
    follows = (direction == "UP" and pct > 0) or (direction == "DOWN" and pct < 0)
    return "MENGIKUTI" if follows else "MELAWAN"


def detect_contradiction(directions: dict[str, str], smap: dict[str, dict[str, Any]], lookup: dict[str, str]) -> str:
    conflicts = []
    for a, d in directions.items():
        if d == "MIXED":
            continue
        p = safe_num(smap.get(lookup.get(a, a), {}).get("changePct"))
        if p is None or abs(p) < 0.10:
            continue
        if (d == "UP" and p < 0) or (d == "DOWN" and p > 0):
            conflicts.append(f"{a} melawan tekanan fundamental rule-based")
    return "; ".join(conflicts[:3])


def build_news(news_meta: list[dict[str, Any]], market: list[dict[str, Any]]) -> list[dict[str, Any]]:
    smap = snapshot_map(market)
    lookup = {
        "XAUUSD":"XAUUSD", "Silver":"Silver", "Copper":"Copper", "DXY":"DXY",
        "US Treasury yields":"US10Y", "USDJPY":"USDJPY", "EURUSD":"EURUSD",
        "GBPUSD":"GBPUSD", "Brent":"Brent", "WTI":"WTI", "US equities":"S&P 500", "Bitcoin":"Bitcoin"
    }
    out = []
    for x in news_meta:
        impact, directions, analysis = classify_headline(x["title"])
        concise_impacts = []
        responses = []
        for a in ALL_IMPACT_ASSETS:
            d = directions.get(a, "MIXED")
            if d == "MIXED":
                continue
            p = safe_num(smap.get(lookup.get(a, a), {}).get("changePct"))
            resp = price_response_for(d, p)
            concise_impacts.append({
                "asset": a,
                "direction": "↑" if d == "UP" else "↓",
                "response": resp,
            })
            if resp:
                responses.append(f"{a} {resp}")
        contradiction = detect_contradiction(directions, smap, lookup)
        title_id = translate_lite(x["title"])
        item = {
            "publishedAtWib": x["published"].astimezone(JKT).strftime("%d %b %Y %H:%M WIB"),
            "source": x["source"],
            "category": news_category(x["title"]),
            "title": x["title"],
            "titleId": title_id if title_id.lower() != x["title"].lower() else "",
            "impact": impact,
            "analysis": analysis,
            "assetImpacts": concise_impacts,
            "priceResponse": " • ".join(responses[:3]),
            "contradictions": contradiction,
            "url": x["url"],
            "publisherUrl": x.get("publisherUrl", ""),
            "verificationLevel": "HEADLINE_METADATA_ONLY",
        }
        out.append(item)
    return out


def pct(m: dict[str, dict[str, Any]], asset: str) -> float | None:
    return safe_num(m.get(asset, {}).get("changePct"))


def bias_label(score: int) -> str:
    if score >= 2: return "BULLISH"
    if score <= -2: return "BEARISH"
    return "NETRAL"


def summary(market: list[dict[str, Any]], calendar: list[dict[str, Any]], news: list[dict[str, Any]]) -> dict[str, Any]:
    m = snapshot_map(market)
    gold, dxy, yld = pct(m,"XAUUSD"), pct(m,"DXY"), pct(m,"US10Y")
    brent, wti = pct(m,"Brent"), pct(m,"WTI")
    xscore = 0
    if gold is not None: xscore += 1 if gold > .10 else -1 if gold < -.10 else 0
    if dxy is not None: xscore += 1 if dxy < -.10 else -1 if dxy > .10 else 0
    if yld is not None: xscore += 1 if yld < -.10 else -1 if yld > .10 else 0
    dscore = (1 if (dxy or 0) > .10 else -1 if (dxy or 0) < -.10 else 0) + (1 if (yld or 0) > .10 else -1 if (yld or 0) < -.10 else 0)
    yscore = 1 if (yld or 0) > .10 else -1 if (yld or 0) < -.10 else 0
    oscore = sum(1 if (v or 0) > .25 else -1 if (v or 0) < -.25 else 0 for v in [brent, wti])
    candidates = [(abs(safe_num(x.get("changePct")) or 0), x.get("asset", "-")) for x in market]
    dominant = max(candidates, default=(0, "-"))[1]
    high = [e for e in calendar if e.get("impact") == "HIGH"]
    next_cat = (high[0]["datetimeWib"] + " • " + high[0]["event"]) if high else (calendar[0]["datetimeWib"] + " • " + calendar[0]["event"] if calendar else "Belum tersedia")
    analyzed = [n for n in news if n.get("analysis")]
    conflicts = [n for n in analyzed if n.get("contradictions")]
    verified_market = sum(1 for x in market if not x.get("stale"))
    confidence = min(82, 40 + verified_market * 2 + min(len(calendar), 6) + min(len(analyzed), 8))
    return {
        "xauusd": bias_label(xscore), "dxy": bias_label(dscore), "yield": bias_label(yscore), "oil": bias_label(oscore),
        "dominantAsset": dominant, "nextCatalyst": next_cat,
        "bullScenario": "XAU menguat bila DXY dan US10Y melemah bersama, terutama setelah data AS lebih lunak. Oil menguat bila supply risk/geopolitik meningkat dan Brent-WTI mengonfirmasi.",
        "bearScenario": "XAU melemah bila DXY dan US10Y menguat bersama, terutama setelah data AS panas/kuat. Oil melemah bila supply normal dan Brent-WTI turun bersama.",
        "confidencePct": confidence, "confidenceLabel": "RULE-BASED", "analysisCount": len(analyzed), "conflictCount": len(conflicts),
    }


def decorate_market(market: list[dict[str, Any]]) -> None:
    for x in market:
        p = safe_num(x.get("changePct"))
        if p is None:
            x["comment"] = "Data perubahan belum tersedia"
        elif p > .10:
            x["comment"] = "Naik dalam ±24 jam"
        elif p < -.10:
            x["comment"] = "Turun dalam ±24 jam"
        else:
            x["comment"] = "Relatif datar / tertahan"


def source_stats(news: list[dict[str, Any]]) -> dict[str, int]:
    out = {v: 0 for v in ALLOWED_PUBLISHERS.values()}
    for n in news:
        src = n.get("source")
        if src in out: out[src] += 1
    return out


def write_history(report: dict[str, Any]) -> None:
    try:
        hist = json.loads(HISTORY.read_text(encoding="utf-8"))
        if not isinstance(hist, list): hist = []
    except Exception:
        hist = []
    compact = {"generatedAtWib": report["generatedAtWib"], "summary": report["summary"], "marketSnapshot": report["marketSnapshot"]}
    hist.insert(0, compact)
    HISTORY.write_text(json.dumps(hist[:96], ensure_ascii=False, indent=2), encoding="utf-8")


def main() -> int:
    previous = load_previous()
    warnings = [
        "Mode gratis: analisis berita bersifat rule-based dari headline/metadata; isi paywall tidak direkonstruksi.",
        "Trading Economics diprioritaskan sebagai sumber berita/referensi publik. API resmi Trading Economics tidak dipakai karena memerlukan API key.",
        "Jika feed publik gagal, data ditandai cached/tidak tersedia dan tidak diisi dengan angka rekaan.",
    ]
    market, w = market_data(previous); warnings.extend(w)
    decorate_market(market)
    calendar, w = ff_calendar(); warnings.extend(w)
    news_meta, w = news_rss(); warnings.extend(w)
    news = build_news(news_meta, market)
    report = {
        "schemaVersion": 3,
        "freeMode": True,
        "demo": False,
        "generatedAtWib": NOW_WIB.strftime("%d %b %Y %H:%M WIB"),
        "generatedAtIso": NOW_WIB.isoformat(),
        "coverageWindow": "24 jam berita • 7 hari kalender",
        "warnings": warnings,
        "summary": summary(market, calendar, news),
        "marketSnapshot": market,
        "news": news,
        "calendar": calendar,
        "sourceStats": source_stats(news),
        "sources": {
            "marketPrimary": "Yahoo Finance public chart endpoint",
            "marketReference": "Trading Economics public market pages",
            "calendarPrimary": "Forex Factory / FairEconomy public feed",
            "calendarReference": "Trading Economics public calendar page",
            "newsDiscovery": "Google News RSS metadata; Trading Economics is prioritised, plus Reuters, Bloomberg, Yahoo Finance and Forex Factory",
            "translation": "Local lightweight Indonesian market dictionary (no external translation API)",
        },
    }
    LATEST.write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")
    write_history(report)
    print(f"NEWS1 Free V2: {report['generatedAtWib']} market={len(market)} news={len(news)} calendar={len(calendar)} TE={report['sourceStats'].get('Trading Economics',0)}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
