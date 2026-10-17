"""
Generates the Bot Manager window (bot_manager.if3) and its init clientscript (bm_init.cs2).

The window is a fixed 512x334 modal: a steel frame, a navigation column, a main panel whose
sections are shown and hidden by the server, and a footer. Every table is a fixed set of rows the
server fills and pages; panels are thinbox-framed empty layers placed behind their contents.

Run from the repository root:  python content/other/bots/pack/tools/gen_bot_manager.py
"""

from pathlib import Path

ROOT = Path(__file__).resolve().parents[1] / "src" / "main" / "resources" / "pack"
IF3 = ROOT / "interfaces" / "bot_manager.if3"
INIT = ROOT / "cs2" / "script" / "[clientscript,bm_init].cs2"

W, H = 512, 334
TOP = 32
SIDE_X, SIDE_W = 7, 104
MAIN_X = SIDE_X + SIDE_W + 4
MAIN_W = W - MAIN_X - 7
MAIN_H = 259
FOOT_Y = TOP + MAIN_H + 3
FOOT_H = H - 7 - FOOT_Y

GOLD = "#FF981F"
WHITE = "#FFFFFF"
MUTED = "#C8B48C"
GREY = "#9F9F9F"
SELECT = "#5A4520"

NAV = [
    ("Overview", 0, 779),
    ("Bot Roster", 0, 782),
    ("Master Meister", 0, -1),
    ("Buy Rules", 1, 431),
    ("Sell Rules", 1, 431),
    ("Schedule", 1, 785),
    ("Active Offers", 1, 436),
    ("Trade History", 1, 775),
    ("Settings", 0, 785),
]

components = []
panels = []


def comp(name, ctype, x, y, w, h, parent=None, **props):
    entry = {"name": name, "type": ctype, "x": x, "y": y, "width": w, "height": h}
    if parent:
        entry["parent"] = parent
    entry.update(props)
    components.append(entry)
    return name


def layer(name, x, y, w, h, parent=None, **props):
    return comp(name, "layer", x, y, w, h, parent, **props)


def text(name, x, y, w, h, parent=None, value="", font="regular", color=WHITE, h_align="left", v_align="center", shadow=True, **props):
    return comp(name, "text", x, y, w, h, parent, text=value, font=font, color=color, h_align=h_align, v_align=v_align, text_shadow=shadow, **props)


ICON_PLACEHOLDER = 431


def graphic(name, x, y, w, h, parent=None, sprite=ICON_PLACEHOLDER, **props):
    """Item icons start hidden on the placeholder sprite; the server sets an obj or keeps them hidden."""
    if sprite == ICON_PLACEHOLDER:
        props.setdefault("hide", True)
    return comp(name, "graphic", x, y, w, h, parent, sprite_id=sprite, **props)


def rect(name, x, y, w, h, parent=None, color=SELECT, opacity=0, hide=True):
    return comp(name, "rectangle", x, y, w, h, parent, color=color, filled=True, opacity=opacity, hide=hide)


def panel(name, x, y, w, h, parent):
    """A thinbox-framed box behind whatever is placed in the same area afterwards."""
    panels.append(layer(name, x, y, w, h, parent))


def button(name, x, y, w, h, parent):
    return layer(name, x, y, w, h, parent)


def field(name, x, y, w, h, parent):
    """A clickable value box: dark fill and a value the server sets."""
    layer(name, x, y, w, h, parent, options=["Edit"])
    rect(f"{name}_fill", 0, 0, w, h, name, color="#1E1810", opacity=0, hide=False)
    text(f"{name}_text", 3, 0, w - 6, h, name)


def card(prefix, x, y, w, h, parent):
    panel(f"{prefix}_box", x, y, w, h, parent)
    text(f"{prefix}_label", x + 5, y + 2, w - 10, 13, parent, font="small", color=MUTED)
    text(f"{prefix}_value", x + 5, y + h - 17, w - 10, 15, parent)


