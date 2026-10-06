package com.halo.decomp;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * What the game draws with, before it starts: OpenGL ES or Vulkan, and for
 * Vulkan the phone's own driver, the Turnip build downloaded for an Adreno,
 * or a driver the player imports (an adrenotools archive: a zip with a
 * meta.json and the driver's library, as Turnip releases are made).
 *
 * It is the "Halo Graphics" icon (and the main icon's "Graphics" shortcut, where the launcher shows one), for a game that does not
 * start with the current choice (the same choices are in Video Setup, in the
 * game). It writes display.renderer and display.vk_driver in config.toml
 * (port/linux/src/port_config.c); the game reads them when it starts. An
 * imported archive is copied to the data folder as vk_driver_custom.zip, which
 * display.vk_driver = "custom" names (port/android/host/host_vk_driver.c).
 *
 * Every button is a plain Button, for a gamepad.
 */
public class GraphicsActivity extends Activity {
    private static final int PICK_DRIVER = 1;
    /** the archive's size limit in the host (host_vk_driver.c ARCHIVE_MAXIMUM) */
    private static final long ARCHIVE_MAXIMUM = 256L << 20;
    /** set by the gear over the game (HaloActivity) */
    static final String EXTRA_FROM_GAME = "com.halo.decomp.FROM_GAME";
    static final String CUSTOM_ARCHIVE = "vk_driver_custom.zip";

    private File dataRoot;
    private File config;
    private TextView current;
    private TextView status;
    private final Handler handler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        dataRoot = getExternalFilesDir(null);
        config = dataRoot != null ? new File(dataRoot, "config.toml") : null;
        // (done before the choice is made: the launcher does it once, and a "gl" chosen here must stay)
        Updater.moveToVulkanDefault(dataRoot);
        buildInterface();
    }

    private boolean isFrench() {
        java.util.Locale locale = getResources().getConfiguration().getLocales().get(0);
        return locale != null && "fr".equals(locale.getLanguage());
    }

    private String t(String french, String english) {
        return isFrench() ? french : english;
    }

    private int dp(float value) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value,
            getResources().getDisplayMetrics());
    }

    private Button button(LinearLayout layout, String text, Runnable action) {
        Button button = new Button(this);
        button.setText(text);
        button.setAllCaps(false);
        button.setOnClickListener(v -> action.run());
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(420),
            LinearLayout.LayoutParams.WRAP_CONTENT);
        params.topMargin = dp(6);
        layout.addView(button, params);
        return button;
    }

    private void buildInterface() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setGravity(Gravity.CENTER_HORIZONTAL);
        layout.setPadding(dp(32), dp(20), dp(32), dp(20));
        layout.setBackgroundColor(Color.rgb(12, 16, 20));

        TextView title = new TextView(this);
        title.setText(t("Graphiques", "Graphics"));
        title.setTextColor(Color.WHITE);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 24);
        title.setGravity(Gravity.CENTER);
        layout.addView(title);

        TextView help = new TextView(this);
        help.setText(t("Choisissez avec quoi le jeu dessine. Le changement s'applique quand le jeu est relancé (fermez-le puis rouvrez-le). "
            + "Ces mêmes choix sont dans les réglages vidéo du jeu.",
            "Choose what the game draws with. It applies when the game is started again (close it, then open it). "
            + "The same choices are in the game's Video Setup."));
        help.setTextColor(Color.rgb(200, 205, 210));
        help.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        help.setGravity(Gravity.CENTER);
        help.setPadding(0, dp(8), 0, dp(8));
        layout.addView(help);

        current = new TextView(this);
        current.setTextColor(Color.rgb(255, 200, 120));
        current.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        current.setGravity(Gravity.CENTER);
        current.setPadding(0, 0, 0, dp(8));
        layout.addView(current);

        Button first = button(layout, t("OpenGL ES (sans Vulkan)", "OpenGL ES (no Vulkan)"),
            () -> choose("gl", ""));
        button(layout, t("Vulkan, pilote du téléphone", "Vulkan, the phone's own driver"),
            () -> choose("vulkan", ""));
        button(layout, t("Vulkan, Turnip (GPU Adreno)", "Vulkan, Turnip (Adreno GPU)"),
            () -> choose("vulkan", "auto"));
        button(layout, t("Vulkan, importer un pilote (zip)...", "Vulkan, import a driver (zip)..."), () -> {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("*/*");
            startActivityForResult(intent, PICK_DRIVER);
        });
        if (getIntent().getBooleanExtra(EXTRA_FROM_GAME, false)) {
            // opened from the gear over the game: back to it; the change applies when it is started again
            button(layout, t("Retour au jeu", "Back to the game"), this::finish);
        } else {
            button(layout, t("Lancer le jeu", "Start the game"), () -> {
                startActivity(new Intent(this, LauncherActivity.class));
                finish();
            });
        }

        status = new TextView(this);
        status.setTextColor(Color.rgb(160, 200, 160));
        status.setGravity(Gravity.CENTER);
        status.setPadding(0, dp(10), 0, 0);
        layout.addView(status);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(Color.rgb(12, 16, 20));
        scroll.addView(layout);
        setContentView(scroll);
        first.requestFocus();
        showCurrent();
    }

    private String setting(String section, String key) {
        String value = config != null && config.isFile() ? Updater.readSetting(config, section, key) : null;

        return value == null ? "" : value.replace("\"", "").trim();
    }

    private void showCurrent() {
        String renderer = setting("display", "renderer");
        String driver = setting("display", "vk_driver");
        String text;

        if (renderer.equals("gl")) {
            text = t("Actuel : OpenGL ES", "Now: OpenGL ES");
        } else {
            String which = driver.equals("auto") ? "Turnip"
                : driver.equals("custom") ? t("pilote importé", "imported driver")
                : t("pilote du téléphone", "the phone's own driver");
            text = t("Actuel : Vulkan, ", "Now: Vulkan, ") + which;
            if (driver.equals("custom") && !new File(dataRoot, CUSTOM_ARCHIVE).isFile())
                text += t(" (aucun pilote importé : celui du téléphone sera utilisé)",
                    " (none imported: the phone's own is used)");
        }
        current.setText(text);
    }

    /** renderer "gl" or "vulkan", and for Vulkan display.vk_driver: "" (the phone's), "auto" (Turnip) or "custom" */
    private void choose(String renderer, String driver) {
        if (config == null || !Updater.writeSetting(config, "display", "renderer", "\"" + renderer + "\"")
            || (renderer.equals("vulkan") && !Updater.writeSetting(config, "display", "vk_driver", "\"" + driver + "\""))) {
            status.setText(t("Impossible d'écrire config.toml.", "config.toml could not be written."));
            return;
        }
        status.setText(t("Enregistré. Il s'applique au prochain démarrage du jeu.",
            "Saved. It applies the next time the game starts."));
        showCurrent();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != PICK_DRIVER || resultCode != RESULT_OK || data == null || data.getData() == null)
            return;
        Uri archive = data.getData();

        status.setText(t("Lecture du pilote...", "Reading the driver..."));
        new Thread(() -> {
            try {
                String name = importDriver(archive);

                handler.post(() -> {
                    choose("vulkan", "custom");
                    status.setText(t("Pilote importé : ", "Driver imported: ") + name
                        + t("\nIl s'applique au prochain démarrage du jeu.",
                            "\nIt applies the next time the game starts."));
                });
            } catch (Exception exception) {
                handler.post(() -> status.setText(t("Import impossible : ", "Import failed: ")
                    + exception.getMessage()));
            }
        }, "driver import").start();
    }

    /**
     * Copies the chosen zip to the data folder as vk_driver_custom.zip after
     * checking it is an adrenotools archive (a meta.json whose libraryName is
     * in it); returns the driver's name. The old archive stays if it is not.
     */
    private String importDriver(Uri source) throws Exception {
        if (dataRoot == null)
            throw new java.io.IOException(t("pas de dossier de données", "no data folder"));
        File partial = new File(dataRoot, CUSTOM_ARCHIVE + ".partial");

        try {
            long total = 0;

            try (InputStream in = getContentResolver().openInputStream(source);
                 OutputStream out = new FileOutputStream(partial)) {
                if (in == null)
                    throw new java.io.IOException(t("fichier illisible", "the file cannot be read"));
                byte[] buffer = new byte[65536];
                int count;

                while ((count = in.read(buffer)) > 0) {
                    total += count;
                    if (total > ARCHIVE_MAXIMUM)
                        throw new java.io.IOException(t("archive trop grande (256 Mo au plus)",
                            "the archive is too large (256 MB at most)"));
                    out.write(buffer, 0, count);
                }
            }
            String name;

            try (ZipFile zip = new ZipFile(partial)) {
                ZipEntry meta = zip.getEntry("meta.json");

                if (meta == null)
                    throw new java.io.IOException(t("pas un pilote adrenotools : meta.json manque",
                        "not an adrenotools driver: meta.json is missing"));
                if (meta.getSize() < 0 || meta.getSize() > 65536)
                    throw new java.io.IOException("meta.json");
                java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();

                try (InputStream in = zip.getInputStream(meta)) {
                    byte[] buffer = new byte[4096];
                    int count;

                    while ((count = in.read(buffer)) > 0)
                        bytes.write(buffer, 0, count);
                }
                JSONObject json = new JSONObject(bytes.toString("UTF-8"));
                String library = json.optString("libraryName", "");

                if (library.isEmpty() || library.contains("/") || library.contains("\\") || library.contains(".."))
                    throw new java.io.IOException(t("meta.json ne nomme pas la bibliothèque du pilote",
                        "meta.json does not name the driver's library"));
                if (zip.getEntry(library) == null)
                    throw new java.io.IOException(library + t(" manque dans l'archive", " is not in the archive"));
                name = json.optString("name", library);
            }
            File target = new File(dataRoot, CUSTOM_ARCHIVE);

            target.delete();
            if (!partial.renameTo(target))
                throw new java.io.IOException(t("copie impossible", "it could not be saved"));
            return name;
        } finally {
            partial.delete();
        }
    }
}
