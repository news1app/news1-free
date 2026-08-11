#!/usr/bin/env python3
"""NEWS1 read-only Moomoo price bridge.

Runs on the same PC as Moomoo OpenD. It exposes quote-only JSON over the
local network for the NEWS1 Android app. It never initializes a trading
context and cannot place orders.
"""
from __future__ import annotations

import json
import math
import os
import socket
import threading
import time
from datetime import datetime, timezone, timedelta
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from typing import Any
from urllib.parse import urlparse

from moomoo import OpenQuoteContext, Market, SecurityType, RET_OK

HOST = os.getenv("NEWS1_BRIDGE_HOST", "0.0.0.0")
PORT = int(os.getenv("NEWS1_BRIDGE_PORT", "8765"))
OPEND_HOST = os.getenv("MOOMOO_OPEND_HOST", "127.0.0.1")
OPEND_PORT = int(os.getenv("MOOMOO_OPEND_PORT", "11111"))
TOKEN = os.getenv("NEWS1_BRIDGE_TOKEN", "").strip()
CACHE_SECONDS = int(os.getenv("NEWS1_BRIDGE_CACHE_SECONDS", "5"))
DISCOVERY_TTL = int(os.getenv("NEWS1_DISCOVERY_TTL_SECONDS", "21600"))
CONFIG_PATH = Path(__file__).with_name("assets.json")
WIB = timezone(timedelta(hours=7))

DEFAULT_ASSETS = [
    {"asset": "Gold Futures", "labelId": "COMEX Gold", "prefix": "GC", "category": "Logam Mulia"},
    {"asset": "Silver Futures", "labelId": "COMEX Silver", "prefix": "SI", "category": "Logam Mulia"},
    {"asset": "Copper Futures", "labelId": "COMEX Copper", "prefix": "HG", "category": "Logam Industri"},
    {"asset": "WTI Futures", "labelId": "NYMEX WTI Crude", "prefix": "CL", "category": "Energi"},
    {"asset": "Brent Futures", "labelId": "Brent Crude", "prefix": "BZ", "category": "Energi"},
    {"asset": "S&P Futures", "labelId": "E-mini S&P 500", "prefix": "ES", "category": "Indeks AS"},
    {"asset": "Nasdaq Futures", "labelId": "E-mini Nasdaq-100", "prefix": "NQ", "category": "Indeks AS"},
    {"asset": "Dow Futures", "labelId": "E-mini Dow", "prefix": "YM", "category": "Indeks AS"},
]

_lock = threading.Lock()
_cached: dict[str, Any] | None = None
_cached_at = 0.0
_discovered: dict[str, str] = {}
_discovered_at = 0.0


def _load_assets() -> list[dict[str, Any]]:
    if CONFIG_PATH.exists():
        try:
            obj = json.loads(CONFIG_PATH.read_text(encoding="utf-8"))
            if isinstance(obj, list):
                return obj
        except Exception:
            pass
    return DEFAULT_ASSETS


def _finite(v: Any) -> float | None:
    try:
        x = float(v)
        if math.isfinite(x):
            return x
    except Exception:
        pass
    return None


def _fmt_change(last: float | None, prev: float | None) -> tuple[float | None, str]:
    if last is None or prev in (None, 0):
        return None, "—"
    pct = (last - prev) / prev * 100.0
    return pct, f"{pct:+.2f}%"


def _resolve_codes(ctx: OpenQuoteContext, assets: list[dict[str, Any]]) -> tuple[dict[str, str], list[str]]:
    global _discovered, _discovered_at
    now = time.time()
    explicit = {a["asset"]: str(a.get("moomooCode", "")).strip() for a in assets if str(a.get("moomooCode", "")).strip()}
    if _discovered and now - _discovered_at < DISCOVERY_TTL:
        out = dict(_discovered)
        out.update(explicit)
        return out, []

    warnings: list[str] = []
    out: dict[str, str] = dict(explicit)
    needed = [a for a in assets if a["asset"] not in out and str(a.get("prefix", "")).strip()]
    if not needed:
        _discovered = out
        _discovered_at = now
        return out, warnings

    ret, data = ctx.get_stock_basicinfo(Market.US, SecurityType.FUTURE)
    if ret != RET_OK:
        warnings.append(f"Auto-discovery futures gagal: {data}")
        return out, warnings

    try:
        records = data.to_dict("records")
    except Exception:
        records = []

    for a in needed:
        prefix = str(a.get("prefix", "")).upper()
        candidates = []
        for row in records:
            code = str(row.get("code", ""))
            sym = code.split(".", 1)[-1].upper()
            if not sym.startswith(prefix):
                continue
            main = bool(row.get("main_contract", False))
            delisted = bool(row.get("delisting", False))
            if delisted:
                continue
            candidates.append((0 if main else 1, len(sym), code, row))
        candidates.sort(key=lambda x: (x[0], x[1], x[2]))
        if candidates:
            out[a["asset"]] = candidates[0][2]
        else:
            warnings.append(f"{a['asset']}: kontrak prefix {prefix} tidak ditemukan. Isi moomooCode manual di assets.json.")

    _discovered = dict(out)
    _discovered_at = now
    return out, warnings


