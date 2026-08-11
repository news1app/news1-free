#!/usr/bin/env python3
"""NEWS1 Free collector.

No paid API keys. Uses publicly reachable endpoints only and degrades gracefully.
- Market prices: Yahoo Finance chart endpoint (public web endpoint; may rate-limit/change).
- Economic calendar: Forex Factory/FairEconomy public JSON feed when reachable.
- News discovery: Google News RSS metadata filtered to the requested publishers.

Important: News analysis is headline/rule based. The script never invents paywalled article content.
"""
from __future__ import annotations

import email.utils
import html
import json
import math
import os
import re
import sys
import time
import urllib.parse
import urllib.request
import xml.etree.ElementTree as ET
from concurrent.futures import ThreadPoolExecutor, as_completed
from dataclasses import dataclass
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
UA = "Mozilla/5.0 (NEWS1-Free-GitHub; +https://github.com/)"

ASSETS = [
    ("XAUUSD", ["XAUUSD=X", "GC=F"]),
    ("DXY", ["DX-Y.NYB", "DX=F"]),
    ("US10Y", ["^TNX"]),
    ("USDJPY", ["JPY=X"]),
    ("EURUSD", ["EURUSD=X"]),
    ("GBPUSD", ["GBPUSD=X"]),
    ("Brent", ["BZ=F"]),
    ("WTI", ["CL=F"]),
    ("S&P 500", ["^GSPC"]),
    ("Dow", ["^DJI"]),
    ("Nasdaq", ["^IXIC"]),
    ("Bitcoin", ["BTC-USD"]),
]

ALL_IMPACT_ASSETS = ["XAUUSD", "DXY", "US Treasury yields", "USDJPY", "EURUSD", "GBPUSD", "Brent", "WTI", "US equities", "Bitcoin"]
ALLOWED_PUBLISHERS = {
    "reuters": "Reuters",
    "bloomberg": "Bloomberg",
    "yahoo finance": "Yahoo Finance",
    "trading economics": "Trading Economics",
    "forex factory": "Forex Factory",
}


def fetch_text(url: str, timeout: int = 5) -> str:
    req = urllib.request.Request(url, headers={"User-Agent": UA, "Accept": "*/*"})
    with urllib.request.urlopen(req, timeout=timeout) as r:
        return r.read().decode("utf-8", errors="replace")


def safe_num(x: Any) -> float | None:
    try:
        v = float(x)
        if math.isnan(v) or math.isinf(v):
            return None
        return v
    except Exception:
        return None


def format_price(asset: str, value: float | None) -> str:
    if value is None:
        return "—"
    if asset == "US10Y":
        y = value / 10.0 if value > 20 else value
        return f"{y:.3f}%"
    if asset in {"USDJPY"}:
        return f"{value:.3f}"
    if asset in {"EURUSD", "GBPUSD"}:
        return f"{value:.5f}"
    if asset in {"DXY"}:
        return f"{value:.3f}"
    if asset in {"XAUUSD", "Brent", "WTI", "Bitcoin", "S&P 500", "Dow", "Nasdaq"}:
        return f"{value:,.2f}"
    return f"{value:.4f}"


