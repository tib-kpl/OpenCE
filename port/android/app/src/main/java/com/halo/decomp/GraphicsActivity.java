package com.halo.decomp;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.Button;
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
 * display.vk_driver = "vk_driver_custom.zip" names (port/android/host/host_vk_driver.c).
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
    private boolean driverBusy;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        dataRoot = getExternalFilesDir(null);
        config = dataRoot != null ? new File(dataRoot, "config.toml") : null;
        // (done before the choice is made: the launcher does it once, and a "gl" chosen here must stay)
        Updater.moveToVulkanDefault(dataRoot);
        Updater.migrateLegacyDriver(dataRoot);
        page = savedInstanceState != null ? savedInstanceState.getInt("settings_page", 1)
            : getIntent().getIntExtra("settings_page", 1);
        buildInterface();
    }

    private boolean isFrench() {
        java.util.Locale locale = getResources().getConfiguration().getLocales().get(0);
        return locale != null && "fr".equals(locale.getLanguage());
    }

    private String t(String french, String english) {
        return isFrench() ? french : english;
    }

    private HaloSettingsUi ui;
    private int page = 1;
    private final java.util.ArrayList<Button> driverChoices = new java.util.ArrayList<>();

    private void buildInterface() {
        buildPage(page);
    }

    private void buildPage(int selected) {
        page = selected;
        driverChoices.clear();
        boolean fromGame = getIntent().getBooleanExtra(EXTRA_FROM_GAME, false);
        ui = new HaloSettingsUi(this, t("PARAMÈTRES", "SETTINGS"),
            t("Halo: Combat Evolved · configuration Android", "Halo: Combat Evolved · Android setup"),
            fromGame ? t("B  Reprendre", "B  Resume") : t("Lancer le jeu", "Start game"), () -> {
                if (fromGame) finish();
                else {
                    startActivity(new Intent(this, LauncherActivity.class));
                    finish();
                }
            });
        current = ui.current;
        status = ui.status;
        ui.primary.setEnabled(fromGame || (dataRoot != null && new File(dataRoot, "maps/ui.map").isFile()));
        String[] names = {t("Jeu et données", "Game data"), t("Graphismes", "Graphics"),
            t("Pilotes", "Drivers"), t("Affichage", "Display")};
        for (int i = 0; i < names.length; i++) {
            final int index = i;
            ui.tab(i + 1, names[i], i == selected, () -> buildPage(index));
        }
        status.setText(t("A  Choisir     ← →  Ajuster     B  Retour", "A  Select     ← →  Adjust     B  Back"));
        if (selected == 0) {
            boolean ready = dataRoot != null && new File(dataRoot, "maps/ui.map").isFile();
            ui.heading(t("INSTALLATION", "INSTALLATION"), t("Jeu et données", "Game data"),
                ready ? t("Vos fichiers Halo sont prêts.", "Your Halo files are ready.")
                    : t("Installez les données de votre disque Xbox pour jouer.", "Install your Xbox disc data to play."));
            ui.row(t("Importer le jeu", "Import game data"), t("Image disque Xbox · .iso / .xiso", "Xbox disc image · .iso / .xiso"),
                false, "", () -> startActivity(new Intent(this, LauncherActivity.class)
                    .putExtra(LauncherActivity.EXTRA_IMPORT, true)
                    .putExtra(LauncherActivity.EXTRA_PICK_IMAGE, true)
                    .putExtra(EXTRA_FROM_GAME, fromGame)));
            ui.content.addView(ui.label(t("Les disques européens sont pris en charge, y compris les fichiers français. "
                + "Les cartes et mods supplémentaires s’importent depuis les menus du jeu.",
                "European discs are supported, including French game data. Additional maps and mods can be imported from the game's menus."), 13, HaloSettingsUi.MUTED));
        } else if (selected == 1) {
            ui.heading(t("RENDU", "RENDERING"), t("Graphismes", "Graphics"),
                t("Choisissez le moteur utilisé au prochain lancement.", "Choose the renderer used on the next launch."));
            ui.choice(t("Moteur graphique", "Renderer"), new String[]{"Vulkan", "OpenGL ES"},
                "gl".equals(setting("display", "renderer")) ? 1 : 0,
                index -> choose(index == 0 ? "vulkan" : "gl", setting("display", "vk_driver")));
            ui.content.addView(ui.label(t("Vulkan utilise le pilote de la console ou une archive Turnip. OpenGL ES offre une solution de repli si un pilote Vulkan pose problème.",
                "Vulkan uses the console's driver or a Turnip archive. OpenGL ES offers a fallback if a Vulkan driver causes problems."), 13, HaloSettingsUi.MUTED));
            ui.row(t("Choisir le pilote Vulkan", "Choose Vulkan driver"), t("Pilote système, Turnip ou archive importée", "System driver, Turnip or imported archive"),
                false, "", () -> buildPage(2));
        } else if (selected == 2) {
            ui.heading("VULKAN", t("Pilotes graphiques", "Graphics drivers"),
                t("Le changement s’applique au prochain lancement du jeu.", "Changes apply the next time the game starts."));
            driverRow(t("Pilote de la console", "Console driver"), t("Fourni par Android", "Provided by Android"), "");
            ui.row("Turnip · Adreno", t("Télécharger et sélectionner le pilote adapté", "Download and select the matching driver"),
                false, "", this::chooseTurnip);
            ui.row(t("Importer un pilote", "Import driver"), t("Archive adrenotools · .zip", "adrenotools archive · .zip"), false, "", () -> {
                Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("*/*");
                startActivityForResult(intent, PICK_DRIVER);
            });
            File folder = dataRoot != null ? new File(dataRoot, Updater.DRIVER_FOLDER) : null;
            File[] archives = folder != null ? folder.listFiles(file -> file.isFile() && file.getName().endsWith(".zip")) : null;
            if (archives != null) {
                java.util.Arrays.sort(archives, java.util.Comparator.comparing(File::getName));
                for (File archive : archives)
                    driverRow(archive.getName(), t("Pilote installé", "Installed driver"), archive.getName());
            }
        } else {
            ui.heading(t("IMAGE ET FLUIDITÉ", "IMAGE AND FRAME RATE"), t("Affichage", "Display"),
                t("Réglez la netteté et la synchronisation de l’image.", "Adjust image sharpness and frame synchronisation."));
            String[] limits = {"native", "2160", "1440", "1080", "720", "480"};
            String currentLimit = setting("display", "resolution_limit");
            int limitIndex = java.util.Arrays.asList(limits).indexOf(currentLimit);
            ui.choice(t("Résolution maximale", "Maximum resolution"), new String[]{t("Native", "Native"), "2160p", "1440p", "1080p", "720p", "480p"},
                limitIndex < 0 ? 1 : limitIndex, index -> writeDisplay("resolution_limit", "\"" + limits[index] + "\""));
            String[] scales = {"native", "75%", "50%", "25%", "original"};
            int scaleIndex = java.util.Arrays.asList(scales).indexOf(setting("display", "resolution_scaling"));
            ui.choice(t("Échelle de rendu", "Render scale"), new String[]{"100 %", "75 %", "50 %", "25 %", "Xbox · 480p"},
                Math.max(0, scaleIndex), index -> writeDisplay("resolution_scaling", "\"" + scales[index] + "\""));
            ui.choice(t("Synchronisation verticale", "Vertical sync"), new String[]{t("Activée", "On"), t("Désactivée", "Off")},
                "false".equals(setting("display", "vsync")) ? 1 : 0, index -> writeDisplay("vsync", index == 0 ? "true" : "false"));
            ui.content.addView(ui.label(t("Une résolution plus basse peut améliorer la fluidité. Les réglages sont enregistrés pour le prochain lancement.",
                "A lower resolution may improve frame rate. Settings are saved for the next launch."), 12, HaloSettingsUi.MUTED));
        }
        showCurrent();
        java.util.ArrayList<android.view.View> focusables = ui.content.getFocusables(android.view.View.FOCUS_FORWARD);
        if (!focusables.isEmpty()) focusables.get(0).requestFocus();
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        state.putInt("settings_page", page);
        super.onSaveInstanceState(state);
    }

    private boolean writeDisplay(String key, String value) {
        if (config == null || !Updater.writeSetting(config, "display", key, value)) {
            status.setText(t("Impossible d’enregistrer le réglage.", "The setting could not be saved."));
            return false;
        }
        status.setText(t("Enregistré · prochain lancement du jeu", "Saved · next game launch"));
        return true;
    }

    private void driverRow(String title, String detail, String driver) {
        Button row = ui.row(title, detail, driver.equals(setting("display", "vk_driver")), "", () -> choose("vulkan", driver));
        row.setTag(driver);
        driverChoices.add(row);
    }

    @Override public boolean dispatchKeyEvent(android.view.KeyEvent event) {
        return (ui != null && ui.key(event)) || super.dispatchKeyEvent(event);
    }

    @Override public boolean onGenericMotionEvent(android.view.MotionEvent event) {
        return (ui != null && ui.motion(event)) || super.onGenericMotionEvent(event);
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
            String which = driver.isEmpty() ? t("pilote du téléphone", "the phone's own driver") : driver;
            text = t("Actuel : Vulkan, ", "Now: Vulkan, ") + which;
        }
        current.setText(text);
        for (Button row : driverChoices)
            row.setSelected(!renderer.equals("gl") && driver.equals(row.getTag()));
    }

    private void chooseTurnip() {
        if (driverBusy) return;
        driverBusy = true;
        status.setText(t("Préparation de Turnip...", "Preparing Turnip..."));
        new Thread(() -> {
            try {
                if (dataRoot == null)
                    throw new java.io.IOException(t("pas de dossier de données", "no data folder"));
                Updater.Choice choice = Updater.driverChoose(dataRoot);
                if (choice == null || choice.driver == null)
                    throw new java.io.IOException(t("aucun pilote Turnip compatible", "no compatible Turnip driver"));
                if (!Updater.driverPresent(dataRoot, choice.driver))
                    Updater.driverDownload(dataRoot, choice.driver, choice.gpu, (received, total) -> handler.post(() -> {
                        if (!isFinishing() && !isDestroyed())
                            status.setText(t("Téléchargement Turnip : ", "Downloading Turnip: ") + (received >> 20)
                                + (total > 0 ? " / " + (total >> 20) : "") + t(" Mo", " MB"));
                    }));
                handler.post(() -> {
                    if (!isFinishing() && !isDestroyed() && choose("vulkan", choice.driver.name)) refreshDrivers();
                });
            } catch (Exception exception) {
                handler.post(() -> {
                    if (!isFinishing() && !isDestroyed())
                        status.setText(t("Turnip indisponible : ", "Turnip unavailable: ") + exception.getMessage());
                });
            } finally {
                handler.post(() -> driverBusy = false);
            }
        }, "Turnip selection").start();
    }

    private void refreshDrivers() {
        if (page != 2) return;
        CharSequence message = status.getText();
        buildPage(page);
        status.setText(message);
    }

    /** renderer "gl" or "vulkan", and for Vulkan display.vk_driver: "" (the phone's), an archive file name */
    private boolean choose(String renderer, String driver) {
        if (config == null || !Updater.writeSetting(config, "display", "renderer", "\"" + renderer + "\"")
            || (renderer.equals("vulkan") && !Updater.writeSetting(config, "display", "vk_driver", "\"" + driver + "\""))) {
            status.setText(t("Impossible d'écrire config.toml.", "config.toml could not be written."));
            return false;
        }
        status.setText(t("Enregistré. Il s'applique au prochain démarrage du jeu.",
            "Saved. It applies the next time the game starts."));
        showCurrent();
        return true;
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
                    choose("vulkan", CUSTOM_ARCHIVE);
                    status.setText(t("Pilote importé : ", "Driver imported: ") + name
                        + t("\nIl s'applique au prochain démarrage du jeu.",
                            "\nIt applies the next time the game starts."));
                    refreshDrivers();
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
        File drivers = new File(dataRoot, Updater.DRIVER_FOLDER);
        if (!drivers.isDirectory() && !drivers.mkdirs())
            throw new java.io.IOException(t("dossier de pilotes inaccessible", "driver folder unavailable"));
        File partial = new File(drivers, CUSTOM_ARCHIVE + ".partial");

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
            File target = new File(drivers, CUSTOM_ARCHIVE);

            target.delete();
            if (!partial.renameTo(target))
                throw new java.io.IOException(t("copie impossible", "it could not be saved"));
            return name;
        } finally {
            partial.delete();
        }
    }
}
