package modvalidator.full;

import arc.*;
import arc.backend.sdl.*;
import arc.files.*;
import arc.func.*;
import arc.struct.*;
import arc.util.*;
import mindustry.*;
import mindustry.core.*;
import mindustry.ctype.*;
import mindustry.game.EventType.*;
import mindustry.game.*;
import mindustry.gen.*;
import mindustry.mod.*;
import mindustry.mod.Mods.*;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/**
 * Full client environment using Arc SDL3 backend.
 * Runs the complete ClientLauncher initialization to get full OpenGL context,
 * shader compilation, atlas loading, and all mod content fully initialized.
 *
 * Cross-platform: LWJGL natives for Linux/Windows/Mac are bundled by Arc.
 * On headless servers, use xvfb-run + Mesa llvmpipe for software rendering.
 */
public class FullTestEnvironment {

    private final String testDataDir;
    private final CopyOnWriteArrayList<String> errorLogs = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<String> warnLogs = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<String> allLogs = new CopyOnWriteArrayList<>();

    private final AtomicBoolean initialized = new AtomicBoolean(false);
    private final AtomicReference<Throwable> initError = new AtomicReference<>();
    private final CountDownLatch testDone = new CountDownLatch(1);
    private final AtomicReference<Runnable> testCallback = new AtomicReference<>();

    private LoadedMod importedMod = null;
    private boolean clientLoaded = false;

    public FullTestEnvironment(String testDataDir){
        this.testDataDir = testDataDir;
    }

    /**
     * Initialize the full client environment and load the mod.
     * Mirrors DesktopLauncher + ClientLauncher setup.
     */
    public void initialize(String modPath) throws Exception {
        if(initialized.get()) return;

        // Clean up previous test data
        Fi dir = new Fi(testDataDir);
        dir.deleteDirectory();

        Log.useColors = false;
        Log.logger = new Log.LogHandler() {
            @Override
            public void log(Log.LogLevel level, String text){
                allLogs.add("[" + level + "] " + text);
                if(level == Log.LogLevel.err){
                    errorLogs.add(text);
                }else if(level == Log.LogLevel.warn){
                    warnLogs.add(text);
                }
            }
        };

        // Set data directory via system property (read by ClientLauncher.setup())
        System.setProperty("mindustry.data.dir", testDataDir);

        Fi modFile = new Fi(modPath);
        if(!modFile.exists()){
            throw new RuntimeException("模组未找到: " + modPath);
        }

        // Prepare mods directory BEFORE SdlApplication starts
        Fi dataFi = new Fi(testDataDir);
        Fi modsDir = dataFi.child("mods");
        modsDir.mkdirs();

        // Copy mod to mods directory (ClientLauncher will auto-scan this dir)
        Fi zipFile;
        if(modFile.isDirectory()){
            zipFile = new Fi(modFile.path() + ".zip");
            zipDirectory(modFile, zipFile);
        }else{
            zipFile = modFile;
        }
        Fi dest = modsDir.child(zipFile.name());
        zipFile.copyTo(dest);

        // Create a custom ClientLauncher that runs tests after full load
        ClientLauncher clientLauncher = new ClientLauncher(){
            @Override
            public void setup(){
                super.setup();



                // Register callback for when client is fully loaded
                Events.on(ClientLoadEvent.class, e -> {
                    clientLoaded = true;
                    Log.info("[FullValidator] 客户端加载完成，模组: " + (importedMod != null ? importedMod.name : "unknown"));

                    // Run test callback if set
                    Runnable callback = testCallback.getAndSet(null);
                    if(callback != null){
                        try{
                            callback.run();
                        }catch(Throwable t){
                            Log.err("[FullValidator] 测试回调崩溃: " + t.getMessage());
                            initError.compareAndSet(null, t);
                        }
                    }

                    // 延迟退出：让渲染线程画几帧，有 GPU 时能看到世界
                    Log.info("[FullValidator] 等待渲染完成...");
                    arc.util.Timer.schedule(() -> {
                        testDone.countDown();
                        Core.app.exit();
                    }, 3);
                });
            }
        };

        // SDL3 config — minimal window, compatible GL version
        SdlConfig config = new SdlConfig();
        config.title = "ModValidator Full Environment";
        config.width = 800;
        config.height = 600;
        config.maximized = false;
        config.coreProfile = false;
        config.glVersions = new int[][]{{3, 0}, {2, 1}, {2, 0}};
        config.allowGl30 = true;

        // Load supplementary native library for NativeUtils (setEnv/unsetEnv/getEnv)
        // BEFORE ArcNativesLoader.load() so the symbols are available when forceUtf8Locale() calls them
        modvalidator.NativeUtilsPatch.ensureLoaded();

        // Launch SDL3 application (blocking until exit)
        // LWJGL automatically loads the correct native library for the current platform
        try{
            new SdlApplication(clientLauncher, config);
        }catch(Throwable t){
            Log.err("[FullValidator] SdlApplication 崩溃: " + t);
            t.printStackTrace();
            initError.compareAndSet(null, t);
            testDone.countDown();
        }

        // Wait for test completion or timeout
        if(!testDone.await(120, TimeUnit.SECONDS)){
            throw new TimeoutException("完整环境初始化超时（120秒）");
        }

        if(initError.get() != null){
            throw new RuntimeException("完整环境初始化失败", initError.get());
        }

        initialized.set(true);
    }

    /**
     * Set the test callback to be executed after client is fully loaded.
     */
    public void setTestCallback(Runnable callback){
        testCallback.set(callback);
    }

    public LoadedMod getImportedMod(){
        // Try to find the mod by scanning loaded mods
        for(LoadedMod mod : Vars.mods.list()){
            if(mod.name != null && !mod.name.isEmpty()){
                return mod;
            }
        }
        return null;
    }

    public boolean hasContentErrors(){
        return Vars.mods.hasContentErrors();
    }

    @SuppressWarnings("unchecked")
    public <T extends Content> Seq<T> getContent(ContentType type){
        return Vars.content.getBy(type);
    }

    public World world(){ return Vars.world; }
    public ContentLoader content(){ return Vars.content; }
    public Mods mods(){ return Vars.mods; }
    public GameState state(){ return Vars.state; }
    public boolean isClientLoaded(){ return clientLoaded; }

    public List<String> getErrorLogs(){ return errorLogs; }
    public List<String> getWarnLogs(){ return warnLogs; }
    public List<String> getAllLogs(){ return allLogs; }

    public void exit(){
        if(Core.app != null){
            Core.app.exit();
        }
    }

    private void zipDirectory(Fi sourceDir, Fi destZip) throws Exception {
        try(java.util.zip.ZipOutputStream zos = new java.util.zip.ZipOutputStream(new java.io.FileOutputStream(destZip.file()))){
            Seq<Fi> files = new Seq<>();
            sourceDir.walk(f -> files.add(f));
            for(Fi f : files){
                String relative = f.path().substring(sourceDir.path().length() + 1);
                if(f.isDirectory()){
                    if(!relative.endsWith("/")) relative += "/";
                    zos.putNextEntry(new java.util.zip.ZipEntry(relative));
                    zos.closeEntry();
                }else{
                    zos.putNextEntry(new java.util.zip.ZipEntry(relative));
                    zos.write(f.readBytes());
                    zos.closeEntry();
                }
            }
        }
    }
}
