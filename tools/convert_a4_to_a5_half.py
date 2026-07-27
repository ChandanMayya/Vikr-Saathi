"""Build a clear Half A5 invoice template from an A4 template JSON.

Does NOT blindly scale (that shrinks fonts/columns until text overlaps).
Rebuilds a compact upright layout for 298 x 420 pt with readable sizes
and table columns tuned for the narrower width.

Usage:
  python tools/convert_a4_to_a5_half.py [input.json] [output.json]
"""
from __future__ import annotations

import json
import sys
import time
import uuid
from pathlib import Path

PAGE_W, PAGE_H = 298, 420
M = 8
CONTENT_W = PAGE_W - 2 * M  # 282
HEADER_H = 56


def uid() -> str:
    return str(uuid.uuid4())


def el(
    kind: str,
    binding: str,
    x: float,
    y: float,
    w: float,
    h: float,
    z: int,
    style: dict | None = None,
    content: dict | None = None,
    element_id: str | None = None,
) -> dict:
    st = {
        "fontSize": 6,
        "bold": False,
        "italic": False,
        "underline": False,
        "textAlign": "LEFT",
        "verticalAlign": "TOP",
        "color": "#000000",
        "fontFamily": "DEFAULT",
        "imageScaleMode": "FIT",
    }
    if style:
        st.update(style)
    return {
        "id": element_id or uid(),
        "kind": kind,
        "binding": binding,
        "bounds": {
            "x": round(float(x), 1),
            "y": round(float(y), 1),
            "width": round(float(w), 1),
            "height": round(float(h), 1),
        },
        "zIndex": z,
        "visible": True,
        "locked": False,
        "style": st,
        "content": content or {},
        "rotationDegrees": 0,
    }


def find_element(src: dict, binding_key: str | None = None, kind: str | None = None):
    for e in src.get("elements", []):
        c = e.get("content") or {}
        if binding_key and c.get("bindingKey") == binding_key:
            return e
        if kind and e.get("kind") == kind and not binding_key:
            return e
    return None


