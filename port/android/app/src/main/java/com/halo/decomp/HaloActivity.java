package com.halo.decomp;

import android.annotation.TargetApi;
import android.app.AlertDialog;
import android.annotation.TargetApi;
import android.content.Context;
import android.content.ContentResolver;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Insets;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.OpenableColumns;
import android.provider.DocumentsContract;
import android.view.Display;
import android.widget.ImageView;
import android.widget.FrameLayout;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.libsdl.app.SDLActivity;

/**
 * The game: SDL3's activity, running libmain.so (port/android/host), which
 * loads the game image from the APK's assets.
 */
public class HaloActivity extends SDLActivity {
    private static final int PR_SET_DUMPABLE = 4; // <linux/prctl.h>

    /**
     * Makes the process dumpable, as it is in a debuggable build. The host
     * reads /proc/self/pagemap to tell whether ART's large object space over
     * the game's fixed memory range is idle before taking it
     * (host_memory.c, range_unused). Android starts the processes of
     * non-debuggable apps non-dumpable, which gives their /proc/self files
     * to root: pagemap cannot be read, the range is left to ART and a
     * release build stops with "cannot load the game image". The process
     * stays private to its own user.
     */
    static void makeDumpable() {
        try {
            android.system.Os.prctl(PR_SET_DUMPABLE, 1, 0, 0, 0);
        } catch (Exception e) {
            android.util.Log.w("halo", "cannot make the process dumpable: " + e);
        }
    }

    /** lets system link's broadcasts in over Wi-Fi while the game runs */
    private WifiManager.MulticastLock multicastLock;
    /**
     * the latest system gesture insets {left, top, right, bottom}, in pixels;
     * written on the UI thread, read by the game's thread: replaced as a
     * whole, never changed in place
     */
    private volatile int[] gestureInsets = new int[] { 0, 0, 0, 0 };
    /** the on-screen touch controls, over SDL's surface */
    private TouchControls touchControls;
    private static final int EXPORT_LAYOUT = 401, IMPORT_LAYOUT = 402, EXPORT_REPLAYS = 403, IMPORT_REPLAYS = 404,
        IMPORT_MODS = 405;
    private String pendingLayoutExport;
    /** the folder of the replays the Theater's EXPORT REPLAYS copies, while the folder picker is open */
    private String pendingReplaysFolder;
    /** the folder the Theater's IMPORT copies replays into, while the file picker is open */
    private String pendingImportFolder;
    /** the mods folder the Mods screen's IMPORT takes .zip files into, while the file picker is open */
    private String pendingModsFolder;
    /** the longest mod folder name the game takes (port/linux/src/port_mods.c's MAXIMUM_MOD_FOLDER_LENGTH less its
     * terminator), in UTF-8's bytes, and the most files a mod's .zip may hold (its MAXIMUM_MOD_FILES) */
    private static final int MOD_FOLDER_LENGTH = 63;
    private static final int MOD_MAXIMUM_FILES = 262144;
    /** a replay's file name ends so, and the file begins so (port/linux/game/network_replay.c) */
    private static final String REPLAY_EXTENSION = ".ocreplay";
    private static final byte[] REPLAY_MAGIC = "OCREPLAY".getBytes(StandardCharsets.US_ASCII);
    /** the longest name the Theater lists (NETWORK_REPLAY_NAME_SIZE less its terminator), and the largest replay
     * taken (network_replay_page.c's REPLAY_PAGE_MAXIMUM_UPLOAD) */
    private static final int REPLAY_NAME_LENGTH = 95;
    private static final long REPLAY_MAXIMUM_SIZE = 256L * 1024 * 1024;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable hideSystemBars = this::hideSystemUi;