def collect_prices() -> dict[str, Any]:
    global _cached, _cached_at
    with _lock:
        now = time.time()
        if _cached is not None and now - _cached_at < CACHE_SECONDS:
            return _cached

        assets = _load_assets()
        ctx = OpenQuoteContext(host=OPEND_HOST, port=OPEND_PORT, is_encrypt=False)
        warnings: list[str] = []
        try:
            code_map, discovery_warnings = _resolve_codes(ctx, assets)
            warnings.extend(discovery_warnings)
            codes = list(dict.fromkeys(code_map.values()))
            if not codes:
                raise RuntimeError("Tidak ada kode Moomoo yang berhasil ditemukan.")

            ret, data = ctx.get_market_snapshot(codes)
            if ret != RET_OK:
                raise RuntimeError(str(data))
            records = {str(r.get("code")): r for r in data.to_dict("records")}

            quotes = []
            for a in assets:
                code = code_map.get(a["asset"])
                if not code:
                    continue
                row = records.get(code)
                if not row:
                    warnings.append(f"{a['asset']}: snapshot kosong untuk {code}.")
                    continue
                last = _finite(row.get("last_price"))
                prev = _finite(row.get("prev_close_price"))
                if last is None or last <= 0:
                    warnings.append(f"{a['asset']}: last_price tidak tersedia untuk {code}; kemungkinan quote right belum aktif.")
                    continue
                pct, change = _fmt_change(last, prev)
                quotes.append({
                    "asset": a["asset"],
                    "labelId": a.get("labelId", a["asset"]),
                    "category": a.get("category", "Moomoo"),
                    "symbol": code,
                    "last": f"{last:,.4f}".rstrip("0").rstrip("."),
                    "lastNumeric": last,
                    "changePct": round(pct, 4) if pct is not None else None,
                    "change": change,
                    "high": _finite(row.get("high_price")),
                    "low": _finite(row.get("low_price")),
                    "previousClose": prev,
                    "source": "Moomoo OpenAPI",
                    "priceType": "FUTURES",
                    "status": "LIVE",
                    "updateTimeSource": str(row.get("update_time", "")),
                    "comment": f"LIVE • {code}",
                    "stale": False,
                })

            result = {
                "ok": True,
                "provider": "Moomoo OpenAPI",
                "readOnly": True,
                "generatedAtWib": datetime.now(WIB).strftime("%Y-%m-%d %H:%M:%S WIB"),
                "opend": f"{OPEND_HOST}:{OPEND_PORT}",
                "quotes": quotes,
                "warnings": warnings,
                "note": "Moomoo OpenAPI tidak menyediakan Global Forex spot sebagai kategori US market. Gold/Silver/Copper/Oil yang tampil dari bridge ini adalah futures dan dilabeli FUTURES.",
            }
            _cached = result
            _cached_at = now
            return result
        finally:
            ctx.close()


def local_ip() -> str:
    try:
        s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        s.connect(("8.8.8.8", 80))
        ip = s.getsockname()[0]
        s.close()
        return ip
    except Exception:
        return "127.0.0.1"


class Handler(BaseHTTPRequestHandler):
    server_version = "NEWS1MoomooBridge/1.0"

    def _json(self, status: int, obj: dict[str, Any]):
        body = json.dumps(obj, ensure_ascii=False).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Cache-Control", "no-store")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def _authorized(self) -> bool:
        if not TOKEN:
            return True
        return self.headers.get("Authorization", "") == f"Bearer {TOKEN}"

    def do_GET(self):
        path = urlparse(self.path).path
        if path == "/health":
            self._json(200, {"ok": True, "service": "NEWS1 Moomoo Bridge", "readOnly": True, "localIp": local_ip(), "port": PORT})
            return
        if path == "/api/prices":
            if not self._authorized():
                self._json(401, {"ok": False, "error": "Unauthorized"})
                return
            try:
                self._json(200, collect_prices())
            except Exception as e:
                self._json(503, {"ok": False, "provider": "Moomoo OpenAPI", "error": str(e), "generatedAtWib": datetime.now(WIB).strftime("%Y-%m-%d %H:%M:%S WIB")})
            return
        self._json(404, {"ok": False, "error": "Not found"})

    def log_message(self, fmt: str, *args):
        print("[bridge]", fmt % args)


def main():
    print("NEWS1 Moomoo Bridge — READ ONLY")
    print(f"OpenD target : {OPEND_HOST}:{OPEND_PORT}")
    print(f"Bridge       : http://{local_ip()}:{PORT}")
    print(f"Health       : http://{local_ip()}:{PORT}/health")
    print(f"Prices       : http://{local_ip()}:{PORT}/api/prices")
    print("No trading context is created by this program.\n")
    ThreadingHTTPServer((HOST, PORT), Handler).serve_forever()


if __name__ == "__main__":
    main()