def yahoo_snapshot(asset: str, symbols: list[str]) -> dict[str, Any] | None:
    errors = []
    for symbol in symbols:
        try:
            enc = urllib.parse.quote(symbol, safe="")
            url = f"https://query1.finance.yahoo.com/v8/finance/chart/{enc}?range=2d&interval=15m&includePrePost=true"
            obj = json.loads(fetch_text(url))
            result = obj.get("chart", {}).get("result") or []
            if not result:
                raise ValueError(obj.get("chart", {}).get("error") or "no result")
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
            source = "Yahoo Finance"
            if asset == "XAUUSD" and symbol == "GC=F":
                source += " (Gold futures proxy)"
            return {
                "asset": asset,
                "symbol": symbol,
                "last": format_price(asset, last),
                "lastNumeric": round(last, 8),
                "changePct": round(pct, 3) if pct is not None else None,
                "change": (f"{pct:+.2f}%" if pct is not None else "—"),
                "source": source,
                "sourceUrl": f"https://finance.yahoo.com/quote/{urllib.parse.quote(symbol, safe='')}",
                "stale": False,
            }
        except Exception as e:
            errors.append(f"{symbol}: {e}")
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
    with ThreadPoolExecutor(max_workers=6) as ex:
        futs = {ex.submit(yahoo_snapshot, asset, symbols): asset for asset, symbols in ASSETS}
        for fut in as_completed(futs):
            asset = futs[fut]
            try:
                results[asset] = fut.result()
            except Exception:
                results[asset] = None
    out = []
    for asset, _symbols in ASSETS:
        snap = results.get(asset)
        if snap:
            out.append(snap)
        elif asset in prev_map:
            old = dict(prev_map[asset])
            old["stale"] = True
            old["source"] = old.get("source", "Yahoo Finance") + " • cached"
            out.append(old)
            warnings.append(f"{asset}: harga live gagal diambil; memakai cache terakhir.")
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
        except Exception as e:
            warnings.append(f"Forex Factory calendar feed tidak tersedia ({url.rsplit('/',1)[-1]}).")
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
            "impact": impact,
            "actual": str(x.get("actual") or "—"),
            "forecast": str(x.get("forecast") or "—"),
            "previous": str(x.get("previous") or "—"),
            "revision": str(x.get("revision") or "—"),
            "source": "Forex Factory / FairEconomy feed",
            "url": "https://www.forexfactory.com/calendar",
        })
    out.sort(key=lambda x: x["timestamp"])
    return out, warnings


def clean_title(s: str) -> str:
    return re.sub(r"\s+", " ", html.unescape(re.sub(r"<[^>]+>", "", s or ""))).strip()


def news_rss() -> tuple[list[dict[str, Any]], list[str]]:
    # Google News is used only as a free discovery/index layer. Publisher metadata is retained.
    base_terms = '(gold OR XAUUSD OR dollar OR DXY OR Treasury OR USDJPY OR EURUSD OR GBPUSD OR oil OR Brent OR WTI OR stocks OR Bitcoin OR CPI OR Fed OR Hormuz) when:1d'
    site_queries = [
        ("Reuters", f"site:reuters.com {base_terms}"),
        ("Bloomberg", f"site:bloomberg.com {base_terms}"),
        ("Yahoo Finance", f"site:finance.yahoo.com {base_terms}"),
        ("Trading Economics", f"site:tradingeconomics.com {base_terms}"),
        ("Forex Factory", f"site:forexfactory.com {base_terms}"),
    ]
    cutoff = NOW_UTC - timedelta(hours=24)

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
            source_url = (src_el.attrib.get("url", "") if src_el is not None else "")
            if pub is None or pub.astimezone(UTC) < cutoff or pub.astimezone(UTC) > NOW_UTC + timedelta(minutes=10):
                continue
            p_low = publisher.lower()
            normalized = None
            for k, v in ALLOWED_PUBLISHERS.items():
                if k in p_low:
                    normalized = v
                    break
            if normalized is None and expected_source.lower() in p_low:
                normalized = expected_source
            if normalized is None:
                continue
            local.append({"title": title, "source": normalized, "published": pub.astimezone(UTC), "url": link, "publisherUrl": source_url})
        return local

    items: list[dict[str, Any]] = []
    warnings: list[str] = []
    with ThreadPoolExecutor(max_workers=5) as ex:
        futs = {ex.submit(one, src, q): src for src, q in site_queries}
        for fut in as_completed(futs):
            src = futs[fut]
            try:
                items.extend(fut.result())
            except Exception:
                warnings.append(f"Headline {src} gagal diindeks pada run ini.")

    dedup = {}
    for x in items:
        k = re.sub(r"\W+", " ", x["title"].lower()).strip()
        if not k:
            continue
        if k not in dedup or x["published"] > dedup[k]["published"]:
            dedup[k] = x
    final = sorted(dedup.values(), key=lambda x: x["published"], reverse=True)[:25]
    return final, warnings