    @Override
    protected String[] getLibraries() {
        return new String[] { "SDL3", "main" };
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        makeDumpable();
        super.onCreate(savedInstanceState);
        if (savedInstanceState != null) {
            pendingLayoutExport = savedInstanceState.getString("pending-layout-export");
            pendingReplaysFolder = savedInstanceState.getString("pending-replays-folder");
            pendingImportFolder = savedInstanceState.getString("pending-import-folder");
            pendingModsFolder = savedInstanceState.getString("pending-mods-folder");
        }
        if (mLayout != null) {
            touchControls = new TouchControls(this);
            mLayout.addView(touchControls, new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        }
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        hideSystemBars();
        preferHighestRefreshRate();
        hideSystemUiSoon();
        trackGestureInsets();
        acquireMulticastLock();
        addGraphicsGear();
        // a new version looked for while the game starts
        Updater.start(this);
    }

    /** the system's file picker, for the touch layout; no storage permission */
    public void chooseLayoutFile(boolean export, String configuration) {
        String message = export
            ? "Choose the folder and the name of the touch layout file."
            : "Choose a touch layout file. It replaces your buttons, the look sensitivity and the General settings.";
        new AlertDialog.Builder(this)
            .setTitle(export ? "Export the layout" : "Import a layout")
            .setMessage(message)
            .setNegativeButton("Cancel", null)
            .setPositiveButton(export ? "Choose the place" : "Choose the file", (dialog, which) -> {
                Intent intent = new Intent(export ? Intent.ACTION_CREATE_DOCUMENT : Intent.ACTION_OPEN_DOCUMENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType(export ? "text/plain" : "*/*");
                if (export) {
                    pendingLayoutExport = configuration;
                    intent.putExtra(Intent.EXTRA_TITLE, "halo-touch-layout.halolayout");
                }
                try {
                    startActivityForResult(intent, export ? EXPORT_LAYOUT : IMPORT_LAYOUT);
                } catch (android.content.ActivityNotFoundException e) {
                    pendingLayoutExport = null;
                    Toast.makeText(this, "This device has no file picker.", Toast.LENGTH_LONG).show();
                }
            })
            .show();
    }

    @Override
    protected void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state);
        state.putString("pending-layout-export", pendingLayoutExport);
        state.putString("pending-replays-folder", pendingReplaysFolder);
        state.putString("pending-import-folder", pendingImportFolder);
        state.putString("pending-mods-folder", pendingModsFolder);
    }

    /**
     * The Theater's EXPORT REPLAYS (port/linux/game/menu_functions.c, through host_android_export_replays): the
     * system's folder picker, then each replay in folder that the folder chosen has not by its name copied there.
     * Called on the game's thread; no storage permission.
     */
    public void exportReplays(String folder) {
        runOnUiThread(() -> {
            File[] replays = replayFiles(folder);
            if (replays.length == 0) {
                Toast.makeText(this, "There are no replays to export yet.", Toast.LENGTH_LONG).show();
                return;
            }
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
            pendingReplaysFolder = folder;
            try {
                startActivityForResult(intent, EXPORT_REPLAYS);
            } catch (android.content.ActivityNotFoundException e) {
                pendingReplaysFolder = null;
                Toast.makeText(this, "This device has no folder picker.", Toast.LENGTH_LONG).show();
            }
        });
    }

    private static File[] replayFiles(String folder) {
        File[] files = folder != null ? new File(folder).listFiles(
            (directory, name) -> name.endsWith(REPLAY_EXTENSION)) : null;
        return files != null ? files : new File[0];
    }

