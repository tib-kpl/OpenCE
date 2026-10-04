package com.halo.decomp;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.widget.ScrollView;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.nio.channels.FileChannel;

/**
 * Starts the game once its data is in place.
 *
 * The game reads the Xbox game data (the folder holding maps/) from the
 * app's external files directory, /sdcard/Android/data/com.halo.decomp/files.
 * If it is missing, this screen lets the player pick an Xbox disc image of
 * the game (.xiso or .iso, any version) with the system file picker, and
 * copies its maps folder there (XisoExtractor), as the desktop games do; or
 * they can push the maps folder with adb.
 */
public class LauncherActivity extends Activity {
    private static final int PICK_IMAGE = 1;
    /** set by the "Import disc image" launcher shortcut: import even with data in place */
    static final String EXTRA_IMPORT = "com.halo.decomp.IMPORT";
    private boolean importRequested;

    private File dataRoot;
    private TextView status;
    private ProgressBar progress;
    private Button pick;
    private final Handler handler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        HaloActivity.makeDumpable();
        dataRoot = getExternalFilesDir(null);
        // created by the app, so that files pushed into it with adb stay
        // readable (a directory adb creates there belongs to the shell user)
        if (dataRoot != null)
            new File(dataRoot, "maps").mkdirs();
        passOnHardwareId();
        passOnInvite(getIntent());
        importRequested = getIntent() != null && getIntent().getBooleanExtra(EXTRA_IMPORT, false);
        if (!importRequested && showFailedStart())
            return;
        if (haveData() && !importRequested) {
            startGame();
            return;
        }
        buildInterface();
    }

    /**
     * An internet play invite link the app was opened with: the game
     * (port/linux/src/p2p.c) picks it up from join_link.txt, whether it is
     * starting now or already running.
     */
    private void passOnInvite(Intent intent) {
        if (intent == null || !Intent.ACTION_VIEW.equals(intent.getAction()) || intent.getData() == null
            || dataRoot == null)
            return;
        // written whole under another name, then renamed: the game never
        // reads it half written
        File partial = new File(dataRoot, "join_link.txt.tmp");
        try (OutputStream out = new FileOutputStream(partial)) {
            out.write(intent.getData().toString().getBytes("UTF-8"));
        } catch (java.io.IOException e) {
            // the link is lost; the player can copy it instead
            partial.delete();
            return;
        }
        if (!partial.renameTo(new File(dataRoot, "join_link.txt")))
            partial.delete();
    }

    /**
     * This device's ANDROID_ID (the app's own: one per app signing key and
     * user, until a factory reset), which native code cannot read: the game
     * (port/linux/src/p2p.c) hashes it from hardware_id.txt into the
     * hardware id a host it joins is told.
     */
    private void passOnHardwareId() {
        String id;

        if (dataRoot == null)
            return;
        try {
            id = Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID);
        } catch (RuntimeException e) {
            return;
        }
        if (id == null || id.isEmpty())
            return;
        File partial = new File(dataRoot, "hardware_id.txt.tmp");
        try (OutputStream out = new FileOutputStream(partial)) {
            out.write(id.getBytes("UTF-8"));
        } catch (java.io.IOException e) {
            partial.delete();
            return;
        }
        if (!partial.renameTo(new File(dataRoot, "hardware_id.txt")))
            partial.delete();
    }

    private boolean haveData() {
        return dataRoot != null && new File(dataRoot, "maps/ui.map").isFile();
    }

    /** the app's language (Android 13+ per-app setting, else the device's) */
    private boolean isFrench() {
        java.util.Locale locale = getResources().getConfiguration().getLocales().get(0);
        return locale != null && "fr".equals(locale.getLanguage());
    }

    private String t(String french, String english) {
        return isFrench() ? french : english;
    }

    /**
     * When the game stopped at start-up (host_fatal), the reason is only in
     * the log, tag "halo" (for example "cannot load the game image"). An app
     * reads its own log lines, also those of its previous process: the
     * warnings and errors of a failed start not reported yet are shown here
     * and written to halo_log.txt next to the maps folder.
     */
    private boolean showFailedStart() {
        java.util.List<String> lines = new java.util.ArrayList<>();
        try {
            Process logcat = new ProcessBuilder("logcat", "-d", "-v", "epoch", "-s", "halo:V")
                .redirectErrorStream(true).start();
            try (java.io.BufferedReader reader = new java.io.BufferedReader(
                new java.io.InputStreamReader(logcat.getInputStream(), "UTF-8"))) {
                String line;
                while ((line = reader.readLine()) != null)
                    lines.add(line);
            }
        } catch (Exception e) {
            return false;
        }
        /* "  1727700000.123  1234  5678 F halo    : message" */
        String fatalPid = null;
        double fatalTime = 0;
        for (String line : lines) {
            String[] fields = line.trim().split("\\s+", 5);
            if (fields.length == 5 && fields[3].equals("F")) {
                try {
                    fatalTime = Double.parseDouble(fields[0]);
                    fatalPid = fields[1];
                } catch (NumberFormatException e) {
                    // not a log line
                }
            }
        }
        SharedPreferences preferences = getSharedPreferences("launcher", MODE_PRIVATE);
        if (fatalPid == null || fatalTime <= Double.parseDouble(preferences.getString("reported_failure", "0")))
            return false;
        preferences.edit().putString("reported_failure", Double.toString(fatalTime)).apply();

        StringBuilder report = new StringBuilder();
        for (String line : lines) {
            String[] fields = line.trim().split("\\s+", 5);
            if (fields.length == 5 && fields[1].equals(fatalPid) && "WEF".contains(fields[3]))
                report.append(fields[3]).append(' ').append(fields[4].replaceFirst("^halo\\s*:\\s*", ""))
                    .append('\n');
        }
        if (dataRoot != null) {
            try (OutputStream out = new FileOutputStream(new File(dataRoot, "halo_log.txt"))) {
                for (String line : lines)
                    out.write((line + "\n").getBytes("UTF-8"));
            } catch (java.io.IOException e) {
                // shown on screen anyway
            }
        }

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(32), dp(16), dp(32), dp(16));
        layout.setBackgroundColor(Color.rgb(12, 16, 20));
        TextView title = new TextView(this);
        title.setText(t("Le jeu s'est arrêté au démarrage", "The game stopped at start-up"));
        title.setTextColor(Color.WHITE);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
        layout.addView(title);
        TextView where = new TextView(this);
        where.setText(t("Journal complet : ", "Full log: ")
            + (dataRoot != null ? new File(dataRoot, "halo_log.txt").getAbsolutePath() : "halo_log.txt"));
        where.setTextColor(Color.rgb(200, 205, 210));
        where.setPadding(0, dp(8), 0, dp(8));
        layout.addView(where);
        Button again = new Button(this);
        again.setText(t("Relancer le jeu", "Start the game again"));
        again.setOnClickListener(v -> {
            if (haveData())
                startGame();
            else
                buildInterface();
        });
        layout.addView(again, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT));
        TextView details = new TextView(this);
        details.setText(report.length() > 0 ? report.toString() : t("(aucun détail)", "(no details)"));
        details.setTextColor(Color.rgb(230, 180, 160));
        details.setTypeface(Typeface.MONOSPACE);
        details.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        details.setTextIsSelectable(true);
        details.setPadding(0, dp(12), 0, 0);
        layout.addView(details);
        ScrollView scroll = new ScrollView(this);
        scroll.addView(layout);
        setContentView(scroll);
        again.requestFocus();
        return true;
    }

    private static String languageName(String language) {
        switch (language) {
            case "": return "English (maps)";
            case "fr": return "Français (maps_fr)";
            case "de": return "Deutsch (maps_de)";
            case "es": return "Español (maps_es)";
            case "it": return "Italiano (maps_it)";
            default: return "maps_" + language;
        }
    }

    /** several languages on the image: the player picks one */
    private void chooseLanguage(Uri image, java.util.List<String> languages) {
        String[] names = new String[languages.size()];
        int preferred = 0;
        for (int index = 0; index < names.length; index++) {
            names[index] = languageName(languages.get(index));
            if (languages.get(index).equals(isFrench() ? "fr" : ""))
                preferred = index;
        }
        final int[] chosen = { preferred };
        new AlertDialog.Builder(this)
            .setTitle(t("Langue du jeu", "Game language"))
            .setSingleChoiceItems(names, preferred, (dialog, which) -> chosen[0] = which)
            .setPositiveButton(t("Installer", "Install"), (dialog, which) -> {
                status.setText(t("Lecture de l'image disque...", "Reading the disc image..."));
                String language = languages.get(chosen[0]);
                new Thread(() -> importImage(image, language)).start();
            })
            .setNegativeButton(t("Annuler", "Cancel"), (dialog, which) -> fail(t("Import annulé.", "Import cancelled.")))
            .setOnCancelListener(dialog -> fail(t("Import annulé.", "Import cancelled.")))
            .show();
    }

    /**
     * Sets game.language in config.toml (port/linux/src/port_config.c) to the
     * language of the maps installed: the game then also picks its
     * translated movies. The game adds the other settings to the file.
     */
    private void setGameLanguage(String language) {
        File config = new File(dataRoot, "config.toml");
        String setting = "language = \"" + language + "\"";
        java.util.List<String> lines = new java.util.ArrayList<>();
        try {
            if (config.isFile())
                lines = new java.util.ArrayList<>(java.nio.file.Files.readAllLines(config.toPath(),
                    java.nio.charset.StandardCharsets.UTF_8));
            int header = -1, existing = -1;
            boolean inGame = false;
            for (int index = 0; index < lines.size(); index++) {
                String line = lines.get(index).trim();
                if (line.startsWith("[")) {
                    inGame = line.equals("[game]");
                    if (inGame)
                        header = index;
                } else if (inGame && line.matches("language\\s*=.*")) {
                    existing = index;
                }
            }
            if (existing >= 0) {
                lines.set(existing, setting);
            } else if (header >= 0) {
                lines.add(header + 1, setting);
            } else {
                lines.add("");
                lines.add("[game]");
                lines.add(setting);
            }
            java.nio.file.Files.write(config.toPath(), lines, java.nio.charset.StandardCharsets.UTF_8);
        } catch (java.io.IOException e) {
            // the game keeps its current language
        }
    }

    private void startGame() {
        // the menus in French when the maps are (MenuTranslation)
        MenuTranslation.sync(this, dataRoot);
        startActivity(new Intent(this, HaloActivity.class));
        finish();
    }

    private int dp(float value) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value,
            getResources().getDisplayMetrics());
    }

    private void buildInterface() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setGravity(Gravity.CENTER);
        layout.setPadding(dp(48), dp(24), dp(48), dp(24));
        layout.setBackgroundColor(Color.rgb(12, 16, 20));

        TextView title = new TextView(this);
        title.setText(importRequested && haveData()
            ? t("Importer une image disque", "Import a disc image")
            : t("Halo a besoin des données du jeu", "Halo needs its game data"));
        title.setTextColor(Color.WHITE);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 24);
        title.setGravity(Gravity.CENTER);
        layout.addView(title);

        TextView message = new TextView(this);
        String adb = "adb push <folder with maps>/. " + (dataRoot != null ? dataRoot.getAbsolutePath() : "") + "/";
        message.setText(t("Choisissez une image disque Xbox de Halo: Combat Evolved (fichier .iso ou .xiso, "
            + "toute version) sur cet appareil. Son dossier maps est copié dans le stockage de l'app (environ "
            + "1,8 Go) ; vous pourrez ensuite supprimer l'image. Une image européenne avec un dossier maps_fr "
            + "installe le jeu en français.\n\n"
            + "Vous pouvez aussi copier un dossier maps depuis un ordinateur :\n" + adb,
            "Choose an Xbox disc image of Halo: Combat Evolved (an .iso or .xiso file, any "
            + "version) on this device. Its maps folder is copied into the app's storage (about 1.8 GB), "
            + "and you can delete the image afterwards. A European image with a maps_fr folder installs "
            + "the game in French when the app's language is French.\n\n"
            + "You can also copy a maps folder from a computer:\n" + adb));
        message.setTextColor(Color.rgb(200, 205, 210));
        message.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        message.setGravity(Gravity.CENTER);
        message.setPadding(0, dp(16), 0, dp(16));
        layout.addView(message);

        pick = new Button(this);
        pick.setText(t("Choisir l'image disque", "Choose disc image"));
        pick.setOnClickListener(v -> {
            // (disc images have no MIME type of their own: any file, checked
            // when it is read)
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("*/*");
            startActivityForResult(intent, PICK_IMAGE);
        });
        layout.addView(pick, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT));

        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setMax(1000);
        progress.setVisibility(View.GONE);
        LinearLayout.LayoutParams progressLayout = new LinearLayout.LayoutParams(dp(480),
            LinearLayout.LayoutParams.WRAP_CONTENT);
        progressLayout.topMargin = dp(16);
        layout.addView(progress, progressLayout);

        status = new TextView(this);
        status.setTextColor(Color.rgb(160, 200, 160));
        status.setGravity(Gravity.CENTER);
        status.setPadding(0, dp(8), 0, 0);
        layout.addView(status);

        setContentView(layout);
        pick.requestFocus();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // data pushed with adb while this screen was open
        if (pick != null && pick.isEnabled() && haveData() && !importRequested)
            startGame();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != PICK_IMAGE || resultCode != RESULT_OK || data == null || data.getData() == null)
            return;
        Uri image = data.getData();
        pick.setEnabled(false);
        progress.setVisibility(View.VISIBLE);
        status.setText(t("Lecture de l'image disque...", "Reading the disc image..."));
        new Thread(() -> {
            java.util.List<String> languages;
            try (ParcelFileDescriptor descriptor = getContentResolver().openFileDescriptor(image, "r");
                 FileInputStream in = new FileInputStream(descriptor.getFileDescriptor())) {
                languages = XisoExtractor.languages(in.getChannel());
            } catch (XisoExtractor.ExtractException exception) {
                fail(exception.getMessage());
                return;
            } catch (Exception exception) {
                fail(t("Lecture impossible : ", "Reading failed: ") + exception.getMessage());
                return;
            }
            if (languages.size() > 1)
                handler.post(() -> chooseLanguage(image, languages));
            else
                importImage(image, languages.isEmpty() ? null : languages.get(0));
        }).start();
    }

    private void report(String text, int permille) {
        handler.post(() -> {
            status.setText(text);
            if (permille >= 0)
                progress.setProgress(permille);
        });
    }

    private void fail(String text) {
        handler.post(() -> {
            status.setText(text);
            progress.setVisibility(View.GONE);
            pick.setEnabled(true);
            pick.requestFocus();
        });
    }

    /** language: "" for maps (English), "fr" for maps_fr..., null to let the extractor choose */
    private void importImage(Uri image, String language) {
        try (ParcelFileDescriptor descriptor = getContentResolver().openFileDescriptor(image, "r")) {
            if (descriptor == null)
                throw new java.io.IOException("the file could not be opened");
            try (FileInputStream in = new FileInputStream(descriptor.getFileDescriptor())) {
                FileChannel channel = in.getChannel();

                String installed = XisoExtractor.extractMaps(channel, dataRoot, language,
                    (file, done, total) -> report(t("Extraction de maps/", "Extracting maps/") + file + " ("
                        + (done >> 20) + t(" sur ", " of ") + (total >> 20) + t(" Mo)", " MB)"),
                        total > 0 ? (int) (done * 1000 / total) : 0));
                setGameLanguage(installed);
            }
            handler.post(() -> {
                if (haveData()) {
                    startGame();
                } else {
                    fail("The extraction finished but maps/ui.map is missing.");
                }
            });
        } catch (XisoExtractor.ExtractException exception) {
            fail(exception.getMessage());
        } catch (Exception exception) {
            fail("Extracting failed: " + exception.getMessage());
        }
    }
}