def table_row(prefix, parent, y, h, cells, icon_x=None, check_x=None, options=("Select",)):
    layer(prefix, 0, y, MAIN_W, h, parent, options=list(options))
    rect(f"{prefix}_bg", 0, 0, MAIN_W, h, prefix)
    if icon_x is not None:
        graphic(f"{prefix}_icon", icon_x, 0, 20, h, prefix)
    for i, (x, w, align) in enumerate(cells):
        text(f"{prefix}_c{i}", x, 0, w, h, prefix, font="small", h_align=align)
    if check_x is not None:
        layer(f"{prefix}_check", check_x, (h - 15) // 2, 15, 15, prefix)


def header_cells(prefix, parent, y, cells):
    for i, (x, w, align) in enumerate(cells):
        text(f"{prefix}_h{i}", x, y, w, 13, parent, font="small", color=GOLD, h_align=align)


# ---------------------------------------------------------------- frame, navigation, main, footer

layer("frame", 0, 0, W, H, no_click_through=True)

panel("side_box", SIDE_X, TOP, SIDE_W, H - TOP - 7, None)
layer("side", SIDE_X, TOP, SIDE_W, H - TOP - 7)
for i, (label, indent, sprite) in enumerate(NAV):
    item = f"nav{i}"
    layer(item, 3, 4 + i * 25, SIDE_W - 6, 23, "side", options=["Open"])
    rect(f"{item}_bg", 0, 0, SIDE_W - 6, 23, item)
    if sprite >= 0:
        graphic(f"{item}_icon", 3 + indent * 8, 2, 20, 19, item, sprite=sprite, hide=False)
    else:
        comp(f"{item}_icon", "model", 1, 0, 24, 23, item, model_zoom=1250, rotation_x=40)  # chathead portrait
    text(f"{item}_text", 27 + indent * 8, 0, SIDE_W - 33 - indent * 8, 23, item, value=label, font="small", color=GOLD if indent == 0 else MUTED)

panel("main_box", MAIN_X, TOP, MAIN_W, MAIN_H, None)
layer("main", MAIN_X, TOP, MAIN_W, MAIN_H)
panel("foot_box", MAIN_X, FOOT_Y, MAIN_W, FOOT_H, None)
layer("foot", MAIN_X, FOOT_Y, MAIN_W, FOOT_H)

# Meister header and tabs, shared by his five sections
layer("mh", 0, 0, MAIN_W, 60, "main")
comp("mh_portrait", "model", 2, 2, 36, 36, "mh", model_zoom=1150, rotation_x=40)
text("mh_name", 42, 2, 106, 16, "mh", value="Master Meister", font="bold", color=GOLD)
layer("mh_dot", 43, 25, 7, 7, "mh")
text("mh_status", 54, 20, 94, 22, "mh", font="small", line_height=11)
card("mh_card0", 150, 2, 106, 38, "mh")
card("mh_card1", 260, 2, 58, 38, "mh")
card("mh_card2", 322, 2, 66, 38, "mh")
for i in range(5):
    button(f"mh_tab{i}", i * 78, 43, 76, 16, "mh")

CONTENT_Y = 61
CONTENT_H = MAIN_H - CONTENT_Y

# ---------------------------------------------------------------- rules (buy and sell)

layer("ru", 0, CONTENT_Y, MAIN_W, CONTENT_H, "main")
text("ru_title", 4, 0, 140, 18, "ru", font="bold", color=GOLD)
button("ru_search", 146, 1, 96, 16, "ru")
button("ru_add", 246, 1, 70, 16, "ru")
button("ru_bulk", 320, 1, 68, 16, "ru")
RULE_CELLS = [(26, 120, "left"), (148, 64, "right"), (214, 32, "right"), (248, 52, "right"), (302, 56, "right")]
header_cells("ru", "ru", 20, [(26, 120, "left")] + RULE_CELLS[1:] + [(362, 28, "centre")])
for r in range(4):
    table_row(f"ru_r{r}", "ru", 34 + r * 19, 18, RULE_CELLS, icon_x=3, check_x=368, options=("Select", "History"))
text("ru_page", 4, 111, 200, 13, "ru", font="small", color=MUTED)
button("ru_prev", 300, 110, 42, 15, "ru")
button("ru_next", 346, 110, 42, 15, "ru")
panel("ed_box", 0, 127, MAIN_W, CONTENT_H - 127, "ru")
layer("ed", 0, 127, MAIN_W, CONTENT_H - 127, "ru")
graphic("ed_icon", 5, 8, 32, 28, "ed")
text("ed_title", 42, 2, 200, 15, "ed", font="bold", color=GOLD)
layer("ed_check", 246, 3, 15, 15, "ed")
text("ed_check_text", 264, 2, 70, 16, "ed", value="Enabled", font="small")
button("ed_remove", 336, 2, 50, 15, "ed")
EDITOR_COLUMNS = [(42, 50, 60), (157, 50, 60), (272, 34, 80)]
for i in range(6):
    x, label_w, field_w = EDITOR_COLUMNS[i % 3]
    y = 20 + (i // 3) * 18
    text(f"ed_l{i}", x, y, label_w, 16, "ed", font="small", color=MUTED)
    field(f"ed_f{i}", x + label_w + 1, y, field_w, 16, "ed")
text("ed_note", 6, 56, MAIN_W - 12, 14, "ed", font="small", color=MUTED)

# ---------------------------------------------------------------- knob lists (schedule, settings)

layer("kn", 0, 0, MAIN_W, MAIN_H, "main")
text("kn_title", 4, 0, 200, 18, "kn", font="bold", color=GOLD)
button("kn_prev", 300, 1, 42, 15, "kn")
button("kn_next", 346, 1, 42, 15, "kn")
text("kn_sub", 4, 18, MAIN_W - 8, 14, "kn", font="small", color=MUTED)
for r in range(9):
    row = f"kn_r{r}"
    layer(row, 0, 34 + r * 21, MAIN_W, 20, "kn")
    text(f"{row}_label", 4, 0, 150, 20, row, font="small", color=GOLD, options=["Explain"])
    text(f"{row}_value", 156, 0, 92, 20, row, font="small")
    button(f"{row}_dec", 250, 2, 22, 16, row)
    button(f"{row}_inc", 276, 2, 22, 16, row)
    button(f"{row}_toggle", 250, 2, 70, 16, row)
    text(f"{row}_info", 302, 0, 86, 20, row, font="small", color=MUTED)
text("kn_help", 4, 225, 290, 32, "kn", font="small", color=MUTED, line_height=12)

# ---------------------------------------------------------------- active offers

layer("of", 0, CONTENT_Y, MAIN_W, CONTENT_H, "main")
text("of_title", 4, 0, 200, 18, "of", font="bold", color=GOLD)
button("of_cancelall", 300, 1, 88, 16, "of")
OFFER_CELLS = [(26, 120, "left"), (148, 34, "left"), (184, 62, "right"), (250, 66, "right"), (322, 66, "left")]
header_cells("of", "of", 20, [(26, 120, "left")] + OFFER_CELLS[1:])
for r in range(8):
    table_row(f"of_r{r}", "of", 34 + r * 16, 15, OFFER_CELLS, icon_x=3)
text("of_sel", 4, 164, 200, 15, "of", font="small")
button("of_cancel", 206, 163, 70, 16, "of")
button("of_replace", 280, 163, 108, 16, "of")
text("of_help", 4, 181, MAIN_W - 8, 16, "of", font="small", color=MUTED)

# ---------------------------------------------------------------- trade history

layer("hi", 0, CONTENT_Y, MAIN_W, CONTENT_H, "main")
text("hi_title", 4, 0, 110, 18, "hi", font="bold", color=GOLD)
text("hi_scope", 116, 0, 272, 18, "hi", font="small", color=MUTED, h_align="right")
for i in range(4):
    card(f"hi_card{i}", i * 98, 18, 94, 30, "hi")
button("hi_search", 0, 51, 112, 16, "hi")
button("hi_side", 116, 51, 66, 16, "hi")
button("hi_range", 186, 51, 70, 16, "hi")
button("hi_sort", 260, 51, 64, 16, "hi")
button("hi_export", 328, 51, 62, 16, "hi")
HIST_CELLS = [
    (2, 30, "left"),
    (54, 68, "left"),
    (124, 28, "left"),
    (154, 30, "right"),
    (186, 40, "right"),
    (228, 34, "right"),
    (264, 60, "right"),
    (326, 62, "right"),
]
header_cells("hi", "hi", 70, [(2, 30, "left"), (34, 88, "left")] + HIST_CELLS[2:])
for r in range(5):
    table_row(f"hi_r{r}", "hi", 84 + r * 17, 16, HIST_CELLS, icon_x=33)
panel("hd_box", 0, 170, MAIN_W, CONTENT_H - 170, "hi")
layer("hd", 0, 170, MAIN_W, CONTENT_H - 170, "hi")
text("hd_title", 4, 1, 160, 14, "hd", font="small", color=GOLD)
text("hd_note", 166, 1, 222, 14, "hd", font="small", color=MUTED, h_align="right")
for i in range(4):
    text(f"hd_v{i}", 4 + i * 96, 14, 94, 14, "hd", font="small")
text("hd_text", 4, 14, MAIN_W - 8, 14, "hd", font="small", color=MUTED)

# ---------------------------------------------------------------- overview

layer("ov", 0, 0, MAIN_W, MAIN_H, "main")
text("ov_title", 4, 0, 200, 18, "ov", value="Overview", font="bold", color=GOLD)
for i in range(4):
    card(f"ov_card{i}", i * 98, 20, 94, 34, "ov")
panel("ov_m_box", 0, 58, MAIN_W, 66, "ov")
comp("ov_m_portrait", "model", 4, 66, 40, 40, "ov", model_zoom=1500, rotation_x=40)
text("ov_m_title", 48, 60, 200, 16, "ov", value="Master Meister", font="bold", color=GOLD)
for i in range(3):
    text(f"ov_m{i}", 48, 76 + i * 15, 336, 15, "ov", font="small")
for i in range(6):
    button(f"ov_b{i}", (i % 3) * 130, 130 + (i // 3) * 22, 126, 18, "ov")
text("ov_note", 4, 178, MAIN_W - 8, 78, "ov", font="small", color=MUTED, line_height=13)

# ---------------------------------------------------------------- bot roster

layer("ro", 0, 0, MAIN_W, MAIN_H, "main")
text("ro_title", 4, 0, 100, 18, "ro", value="Bot Roster", font="bold", color=GOLD)
button("ro_search", 104, 1, 86, 16, "ro")
button("ro_fstate", 194, 1, 64, 16, "ro")
button("ro_frole", 262, 1, 64, 16, "ro")
button("ro_fdiff", 330, 1, 58, 16, "ro")
ROSTER_CELLS = [(4, 78, "left"), (84, 64, "left"), (150, 70, "left"), (222, 44, "left"), (268, 38, "right"), (308, 22, "right"), (332, 56, "right")]
header_cells("ro", "ro", 20, ROSTER_CELLS)
for r in range(8):
    table_row(f"ro_r{r}", "ro", 34 + r * 16, 15, ROSTER_CELLS)
text("ro_page", 4, 164, 150, 15, "ro", font="small", color=MUTED)
button("ro_create", 160, 163, 44, 16, "ro")
button("ro_bulk", 208, 163, 88, 16, "ro")
button("ro_prev", 300, 163, 42, 16, "ro")
button("ro_next", 346, 163, 42, 16, "ro")
panel("rd_box", 0, 183, MAIN_W, MAIN_H - 183, "ro")
layer("rd", 0, 183, MAIN_W, MAIN_H - 183, "ro")
text("rd_title", 4, 2, 200, 15, "rd", font="bold", color=GOLD)
text("rd_info", 4, 18, 210, 54, "rd", font="small", line_height=13)
for i in range(6):
    button(f"rd_a{i}", 218 + (i % 3) * 57, 4 + (i // 3) * 22, 54, 18, "rd")
text("rd_hint", 218, 50, 170, 24, "rd", font="small", color=MUTED, line_height=12)

# ---------------------------------------------------------------- footer

layer("ft_dot0", 3, 5, 7, 7, "foot")
text("ft_t0", 13, 0, 150, 16, "foot", font="small")
layer("ft_dot1", 3, 21, 7, 7, "foot")
text("ft_t1", 13, 16, 150, 16, "foot", font="small")
button("ft_b0", 166, 6, 72, 20, "foot")
button("ft_b1", 242, 6, 68, 20, "foot")
button("ft_b2", 314, 6, 74, 20, "foot")

# ---------------------------------------------------------------- output


def toml_value(value):
    if isinstance(value, bool):
        return "true" if value else "false"
    if isinstance(value, int):
        return str(value)
    if isinstance(value, list):
        return "[" + ", ".join(toml_value(v) for v in value) + "]"
    return '"' + str(value).replace("\\", "\\\\").replace('"', '\\"') + '"'


ORDER = ["name", "type", "parent", "x", "y", "width", "height"]


def write_if3():
    lines = [
        "# Generated by content/other/bots/pack/tools/gen_bot_manager.py - edit the generator, not this file.",
        "[[interface]]",
        'name = "interface.bot_manager"',
        f"width = {W}",
        f"height = {H}",
        'on_load = ["clientscript.bm_init"]',
        "",
    ]
    for entry in components:
        lines.append("[[component]]")
        for key in ORDER + sorted(k for k in entry if k not in ORDER):
            if key in entry:
                lines.append(f"{key} = {toml_value(entry[key])}")
        lines.append("")
    IF3.write_text("\n".join(lines), encoding="utf-8")


def write_init():
    body = ['[clientscript,bm_init]', '~steelborder(bot_manager:frame, "Bot Manager", 0);']
    for name in panels:
        body.append(f"cc_deleteall(bot_manager:{name});")
        body.append(f"~thinbox(bot_manager:{name}, 0);")
    INIT.write_text("\n".join(body) + "\n", encoding="utf-8")


write_if3()
write_init()
print(f"{len(components)} components, {len(panels)} panels")
