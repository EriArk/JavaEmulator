package io.github.eriark.abyssme.symbian;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.hardware.input.InputManager;
import android.os.Bundle;
import android.net.Uri;
import android.graphics.Color;
import android.text.InputFilter;
import android.view.*;
import android.widget.*;
import com.github.eka2l1.emu.Emulator;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import ru.playsoftware.j2meloader.input.GameMenuDialog;
import ru.playsoftware.j2meloader.R;
import ru.playsoftware.j2meloader.BuildConfig;

/** Opt-in environment/session prototype. It does not expose arbitrary host paths. */
public final class SymbianActivity extends Activity implements Emulator.Host, InputManager.InputDeviceListener {
    @Override protected void attachBaseContext(android.content.Context base) {
        super.attachBaseContext(ru.playsoftware.j2meloader.util.AppearanceContext.wrap(base));
    }

    private static final ExecutorService worker = Executors.newSingleThreadExecutor();
    // Accessed only on worker: a destroyed activity must not stop its successor's session.
    private static SymbianActivity sessionOwner;
    private final Map<Integer, Integer> held = new HashMap<>();
    private LinearLayout page;
    private TextView status;
    private ProgressBar progressBar;
    private TextureView surface;
    private volatile boolean running;
    private volatile long sessionGeneration;
    private boolean attached, busy, returnRequested, menuOpen;
    private boolean resumed;
    private volatile boolean closing;
    private InputManager inputManager;
    private int pendingUid;
    private AlertDialog inputDialog;
    private GameMenuDialog systemChoice;
    private String[] devices = new String[0];
    private String[] apps = new String[0];
    private SystemFileCatalog catalog;
    private File runtimeRoot;
    private List<SystemFileCatalog.Entry> systems = Collections.emptyList();
    private PendingGameImport pendingImport;
    private GameInstallFlow installFlow;
    private PlatformStore platforms;
    private ComponentStore components;
    private ArchiveBatch archives;
    private ArchiveQueue archiveQueue;
    private boolean archivePage;
    private ArchiveBatchView archiveView;
    private volatile boolean pauseArchive;
    private Runnable afterTask;
    private boolean hasPendingImport;
    private boolean installingInGuest;
    private boolean directLaunch;
    private SymbianLibraryIndex libraryIndex;
    private SymbianSessionUi sessionUi;
    private boolean platformSaved;
    private String platformError;
    private String backupSystem;
    private File restoreArchive;
    private static final int SAVE_PHONE = 10, RESTORE_PHONE = 11;

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        ru.playsoftware.j2meloader.ui.AppPalette.apply(this);
        if (!BuildConfig.HANDHELD_MODE) setRequestedOrientation(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR);
        catalog = new SystemFileCatalog(this);
        if (saved != null) backupSystem = saved.getString("backupSystem");
        libraryIndex = new SymbianLibraryIndex(this);
        pendingImport = new PendingGameImport(new File(getFilesDir(), "symbian-game-import"));
        platforms = new PlatformStore(new File(getFilesDir(), "symbian-platforms"));
        components = new ComponentStore(new File(getFilesDir(), "symbian-components"));
        installFlow = new GameInstallFlow(pendingImport, catalog, new GameInstallFlow.Backend() {
            private SystemFileCatalog.Entry installingPhone;
            @Override public void open(SystemFileCatalog.Entry entry) throws Exception { openSystem(entry); installingPhone=entry; }
            @Override public boolean hasPlatform() { return Arrays.asList(Emulator.getCatalogApps()).contains(Integer.toString(GameInstallFlow.PLATFORM_UID)); }
            @Override public SisMetadata.Version installedVersion(long uid) {
                int[] version=Emulator.installedPackageVersion(uid);
                return version==null ? null : new SisMetadata.Version(version[0],version[1],version[2]);
            }
            @Override public void status(String message) throws Exception {
                checkImportOpen(); runOnUiThread(() -> { if(!closing) status.setText(message); });
            }
            @Override public void install(File file, GameImport.Kind kind) throws Exception {
                checkImportOpen();
                int code = kind == GameImport.Kind.CARD ? Emulator.installNGageGame(file.getAbsolutePath())
                        : Emulator.installApp(file.getAbsolutePath());
                if (code != 0) throw new IOException("Installation failed (" + code + "). Your game import has been kept.");
                Emulator.prepare(runtimeRoot.getAbsolutePath());
                // A batch may switch phones before refresh(), so publish each completed installation.
                libraryIndex.publish(installingPhone,Emulator.getLibraryApps(),true);
            }
            @Override public void stage(File file, String hash) throws Exception {
                checkImportOpen();
                String drive = Emulator.driveE();
                if (drive == null) throw new IOException("This system has no memory card");
                String name = hash + ".n-gage";
                File destination = new File(drive, "n-gage/" + name);
                if (!destination.exists()) SymbianFiles.stageCardPackage(runtimeRoot, new File(drive), file, name);
                else if (!SystemFileCatalog.hash(destination).equals(hash)) throw new IOException("The staged game package has changed");
            }
        }, platforms, components);
        archives=new ArchiveBatch(new File(getFilesDir(),"symbian-archive-import"));
        archiveQueue=new ArchiveQueue(archives,pendingImport,installFlow,components,platforms);
        runtimeRoot = SymbianFiles.root(this);
        directLaunch = getIntent().hasExtra(SymbianLibraryIndex.UID_EXTRA);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        inputManager = (InputManager) getSystemService(INPUT_SERVICE);
        inputManager.registerInputDeviceListener(this, null);
        library();
        task("Preparing Symbian", () -> {
            if(getIntent().getBooleanExtra(SymbianLibraryIndex.REMOVE_EXTRA,false)
                    && sessionOwner!=null && sessionOwner.running)
                throw new IOException("Close the running Symbian game before uninstalling. No game was stopped or removed");
            Emulator.stopSession();
            sessionOwner = this;
            Emulator.host = this;
            catalog.recoverInterruptedImports();
            File[] interruptedTransfers=getCacheDir().listFiles(f -> f.getName().startsWith("phone-backup-") || f.getName().startsWith("phone-restore-"));
            if(interruptedTransfers!=null) for(File file:interruptedTransfers) removeTransfer(file);
            pendingImport.recover();
            archives.recover();
            CompressedGameArchive.recover(new File(SymbianFiles.root(this),"imports"));
            runtimeRoot = catalog.activeRoot(runtimeRoot);
            if(getIntent().getBooleanExtra("symbian.backups",false)) {
                getIntent().removeExtra("symbian.backups"); refreshStopped(null); afterTask=this::phoneBackups; return;
            }
            String selectedSystem = getIntent().getStringExtra(SymbianLibraryIndex.SYSTEM_EXTRA);
            if (selectedSystem != null) {
                SystemFileCatalog.Entry entry = catalog.read(selectedSystem);
                openSystem(entry,getIntent().getBooleanExtra(SymbianLibraryIndex.REMOVE_EXTRA,false));
                if(getIntent().getBooleanExtra(SymbianLibraryIndex.REMOVE_EXTRA,false)) {
                    long uid=getIntent().getLongExtra(SymbianLibraryIndex.UID_EXTRA,0);
                    // Remove the launch request before showing any dialog or handling an error.
                    getIntent().removeExtra(SymbianLibraryIndex.REMOVE_EXTRA);
                    getIntent().removeExtra(SymbianLibraryIndex.UID_EXTRA);
                    directLaunch=false;
                    reviewRemoval(entry,uid);
                    return;
                }
                if (getIntent().hasExtra(SymbianLibraryIndex.UID_EXTRA)) {
                    long uid = getIntent().getLongExtra(SymbianLibraryIndex.UID_EXTRA, 0);
                    boolean found = false;
                    String[] installed = Emulator.getLibraryApps();
                    for (int i = 0; i + 2 < installed.length; i += 3) found |= installed[i].equals(Long.toString(uid));
                    if (!found) throw new IOException("This game is no longer installed on its selected phone");
                    directLaunch = true;
                    refresh("Starting game");
                    afterTask = () -> play((int) uid);
                } else refresh();
                return;
            }
            String incoming = getIntent().getStringExtra("symbian.importFile");
            if (incoming != null) {
                String name = getIntent().getStringExtra("symbian.importName");
                getIntent().removeExtra("symbian.importFile");
                File staged=SymbianFiles.claimStaged(this,incoming);
                if(ImportKind.inspect(staged,name)==ImportKind.Kind.BACKUP) {
                    try { refreshStopped(null); previewRestore(staged); }
                    catch(Exception error) { removeTransfer(staged); throw error; }
                    return;
                }
                SymbianFiles.prepareAssets(this, runtimeRoot);
                Emulator.prepare(runtimeRoot.getAbsolutePath());
                refresh("Inspecting selected file");
                afterTask = () -> importInputs(4, Collections.emptyList(), incoming, name);
                return;
            }
            // Upgrade existing installations into the shared launcher without booting every ROM.
            try {
                for (SystemFileCatalog.Entry entry : catalog.boundSystems()) {
                    checkImportOpen();
                    try {
                        Emulator.stopSession();
                        File root = catalog.runtime(entry);
                        SymbianFiles.prepareAssets(this, root);
                        Emulator.prepare(root.getAbsolutePath());
                        libraryIndex.publish(entry, Emulator.getLibraryApps(), true);
                    } catch (IOException | RuntimeException error) {
                        android.util.Log.w("AbyssSymbian", "Library refresh failed: " + entry.id, error);
                    }
                }
            } finally {
                Emulator.stopSession();
                if (!closing) {
                    SymbianFiles.prepareAssets(this, runtimeRoot);
                    Emulator.prepare(runtimeRoot.getAbsolutePath());
                }
            }
            checkImportOpen();
            refresh();
        });
    }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private TextView label(String text, int size) {
        TextView view = new TextView(this); view.setText(text); view.setTextSize(size);
        view.setTextColor(ru.playsoftware.j2meloader.ui.AppPalette.color(this, ru.playsoftware.j2meloader.R.color.text_primary)); view.setPadding(dp(12), dp(8), dp(12), dp(8));
        return view;
    }
    private void action(LinearLayout parent, String title, Runnable run) {
        Button b = new Button(this); b.setText(title); b.setAllCaps(false); b.setTextColor(ru.playsoftware.j2meloader.ui.AppPalette.color(this, ru.playsoftware.j2meloader.R.color.text_primary));
        b.setTextSize(15); b.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        b.setPadding(dp(14),dp(8),dp(14),dp(8));
        ru.playsoftware.j2meloader.ui.ConsoleSurface.apply(b, ru.playsoftware.j2meloader.ui.ConsoleSurface.Kind.KEY);
        b.setMinHeight(dp(48)); b.setOnClickListener(v -> { if (!busy) run.run(); });
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1,-2); params.topMargin=dp(4);
        parent.addView(b,params);
    }
    private void library() {
        archiveView=null;
        archivePage=false;
        page = new LinearLayout(this); page.setOrientation(LinearLayout.VERTICAL);
        ru.playsoftware.j2meloader.ui.ConsoleSurface.apply(page, ru.playsoftware.j2meloader.ui.ConsoleSurface.Kind.CHASSIS);
        page.setPadding(dp(10), dp(8), dp(10), dp(8));
        page.addView(label("System files", 22));
        status = label("", 14); page.addView(status);
        progressBar=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);
        progressBar.setIndeterminate(true);
        progressBar.setIndeterminateTintList(android.content.res.ColorStateList.valueOf(ru.playsoftware.j2meloader.ui.AppPalette.color(this, ru.playsoftware.j2meloader.R.color.accent)));
        progressBar.setVisibility(busy?View.VISIBLE:View.GONE);
        page.addView(progressBar,new LinearLayout.LayoutParams(-1,dp(4)));
        if (directLaunch) {
            action(page, "Library", this::finish);
            setContentView(page); hideBars();
            return;
        }
        ScrollView scroll = new ScrollView(this);
        ru.playsoftware.j2meloader.ui.ConsoleSurface.apply(scroll, ru.playsoftware.j2meloader.ui.ConsoleSurface.Kind.SCREEN);
        LinearLayout list = new LinearLayout(this); list.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(list); page.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        if (hasPendingImport) action(list, "Pending import", this::pendingActions);
        if (archives.exists()) action(list,"Archive queue",this::openArchive);
        action(list, "Add system files", () -> pick(1,true));
        for (SystemRequirements.Row row : SystemRequirements.rows(systems, platformSaved)) {
            action(list, row.title + "\n" + row.status, () -> requirements(row));
        }
        if (platformError != null) list.addView(label(platformError,14));
        action(list, "What files do I need?", () -> notice("Phone system files",SystemRequirements.formats(),"Add system files",() -> pick(1,true),"Close"));
        action(list, "Installed phones", this::systemFiles);
        action(list, "Phone backups", this::phoneBackups);
        action(list, "Saved components", this::savedComponents);
        action(list, "Add game", () -> { if (hasPendingImport) pendingActions(); else pick(2, true); });
        if (apps.length > 0) action(list, "Phone tools", () -> {
            GameMenuDialog menu = dialog("Phone tools");
            for (int i = 0; i + 1 < apps.length; i += 2) {
                final int uid = (int) Long.parseLong(apps[i]);
                menu.action(apps[i + 1],0,() -> { menu.dismiss(); play(uid); });
            }
        });
        action(page, "Library", this::finish);
        setContentView(page);
        hideBars();
    }
    private void savedComponents() {
        task("Reading saved components",() -> {
            List<ComponentStore.Item> saved=components.list();
            afterTask=() -> {
                status.setText(saved.isEmpty() ? "No saved component installers" : saved.size()+" saved component installer"+(saved.size()==1 ? "" : "s"));
                GameMenuDialog menu=dialog("Saved components");
                menu.message(saved.isEmpty() ? "No component installers saved. Games will ask for the ones they need."
                        : "Installers are reused when a game needs them. Removing a saved installer does not uninstall it from any phone.");
                menu.action("Add installer",android.R.drawable.ic_input_add,() -> { menu.dismiss(); pick(6,false); });
                for(ComponentStore.Item item:saved) menu.action(item.metadata.name+"  "+item.metadata.version,android.R.drawable.ic_menu_delete,() -> {
                    menu.dismiss(); notice("Remove saved installer?",item.metadata.name+"\nInstalled games and saves stay untouched.",
                            "Remove",() -> task("Removing saved installer",() -> { components.remove(item.hash); refresh("Saved installer removed"); }),"Cancel");
                });
                menu.action("Remove saved installers",android.R.drawable.ic_menu_delete,() -> {
                    menu.dismiss(); notice("Remove saved installers?","Only saved component installers are removed. Installed phones, games and saves stay untouched.",
                            "Remove",() -> task("Removing saved installers",() -> { components.clear(); refresh("Saved installers removed"); }),"Cancel");
                });
                menu.action("Close",0,menu::dismiss);
            };
        });
    }
    private void requirements(SystemRequirements.Row row) {
        GameMenuDialog menu = dialog(row.title);
        menu.message(row.description);
        if (row.phoneReady && (!row.platform || platformSaved))
            menu.action("Back to library",android.R.drawable.ic_media_play,() -> { menu.dismiss(); finish(); });
        if (!row.title.equals("Java / Mophun")) {
            if (row.platform && row.phoneReady && !platformSaved)
                menu.action("Add N-Gage 2.0 installer",android.R.drawable.ic_input_add,
                        () -> { menu.dismiss(); pick(3,false); });
            menu.action(row.phoneReady ? "Add another phone" : "Add phone files", android.R.drawable.ic_input_add,
                    () -> { menu.dismiss(); pick(1,true); });
            if (row.platform && (!row.phoneReady || platformSaved)) menu.action("Add N-Gage 2.0 installer",android.R.drawable.ic_input_add,
                    () -> { menu.dismiss(); pick(3,false); });
            menu.action("About phone dumps",android.R.drawable.ic_menu_info_details,() -> {
                menu.dismiss(); notice("Phone system files",SystemRequirements.formats(),"Back",() -> requirements(row),null);
            });
        }
        menu.action("Close",0,menu::dismiss);
    }
    private void hideBars() {
        if (android.os.Build.VERSION.SDK_INT >= 30) {
            getWindow().setDecorFitsSystemWindows(false);
            WindowInsetsController controller = getWindow().getInsetsController();
            if (controller != null) {
                controller.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
                controller.hide(WindowInsets.Type.systemBars());
            }
            return;
        }
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
    }
    @Override public void onWindowFocusChanged(boolean focus) {
        super.onWindowFocusChanged(focus); if (focus) hideBars();
        if (sessionUi != null) sessionUi.focused(focus);
    }
    private interface Work { void run() throws Exception; }
    private void task(String text, Work work) {
        if (busy || closing) return;
        busy = true; pauseArchive=false; afterTask = null; status.setText(text);
        if(archiveView!=null)archiveView.working(true);
        if(progressBar!=null) progressBar.setVisibility(View.VISIBLE);
        worker.execute(() -> {
            try { if (!closing) work.run(); }
            catch (Exception error) {
                afterTask = null;
                android.util.Log.e("AbyssSymbian", text, error);
                runOnUiThread(() -> { if (!closing) {
                    if (!running) { hasPendingImport = pendingImport.exists(); library(); }
                    status.setText(error.getMessage());
                    if (!(error instanceof java.io.InterruptedIOException))
                        notice("Could not finish", error.getMessage() == null ? text : error.getMessage(), "Close", null, null);
                } });
            } finally { runOnUiThread(() -> {
                busy = false;
                if(archiveView!=null)archiveView.working(false);
                if(progressBar!=null) progressBar.setVisibility(View.GONE);
                if (returnRequested) { returnRequested = false; returnToLibrary(); }
                else if (!closing && afterTask != null) { Runnable next = afterTask; afterTask = null; next.run(); }
            }); }
        });
    }
    private void refresh() throws IOException {
        refresh(null);
    }
    private void refresh(String message) throws IOException {
        refresh(message,true);
    }
    private void refreshStopped(String message) throws IOException { refresh(message,false); }
    private void refresh(String message,boolean prepared) throws IOException {
        systems = catalog.list();
        platformError = null;
        try (GameImport saved = platforms.open()) { platformSaved = saved != null; }
        catch (IOException error) { platformSaved = false; platformError = error.getMessage(); }
        devices = prepared ? Emulator.getDevices() : new String[0];
        apps = devices.length == 0 ? new String[0] : Emulator.getCatalogApps();
        for (SystemFileCatalog.Entry entry : systems) if (prepared && catalog.runtime(entry).equals(runtimeRoot)) {
            libraryIndex.publish(entry, Emulator.getLibraryApps(), true);
            break;
        }
        hasPendingImport = pendingImport.exists();
        runOnUiThread(() -> {
            if (closing) return;
            library();
            status.setText(message != null ? message : systems.isEmpty() ? "No phone files added"
                    : systems.size() == 1 ? "1 phone available" : systems.size() + " phones available");
        });
    }
    private void systemFiles() {
        GameMenuDialog menu = dialog("Installed phones");
        for (SystemFileCatalog.Entry entry : systems) menu.action(entry.label(),0,() -> {
            menu.dismiss(); task("Opening system", () -> { openSystem(entry); refresh(); });
        });
        menu.action("Add system files",android.R.drawable.ic_input_add,() -> { menu.dismiss(); pick(1,true); });
        menu.action("Close",0,menu::dismiss);
    }
    private void phoneBackups() {
        GameMenuDialog menu = dialog("Phone backups");
        menu.message("Transfer a whole Symbian / N-Gage phone with its games, saves and controls. Java and Mophun use Library transfer.");
        if (!systems.isEmpty()) menu.action("Back up phone",android.R.drawable.ic_menu_save,() -> {
            menu.dismiss(); GameMenuDialog choose=dialog("Choose phone to back up");
            for(SystemFileCatalog.Entry entry:systems) choose.action(entry.label(),0,() -> {
                choose.dismiss(); requestPhoneBackup(entry);
            });
            choose.action("Cancel",0,choose::dismiss);
        });
        menu.action("Restore phone backup",android.R.drawable.ic_menu_revert,() -> { menu.dismiss(); pick(RESTORE_PHONE,false); });
        menu.action("Close",0,menu::dismiss);
    }
    private void requestPhoneBackup(SystemFileCatalog.Entry entry) {
        notice("Back up phone",entry.label()
                + "\n\nIncludes system files, installed games, saves and game settings. Keep this archive private."
                + "\n\nThe game must be closed. This is not an in-game save state.","Choose where to save",() -> {
            backupSystem=entry.id;
            Intent intent=new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("application/zip")
                    .putExtra(Intent.EXTRA_TITLE,"AbyssME-phone-"+entry.firmware+"-"
                            +new java.text.SimpleDateFormat("yyyy-MM-dd-HHmm",Locale.ROOT).format(new Date())+".zip");
            startActivityForResult(intent,SAVE_PHONE);
        },"Cancel");
    }
    private PreparedSystemArchive.Progress transferProgress() {
        final long[] last={0};
        return text -> {
            if(closing) throw new java.io.InterruptedIOException("Phone transfer cancelled. Existing phones are unchanged.");
            long now=android.os.SystemClock.uptimeMillis();
            if(now-last[0]>=150) { last[0]=now; runOnUiThread(() -> { if(!closing) status.setText(text); }); }
        };
    }
    private void exportPhone(Uri destination) {
        String selected=backupSystem; backupSystem=null;
        task("Backing up phone",() -> {
            if(running || selected==null) throw new IOException("Close the game and select the phone again");
            File archive=File.createTempFile("phone-backup-",".zip",getCacheDir());
            boolean saved=false;
            try {
                Emulator.stopSession();
                SystemFileCatalog.Entry entry=catalog.read(selected);
                PreparedSystemArchive.Progress progress=transferProgress();
                PhoneLibraryTitles.migrate(getFilesDir(),entry.id,getSharedPreferences("symbian-library-presentation",MODE_PRIVATE));
                new PhoneBackup(getFilesDir()).exportPhone(entry,archive,Emulator.portablePhoneSettings(catalog.runtime(entry).getAbsolutePath()),progress);
                progress.update("Saving backup to selected location");
                try(java.io.InputStream input=new java.io.FileInputStream(archive);
                    java.io.OutputStream output=getContentResolver().openOutputStream(destination,"wt")) {
                    if(output==null) throw new IOException("Cannot write to the selected location");
                    byte[] bytes=new byte[65536]; long copied=0;
                    for(int n;(n=input.read(bytes))!=-1;) { progress.update("Saving backup: "+copied/(1024*1024)+" MB"); output.write(bytes,0,n); copied+=n; }
                    output.flush();
                }
                saved=true;
                afterTask=() -> notice("Backup saved","Your phone, installed games, saves and game settings are in the selected ZIP. Use Phone backups > Restore phone backup on the other device.","Close",null,null);
            } finally {
                Files.deleteIfExists(archive.toPath());
                if(!saved) {
                    try { android.provider.DocumentsContract.deleteDocument(getContentResolver(),destination); }
                    catch(Exception ignored) { android.util.Log.w("AbyssSymbian","Incomplete backup may remain at the selected location"); }
                }
                if(!closing) refreshStopped(saved?"Backup saved":null);
            }
        });
    }
    private void inspectRestore(Uri source) {
        task("Reading phone backup",() -> {
            File archive=SymbianFiles.stage(this,source,PhoneBackup.MAX_INPUT,transferProgress());
            try { previewRestore(archive); }
            catch(Exception error) { Files.deleteIfExists(archive.toPath()); throw error; }
        });
    }
    private void previewRestore(File archive) throws IOException {
        PhoneBackup.Preview preview=PhoneBackup.inspect(archive);
        File retained=File.createTempFile("phone-restore-",".zip",getCacheDir());
        try { Files.move(archive.toPath(),retained.toPath(),java.nio.file.StandardCopyOption.REPLACE_EXISTING); }
        catch(IOException error) { removeTransfer(retained); throw error; }
        restoreArchive=retained;
        afterTask=() -> {
            GameMenuDialog menu=dialog("Restore phone backup"); menu.message(preview.description());
            menu.action("Restore as new phone",android.R.drawable.ic_menu_revert,() -> {
                restoreArchive=null; menu.dismiss(); restorePhone(retained);
            });
            menu.action("Cancel",0,menu::dismiss);
            menu.setOnDismissListener(d -> {
                if(restoreArchive==retained) { restoreArchive=null; worker.execute(() -> removeTransfer(retained)); }
            });
        };
    }
    private void restorePhone(File archive) {
        task("Restoring phone",() -> {
            if(running) throw new IOException("Close the game before restoring a phone");
            File previous=runtimeRoot;
            try {
                Emulator.stopSession();
                SystemFileCatalog.Entry restored=new PhoneBackup(getFilesDir()).restore(archive,(runtime,metadata) -> {
                    String[] settings=Emulator.portablePhoneSettings(runtime.getAbsolutePath());
                    Files.write(new File(runtime,"config.yml").toPath(),settings[0].getBytes(java.nio.charset.StandardCharsets.UTF_8));
                    Files.write(new File(runtime,"data/devices.yml").toPath(),settings[1].getBytes(java.nio.charset.StandardCharsets.UTF_8));
                    SymbianFiles.prepareAssets(this,runtime);
                    try {
                        Emulator.prepare(runtime.getAbsolutePath());
                        String[] info=Emulator.getDeviceInfo();
                        if(info==null || info.length!=3 || !info[0].equals(metadata.getString("firmware"))
                                || !info[1].equals(metadata.getString("model")) || !info[2].equals(metadata.getString("os")))
                            throw new IOException("The restored phone does not match its backup");
                        Emulator.getLibraryApps();
                    } finally { Emulator.stopSession(); }
                },transferProgress());
                openSystem(restored); refresh("Phone restored. Existing phones are unchanged.");
                afterTask=() -> notice("Phone restored","Games from the restored copy are now in your library. Your previous phones and saves were kept.","Library",this::finish,"Close");
            } catch(Exception error) {
                Emulator.stopSession(); runtimeRoot=previous;
                if(!closing) refreshStopped(null);
                throw error;
            } finally { removeTransfer(archive); }
        });
    }
    private static void removeTransfer(File file) {
        try { if(file!=null) Files.deleteIfExists(file.toPath()); }
        catch(IOException error) { android.util.Log.w("AbyssSymbian","Cannot remove temporary backup",error); }
    }
    @Override protected void onSaveInstanceState(Bundle state) {
        state.putString("backupSystem",backupSystem); super.onSaveInstanceState(state);
    }
    private GameMenuDialog dialog(String title) { GameMenuDialog menu = new GameMenuDialog(this, "Symbian / N-Gage"); menu.useEdgePanel(); menu.show(); menu.page(title); return menu; }
    private void notice(String title,String text,String action,Runnable run,String cancel) {
        GameMenuDialog menu = dialog(title); menu.message(text);
        menu.action(action,0,() -> { menu.dismiss(); if (run != null) run.run(); });
        if (cancel != null) menu.action(cancel,0,menu::dismiss);
    }
    private void openSystem(SystemFileCatalog.Entry entry) throws Exception {
        openSystem(entry,false);
    }
    private void openSystem(SystemFileCatalog.Entry entry,boolean removalReview) throws Exception {
        checkImportOpen();
        Emulator.stopSession();
        runtimeRoot = catalog.runtime(entry);
        SymbianFiles.prepareAssets(this, runtimeRoot);
        Emulator.prepare(runtimeRoot.getAbsolutePath());
        catalog.activate(entry);
        GameRemoval removal=removal(entry);
        removal.recover();
        if(removal.pending() && !removalReview)
            throw new IOException("An uninstall is incomplete on this phone. Retry it from the game's actions or restore a phone backup before making further changes");
    }
    private GameRemoval removal(SystemFileCatalog.Entry entry) throws IOException {
        String runtime=catalog.runtime(entry).getAbsolutePath();
        return new GameRemoval(new File(getFilesDir(),"symbian-library"),entry.id,new GameRemoval.Backend() {
            public String[] inspect(long uid) throws Exception { return Emulator.inspectRemoval(uid,runtime); }
            public String[] apps() { return Emulator.getLibraryApps(); }
            public void remove(long uid,long owner,int index) throws Exception {
                checkImportOpen();
                if(running) throw new IOException("Close the game before uninstalling");
                Emulator.uninstallGame(uid,owner,index,runtime);
            }
            public void reload() { Emulator.prepare(runtime); }
            public boolean complete(GameRemoval.Plan plan) throws Exception { return Emulator.removalComplete(plan.rows,runtime); }
            public void publish(String[] apps,Set<Long> removed) throws Exception { libraryIndex.publishRemoval(entry,apps,removed); }
        });
    }
    private void reviewRemoval(SystemFileCatalog.Entry entry,long uid) throws Exception {
        GameRemoval.Plan plan=removal(entry).inspect(uid);
        refreshStopped("Review uninstall");
        afterTask=() -> {
            GameMenuDialog menu=dialog("Uninstall from phone?");
            menu.message(plan.description(entry.label()+" ["+entry.id.substring(0,8)+"]"));
            menu.setBackAction(() -> { menu.dismiss(); finish(); });
            menu.action("Cancel",0,() -> { menu.dismiss(); finish(); });
            menu.action("Back up this phone",android.R.drawable.ic_menu_save,() -> { menu.dismiss(); requestPhoneBackup(entry); });
            menu.action("Uninstall",android.R.drawable.ic_menu_delete,() -> {
                menu.dismiss(); task("Uninstalling from phone",() -> {
                    if(running) throw new IOException("Close the game before uninstalling");
                    openSystem(entry,true);
                    removal(entry).remove(plan);
                    refresh("Uninstalled from " + entry.displayName());
                    afterTask=() -> notice("Uninstalled", "The package was removed from " + entry.displayName()
                            + ". Other phones are unchanged.","Library",this::finish,"Close");
                });
            });
        };
    }
    private void checkImportOpen() throws java.io.InterruptedIOException {
        if (closing) throw new java.io.InterruptedIOException("Import paused. Continue it from Pending import.");
    }
    private void openArchive() {
        task("Reading archive queue",this::showArchive);
    }
    private void showArchive() throws Exception {
        ArchiveBatch.Batch batch=archives.read();
        refresh("Archive import saved");
        if(batch!=null) afterTask=()->archiveScreen(batch);
    }
    private void archiveScreen(ArchiveBatch.Batch batch) {
        if(closing) return;
        archivePage=true;
        ArchiveBatchView view=new ArchiveBatchView(this,batch,new ArchiveBatchView.Host() {
            public void changed() {
                if(busy || closing || batch.started) return;
                ArchiveBatch.Batch snapshot=ArchiveBatch.snapshot(batch);
                worker.execute(()-> {
                    try { archives.save(snapshot); }
                    catch(IOException error) { runOnUiThread(()-> { if(!closing) status.setText(error.getMessage()); }); }
                });
            }
            public void row(int id) { if(!busy && id>=0 && id<batch.items.size()) archiveItem(batch.items.get(id),batch.started); }
            public void start() {
                if(busy) return; pauseArchive=false;
                ArchiveBatch.Batch snapshot=ArchiveBatch.snapshot(batch);
                task("Importing selected files",()-> {
                    if(!snapshot.started) archives.save(snapshot);
                    else if(!pendingImport.exists() && snapshot.items.stream().noneMatch(i->i.state==ArchiveBatch.State.READY || i.state==ArchiveBatch.State.ACTIVE))
                        for(ArchiveBatch.Item item:snapshot.items) if(item.state==ArchiveBatch.State.WAITING) {archiveQueue.retry(item.id);break;}
                    runArchive(null,false);
                });
            }
            public void others() {
                if(busy) return; pauseArchive=false;
                task("Continuing other files",()-> { archiveQueue.defer(); runArchive(null,false); });
            }
            public void close() { onBackPressed(); }
            public void done() {
                if(busy)return;
                task("Finishing archive import",()-> {archiveQueue.discard(); refresh(); afterTask=SymbianActivity.this::finish;});
            }
            public void remove() {
                if(busy) return;
                notice("Remove archive queue?","Only retained queue files are removed. Original archives, installed games and saves stay untouched.",
                        "Remove",()->task("Removing archive queue",()-> { archiveQueue.discard(); refresh(); }),"Cancel");
            }
        });
        status=view.status; progressBar=view.progress;
        archiveView=view;
        setContentView(view); hideBars();
    }
    private void archiveItem(ArchiveBatch.Item item,boolean started) {
        GameMenuDialog menu=dialog(item.label());
        menu.message(item.name+"\n\n"+(!item.problem.isEmpty() ? item.problem : item.detail.isEmpty()
                ? item.role==ArchiveBatch.Role.GAME ? "Install as a game." : "Save this installer for games that need it." : item.detail));
        if(started && item.problem.isEmpty() && (item.state==ArchiveBatch.State.WAITING || item.state==ArchiveBatch.State.FAILED))
            menu.action("Retry",android.R.drawable.ic_menu_rotate,()-> {
                menu.dismiss(); notice("Retry this file?","An interrupted installation may have written files. Retry may reinstall them on the same phone.",
                        "Retry",()->task("Retrying queued file",()-> { pauseArchive=false; archiveQueue.retry(item.id); runArchive(null,true); }),"Cancel");
            });
        if(started && item.state!=ArchiveBatch.State.DONE && item.state!=ArchiveBatch.State.SKIPPED)
            menu.action("Skip",android.R.drawable.ic_media_next,()-> { menu.dismiss(); task("Skipping queued file",()-> { archiveQueue.skip(item.id); showArchive(); }); });
        menu.action("Close",0,menu::dismiss);
    }
    private void runArchive(String selected,boolean retry) throws Exception {
        GameInstallFlow.Result result=archiveQueue.run(selected,retry,message-> {
            checkImportOpen(); if(pauseArchive) throw new java.io.InterruptedIOException("Archive queue paused. Continue it from Archive queue.");
            runOnUiThread(()-> { if(!closing) status.setText(message); });
        });
        if(result==null) showArchive(); else showImportResult(result);
    }
    private void resumeImport(String selected, boolean retry) {
        task("Continuing import", () -> {
            if(archiveQueue.ownsPending()) runArchive(selected,retry);
            else showImportResult(installFlow.resume(selected,retry));
        });
    }
    private void showImportResult(GameInstallFlow.Result result) throws Exception {
        if(archiveQueue.ownsPending()) {
            archiveQueue.accept(result);
            if(result.action==GameInstallFlow.Action.DONE) { runArchive(null,false); return; }
        }
        PendingGameImport.Entry job = pendingImport.read();
        refresh(result.action == GameInstallFlow.Action.DONE ? "Installation complete" : "Import saved: " + job.name);
        afterTask = () -> {
            switch (result.action) {
                case SYSTEM:
                    notice("System files needed", SystemRequirements.needed(job.kind)
                            + "\n\n" + SystemRequirements.formats() + "\n\nYour game is kept and will continue after import.",
                            "Add system files", () -> pick(1,true), "Later");
                    break;
                case CHOOSE: chooseSystem(result.choices); break;
                case PLATFORM:
                    notice("N-Gage 2.0 needed", "Phone system files are ready. Add the N-Gage 2.0 platform installer (SIS or ZIP), not a game. AbyssME keeps a copy for later games.",
                            "Add platform", () -> pick(3,false), "Later");
                    break;
                case RETRY:
                    notice("Installation interrupted", "The previous installation did not finish. Your game is kept. Retrying may reinstall files on the selected phone.",
                            "Retry", () -> resumeImport(null,true), "Later");
                    break;
                case COMPONENT:
                    notice("Component needed",result.component.description()
                            +"\n\nAdd this component's SIS/SISX installer, or a ZIP containing it. Use a version for this Symbian phone. Your game is kept; installation continues after you add the file. The installer is saved for later games.",
                            "Add installer",() -> pick(5,false),"Later");
                    break;
                case OPEN_PLATFORM: installingInGuest = true; play(GameInstallFlow.PLATFORM_UID); break;
                case DONE: notice("Installation complete", "The installed game is available in your library.","Library",this::finish,null); break;
                default: break;
            }
        };
    }
    private void pendingActions() {
        GameMenuDialog menu = dialog("Pending import");
        menu.action("Continue import",android.R.drawable.ic_media_play,() -> { menu.dismiss(); resumeImport(null,false); });
        menu.action("Remove pending import",android.R.drawable.ic_menu_delete,() -> {
            menu.dismiss(); notice("Remove pending import?","Only the retained import is removed. Original files, installed games and saves stay untouched.",
                    "Remove",() -> task("Removing pending import",() -> {
                        if(archiveQueue.ownsPending()) {
                            ArchiveBatch.Batch batch=archives.read(); archiveQueue.skip(batch.owned(pendingImport.read().owner).id);
                        } else pendingImport.discard();
                        refresh();
                    }),"Cancel");
        });
        menu.action("Close",0,menu::dismiss);
    }
    private void pick(int request, boolean multiple) {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("*/*")
                .addCategory(Intent.CATEGORY_OPENABLE).putExtra(Intent.EXTRA_ALLOW_MULTIPLE, multiple);
        startActivityForResult(intent, request);
    }
    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if(request==SAVE_PHONE || request==RESTORE_PHONE) {
            if(result==RESULT_OK && data!=null && data.getData()!=null) {
                Uri uri=data.getData();
                whenReady(() -> { if(request==SAVE_PHONE) exportPhone(uri); else inspectRestore(uri); });
            } else backupSystem=null;
            return;
        }
        if (request < 1 || request == 4 || request > 6) return;
        if (result != RESULT_OK || data == null) return;
        List<Uri> uris = new ArrayList<>();
        if (data.getClipData() != null) {
            for (int i = 0; i < data.getClipData().getItemCount(); i++) uris.add(data.getClipData().getItemAt(i).getUri());
        } else if (data.getData() != null) uris.add(data.getData());
        importInputs(request, uris, null, null);
    }
    private void whenReady(Runnable action) {
        if(closing) return;
        if(busy) new android.os.Handler(getMainLooper()).postDelayed(() -> whenReady(action),100);
        else action.run();
    }
    private void importInputs(int request, List<Uri> uris, String incoming, String incomingName) {
        task(request == 1 ? "Adding system files" : request == 3 ? "Adding N-Gage 2.0" : request>=5 ? "Adding component" : "Inspecting game", () -> {
            List<File> staged = new ArrayList<>();
            GameImport game = null;
            try {
                PreparedSystemArchive.Progress progress = text -> {
                    if (closing) throw new java.io.InterruptedIOException("Import cancelled");
                    runOnUiThread(() -> { if (!closing) status.setText(text); });
                };
                if (incoming != null) staged.add(SymbianFiles.claimStaged(this, incoming));
                else {
                    if(uris.size()>256) throw new IOException("Select up to 256 files at a time");
                    long total=0;
                    for(Uri uri:uris) {
                        File file=SymbianFiles.stage(this,uri,request==1 ? PreparedSystemArchive.MAX_INPUT : ArchiveBatch.LIMIT,progress);
                        staged.add(file); total+=file.length();
                        if(request==2 && total>ArchiveBatch.LIMIT) throw new IOException("Selected games exceed 1 GB");
                    }
                }
                for(int i=0;i<staged.size();i++) {
                    File source=staged.get(i);
                    if(CompressedGameArchive.accepts(source) && !(CompressedGameArchive.isSeven(source)
                            && (request==1 || (request==4 && CompressedGameArchive.sevenHasSystemFiles(source))))) {
                        File decoded=CompressedGameArchive.toZip(source,progress); staged.set(i,decoded); Files.deleteIfExists(source.toPath());
                    }
                }
                if(request==2 && staged.size()>1) {
                    if(pendingImport.exists()) throw new IOException("Finish or remove the pending game first");
                    List<String> names=new ArrayList<>(); for(Uri uri:uris) names.add(SymbianFiles.name(this,uri));
                    archives.beginFiles(staged,names,progress); showArchive(); return;
                }
                if(staged.size()==1 && GameImport.isZip(staged.get(0))) {
                    try(java.util.zip.ZipFile zip=new java.util.zip.ZipFile(staged.get(0))) {
                        if(zip.getEntry(PhoneBackup.MANIFEST)!=null) {
                            previewRestore(staged.get(0)); staged.remove(0); return;
                        }
                    }
                }
                int route = request;
                if (route == 4) {
                    ImportKind.Kind kind = ImportKind.inspect(staged.get(0), incomingName == null ? incoming : incomingName);
                    if (kind == ImportKind.Kind.EXISTING) throw new IOException("This archive contains Java / Mophun files. Select their JAR / MPN files or a ZIP from the library.");
                    route = kind == ImportKind.Kind.SYSTEM ? 1 : kind == ImportKind.Kind.PLATFORM ? 3 : 2;
                    if(kind==ImportKind.Kind.BACKUP) {
                        File archive=staged.get(0); previewRestore(archive); staged.remove(0); return;
                    }
                }
                if (route == 1) {
                    File previous = runtimeRoot;
                    Emulator.stopSession();
                    try {
                        List<List<File>> sets = new ArrayList<>();
                        boolean allArchives = !staged.isEmpty();
                        for (File file : staged) allArchives &= PreparedSystemArchive.isArchive(file);
                        if (allArchives) for (File file : staged) sets.add(Collections.singletonList(file));
                        else sets.add(staged);
                        SystemFileCatalog.Entry last = null;
                        List<String> reports = new ArrayList<>();
                        boolean hadSystems = !catalog.list().isEmpty();
                        boolean importWarnings = false;
                        for (List<File> inputs : sets) {
                            SystemFileCatalog.ImportResult imported = inputs.size() == 1 && PreparedSystemArchive.isArchive(inputs.get(0))
                                    ? catalog.addArchive(this, inputs.get(0), progress) : null;
                            if (imported == null) {
                                last = catalog.add(this, inputs);
                                reports.add(last.label() + " ready");
                            } else {
                                reports.add(imported.summary());
                                importWarnings |= !imported.failures.isEmpty();
                                if (!imported.entries.isEmpty()) last = imported.entries.get(0);
                            }
                        }
                        if (hadSystems || last == null || catalog.list().size() > 1) {
                            Emulator.stopSession(); runtimeRoot = previous;
                            Emulator.prepare(runtimeRoot.getAbsolutePath());
                        } else openSystem(last);
                        refresh(last == null ? "No systems added" : "System files updated");
                        String report = String.join("\n\n", reports);
                        boolean resume = pendingImport.exists();
                        if (resume && last != null && !importWarnings) afterTask = () -> resumeImport(null, false);
                        else afterTask = () -> notice("System import",report,resume ? "Continue import" : "Close", () -> {
                                    if (resume) resumeImport(null, false);
                                }, null);
                    } catch (Exception error) {
                        Emulator.stopSession();
                        runtimeRoot = previous;
                        Emulator.prepare(runtimeRoot.getAbsolutePath());
                        refresh();
                        throw error;
                    }
                } else {
                    if(route==2 && staged.size()==1 && ArchiveBatch.multiple(staged.get(0))) {
                        if(pendingImport.exists()) throw new IOException("Finish or remove the pending game first");
                        archives.beginZip(staged.get(0),incoming==null ? SymbianFiles.name(this,uris.get(0)) : incomingName,progress);
                        showArchive(); return;
                    }
                    if (staged.size() != 1) throw new IOException("Select one installer or game");
                    game = GameImport.inspect(staged.get(0));
                    checkImportOpen();
                    if(route==5) showImportResult(installFlow.addComponent(game));
                    else if(route==6) {
                        SisMetadata metadata=components.save(game,null);
                        PendingGameImport.Entry job=pendingImport.read();
                        if(job!=null && job.step==PendingGameImport.Step.COMPONENT) showImportResult(installFlow.resume(null,false));
                        else {
                            refresh("Saved component: "+metadata.name);
                            afterTask=() -> notice("Component saved",metadata.name+"\nThe installer is ready for games that need it. Installed phones were not changed.","Close",null,null);
                        }
                    }
                    else if (route == 3) {
                        platforms.save(game);
                        PendingGameImport.Entry job = pendingImport.read();
                        if (job != null && job.kind == GameImport.Kind.NGAGE2)
                            showImportResult(installFlow.resume(null, false));
                        else {
                            refresh("N-Gage 2.0 installer saved");
                            afterTask = () -> notice("N-Gage platform saved", "The installer is ready for games that need it. Existing phones and games were not changed.","Close",null,null);
                        }
                    }
                    else {
                        pendingImport.begin(game, incoming == null ? SymbianFiles.name(this, uris.get(0)) : incomingName);
                        showImportResult(installFlow.resume(null, false));
                    }
                }
            } finally { releaseImport(game, staged); }
        });
    }
    private static void releaseImport(GameImport game, List<File> staged) throws IOException {
        IOException failure = null;
        try { if (game != null) game.close(); } catch (IOException error) { failure = error; }
        for (File file : staged) {
            try { Files.deleteIfExists(file.toPath()); }
            catch (IOException error) { if (failure == null) failure = error; else failure.addSuppressed(error); }
        }
        if (failure != null) throw failure;
    }
    private void chooseSystem(List<SystemFileCatalog.Entry> matches) {
        systemChoice = dialog("Choose system for this game");
        GameMenuDialog choice = systemChoice;
        boolean queued;
        try {queued=archiveQueue.ownsPending();}catch(IOException error){queued=false;}
        final boolean archive=queued; final boolean[] reuse={true};
        choice.message("More than one phone matches this game's format. The choice is saved for this game; installed games and saves are unchanged.");
        if(archive)choice.toggle("Reuse for this package type in this archive",true,value->reuse[0]=value);
        for (SystemFileCatalog.Entry entry : matches) choice.action(entry.model + " (" + entry.firmware + ")",0,() -> {
                    if (busy || closing) return;
                    choice.dismiss();
                    if(archive)task("Choosing archive phone",()-> {archiveQueue.rememberPhone(entry.id,reuse[0]);runArchive(entry.id,false);});
                    else resumeImport(entry.id, false);
                });
        choice.action("Later",0,choice::dismiss);
        choice.setOnDismissListener(d -> systemChoice = null);
    }
    public void play(int uid) {
        task("Starting game", () -> {
            Emulator.lastGuestExit = null;
            if (!Emulator.startSession()) throw new IOException("The phone could not start");
            runOnUiThread(() -> {
                if (closing) return;
                sessionGeneration++;
                running = true; pendingUid = uid;
                TextureView sessionSurface = new TextureView(this);
                surface = sessionSurface;
                sessionSurface.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
                    private Surface output;
                    @Override public void onSurfaceTextureAvailable(android.graphics.SurfaceTexture texture, int w, int height) {
                        output = new Surface(texture); onSurfaceTextureSizeChanged(texture,w,height);
                    }
                    @Override public void onSurfaceTextureSizeChanged(android.graphics.SurfaceTexture texture, int w, int height) {
                        if (surface != sessionSurface || !running || closing) return;
                        Emulator.setScreenParams(Color.BLACK, 100, sessionUi == null ? 1 : sessionUi.scale(), 2, "", 0, true);
                        Emulator.surfaceChanged(output, w, height); attached = true;
                        Emulator.pauseSession(!resumed || menuOpen);
                        if (pendingUid != 0) {
                            int uid = pendingUid; pendingUid = 0;
                            if (!Emulator.launchGame(uid)) {
                                Emulator.lastGuestExit = "The game could not start";
                                returnToLibrary();
                            }
                        }
                    }
                    @Override public boolean onSurfaceTextureDestroyed(android.graphics.SurfaceTexture texture) {
                        if (surface == sessionSurface) detach();
                        if (output != null) output.release();
                        output = null; return true;
                    }
                    @Override public void onSurfaceTextureUpdated(android.graphics.SurfaceTexture texture) {
                        if (surface == sessionSurface && sessionUi != null) sessionUi.frameAvailable();
                    }
                });
                try {
                    String identity = "legacy", title = "Symbian game";
                    for (SystemFileCatalog.Entry entry : systems) if (catalog.runtime(entry).equals(runtimeRoot)) identity = entry.id;
                    for (int i = 0; i + 1 < apps.length; i += 2) if (Long.parseLong(apps[i]) == Integer.toUnsignedLong(uid)) title = apps[i + 1];
                    sessionUi = new SymbianSessionUi(this, surface, identity, Integer.toUnsignedLong(uid), title, new SymbianSessionUi.Host() {
                        public int[] displayGeometry() {
                            return running && attached && !closing ? Emulator.displayGeometry() : null;
                        }
                        public android.graphics.RectF touchBounds() {
                            if (!running || !attached || closing || menuOpen) return null;
                            int[] bounds = Emulator.touchBounds();
                            return bounds == null ? null : new android.graphics.RectF(bounds[0],bounds[1],bounds[2],bounds[3]);
                        }
                        public void touch(int x, int y, int pressure, int action, int pointer) {
                            if (running && attached && !closing) Emulator.touchScreen(x,y,pressure,action,pointer);
                        }
                        public void key(int scan, boolean down) { if (running && attached && !closing) Emulator.pressKey(scan, down ? 0 : 1); }
                        public void pause(boolean paused) { menuOpen = paused; if (running && attached && !closing) Emulator.pauseSession(!resumed || paused); }
                        public void display(int scale) { if (running && attached && !closing) Emulator.setScreenParams(Color.BLACK,100,scale,2,"",0,true); }
                        public void frameRate(int fps) { if (running && !closing) Emulator.setFrameRateLimit(fps); }
                        public void speed(int percent) { if (running && !closing) Emulator.setEmulationSpeed(percent); }
                        public void leave() { returnToLibrary(); }
                        public void restart() { restartGame(uid); }
                        public android.graphics.Bitmap capture() throws IOException {
                            if (!running || !attached || closing || menuOpen) return null;
                            // The upstream capture queues GPU readback. Keep pause/teardown on this
                            // same UI thread until it returns; gallery storage happens off-thread.
                            File png = File.createTempFile("symbian-frame-", ".png", getCacheDir());
                            try {
                                if (!Emulator.saveScreenshotTo(png.getAbsolutePath())) return null;
                                android.graphics.Bitmap bitmap = android.graphics.BitmapFactory.decodeFile(png.getAbsolutePath());
                                if (bitmap == null) throw new IOException("The engine did not write a valid screenshot");
                                return bitmap;
                            } finally { Files.deleteIfExists(png.toPath()); }
                        }
                    });
                    setContentView(sessionUi.root); hideBars();
                } catch (IOException error) { Toast.makeText(this,error.getMessage(),Toast.LENGTH_LONG).show(); returnToLibrary(); }
            });
        });
    }
    private void releaseKeys() {
        if (sessionUi != null) sessionUi.clear();
        if (running) for (int key : new HashSet<>(held.values())) Emulator.pressKey(key, 1);
        held.clear();
    }
    private void key(int source, int scan, boolean down) {
        if (down) {
            if (held.containsKey(source)) return;
            boolean alreadyPressed = held.containsValue(scan);
            held.put(source, scan);
            if (!alreadyPressed) Emulator.pressKey(scan, 0);
        } else {
            Integer old = held.remove(source);
            if (old != null && !held.containsValue(old)) Emulator.pressKey(old, 1);
        }
    }
    private void detach() {
        releaseKeys();
        if (attached) { Emulator.surfaceDestroyed(); attached = false; }
    }
    private static int scan(int key) {
        switch (key) {
            case KeyEvent.KEYCODE_DPAD_UP: return 0x10;
            case KeyEvent.KEYCODE_DPAD_DOWN: return 0x11;
            case KeyEvent.KEYCODE_DPAD_LEFT: return 0x0e;
            case KeyEvent.KEYCODE_DPAD_RIGHT: return 0x0f;
            case KeyEvent.KEYCODE_DPAD_CENTER: case KeyEvent.KEYCODE_BUTTON_A: case KeyEvent.KEYCODE_ENTER: return 0xa7;
            case KeyEvent.KEYCODE_BUTTON_B: case KeyEvent.KEYCODE_BUTTON_R1: return 0xa5;
            case KeyEvent.KEYCODE_BUTTON_SELECT: case KeyEvent.KEYCODE_BUTTON_START: case KeyEvent.KEYCODE_BUTTON_L1: return 0xa4;
            case KeyEvent.KEYCODE_BUTTON_X: return '5';
            case KeyEvent.KEYCODE_BUTTON_Y: return '0';
            default: return key >= KeyEvent.KEYCODE_0 && key <= KeyEvent.KEYCODE_9 ? '0' + key - KeyEvent.KEYCODE_0 : 0;
        }
    }
    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        if (sessionUi != null) return sessionUi.key(event) || super.dispatchKeyEvent(event);
        int key = scan(event.getKeyCode());
        if (running && attached && !menuOpen && key != 0) {
            int source = ((event.getDeviceId() & 0xffff) << 16) | event.getKeyCode();
            if (event.getAction() == KeyEvent.ACTION_DOWN) key(source, key, true);
            if (event.getAction() == KeyEvent.ACTION_UP) key(source, key, false);
            return true;
        }
        return super.dispatchKeyEvent(event);
    }
    @Override public boolean onGenericMotionEvent(MotionEvent event) {
        if (sessionUi != null) return sessionUi.motion(event) || super.onGenericMotionEvent(event);
        if (running && attached && !menuOpen && (event.getSource() & InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK) {
            float x = event.getAxisValue(MotionEvent.AXIS_X), y = event.getAxisValue(MotionEvent.AXIS_Y);
            float hatX = event.getAxisValue(MotionEvent.AXIS_HAT_X), hatY = event.getAxisValue(MotionEvent.AXIS_HAT_Y);
            int source = Integer.MIN_VALUE | ((event.getDeviceId() & 0xffff) << 4);
            key(source, 0x0e, x < -0.5f || hatX < -0.5f);
            key(source + 1, 0x0f, x > 0.5f || hatX > 0.5f);
            key(source + 2, 0x10, y < -0.5f || hatY < -0.5f);
            key(source + 3, 0x11, y > 0.5f || hatY > 0.5f);
            return true;
        }
        return super.onGenericMotionEvent(event);
    }
    @Override public void onBackPressed() {
        if(archivePage) {
            if(busy) { pauseArchive=true; status.setText("Pausing after the current file"); }
            else library();
            return;
        }
        if (sessionUi != null) { sessionUi.showMenu(); return; }
        if (!running) { super.onBackPressed(); return; }
        if (menuOpen) return;
        menuOpen = true;
        releaseKeys(); Emulator.pauseSession(true);
        new AlertDialog.Builder(this).setTitle("Leave game?")
                .setMessage("Unsaved progress will be lost.")
                .setPositiveButton("Library", (d, w) -> returnToLibrary())
                .setNegativeButton("Resume", (d, w) -> resumeGame())
                .setOnCancelListener(d -> resumeGame()).show();
    }
    private void resumeGame() { menuOpen = false; if (running && attached && !closing) Emulator.pauseSession(!resumed); }
    private void restartGame(int uid) {
        if (closing || !running || busy) return;
        sessionGeneration++;
        if (sessionUi != null) { sessionUi.close(); sessionUi = null; }
        detach(); running = false; menuOpen = false; pendingUid = 0;
        library();
        task("Restarting game", () -> {
            Emulator.stopSession();
            Emulator.prepare(runtimeRoot.getAbsolutePath());
            Emulator.lastGuestExit = null;
            afterTask = () -> play(uid);
        });
    }

    private void returnToLibrary() {
        if (closing || !running) return;
        if (busy) { returnRequested = true; return; }
        sessionGeneration++;
        if (sessionUi != null) { sessionUi.close(); sessionUi = null; }
        menuOpen = false;
        detach(); running = false; library();
        task("Closing game", () -> {
            Emulator.stopSession();
            File log = new File(runtimeRoot, "EKA2L1.log");
            if (log.isFile()) Files.copy(log.toPath(), new File(runtimeRoot, "last-session.log").toPath(),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            Emulator.prepare(runtimeRoot.getAbsolutePath());
            refresh(Emulator.lastGuestExit == null ? null : "Game stopped: " + Emulator.lastGuestExit);
            if (directLaunch) {
                directLaunch = false;
                afterTask = () -> {
                    if (Emulator.lastGuestExit != null && !Emulator.lastGuestExit.startsWith("Kill (0)"))
                        Toast.makeText(this, "Game stopped: " + Emulator.lastGuestExit, Toast.LENGTH_LONG).show();
                    finish();
                };
                return;
            }
            if (installingInGuest) {
                installingInGuest = false;
                afterTask = () -> notice("N-Gage installation",
                        "Did N-Gage finish installing the game? Keep the import to return to the installer later.",
                        "Finished",() -> task("Finishing import", () -> {
                            if(archiveQueue.ownsPending()) {
                                PendingGameImport.Entry job=pendingImport.read(); pendingImport.update(job,job.system,PendingGameImport.Step.COMPLETE);
                                archiveQueue.accept(new GameInstallFlow.Result(GameInstallFlow.Action.DONE)); runArchive(null,false);
                            } else { pendingImport.discard(); refresh(); }
                        }),"Keep for later");
            }
        });
    }
    @Override protected void onPause() {
        resumed = false;
        if (sessionUi != null) sessionUi.visible(false);
        releaseKeys(); if (running) Emulator.pauseSession(true); super.onPause();
    }
    @Override protected void onResume() {
        super.onResume(); resumed = true;
        if (running && attached && !menuOpen && !closing) Emulator.pauseSession(false);
        if (sessionUi != null) sessionUi.visible(true);
    }
    @Override protected void onDestroy() {
        closing = true;
        sessionGeneration++;
        if (sessionUi != null) { sessionUi.close(); sessionUi = null; }
        detach(); running = false;
        if (systemChoice != null) systemChoice.dismiss();
        inputManager.unregisterInputDeviceListener(this);
        if (Emulator.host == this) Emulator.host = null;
        worker.execute(() -> {
            if (sessionOwner == this) { Emulator.stopSession(); sessionOwner = null; }
            removeTransfer(restoreArchive); restoreArchive=null;
        });
        super.onDestroy();
    }
    @Override public void exited() {
        long generation = sessionGeneration;
        if (!running || closing) return;
        runOnUiThread(() -> { if (generation == sessionGeneration) returnToLibrary(); });
    }
    @Override public void onInputDeviceAdded(int id) { }
    @Override public void onInputDeviceChanged(int id) { releaseKeys(); }
    @Override public void onInputDeviceRemoved(int id) { releaseKeys(); }
    @Override public void question(String text, String yes, String no) {
        runOnUiThread(() -> {
            if (closing) { Emulator.submitQuestionDialogResponse(0); return; }
            new AlertDialog.Builder(this).setMessage(text)
                    .setPositiveButton(yes, (d, w) -> Emulator.submitQuestionDialogResponse(1))
                    .setNegativeButton(no, (d, w) -> Emulator.submitQuestionDialogResponse(0))
                    .setOnCancelListener(d -> Emulator.submitQuestionDialogResponse(0)).show();
        });
    }
    @Override public void input(String text, int maxLength) {
        runOnUiThread(() -> {
            if (closing) { Emulator.submitInput(""); return; }
            EditText edit = new EditText(this); edit.setText(text);
            edit.setFilters(new InputFilter[]{new InputFilter.LengthFilter(Math.max(0, maxLength))});
            inputDialog = new AlertDialog.Builder(this).setView(edit)
                    .setPositiveButton("OK", (d, w) -> Emulator.submitInput(edit.getText().toString()))
                    .setNegativeButton("Cancel", (d, w) -> Emulator.submitInput(""))
                    .setOnCancelListener(d -> Emulator.submitInput("")).show();
        });
    }
    @Override public void closeInput() { runOnUiThread(() -> { if (inputDialog != null) inputDialog.dismiss(); }); }
}
