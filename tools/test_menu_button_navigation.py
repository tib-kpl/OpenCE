"""Run real engine focus/tab code against generated menu button bars."""
from pathlib import Path
import shutil
import subprocess
from xml.etree import ElementTree as ET

import pytest

from tools.harness import function

ROOT = Path(__file__).resolve().parents[1]


def test_android_mod_actions_clear_the_details_panel():
    widgets = {w.get("name"): w for w in ET.parse(ROOT / "port/assets/menus/ce/main_menu.mods.xml").getroot().findall("widget")}
    base = "main_menu/mods/"
    detail = widgets[base + "mod_details_text"]
    panel_bottom = int(detail.get("top")) + int(detail.get("height")) + 6
    bars = [c for c in widgets[base + "mods_list"].findall("child")
            if c.get("widget") in (base + "button_bar_files", base + "button_bar")
            and "android" in c.get("platform", "android").split()]
    assert len(bars) == 2
    previous_bottom = panel_bottom
    for placed in bars:
        bar = widgets[placed.get("widget")]
        top = int(placed.get("y"))
        assert top >= previous_bottom
        previous_bottom = top + int(bar.get("height"))
        assert previous_bottom <= 480


@pytest.mark.parametrize("platform,menu,bar,mode", [
    ("android", "mods", "button_bar", None),
    ("android", "mods", "button_bar_files", None),
    ("desktop", "mods", "button_bar", None),
    ("android", "theater", "button_bar", None),
    ("desktop", "theater", "button_bar", None),
    ("android", "multiplayer_type_select.join_game", "join_game_button_bar", "internet"),
    ("android", "multiplayer_type_select.join_game", "join_game_button_bar", "lan"),
    ("android", "multiplayer_type_select.join_game", "join_game_button_bar", "direct"),
])
def test_return_to_bar_and_reach_every_button(tmp_path, platform, menu, bar, mode):
    cc = shutil.which("clang")
    if not cc:
        pytest.skip("clang required")
    xml = ET.parse(ROOT / f"port/assets/menus/ce/main_menu.{menu}.xml")
    definition = next(w for w in xml.getroot().findall("widget") if w.get("name") == f"main_menu/{menu.replace('.', '/')}/{bar}")
    buttons = [c for c in definition.findall("child") if platform in c.get("platform", platform).split()]
    if mode:
        hidden = {"join_game_button_update"}
        hidden |= {"button_clipboard"} if mode != "direct" else set()
        hidden |= {"join_game_button_refresh", "join_game_button_filters"} if mode != "internet" else set()
        buttons = [c for c in buttons if c.get("widget").rsplit("/", 1)[-1] not in hidden]
    assert len(buttons) >= 2
    if platform == "android" and menu == "mods":
        expected = ["button_import", "button_delete", "button_up", "button_down"] if bar == "button_bar_files" else ["button_ok", "common_button_cancel"]
        assert [c.get("widget").rsplit("/", 1)[-1] for c in buttons] == expected
    flags = {"pass_unhandled_to_focused_child": 0, "left_right_tabs_children": 4, "left_right_tabs_items": 6}
    mask = sum(1 << flags[f] for f in definition.get("flags", "").split())
    kind = {None: 0, "column_list": 2}[definition.get("type")]
    engine = (ROOT / "source/interface/ui_widget.c").read_text(encoding="utf-8")
    source = r'''
#include <assert.h>
#include <stddef.h>
#define TRUE 1
#define FALSE 0
#define TEST_FLAG(f,b) ((f)&(1L<<(b)))
typedef int boolean;
enum { _ui_widget_type_spinner_list=3, _ui_widget_type_column_list=2,
 _widget_pass_unhandled_events_to_children_bit=0, _widget_dont_focus_a_specific_child_bit=1,
 _widget_dpad_leftright_tabs_thru_children_bit=4 };
struct ui_widget_definition { long flags; struct { int count; } event_handlers; } defs[2];
struct widget_instance { int disabled,visible,type,definition_tag_index;
 struct { int current_frame_index,number_of_sprite_frames; } animation;
 struct widget_instance *child,*focused_child,*parent,*next,*previous; } root,bar,row,row_leaf,buttons[8];
static struct ui_widget_definition *ui_widget_definition_get(int id) { return &defs[id]; }
static int pc_menu_tag(int id) { return 1; }
static struct widget_instance *widget_instance_get_tail_child_widget(struct widget_instance *w) {
 struct widget_instance *c=w->child; while(c && c->next)c=c->next; return c;
}
static struct widget_instance *widget_instance_get_topmost_parent(struct widget_instance *w) {
 while(w->parent)w=w->parent; return w;
}
'''
    source += "\n".join(function(engine, name) for name in (
        "widget_instance_can_handle_events", "widget_instance_port_is_label",
        "widget_instance_refocus_passthrough", "widget_instance_give_focus_directly",
        "widget_instance_tab_to_next_valid_widget", "widget_instance_tab_to_previous_valid_widget", "column_list_update"))
    source += f"\n#define BUTTONS {len(buttons)}\n#define BAR_KIND {kind}\n#define BAR_FLAGS {mask}\n"
    source += r'''
int main(void) {
 int i;
 defs[0].flags=BAR_FLAGS; defs[1].event_handlers.count=1;
 root.child=&row; root.focused_child=&row; row.parent=&root; row.next=&bar;
 row.visible=1; row.definition_tag_index=1;
 row.child=&row_leaf; row.focused_child=&row_leaf;
 row_leaf.parent=&row; row_leaf.visible=1; row_leaf.definition_tag_index=1;
 bar.parent=&root; bar.previous=&row; bar.child=&buttons[0]; bar.type=BAR_KIND; bar.visible=1;
 for(i=0;i<BUTTONS;i++) {
  buttons[i].parent=&bar; buttons[i].visible=1; buttons[i].definition_tag_index=1;
  buttons[i].animation.number_of_sprite_frames=2;
  buttons[i].next=i+1<BUTTONS ? &buttons[i+1] : NULL;
  buttons[i].previous=i ? &buttons[i-1] : NULL;
 }
 /* Enter from the list, then visit every button with the engine's tabs. */
 widget_instance_give_focus_directly(&root,&bar);
 assert(bar.focused_child==&buttons[0]);
 assert(TEST_FLAG(defs[0].flags,4));
 for(i=1;i<BUTTONS;i++) {
  widget_instance_tab_to_next_valid_widget(&bar);
  assert(bar.focused_child==&buttons[i]);
 }
 widget_instance_tab_to_next_valid_widget(&bar); assert(bar.focused_child==&buttons[0]);
 widget_instance_tab_to_previous_valid_widget(&bar); assert(bar.focused_child==&buttons[BUTTONS-1]);
 column_list_update(&bar,&defs[0]);
 assert(buttons[BUTTONS-1].animation.current_frame_index==1);
 /* Child-tab navigation preserves the old bar's pointer. It must not glow. */
 root.focused_child=&row;
 column_list_update(&bar,&defs[0]);
 assert(!buttons[BUTTONS-1].animation.current_frame_index);
 /* The parent's up/down tabs enter the bar and return to the game row. */
 bar.focused_child=NULL;
 widget_instance_tab_to_next_valid_widget(&root);
 assert(root.focused_child==&bar && bar.focused_child==&buttons[0]);
 widget_instance_tab_to_previous_valid_widget(&root);
 assert(root.focused_child==&row);
 widget_instance_tab_to_next_valid_widget(&root);
 assert(root.focused_child==&bar);
 root.focused_child=&bar;
 /* A mouse/other-row focus clears the old path; return and repeat. */
 for(i=0;i<3;i++) {
  widget_instance_give_focus_directly(&root,&row_leaf);
  assert(!bar.focused_child);
  column_list_update(&bar,&defs[0]);
  { int j; for(j=0;j<BUTTONS;j++) assert(!buttons[j].animation.current_frame_index); }
  widget_instance_give_focus_directly(&root,&bar);
  assert(bar.focused_child==&buttons[0]);
  widget_instance_tab_to_next_valid_widget(&bar); assert(bar.focused_child==&buttons[1]);
 }
 return 0;
}
'''
    path = tmp_path / "navigation.c"
    path.write_text(source, encoding="utf-8")
    binary = tmp_path / "navigation"
    subprocess.run([cc, "-m32", "-std=gnu89", str(path), "-o", str(binary)], check=True)
    subprocess.run([str(binary)], check=True)