def direction_from_words(text: str) -> str | None:
    t = text.lower()
    up = ["rises", "rise", "jumps", "surges", "gains", "higher", "accelerates", "hotter", "hawkish", "hike", "tightens", "cuts supply", "output cut"]
    down = ["falls", "fall", "drops", "slides", "lower", "eases", "cools", "slows", "dovish", "rate cut", "weakens", "output rises", "supply rises"]
    if any(w in t for w in up): return "UP"
    if any(w in t for w in down): return "DOWN"
    return None


def classify_headline(title: str) -> tuple[str, dict[str, str], str]:
    t = title.lower()
    impacts = {a: "MIXED" for a in ALL_IMPACT_ASSETS}
    impact = "LOW"
    note = "Dampak tidak dapat ditentukan kuat dari headline saja."

    def set_many(mapping: dict[str, str]):
        impacts.update(mapping)

    if any(k in t for k in ["hormuz", "iran", "war", "missile", "attack", "strait"]):
        impact = "HIGH"
        set_many({"XAUUSD":"UP", "Brent":"UP", "WTI":"UP", "US equities":"DOWN", "Bitcoin":"DOWN", "DXY":"MIXED", "US Treasury yields":"MIXED"})
        note = "Rule-based: eskalasi geopolitik/Hormuz biasanya menaikkan risk premium minyak dan safe haven; efek USD/yield dapat campuran."
    elif any(k in t for k in ["cpi", "inflation", "ppi", "pce"]):
        impact = "HIGH"
        d = direction_from_words(t)
        if d == "DOWN":
            set_many({"XAUUSD":"UP", "DXY":"DOWN", "US Treasury yields":"DOWN", "USDJPY":"DOWN", "EURUSD":"UP", "GBPUSD":"UP", "US equities":"UP", "Bitcoin":"UP"})
            note = "Rule-based: inflasi yang lebih dingin cenderung menekan yield/USD dan mendukung gold/risk assets."
        elif d == "UP":
            set_many({"XAUUSD":"DOWN", "DXY":"UP", "US Treasury yields":"UP", "USDJPY":"UP", "EURUSD":"DOWN", "GBPUSD":"DOWN", "US equities":"DOWN", "Bitcoin":"DOWN"})
            note = "Rule-based: inflasi yang lebih panas cenderung menaikkan yield/USD dan menekan gold/risk assets."
        else:
            note = "Data inflasi adalah katalis tinggi; arah akhir memerlukan actual vs forecast dan reaksi yield/DXY."
    elif any(k in t for k in ["federal reserve", " fed ", "powell", "rate cut", "rate hike", "interest rate"]):
        impact = "HIGH"
        d = direction_from_words(t)
        if "cut" in t or "dovish" in t or d == "DOWN":
            set_many({"XAUUSD":"UP", "DXY":"DOWN", "US Treasury yields":"DOWN", "USDJPY":"DOWN", "EURUSD":"UP", "GBPUSD":"UP", "US equities":"UP", "Bitcoin":"UP"})
            note = "Rule-based: Fed dovish/rate-cut bias cenderung bullish gold dan bearish USD/yield."
        elif "hike" in t or "hawkish" in t or d == "UP":
            set_many({"XAUUSD":"DOWN", "DXY":"UP", "US Treasury yields":"UP", "USDJPY":"UP", "EURUSD":"DOWN", "GBPUSD":"DOWN", "US equities":"DOWN", "Bitcoin":"DOWN"})
            note = "Rule-based: Fed hawkish/rate-hike bias cenderung bullish USD/yield dan bearish gold."
        else:
            note = "Headline Fed berdampak tinggi; arah belum aman disimpulkan tanpa konteks lengkap."
    elif any(k in t for k in ["payroll", "jobs", "unemployment", "labor market"]):
        impact = "HIGH"
        note = "Data tenaga kerja berdampak tinggi pada ekspektasi Fed; actual vs forecast dibutuhkan untuk arah yang valid."
    elif any(k in t for k in ["opec", "oil output", "crude supply", "production"]):
        impact = "MEDIUM"
        d = direction_from_words(t)
        if "cut" in t or d == "DOWN":
            impacts["Brent"] = impacts["WTI"] = "UP"
            note = "Rule-based: pengurangan supply/output cenderung mendukung harga minyak."
        elif "output" in t and d == "UP":
            impacts["Brent"] = impacts["WTI"] = "DOWN"
            note = "Rule-based: kenaikan output/supply cenderung menekan harga minyak."
    elif any(k in t for k in ["boj", "bank of japan", "yen"]):
        impact = "MEDIUM"
        if "hike" in t or "hawkish" in t:
            impacts["USDJPY"] = "DOWN"
            note = "Rule-based: BOJ hawkish biasanya mendukung JPY dan menekan USDJPY."
        elif "cut" in t or "dovish" in t:
            impacts["USDJPY"] = "UP"
            note = "Rule-based: BOJ dovish biasanya melemahkan JPY dan mendukung USDJPY."
    elif any(k in t for k in ["ecb", "euro"]):
        impact = "MEDIUM"
        if "hawkish" in t or "hike" in t: impacts["EURUSD"] = "UP"
        if "dovish" in t or "cut" in t: impacts["EURUSD"] = "DOWN"
        note = "Rule-based: headline ECB terutama memengaruhi EURUSD; konteks kebijakan menentukan arah."
    elif any(k in t for k in ["boe", "bank of england", "sterling", "pound"]):
        impact = "MEDIUM"
        if "hawkish" in t or "hike" in t: impacts["GBPUSD"] = "UP"
        if "dovish" in t or "cut" in t: impacts["GBPUSD"] = "DOWN"
        note = "Rule-based: headline BoE terutama memengaruhi GBPUSD; konteks kebijakan menentukan arah."
    elif any(k in t for k in ["treasury", "yield", "bond"]):
        impact = "MEDIUM"
        note = "Yield Treasury adalah driver penting DXY, XAUUSD dan saham; headline saja belum cukup menentukan sebab pergerakan."
    elif any(k in t for k in ["bitcoin", "crypto"]):
        impact = "MEDIUM"
        note = "Headline crypto terutama memengaruhi Bitcoin; spillover ke aset makro biasanya lebih kecil."
    return impact, impacts, note