    /** the replays copied into the tree's folder, on a thread of their own; a Toast says how many */
    private void copyReplays(Uri tree, String folder) {
        ContentResolver resolver = getContentResolver();
        Uri parent = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree));
        Set<String> present = new HashSet<>();
        Uri children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree));
        try (Cursor cursor = resolver.query(children,
                new String[] { DocumentsContract.Document.COLUMN_DISPLAY_NAME }, null, null, null)) {
            while (cursor != null && cursor.moveToNext())
                present.add(cursor.getString(0));
        } catch (Exception e) {
            // (then every replay is copied: the provider names a second copy itself)
        }
        int copied = 0, skipped = 0;
        String failed = null;
        for (File replay : replayFiles(folder)) {
            if (present.contains(replay.getName())) {
                skipped++;
                continue;
            }
            try {
                Uri document = DocumentsContract.createDocument(resolver, parent, "application/octet-stream",
                    replay.getName());
                if (document == null)
                    throw new IOException("the folder refused " + replay.getName());
                try (InputStream input = new FileInputStream(replay);
                     OutputStream output = resolver.openOutputStream(document, "w")) {
                    if (output == null)
                        throw new IOException(replay.getName() + " cannot be written");
                    byte[] buffer = new byte[65536];
                    int size;
                    while ((size = input.read(buffer)) != -1)
                        output.write(buffer, 0, size);
                }
                copied++;
            } catch (Exception e) {
                failed = e.getMessage();
                break;
            }
        }
        String text = failed != null ? "The export stopped: " + failed
            : copied + (copied == 1 ? " replay exported" : " replays exported")
                + (skipped > 0 ? " (" + skipped + " already there)" : "") + ".";
        runOnUiThread(() -> Toast.makeText(this, text, Toast.LENGTH_LONG).show());
    }

    /**
     * The Theater's IMPORT (port/linux/game/menu_functions.c, through host_android_import_replays): the system's
     * file picker, several files at once, then each one chosen copied into folder, where the Theater's list finds
     * it. Called on the game's thread; no storage permission.
     */
    public void importReplays(String folder) {
        runOnUiThread(() -> {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            // (a replay has no type of its own: the providers call it anything)
            intent.setType("*/*");
            intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
            pendingImportFolder = folder;
            try {
                startActivityForResult(intent, IMPORT_REPLAYS);
            } catch (android.content.ActivityNotFoundException e) {
                pendingImportFolder = null;
                Toast.makeText(this, "This device has no file picker.", Toast.LENGTH_LONG).show();
            }
        });
    }

    /**
     * the name a replay chosen is kept under: its own, with anything but letters, digits, '-', '_' and '.' made
     * '_' (as the consoles' replay page takes them), ending .ocreplay, short enough for the Theater to list
     */
    private static String replayName(String chosen) {
        String name = chosen != null ? chosen.replaceAll("[^A-Za-z0-9._-]", "_") : "";
        if (name.endsWith(REPLAY_EXTENSION))
            name = name.substring(0, name.length() - REPLAY_EXTENSION.length());
        if (name.isEmpty())
            name = "imported";
        if (name.length() > REPLAY_NAME_LENGTH - REPLAY_EXTENSION.length())
            name = name.substring(0, REPLAY_NAME_LENGTH - REPLAY_EXTENSION.length());
        return name + REPLAY_EXTENSION;
    }

    /** the document's name as its provider shows it, else its path's last part */
    private String documentName(Uri document) {
        try (Cursor cursor = getContentResolver().query(document,
                new String[] { OpenableColumns.DISPLAY_NAME }, null, null, null)) {
            if (cursor != null && cursor.moveToFirst() && cursor.getString(0) != null)
                return cursor.getString(0);
        } catch (Exception e) {
            // (the path's, then)
        }
        return document.getLastPathSegment();
    }

    /**
     * the documents chosen copied into folder, on a thread of their own; a Toast says how many. Each goes to
     * <name>.part, which becomes the replay only once it is whole and begins as a replay does; a name the folder has
     * already is left as it is.
     */
    private void copyReplaysIn(List<Uri> documents, String folder) {
        File directory = new File(folder);
        directory.mkdirs();
        int copied = 0, skipped = 0, refused = 0;
        String failed = null;
        for (Uri document : documents) {
            String name = replayName(documentName(document));
            File replay = new File(directory, name);
            File part = new File(directory, name + ".part");
            if (replay.exists()) {
                skipped++;
                continue;
            }
            try (InputStream input = getContentResolver().openInputStream(document)) {
                if (input == null)
                    throw new IOException(name + " cannot be opened");
                byte[] buffer = new byte[65536];
                int first = 0, size;
                while (first < REPLAY_MAGIC.length && (size = input.read(buffer, first, REPLAY_MAGIC.length - first)) != -1)
                    first += size;
                boolean isReplay = first == REPLAY_MAGIC.length;
                for (int index = 0; isReplay && index < REPLAY_MAGIC.length; index++)
                    isReplay = buffer[index] == REPLAY_MAGIC[index];
                if (!isReplay) {
                    refused++;
                    continue;
                }
                long total = first;
                try (OutputStream output = new FileOutputStream(part)) {
                    output.write(buffer, 0, first);
                    while ((size = input.read(buffer)) != -1) {
                        total += size;
                        if (total > REPLAY_MAXIMUM_SIZE)
                            throw new IOException(name + " is too large");
                        output.write(buffer, 0, size);
                    }
                }
                if (!part.renameTo(replay))
                    throw new IOException(name + " cannot be kept");
                copied++;
            } catch (Exception e) {
                part.delete();
                failed = e.getMessage();
                break;
            }
        }
        String text = failed != null ? "The import stopped: " + failed
            : copied + (copied == 1 ? " replay imported" : " replays imported")
                + (skipped > 0 ? " (" + skipped + " already there)" : "")
                + (refused > 0 ? " (" + refused + (refused == 1 ? " is" : " are") + " not a replay)" : "") + ".";
        runOnUiThread(() -> Toast.makeText(this, text, Toast.LENGTH_LONG).show());
    }

    /**
     * The Mods screen's IMPORT (port/linux/game/menu_functions.c, through host_android_import_mods): the system's
     * file picker, several .zip files at once, then each one taken into a folder of its own name in folder, the mods
     * folder, where the Mods screen finds it. A mod's .zip holds its folder's files as they are laid out there
     * (mod.toml and tags/ at its top). Called on the game's thread; no storage permission.
     */
    public void importMods(String folder) {
        runOnUiThread(() -> {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            // (the providers call a .zip any of these)
            intent.setType("*/*");
            intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[] { "application/zip", "application/x-zip-compressed",
                "application/x-zip", "application/octet-stream" });
            intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
            pendingModsFolder = folder;
            try {
                startActivityForResult(intent, IMPORT_MODS);
            } catch (android.content.ActivityNotFoundException e) {
                pendingModsFolder = null;
                Toast.makeText(this, "This device has no file picker.", Toast.LENGTH_LONG).show();
            }
        });
    }

    /**
     * the folder a mod's .zip is taken into: the file's name without .zip, with what a folder's name cannot hold
     * (slashes, a leading dot, which the game passes over, and control characters) made '_', no longer than the game
     * takes
     */
    private static String modFolderName(String chosen) {
        String name = chosen != null ? chosen : "";
        if (name.toLowerCase(java.util.Locale.ROOT).endsWith(".zip"))
            name = name.substring(0, name.length() - 4);
        name = name.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "_").trim();
        if (name.startsWith("."))
            name = "_" + name.substring(1);
        if (name.isEmpty())
            name = "mod";
        while (name.getBytes(StandardCharsets.UTF_8).length > MOD_FOLDER_LENGTH)
            name = name.substring(0, name.offsetByCodePoints(name.length(), -1));
        return name;
    }

    /** a file, or a folder and everything in it, deleted */
    private static void deleteTree(File file) {
        File[] children = file.isDirectory() ? file.listFiles() : null;
        if (children != null) {
            for (File child : children)
                deleteTree(child);
        }
        file.delete();
    }

    /** a .zip's entries written into folder, which none may leave */
    private void unzipInto(Uri document, File folder) throws IOException {
        String top = folder.getCanonicalPath() + File.separator;
        int files = 0;
        try (InputStream input = getContentResolver().openInputStream(document)) {
            if (input == null)
                throw new IOException("it cannot be opened");
            try (ZipInputStream zip = new ZipInputStream(input)) {
                byte[] buffer = new byte[65536];
                ZipEntry entry;
                while ((entry = zip.getNextEntry()) != null) {
                    String path = entry.getName().replace('\\', '/');
                    File target = new File(folder, path);
                    if (!target.getCanonicalPath().startsWith(top) && !target.getCanonicalPath().equals(folder.getCanonicalPath()))
                        throw new IOException("it names a file outside its folder (" + entry.getName() + ")");
                    if (entry.isDirectory()) {
                        target.mkdirs();
                        continue;
                    }
                    if (++files > MOD_MAXIMUM_FILES)
                        throw new IOException("it has more than " + MOD_MAXIMUM_FILES + " files");
                    File parent = target.getParentFile();
                    if (parent != null)
                        parent.mkdirs();
                    try (OutputStream output = new FileOutputStream(target)) {
                        int size;
                        while ((size = zip.read(buffer)) != -1)
                            output.write(buffer, 0, size);
                    }
                }
            }
        }
        if (files == 0)
            throw new IOException("it is not a .zip file, or it is empty");
    }

    /**
     * the .zip files chosen taken into the mods folder, on a thread of their own; a Toast says how it went. Each is
     * written into .import/<name> (the game passes over a folder whose name begins with a dot), which becomes the mod
     * only once it is whole and has a mod.toml at its top; a name the folder has already is left as it is.
     */
    private void copyModsIn(List<Uri> documents, String folder) {
        File mods = new File(folder);
        File staging = new File(mods, ".import");
        int copied = 0, skipped = 0;
        String failed = null;
        runOnUiThread(() -> Toast.makeText(this, "Importing...", Toast.LENGTH_SHORT).show());
        for (Uri document : documents) {
            String name = modFolderName(documentName(document));
            File mod = new File(mods, name);
            File part = new File(staging, name);
            if (mod.exists()) {
                skipped++;
                continue;
            }
            try {
                deleteTree(part);
                if (!part.mkdirs())
                    throw new IOException("its folder cannot be made");
                unzipInto(document, part);
                if (!new File(part, "mod.toml").isFile())
                    throw new IOException("it has no mod.toml at its top");
                if (!part.renameTo(mod))
                    throw new IOException("its folder cannot be kept");
                copied++;
            } catch (Exception e) {
                failed = name + ": " + e.getMessage();
                break;
            } finally {
                deleteTree(part);
            }
        }
        deleteTree(staging);
        String text = failed != null ? (copied > 0 ? copied + " imported; then " : "") + "the import stopped: " + failed
            : copied + (copied == 1 ? " mod imported" : " mods imported")
                + (skipped > 0 ? " (" + skipped + " already there: delete " + (skipped == 1 ? "it" : "them")
                    + " first to import again)" : "") + ".";
        runOnUiThread(() -> Toast.makeText(this, text, Toast.LENGTH_LONG).show());
    }

    /** the documents a picker of several files chose */
    private static List<Uri> chosenDocuments(Intent data) {
        List<Uri> documents = new ArrayList<>();
        if (data.getClipData() != null) {
            for (int index = 0; index < data.getClipData().getItemCount(); index++)
                documents.add(data.getClipData().getItemAt(index).getUri());
        } else if (data.getData() != null) {
            documents.add(data.getData());
        }
        return documents;
    }

    @Override
    protected void onActivityResult(int request, int result, Intent data) {
        if (request == IMPORT_REPLAYS) {
            String folder = pendingImportFolder;
            pendingImportFolder = null;
            if (result != RESULT_OK || data == null || folder == null)
                return;
            List<Uri> documents = chosenDocuments(data);
            if (!documents.isEmpty())
                new Thread(() -> copyReplaysIn(documents, folder), "halo-import-replays").start();
            return;
        }
        if (request == IMPORT_MODS) {
            String folder = pendingModsFolder;
            pendingModsFolder = null;
            if (result != RESULT_OK || data == null || folder == null)
                return;
            List<Uri> documents = chosenDocuments(data);
            if (!documents.isEmpty())
                new Thread(() -> copyModsIn(documents, folder), "halo-import-mods").start();
            return;
        }
        if (request == EXPORT_REPLAYS) {
            String folder = pendingReplaysFolder;
            pendingReplaysFolder = null;
            if (result == RESULT_OK && data != null && data.getData() != null && folder != null) {
                Uri tree = data.getData();
                new Thread(() -> copyReplays(tree, folder), "halo-export-replays").start();
            }
            return;
        }
        if (request != EXPORT_LAYOUT && request != IMPORT_LAYOUT) {
            super.onActivityResult(request, result, data);
            return;
        }
        String exported = pendingLayoutExport;
        pendingLayoutExport = null;
        if (result != RESULT_OK || data == null || data.getData() == null)
            return;
        Uri document = data.getData();
        // (the file is read and written away from the UI thread)
        new Thread(() -> {
            try {
                if (request == EXPORT_LAYOUT)
                    writeLayoutFile(document, exported);
                else
                    readLayoutFile(document);
            } catch (Exception e) {
                runOnUiThread(() -> layoutFileError(e));
            }
        }, "halo-touch-layout-file").start();
    }

    private void writeLayoutFile(Uri document, String configuration) throws IOException {
        if (configuration == null)
            throw new IOException("the layout to export is gone");
        try (OutputStream output = getContentResolver().openOutputStream(document, "wt")) {
            if (output == null)
                throw new IOException("the file cannot be opened");
            output.write(configuration.getBytes(StandardCharsets.UTF_8));
        }
        runOnUiThread(() -> Toast.makeText(this, "The layout is exported.", Toast.LENGTH_SHORT).show());
    }

    private void readLayoutFile(Uri document) throws IOException {
        ByteArrayOutputStream contents = new ByteArrayOutputStream();
        try (InputStream input = getContentResolver().openInputStream(document)) {
            if (input == null)
                throw new IOException("the file cannot be opened");
            byte[] buffer = new byte[4096];
            int size;
            while ((size = input.read(buffer)) != -1) {
                if (contents.size() + size > 65536)
                    throw new IOException("the file is too large");
                contents.write(buffer, 0, size);
            }
        }
        String configuration = new String(contents.toByteArray(), StandardCharsets.UTF_8);
        // checked here, then applied to the overlay at once
        TouchLayout.importConfiguration(configuration);
        runOnUiThread(() -> {
            if (isFinishing() || isDestroyed() || touchControls == null)
                return;
            try {
                touchControls.importLayout(configuration);
                Toast.makeText(this, "The layout is imported and saved.", Toast.LENGTH_SHORT).show();
            } catch (IllegalArgumentException e) {
                layoutFileError(e);
            }
        });
    }

    private void layoutFileError(Exception error) {
        if (isFinishing() || isDestroyed())
            return;
        new AlertDialog.Builder(this)
            .setTitle("Touch layout file")
            .setMessage("The operation did not complete: " + error.getMessage())
            .setPositiveButton("OK", null)
            .show();
    }

    // ---------- the graphics gear

    /**
     * A small, faint gear at the top right, over the game at any time: it opens GraphicsActivity (OpenGL ES or
     * Vulkan, the Vulkan driver, a driver to import), whose change applies when the game is started again.
     * A touch on the gear only; the rest of the screen is the game's.
     */
    private void addGraphicsGear() {
        ImageView gear = new ImageView(this);
        int size = (int) (44 * getResources().getDisplayMetrics().density);
        int margin = (int) (6 * getResources().getDisplayMetrics().density);
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(size, size, Gravity.TOP | Gravity.END);

        params.setMargins(0, margin, margin, 0);
        gear.setImageResource(R.drawable.ic_gear);
        gear.setScaleType(ImageView.ScaleType.FIT_CENTER);
        gear.setPadding(size / 6, size / 6, size / 6, size / 6);
        gear.setAlpha(0.3f);
        gear.setContentDescription("Graphics");
        gear.setOnClickListener(v -> startActivity(new Intent(this, GraphicsActivity.class)
            .putExtra(GraphicsActivity.EXTRA_FROM_GAME, true)));
        addContentView(gear, params);
    }

    // ---------- full screen

    /**
     * The game is always full screen (sdl_platform.c asks for it), but SDL
     * starts the window as an ordinary one (SDLActivity.onCreate) and only
     * hides the system bars when the game asks, with the flags Android has
     * deprecated; it does not hide them again when something takes them
     * back: the soft keyboard, a dialog, the notification shade. So the bars
     * are hidden here, with the window insets controller (the flags below
     * Android 11), at start-up, whenever the window gets the focus back, on
     * resume and whenever the bars show up: swiping from an edge still shows
     * them for a moment.
     */
    private void hideSystemUi() {
        Window window = getWindow();

        if (window == null)
            return;
        View decor = window.getDecorView();

        // (SDLActivity.onSystemUiVisibilityChange hides them again as well)
        mFullscreenModeActive = true;
        window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        window.clearFlags(WindowManager.LayoutParams.FLAG_FORCE_NOT_FULLSCREEN);
        if (Build.VERSION.SDK_INT >= 28) {
            // the game draws under a display's notch or camera hole
            WindowManager.LayoutParams attributes = window.getAttributes();

            attributes.layoutInDisplayCutoutMode = Build.VERSION.SDK_INT >= 30
                ? WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
                : WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
            window.setAttributes(attributes);
        }
        if (Build.VERSION.SDK_INT >= 30) {
            WindowInsetsController controller = window.getInsetsController();

            if (controller != null) {
                controller.hide(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
                controller.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            }
        } else {
            decor.setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN |
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY |
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION |
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
        }
    }

    /**
     * Hides the bars now and again shortly after: SDL's own window style
     * message, posted by its onCreate, arrives after this activity's and
     * shows them.
     */
    private void hideSystemUiSoon() {
        handler.removeCallbacks(hideSystemBars);
        handler.post(hideSystemBars);
        handler.postDelayed(hideSystemBars, 400);
        handler.postDelayed(hideSystemBars, 1500);
    }

    @Override
    protected void onResume() {
        super.onResume();
        hideSystemUiSoon();
        if (touchControls != null && getWindow().getDecorView().hasWindowFocus())
            touchControls.startDeviceInput();
    }

    @Override
    protected void onPause() {
        if (touchControls != null)
            touchControls.stopDeviceInput();
        super.onPause();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        if (touchControls != null) {
            if (hasFocus)
                touchControls.startDeviceInput();
            else
                touchControls.stopDeviceInput();
        }
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus)
            hideSystemUiSoon();
    }

    @Override
    public void onSystemUiVisibilityChange(int visibility) {
        super.onSystemUiVisibilityChange(visibility);
        if ((visibility & View.SYSTEM_UI_FLAG_FULLSCREEN) == 0)
            hideSystemUiSoon();
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacks(hideSystemBars);
        if (touchControls != null)
            touchControls.stopDeviceInput();
        if (multicastLock != null && multicastLock.isHeld())
            multicastLock.release();
        multicastLock = null;
        super.onDestroy();
    }

    /**
     * keeps gestureInsets current; Android sends the insets again when the
     * activity rotates (after the game's native code has started on a phone
     * launched from portrait), so a single read at startup would keep the
     * portrait values; the listener hands the insets on so SDL's own
     * handling still sees them
     */
    private void trackGestureInsets() {
        getWindow().getDecorView().setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener() {
            @Override
            public WindowInsets onApplyWindowInsets(View view, WindowInsets insets) {
                gestureInsets = readGestureInsets(insets);
                return view.onApplyWindowInsets(insets);
            }
        });
    }

    @SuppressWarnings("deprecation") // getSystemGestureInsets is the only call on Android 10
    private static int[] readGestureInsets(WindowInsets insets) {
        Insets gesture;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
            gesture = insets.getInsets(WindowInsets.Type.systemGestures());
        else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
            gesture = insets.getSystemGestureInsets();
        else
            return new int[] { 0, 0, 0, 0 };
        return new int[] { gesture.left, gesture.top, gesture.right, gesture.bottom };
    }

    /**
     * the edges of the screen where Android keeps its gestures; in sticky
     * full screen the first swipe from an edge only shows the system bars,
     * and Android hands that swipe to the game as an ordinary finger, so
     * the game (port/linux/src/touch_input.c) must ignore touches that
     * begin there; returns a copy of {left, top, right, bottom} in pixels:
     * all 0 before Android 10 (which has no insets) and until the first
     * insets arrive. The game's native code calls this through JNI by name
     * and signature (host_main.c GetMethodID(...,
     * "getSystemGestureInsetsPixels", "()[I")), at every finger down, so
     * it must not be renamed, retyped or removed as unused
     */
    public int[] getSystemGestureInsetsPixels() {
        return gestureInsets.clone();
    }

    /**
     * Many phones drop the Wi-Fi's broadcast and multicast datagrams to
     * save power unless an app holds this: without it they would not see
     * system link games on the local network, nor be seen hosting one.
     */
    private void acquireMulticastLock() {
        try {
            WifiManager wifi = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            if (wifi == null)
                return;
            multicastLock = wifi.createMulticastLock("halo-system-link");
            multicastLock.setReferenceCounted(false);
            multicastLock.acquire();
        } catch (RuntimeException e) {
            // (no Wi-Fi, or not allowed: the local network may miss games)
            multicastLock = null;
        }
    }

    /**
     * Keeps the status and navigation bars out of the way. The manifest's
     * fullscreen theme does this on older releases, but Android 15 and later
     * draw apps edge to edge regardless, so the bars have to be asked away.
     * Swiping brings them back transiently and they leave again on their own.
     *
     * The two ways of asking are split, because {@code WindowInsetsController}
     * arrived in Android 11: on 9 and 10 the call is a hidden one, which the
     * system refuses (and logs), and it is not the older call that hides the
     * bars there.
     */
    private void hideSystemBars() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
            hideSystemBarsWithInsets();
        else
            hideSystemBarsLegacy();
    }

    /** Android 11 and later */
    @TargetApi(Build.VERSION_CODES.R)
    private void hideSystemBarsWithInsets() {
        WindowInsetsController controller = getWindow().getInsetsController();

        if (controller == null) {
            hideSystemBarsLegacy();
            return;
        }
        controller.hide(WindowInsets.Type.systemBars());
        controller.setSystemBarsBehavior(
                WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
    }

    /** Android 9 and 10, and anywhere the above has no controller */
    private void hideSystemBarsLegacy() {
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_FULLSCREEN |
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_LAYOUT_STABLE |
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
    }

    /**
     * The game draws a frame at every display refresh, between its 30 Hz
     * ticks (port/linux/game/render_interpolation.c); Android otherwise
     * often keeps an app at 60 Hz on a faster display.
     */
    private void preferHighestRefreshRate() {
        Display display = getWindowManager().getDefaultDisplay();
        Display.Mode current = display.getMode();
        Display.Mode best = current;

        for (Display.Mode mode : display.getSupportedModes()) {
            if (mode.getPhysicalWidth() == current.getPhysicalWidth() &&
                mode.getPhysicalHeight() == current.getPhysicalHeight() &&
                mode.getRefreshRate() > best.getRefreshRate()) {
                best = mode;
            }
        }
        WindowManager.LayoutParams attributes = getWindow().getAttributes();
        attributes.preferredDisplayModeId = best.getModeId();
        getWindow().setAttributes(attributes);
    }
}
