package modvalidator.full;

import arc.*;
import arc.struct.*;
import arc.util.*;
import mindustry.*;
import mindustry.core.*;
import mindustry.core.GameState.*;
import mindustry.ctype.*;
import mindustry.game.*;
import mindustry.gen.*;
import mindustry.mod.*;
import mindustry.mod.Mods.*;
import mindustry.type.*;
import mindustry.world.*;

import monitor.report.*;
import monitor.stage.*;

import java.util.*;
import java.util.concurrent.*;

/**
 * Runs mod validation using the full client environment.
 * Tests blocks (60 tick + infinite resources) and units (60 tick + AI movement).
 */
public class FullValidator {

    private final FullTestEnvironment env;
    private final ValidationResult result;

    public FullValidator(FullTestEnvironment env, ValidationResult result){
        this.env = env;
        this.result = result;
    }

    private final MonitorReport deepReport = new MonitorReport();

    /**
     * Run all tests. Must be called from within the ClientLoadEvent callback
     * (i.e., after full client initialization).
     * 五阶段管线：S3 贴图验证 → S4 渲染分析 → S5 GL管线 → 动态测试 → S2 字段检测
     */
    public void runAllTests(){
        long testStart = System.currentTimeMillis();

        // Wait for content to be fully loaded
        if(!env.isClientLoaded()){
            result.addIssue(ValidationResult.Severity.ERROR, "full-env", "客户端未完全加载");
            return;
        }

        // Set up game state: playing + infinite resources
        Vars.state.set(GameState.State.playing);
        Vars.state.rules.infiniteResources = true;

        // 初始化 MinimapRenderer pixmap，避免 Renderer.update() 时 NPE
        try{
            if(Vars.renderer.minimap.getPixmap() == null){
                if(Vars.world.width() < 1) Vars.world.resize(1, 1);
                Vars.renderer.minimap.reset();
            }
        }catch(Throwable t){
            // minimap 初始化失败不影响验证
        }

        // Collect mod info
        LoadedMod mod = env.getImportedMod();
        if(mod != null){
            result.modName = mod.name;
        }

        // Check content errors
        if(env.hasContentErrors()){
            for(LoadedMod m : Vars.mods.list()){
                if(m.hasContentErrors()){
                    for(Content cont : m.erroredContent){
                        result.addIssue(ValidationResult.Severity.ERROR, "content-parse",
                            "[" + cont.minfo.sourceFile.path() + "] " +
                            (cont.minfo.baseError != null ? cont.minfo.baseError.getMessage() : "unknown"));
                    }
                }
            }
        }

        // S3: 贴图资源验证
        new TextureResourceMonitor(deepReport).run();

        // S4: 运行时渲染监控
        new RenderPipelineMonitor(deepReport).run();

        // S5: GL 渲染管线监控（full 版独有）
        new GLRenderMonitor(deepReport).run();

        // 动态测试：创建世界、放置方块/单位、运行 60 tick
        testBlocks();
        testUnits();

        // S2: 字段异常检测（在世界加载并运行后检测，此时字段已完全初始化）
        new LoadPipelineMonitor(deepReport).run();

        // 将深度监控结果合并到主报告
        mergeDeepReport();

        result.testTimeMs = System.currentTimeMillis() - testStart;
    }

    private void mergeDeepReport(){
        for(MonitorReport.Issue issue : deepReport.getIssues()){
            result.addIssue(ValidationResult.Severity.valueOf(issue.severity.name()),
                issue.category, issue.message);
        }
    }