def snapshot_map(market: list[dict[str, Any]]) -> dict[str, dict[str, Any]]:
    return {x.get("asset"): x for x in market if isinstance(x, dict)}


def price_response_for(direction: str, pct: float | None) -> str:
    if direction == "MIXED" or pct is None:
        return "TERTAHAN / BELUM TERKONFIRMASI"
    if abs(pct) < 0.10:
        return "TERTAHAN"
    follows = (direction == "UP" and pct > 0) or (direction == "DOWN" and pct < 0)
    return "MENGIKUTI" if follows else "MELAWAN"


def build_news(news_meta: list[dict[str, Any]], market: list[dict[str, Any]]) -> list[dict[str, Any]]:
    smap = snapshot_map(market)
    asset_lookup = {
        "XAUUSD":"XAUUSD", "DXY":"DXY", "US Treasury yields":"US10Y", "USDJPY":"USDJPY",
        "EURUSD":"EURUSD", "GBPUSD":"GBPUSD", "Brent":"Brent", "WTI":"WTI",
        "US equities":"S&P 500", "Bitcoin":"Bitcoin"
    }
    out = []
    for x in news_meta:
        impact, directions, interpretation = classify_headline(x["title"])
        impacts = []
        responses = []
        for a in ALL_IMPACT_ASSETS:
            d = directions.get(a, "MIXED")
            snap = smap.get(asset_lookup.get(a, a), {})
            pct = safe_num(snap.get("changePct"))
            resp = price_response_for(d, pct)
            responses.append((a, d, resp))
            explanation = "Tidak ada arah kuat yang aman disimpulkan dari headline saja." if d == "MIXED" else f"Rule-based pressure: {d}; konfirmasi harga: {resp}."
            impacts.append({"asset": a, "direction": "↑" if d == "UP" else "↓" if d == "DOWN" else "↔/mixed", "explanation": explanation})
        primary = next((r for r in responses if r[1] != "MIXED"), None)
        price_response = f"{primary[0]}: {primary[2]}" if primary else "Belum ada arah fundamental yang cukup spesifik untuk diuji terhadap harga."
        t = x["title"].lower()
        official = "Headline memuat pernyataan/komentar; buka sumber untuk konteks lengkap." if any(k in t for k in ["says", "said", "warns", "minister", "president", "fed", "central bank"]) else "—"
        out.append({
            "publishedAtWib": x["published"].astimezone(JKT).strftime("%d %b %Y %H:%M WIB"),
            "source": x["source"],
            "title": x["title"],
            "fact": "Fakta terverifikasi otomatis terbatas pada metadata/headline yang terindeks: " + x["title"],
            "officialStatement": official,
            "consensus": "— (NEWS1 Free tidak mengarang konsensus dari artikel yang tidak dibaca)",
            "interpretation": interpretation,
            "impact": impact,
            "assetImpacts": impacts,
            "priceResponse": price_response,
            "contradictions": detect_contradiction(directions, smap, asset_lookup),
            "url": x["url"],
            "publisherUrl": x.get("publisherUrl", ""),
            "verificationLevel": "HEADLINE_METADATA_ONLY",
        })
    return out


