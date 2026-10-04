# Translations of the menus

`<language>.json` holds a language's texts for the PC menus (`ce/*.xml`):

```json
{
 "text":    { "NEW CAMPAIGN": "NOUVELLE CAMPAGNE", ... },
 "strings": { "ON|OFF": "ACTIVÉ|DÉSACTIVÉ", ... }
}
```

- `text` maps the English text of a `text="..."` attribute to its translation.
  `\n` is a line break, as in the XML files; `%s`, `%d` and the other
  formats keep their order, and a `%` is never followed by a letter or a
  space and a letter (the game's formatting would read a format).
- `strings` maps a spinner's whole `strings="A|B|C"` list to its translation,
  with as many values.

The XML files stay English, so merging the project that has them never
conflicts with a translation. `tools/translate_menus.py` writes a language's
copy of the files, with only the texts changed, and checks each against its
English file:

    python tools/translate_menus.py --lang fr --out some/folder
    python tools/translate_menus.py --lang fr --check

`--check` lists the texts that are new since the translation was written (they
show in English until they are added here) and its entries no menu has.

The Android app (`port/android/app`) makes the French files while it builds
and puts them beside `config.toml`, in `menus`, when `game.language` is `fr`:
the game takes a file of that folder in place of its built-in one
(`port/linux/src/menu_files.c`). On a desktop port, copy the files there.

The titles drawn as pictures (the screens' headers, the main menu's items) are
the maps' own: the French maps have theirs in French.
