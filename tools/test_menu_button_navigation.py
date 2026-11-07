"""Run real engine focus/tab code against the generated Mods/Cinema bars."""
from pathlib import Path
import shutil
import subprocess
from xml.etree import ElementTree as ET

import pytest

from tools.harness import function

ROOT = Path(__file__).resolve().parents[1]


@pytest.mark.parametrize("platform,menu,bar", [
    ("android", "mods", "button_bar"),
    ("android", "mods", "button_bar_files"),
    ("desktop", "mods", "button_bar"),
    ("android", "theater", "button_bar"),
    ("desktop", "theater", "button_bar"),
])
def test_return_to_bar_and_reach_every_button(tmp_path, platform, menu, bar):
    cc = shutil.which("clang")
    if not cc:
        pytest.skip("clang required")
    xml = ET.parse(ROOT / f"port/assets/menus/ce/main_menu.{menu}.xml")
    definition = next(w for w in xml.getroot().findall("widget") if w.get("name") == f"main_menu/{menu}/{bar}")
    buttons = [c for c in definition.findall("child") if platform in c.get("platform", platform).split()]
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
        "widget_instance_tab_to_next_valid_widget", "widget_instance_tab_to_previous_valid_widget"))
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
 /* A mouse/other-row focus clears the old path; return and repeat. */
 for(i=0;i<3;i++) {
  widget_instance_give_focus_directly(&root,&row_leaf);
  assert(!bar.focused_child);
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