def detect_contradiction(directions: dict[str, str], smap: dict[str, dict[str, Any]], lookup: dict[str,str]) -> str:
    conflicts = []
    for a, d in directions.items():
        if d == "MIXED": continue
        pct = safe_num(smap.get(lookup.get(a, a), {}).get("changePct"))
        if pct is None or abs(pct) < 0.10: continue
        if (d == "UP" and pct < 0) or (d == "DOWN" and pct > 0):
            conflicts.append(f"{a} melawan tekanan rule-based")
    return "; ".join(conflicts[:3]) if conflicts else "—"


def pct(market_map: dict[str, dict[str, Any]], asset: str) -> float | None:
    return safe_num(market_map.get(asset, {}).get("changePct"))


def bias_label(score: int) -> str:
    if score >= 2: return "BULLISH"
    if score <= -2: return "BEARISH"
    return "NETRAL"


def summary(market: list[dict[str, Any]], calendar: list[dict[str, Any]], news: list[dict[str, Any]]) -> dict[str, Any]:
    m = snapshot_map(market)
    gold, dxy, yld = pct(m,"XAUUSD"), pct(m,"DXY"), pct(m,"US10Y")
    brent, wti = pct(m,"Brent"), pct(m,"WTI")

    xscore = 0
    if gold is not None: xscore += 1 if gold > 0.10 else -1 if gold < -0.10 else 0
    if dxy is not None: xscore += 1 if dxy < -0.10 else -1 if dxy > 0.10 else 0
    if yld is not None: xscore += 1 if yld < -0.10 else -1 if yld > 0.10 else 0
    dscore = (1 if (dxy or 0) > 0.10 else -1 if (dxy or 0) < -0.10 else 0) + (1 if (yld or 0) > 0.10 else -1 if (yld or 0) < -0.10 else 0)
    yscore = 1 if (yld or 0) > 0.10 else -1 if (yld or 0) < -0.10 else 0
    oscore = sum(1 if (v or 0) > 0.25 else -1 if (v or 0) < -0.25 else 0 for v in [brent,wti])

    candidates = [(abs(safe_num(x.get("changePct")) or 0), x.get("asset","-")) for x in market]
    dominant = max(candidates, default=(0,"-"))[1]
    high = [e for e in calendar if e.get("impact") == "HIGH"]
    next_cat = (high[0]["datetimeWib"] + " • " + high[0]["event"]) if high else (calendar[0]["datetimeWib"] + " • " + calendar[0]["event"] if calendar else "Belum tersedia dari feed kalender gratis")

    verified_market = sum(1 for x in market if not x.get("stale"))
    confidence = min(78, 42 + verified_market * 2 + min(len(calendar),5) + min(len(news),5))
    return {
        "xauusd": bias_label(xscore),
        "dxy": bias_label(dscore),
        "yield": bias_label(yscore),
        "oil": bias_label(oscore),
        "dominantAsset": dominant,
        "nextCatalyst": next_cat,
        "bullScenario": "XAU bullish rule-based bila DXY dan US10Y melemah bersamaan, terutama jika data AS lebih lunak dari ekspektasi. Oil bullish bila risk premium/supply disruption menguat dan Brent-WTI mengonfirmasi.",
        "bearScenario": "XAU bearish rule-based bila DXY dan US10Y menguat bersamaan, terutama jika data AS lebih panas/kuat. Oil bearish bila supply normalisasi dan Brent-WTI turun bersamaan.",
        "confidencePct": confidence,
        "confidenceLabel": "RULE-BASED • bukan analisis AI",
        "method": "Harga + kalender publik + headline metadata; tanpa membaca paywall dan tanpa paid AI/API.",
    }


