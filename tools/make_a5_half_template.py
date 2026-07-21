"""A5 half-sheet with left→right (tilted) invoice flow.

Page stays A5 portrait (420 x 595). Content fills the TOP half only.
Layout is the A4 invoice conceptually rotated 90° CCW:
  Header strip on the LEFT → buyer/table in the MIDDLE → total/signature on the RIGHT.
Bottom half stays blank for a second print on the same sheet.
"""
import json
import time
import uuid

PAGE_W, PAGE_H = 420, 595
HALF = 297.5
M = 10

# Usable band in top half
L, T = M, M
R, B = PAGE_W - M, HALF - M
BAND_W = R - L  # 400
BAND_H = B - T  # 277.5


def el(kind, binding, x, y, w, h, z, style=None, content=None):
    st = {
        "fontSize": 9,
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
        "id": str(uuid.uuid4()),
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
    }


# Column widths L→R (rotated from top→bottom)
header_w = 64
right_w = 92
gap = 6
main_w = BAND_W - header_w - right_w - 2 * gap

hx = L
mx = hx + header_w + gap
rx = mx + main_w + gap

elements = []

# --- LEFT: header (was top of portrait invoice) ---
elements.append(
    el(
        "IMAGE",
        "DYNAMIC",
        hx,
        T,
        header_w,
        BAND_H,
        1,
        style={"textAlign": "CENTER", "imageScaleMode": "STRETCH"},
        content={"bindingKey": "HEADER_IMAGE"},
    )
)

# --- MIDDLE: invoice meta, buyer, items (was body) ---
y = T
row_h = 14
elements.append(
    el(
        "TEXT",
        "DYNAMIC",
        mx,
        y,
        main_w * 0.55,
        row_h,
        3,
        style={"fontSize": 8},
        content={"bindingKey": "BILL_NUMBER", "prefix": "Invoice No: "},
    )
)
elements.append(
    el(
        "TEXT",
        "DYNAMIC",
        mx + main_w * 0.55,
        y,
        main_w * 0.45,
        row_h,
        4,
        style={"fontSize": 8, "textAlign": "RIGHT"},
        content={"bindingKey": "BILL_DATE", "prefix": "Date: "},
    )
)
y += row_h + 3
elements.append(el("LINE", "STATIC", mx, y, main_w, 1, 5, content={}))
y += 5
elements.append(
    el(
        "TEXT",
        "STATIC",
        mx,
        y,
        main_w,
        12,
        6,
        style={"fontSize": 9, "bold": True},
        content={"text": "Buyer Details"},
    )
)
y += 13
for z, key, prefix in (
    (7, "CUSTOMER_NAME", "Name: "),
    (8, "CUSTOMER_ADDRESS", "Address: "),
    (9, "CUSTOMER_PHONE", "Phone: "),
):
    elements.append(
        el(
            "TEXT",
            "DYNAMIC",
            mx,
            y,
            main_w,
            11,
            z,
            style={"fontSize": 8},
            content={"bindingKey": key, "prefix": prefix},
        )
    )
    y += 12

y += 4
table_h = B - y
cols = json.dumps(
    [
        {"key": "sl", "label": "Sl", "widthPercent": 7, "align": "CENTER"},
        {"key": "name", "label": "Particulars", "widthPercent": 43, "align": "LEFT"},
        {"key": "quantity", "label": "Qty", "widthPercent": 11, "align": "CENTER"},
        {"key": "mrp", "label": "MRP", "widthPercent": 11, "align": "CENTER"},
        {"key": "discount", "label": "Disc%", "widthPercent": 11, "align": "CENTER"},
        {"key": "lineTotal", "label": "Price", "widthPercent": 17, "align": "CENTER"},
    ]
)
elements.append(
    el(
        "TABLE",
        "DYNAMIC",
        mx,
        y,
        main_w,
        table_h,
        10,
        style={"fontSize": 8},
        content={
            "bindingKey": "BILL_ITEMS",
            "columns": cols,
            "showHeader": "true",
            "borderWidthDp": "1.2",
        },
    )
)

# --- RIGHT: total + words + signature (was bottom of portrait invoice) ---
ry = T
elements.append(
    el(
        "TEXT",
        "DYNAMIC",
        rx,
        ry,
        right_w,
        16,
        11,
        style={"fontSize": 10, "bold": True, "textAlign": "RIGHT"},
        content={"bindingKey": "BILL_TOTAL", "prefix": "Total: "},
    )
)
ry += 20
elements.append(
    el(
        "TEXT",
        "DYNAMIC",
        rx,
        ry,
        right_w,
        90,
        12,
        style={"fontSize": 7, "textAlign": "RIGHT"},
        content={
            "bindingKey": "BILL_TOTAL_WORDS",
            "prefix": "Amount in Words: ",
            "wrap": "true",
        },
    )
)
sig_h = 40
sig_y = B - sig_h - 16
elements.append(
    el(
        "IMAGE",
        "DYNAMIC",
        rx,
        sig_y,
        right_w,
        sig_h,
        13,
        style={"imageScaleMode": "FIT"},
        content={"bindingKey": "SIGNATURE_IMAGE"},
    )
)
elements.append(
    el(
        "TEXT",
        "STATIC",
        rx,
        sig_y + sig_h + 2,
        right_w,
        12,
        14,
        style={"fontSize": 7, "textAlign": "RIGHT"},
        content={"text": "Authorised Signature"},
    )
)

out = {
    "id": 0,
    "name": "A5 Half-Sheet Landscape",
    "isDefault": False,
    "pageWidthPt": PAGE_W,
    "pageHeightPt": PAGE_H,
    "marginLeft": M,
    "marginTop": M,
    "marginRight": M,
    "marginBottom": M,
    "version": 2,
    "updatedAt": int(time.time() * 1000),
    "guides": [
        {
            "id": str(uuid.uuid4()),
            "orientation": "HORIZONTAL",
            "positionPt": HALF,
        }
    ],
    "elements": elements,
}

repo = r"D:\pseudo-world\VikrSaathi\A5_Half_Sheet_Landscape_Import.json"
dl = r"C:\Users\LOCTELL\Downloads\A5_Half_Sheet_Landscape_Import.json"
for path in (repo, dl):
    with open(path, "w", encoding="utf-8") as f:
        json.dump(out, f, indent=2)

print("Wrote", repo)
print("Wrote", dl)
print("Layout L→R: HEADER | body+table | total+signature  (top half of A5)")
for e in elements:
    b = e["bounds"]
    key = e["content"].get("bindingKey") or e["content"].get("text", "")
    print(
        f"{e['kind']:5} {str(key)[:24]:24} "
        f"x={b['x']:6} y={b['y']:6} w={b['width']:6} h={b['height']:6}"
    )