    @SuppressWarnings("unchecked")
    private void testBlocks(){
        Seq<Block> blocks = new Seq<>();
        for(Content c : env.getContent(ContentType.block)){
            if(c.minfo != null && c.minfo.mod != null) blocks.add((Block)c);
        }

        if(blocks.isEmpty()){
            result.addIssue(ValidationResult.Severity.INFO, "block-test", "未找到模组方块进行测试");
            return;
        }

        World world = env.world();
        int size = Math.max(16, blocks.size * 2 + 4);
        world.resize(size, size);
        world.tiles.fill();

        long deadline = System.currentTimeMillis() + 120000;
        int x = 2, y = 2;
        int tested = 0, crashed = 0;
        for(Block block : blocks){
            if(System.currentTimeMillis() > deadline){
                result.addIssue(ValidationResult.Severity.WARN, "block-test", "方块测试超时（120s），跳过剩余");
                break;
            }
            try{
                world.tile(x, y).setBlock(block, Team.get(0), 0);

                // 600 tick with time advancement
                for(int i = 0; i < 600; i++){
                    if(world.tile(x, y).build != null){
                        Time.delta = 1f;
                        Time.update();
                        world.tile(x, y).build.update();
                    }
                }
                tested++;
            }catch(Throwable t){
                crashed++;
                result.addIssue(ValidationResult.Severity.ERROR, "block-test",
                    "方块 '" + block.name + "' 更新时崩溃: " + t.getClass().getSimpleName() + ": " + t.getMessage());
            }

            x++;
            if(x >= size - 1){ x = 2; y++; }
            if(y >= size - 1) break;
        }
        result.addIssue(ValidationResult.Severity.INFO, "block-test",
            "方块测试完成: " + tested + " 通过, " + crashed + " 崩溃, 共 " + blocks.size + " 个 (600 tick)");
    }

    @SuppressWarnings("unchecked")
    private void testUnits(){
        Seq<UnitType> units = new Seq<>();
        for(Content c : env.getContent(ContentType.unit)){
            if(c.minfo != null && c.minfo.mod != null) units.add((UnitType)c);
        }

        if(units.isEmpty()){
            result.addIssue(ValidationResult.Severity.INFO, "unit-test", "未找到模组单位进行测试");
            return;
        }

        World world = env.world();
        if(world.width() < 10 || world.height() < 10){
            world.resize(64, 64);
            world.tiles.fill();
        }

        long deadline = System.currentTimeMillis() + 120000;
        int tested = 0, crashed = 0;
        for(UnitType unit : units){
            if(System.currentTimeMillis() > deadline){
                result.addIssue(ValidationResult.Severity.WARN, "unit-test", "单位测试超时（120s），跳过剩余");
                break;
            }
            try{
                Unit spawned = unit.create(Team.get(0));
                spawned.set(32f, 32f);
                spawned.add();

                // 在单位附近放一个敌方单位作为靶子，让AI能开火
                mindustry.gen.Unit target = mindustry.content.UnitTypes.dagger.create(Team.get(1));
                target.set(64f, 32f);
                target.add();

                // 600 tick with time advancement
                for(int i = 0; i < 600; i++){
                    Time.delta = 1f;
                    Time.update();
                    spawned.update();
                    target.update();
                }
                // 清理
                spawned.remove();
                target.remove();
                tested++;
            }catch(Throwable t){
                crashed++;
                result.addIssue(ValidationResult.Severity.ERROR, "unit-test",
                    "单位 '" + unit.name + "' 崩溃: " + t.getClass().getSimpleName() + ": " + t.getMessage());
            }
        }
        result.addIssue(ValidationResult.Severity.INFO, "unit-test",
            "单位测试完成: " + tested + " 通过, " + crashed + " 崩溃, 共 " + units.size + " 个 (600 tick + 开火)");
    }

    /**
     * Lightweight validation result (mirrors the headless version's structure).
     */
    public static class ValidationResult {
        public String modPath = "";
        public String modName = "";
        public boolean loadSuccess = true;
        public long loadTimeMs = 0;
        public long testTimeMs = 0;

        public enum Severity { INFO, WARN, ERROR, CRITICAL }

        public record Issue(Severity severity, String category, String message){}

        private final List<Issue> issues = new ArrayList<>();

        public void addIssue(Severity severity, String category, String message){
            issues.add(new Issue(severity, category, message));
        }

        public List<Issue> getIssues(){ return issues; }

        public boolean hasErrors(){
            return issues.stream().anyMatch(i -> i.severity == Severity.ERROR || i.severity == Severity.CRITICAL);
        }

        public long getErrorCount(){
            return issues.stream().filter(i -> i.severity == Severity.ERROR || i.severity == Severity.CRITICAL).count();
        }

        public long getWarnCount(){
            return issues.stream().filter(i -> i.severity == Severity.WARN).count();
        }

        public long getInfoCount(){
            return issues.stream().filter(i -> i.severity == Severity.INFO).count();
        }
    }
}