def decorate_market(market: list[dict[str, Any]]) -> None:
    for x in market:
        p = safe_num(x.get("changePct"))
        if p is None:
            x["priceResponse"] = "Belum ada perubahan yang dapat dihitung."
        elif p > 0.10:
            x["priceResponse"] = "Harga naik dalam jendela perbandingan terakhir."
        elif p < -0.10:
            x["priceResponse"] = "Harga turun dalam jendela perbandingan terakhir."
        else:
            x["priceResponse"] = "Harga relatif tertahan/datar."


def write_history(report: dict[str, Any]) -> None:
    try:
        hist = json.loads(HISTORY.read_text(encoding="utf-8"))
        if not isinstance(hist, list): hist = []
    except Exception:
        hist = []
    compact = {
        "generatedAtWib": report["generatedAtWib"],
        "summary": report["summary"],
        "marketSnapshot": report["marketSnapshot"],
    }
    hist.insert(0, compact)
    hist = hist[:72]
    HISTORY.write_text(json.dumps(hist, ensure_ascii=False, indent=2), encoding="utf-8")


def main() -> int:
    previous = load_previous()
    warnings = [
        "NEWS1 Free = rule-based, bukan AI. Interpretasi otomatis tidak menggantikan pembacaan artikel penuh.",
        "Artikel paywall tidak dibaca dan tidak direkonstruksi. Fakta berita otomatis dibatasi pada headline/metadata yang terindeks.",
        "Trading Economics API resmi membutuhkan API key berlangganan; mode gratis tidak mengakses endpoint berbayarnya.",
        "Endpoint web publik dapat berubah/rate-limit; jika gagal aplikasi menandai data cached/tidak tersedia, bukan mengarang angka.",
    ]
    market, w = market_data(previous); warnings.extend(w)
    decorate_market(market)
    calendar, w = ff_calendar(); warnings.extend(w)
    news_meta, w = news_rss(); warnings.extend(w)
    news = build_news(news_meta, market)
    report = {
        "schemaVersion": 2,
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
        "sources": {
            "market": "Yahoo Finance public chart endpoint",
            "calendar": "Forex Factory/FairEconomy public calendar feed when reachable",
            "newsDiscovery": "Google News RSS metadata filtered to Reuters, Bloomberg, Yahoo Finance, Trading Economics, Forex Factory",
        },
    }
    LATEST.write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")
    write_history(report)
    print(f"NEWS1 Free generated: {report['generatedAtWib']} | market={len(market)} news={len(news)} calendar={len(calendar)}")
    return 0

if __name__ == "__main__":
    raise SystemExit(main())
