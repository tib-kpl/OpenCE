package com.halo.decomp;

import android.content.Context;
import android.content.res.AssetManager;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The menus in French.
 *
 * The game's menus are XML files built into it (port/assets/menus); each is
 * replaced by a file of the same name in a menus folder beside config.toml
 * (port/linux/src/menu_files.c). The app carries a French copy of the
 * menus' files (assets/menus_fr: tools/translate_menus.py makes them while
 * the app is built, from port/assets/menus/lang/fr.json), and puts them in
 * that folder when config.toml's game.language is French, the language of
 * the maps the importer installed (LauncherActivity); for any other
 * language it takes back the files it put there. Files of the folder that
 * are not its own are left alone, and replaced only by the French menus'
 * names.
 */
final class MenuTranslation {
    private static final String ASSETS = "menus_fr";
    /** in the menus folder: the files this class put there, one a line */
    private static final String MARKER = ".translation";

    private MenuTranslation() {
    }

    /** game.language of config.toml's [game] section, "" for English */
    static String gameLanguage(File dataRoot) {
        File config = new File(dataRoot, "config.toml");
        boolean inGame = false;

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(config), "UTF-8"))) {
            String line;

            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.startsWith("[")) {
                    inGame = line.startsWith("[game]");
                } else if (inGame && line.matches("language\\s*=.*")) {
                    String value = line.substring(line.indexOf('=') + 1).trim();

                    return value.replace("\"", "").replace("'", "").trim().toLowerCase(Locale.ROOT);
                }
            }
        } catch (IOException e) {
            // no settings yet: English
        }
        return "";
    }

    /** puts the French menus in place, or takes them out; never fails the start */
    static void sync(Context context, File dataRoot) {
        if (dataRoot == null)
            return;
        try {
            File menus = new File(dataRoot, "menus");
            File marker = new File(menus, MARKER);

            if (gameLanguage(dataRoot).startsWith("fr")) {
                List<String> files = new ArrayList<>();

                list(context.getAssets(), ASSETS, "", files);
                if (!files.isEmpty()) {
                    for (String file : files)
                        copy(context.getAssets(), ASSETS + "/" + file, new File(menus, file));
                    writeMarker(marker, files);
                    return;
                }
            }
            remove(menus, marker);
        } catch (IOException | RuntimeException e) {
            android.util.Log.w("halo", "menus: " + e);
        }
    }

    /** the files below an assets folder, by their paths in it */
    private static void list(AssetManager assets, String folder, String prefix, List<String> out) throws IOException {
        String[] names = assets.list(folder);

        if (names == null)
            return;
        for (String name : names) {
            String path = folder + "/" + name;
            String[] children = assets.list(path);

            if (children != null && children.length > 0)
                list(assets, path, prefix + name + "/", out);
            else
                out.add(prefix + name);
        }
    }

    private static void copy(AssetManager assets, String asset, File target) throws IOException {
        File folder = target.getParentFile();
        File temporary = new File(folder, target.getName() + ".part");

        if (folder != null && !folder.isDirectory() && !folder.mkdirs())
            throw new IOException("cannot make " + folder);
        try (InputStream in = assets.open(asset); OutputStream out = new FileOutputStream(temporary)) {
            byte[] buffer = new byte[65536];
            int count;

            while ((count = in.read(buffer)) > 0)
                out.write(buffer, 0, count);
        }
        if (!temporary.renameTo(target)) {
            target.delete();
            if (!temporary.renameTo(target))
                throw new IOException("cannot write " + target);
        }
    }

    private static void writeMarker(File marker, List<String> files) throws IOException {
        try (OutputStream out = new FileOutputStream(marker)) {
            for (String file : files)
                out.write((file + "\n").getBytes("UTF-8"));
        }
    }

    /** the files the marker lists, and then the marker */
    private static void remove(File menus, File marker) throws IOException {
        if (!marker.isFile())
            return;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(marker), "UTF-8"))) {
            String file;

            while ((file = reader.readLine()) != null) {
                if (file.isEmpty() || file.contains(".."))
                    continue;
                File target = new File(menus, file);

                target.delete();
                File folder = target.getParentFile();

                if (folder != null && !folder.equals(menus))
                    folder.delete(); // (only when empty)
            }
        }
        marker.delete();
        menus.delete(); // (only when empty)
    }
}