def convert(src: dict) -> dict:
    """Purpose-built Half A5 layout preserving client's bindings/columns where possible."""
    header = find_element(src, "HEADER_IMAGE")
    table = find_element(src, "BILL_ITEMS")
    signature = find_element(src, "SIGNATURE_IMAGE")

    table_cols = None
    if table:
        raw = (table.get("content") or {}).get("columns")
        if raw:
            try:
                table_cols = json.loads(raw) if isinstance(raw, str) else raw
            except json.JSONDecodeError:
                table_cols = None

    # Narrow-page column mix: more room for Particulars + currency cols
    if not table_cols:
        table_cols = [
            {"key": "sl", "label": "Sl", "widthPercent": 6, "align": "CENTER"},
            {"key": "name", "label": "Particulars", "widthPercent": 40, "align": "LEFT"},
            {"key": "quantity", "label": "Qty", "widthPercent": 8, "align": "CENTER"},
            {"key": "mrp", "label": "MRP", "widthPercent": 15, "align": "RIGHT"},
            {"key": "discount", "label": "D%", "widthPercent": 9, "align": "CENTER"},
            {"key": "lineTotal", "label": "Price", "widthPercent": 22, "align": "RIGHT"},
        ]
    else:
        # Retune percentages for ~282pt width; shorten Disc% label
        remapped = []
        defaults = {
            "sl": (6, "Sl", "CENTER"),
            "name": (40, "Particulars", "LEFT"),
            "quantity": (8, "Qty", "CENTER"),
            "mrp": (15, "MRP", "RIGHT"),
            "discount": (9, "D%", "CENTER"),
            "lineTotal": (22, "Price", "RIGHT"),
        }
        for col in table_cols:
            key = col.get("key", "")
            w, label, align = defaults.get(
                key,
                (col.get("widthPercent", 10), col.get("label", key), col.get("align", "CENTER")),
            )
            if key == "discount":
                label = "D%"
            if key in ("mrp", "lineTotal", "amount", "price"):
                align = "RIGHT"
            remapped.append(
                {
                    "key": key,
                    "label": label if key in defaults else col.get("label", label),
                    "widthPercent": w if key in defaults else col.get("widthPercent", w),
                    "align": align,
                }
            )
        # Normalize to 100
        total = sum(c["widthPercent"] for c in remapped) or 1
        for c in remapped:
            c["widthPercent"] = round(c["widthPercent"] / total * 100, 1)
        table_cols = remapped

    elements = []
    y = M
    z = 1

    # Header image — full page width
    header_h = HEADER_H
    elements.append(
        el(
            "IMAGE",
            "DYNAMIC",
            0,
            0,
            PAGE_W,
            header_h,
            z,
            style={"textAlign": "CENTER", "imageScaleMode": "STRETCH"},
            content={"bindingKey": "HEADER_IMAGE"},
            element_id=(header or {}).get("id"),
        )
    )
    z += 1
    y = header_h + 4

    # Invoice no + date (same size as table text)
    meta_h = 9
    half = (CONTENT_W - 4) / 2
    elements.append(
        el(
            "TEXT",
            "DYNAMIC",
            M,
            y,
            half,
            meta_h,
            z,
            style={"fontSize": 6},
            content={"bindingKey": "BILL_NUMBER", "prefix": "Inv: "},
        )
    )
    z += 1
    elements.append(
        el(
            "TEXT",
            "DYNAMIC",
            M + half + 4,
            y,
            half,
            meta_h,
            z,
            style={"fontSize": 6, "textAlign": "RIGHT"},
            content={"bindingKey": "BILL_DATE", "prefix": "Date: "},
        )
    )
    z += 1
    y += meta_h + 3

    elements.append(el("LINE", "STATIC", M, y, CONTENT_W, 1, z, content={}))
    z += 1
    y += 4

    # Buyer block
    elements.append(
        el(
            "TEXT",
            "STATIC",
            M,
            y,
            CONTENT_W,
            9,
            z,
            style={"fontSize": 6.5, "bold": True},
            content={"text": "Buyer Details"},
        )
    )
    z += 1
    y += 10
    for key, prefix in (
        ("CUSTOMER_NAME", "Name: "),
        ("CUSTOMER_ADDRESS", "Address: "),
        ("CUSTOMER_PHONE", "Phone: "),
    ):
        elements.append(
            el(
                "TEXT",
                "DYNAMIC",
                M,
                y,
                CONTENT_W,
                9,
                z,
                style={"fontSize": 6},
                content={"bindingKey": key, "prefix": prefix},
            )
        )
        z += 1
        y += 10

    y += 3
    # Table — font matches bill number (6pt); now honored by renderer
    footer_reserve = 78
    table_h = max(140.0, PAGE_H - M - footer_reserve - y)
    elements.append(
        el(
            "TABLE",
            "DYNAMIC",
            M,
            y,
            CONTENT_W,
            table_h,
            z,
            style={"fontSize": 6},
            content={
                "bindingKey": "BILL_ITEMS",
                "columns": json.dumps(table_cols, separators=(",", ":")),
                "showHeader": "true",
                "borderWidthDp": "0.75",
            },
            element_id=(table or {}).get("id"),
        )
    )
    z += 1
    y += table_h + 4

    # Total
    elements.append(
        el(
            "TEXT",
            "DYNAMIC",
            M,
            y,
            CONTENT_W,
            11,
            z,
            style={"fontSize": 7, "bold": True, "textAlign": "RIGHT"},
            content={"bindingKey": "BILL_TOTAL", "prefix": "Total: "},
        )
    )
    z += 1
    y += 12

    # Amount in words
    elements.append(
        el(
            "TEXT",
            "DYNAMIC",
            M,
            y,
            CONTENT_W,
            22,
            z,
            style={"fontSize": 6, "textAlign": "LEFT"},
            content={
                "bindingKey": "BILL_TOTAL_WORDS",
                "prefix": "In words: ",
                "wrap": "true",
            },
        )
    )
    z += 1
    y += 24

    # Signature
    sig_w = 90
    sig_h = 28
    sig_x = M + CONTENT_W - sig_w
    elements.append(
        el(
            "IMAGE",
            "DYNAMIC",
            sig_x,
            y,
            sig_w,
            sig_h,
            z,
            style={"imageScaleMode": "FIT"},
            content={"bindingKey": "SIGNATURE_IMAGE"},
            element_id=(signature or {}).get("id"),
        )
    )
    z += 1
    elements.append(
        el(
            "TEXT",
            "STATIC",
            sig_x,
            y + sig_h + 1,
            sig_w,
            9,
            z,
            style={"fontSize": 5.5, "textAlign": "RIGHT"},
            content={"text": "Authorised Signature"},
        )
    )

    name = src.get("name", "Template")
    if "Half" not in name:
        name = f"{name} (Half A5)"

    return {
        "id": 0,
        "name": name,
        "isDefault": False,
        "sheetType": "HALF_A5",
        "pageWidthPt": PAGE_W,
        "pageHeightPt": PAGE_H,
        "marginLeft": float(M),
        "marginTop": float(M),
        "marginRight": float(M),
        "marginBottom": float(M),
        "version": 1,
        "updatedAt": int(time.time() * 1000),
        "guides": [],
        "elements": elements,
    }


def main():
    default_in = Path(r"C:\Users\LOCTELL\Downloads\template_My_Template.json")
    default_out = Path(r"C:\Users\LOCTELL\Downloads\template_My_Template_A5_Half.json")
    repo_out = Path(__file__).resolve().parents[1] / "A5_Half_From_My_Template.json"

    in_path = Path(sys.argv[1]) if len(sys.argv) > 1 else default_in
    out_path = Path(sys.argv[2]) if len(sys.argv) > 2 else default_out

    with in_path.open(encoding="utf-8") as f:
        src = json.load(f)

    converted = convert(src)
    for path in {out_path, repo_out}:
        with path.open("w", encoding="utf-8") as f:
            json.dump(converted, f, indent=2)
        print("Wrote", path)
    print(f"Half A5 {PAGE_W}x{PAGE_H}; elements={len(converted['elements'])}")


if __name__ == "__main__":
    main()