def test_browser_directional_events_reach_rows_and_buttons():
    xml = ET.parse(ROOT / "port/assets/menus/ce/main_menu.multiplayer_type_select.join_game.xml")
    widgets = {w.get("name").rsplit("/", 1)[-1]: w for w in xml.getroot().findall("widget")}
    handlers = {h.get("event"): h.get("run") for h in widgets["join_game_items_list"].findall("on")}
    assert not {"up", "down", "left", "right"} & handlers.keys()
    assert handlers["right_stick_up"] == "port browser sort increasing"
    assert handlers["right_stick_right"] == "port browser column next"
    assert "up_down_tabs_children" in widgets["join_game_items_list"].get("flags").split()


def test_browser_overrides_regenerate_without_other_menu_changes(tmp_path):
    import ast
    from xml.sax.saxutils import quoteattr
    from tools import port_settings

    # Exercise the generator's real text-only path without its art dependencies.
    tree = ast.parse((ROOT / "tools/ce_menus.py").read_text(encoding="utf-8"))
    selected = ast.Module(body=[node for node in tree.body if isinstance(node, ast.FunctionDef) and
                               node.name in ("attributes", "regenerate_widget_overrides")], type_ignores=[])
    generator = {"CE": tmp_path, "port_settings": port_settings, "quoteattr": quoteattr}
    exec(compile(selected, "ce_menus.py", "exec"), generator)

    name = "main_menu.multiplayer_type_select.join_game.xml"
    original = (ROOT / "port/assets/menus/ce" / name).read_text(encoding="utf-8")
    path = tmp_path / name
    path.write_text(original, encoding="utf-8")
    base = "main_menu/multiplayer_type_select/join_game/"
    names = [base + suffix for suffix in ("join_game_button_bar", "join_game_items_list", "join_game_screen")]
    generator["regenerate_widget_overrides"](names)
    assert path.read_text(encoding="utf-8") == original
    path.write_text(original.replace("right_stick_up", "up").replace("left_right_tabs_children", "left_right_tabs_items"), encoding="utf-8")
    generator["regenerate_widget_overrides"](names)
    assert path.read_text(encoding="utf-8") == original
