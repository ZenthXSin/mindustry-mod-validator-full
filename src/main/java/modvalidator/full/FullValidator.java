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

    /**
     * Run all tests. Must be called from within the ClientLoadEvent callback
     * (i.e., after full client initialization).
     */
    public void runAllTests(){
        // Wait for content to be fully loaded
        if(!env.isClientLoaded()){
            result.addIssue(ValidationResult.Severity.ERROR, "full-env", "客户端未完全加载");
            return;
        }

        // Set up game state: playing + infinite resources
        Vars.state.set(GameState.State.playing);
        Vars.state.rules.infiniteResources = true;

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

        // Run dynamic tests
        testBlocks();
        testUnits();
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

        long deadline = System.currentTimeMillis() + 30000;
        int x = 2, y = 2;
        for(Block block : blocks){
            if(System.currentTimeMillis() > deadline){
                result.addIssue(ValidationResult.Severity.WARN, "block-test", "方块测试超时（30s），跳过剩余");
                break;
            }
            try{
                world.tile(x, y).setBlock(block, Team.get(0), 0);

                // 60 tick with time advancement
                for(int i = 0; i < 60; i++){
                    if(world.tile(x, y).build != null){
                        Time.delta = 1f;
                        Time.update();
                        world.tile(x, y).build.update();
                    }
                }
            }catch(Throwable t){
                result.addIssue(ValidationResult.Severity.ERROR, "block-test",
                    "方块 '" + block.name + "' 更新时崩溃: " + t.getClass().getSimpleName() + ": " + t.getMessage());
            }

            x++;
            if(x >= size - 1){ x = 2; y++; }
            if(y >= size - 1) break;
        }
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

        long deadline = System.currentTimeMillis() + 30000;
        for(UnitType unit : units){
            if(System.currentTimeMillis() > deadline){
                result.addIssue(ValidationResult.Severity.WARN, "unit-test", "单位测试超时（30s），跳过剩余");
                break;
            }
            try{
                Unit spawned = unit.create(Team.get(0));
                spawned.set(32f, 32f);
                spawned.add();

                // 60 tick with time advancement for AI movement
                for(int i = 0; i < 60; i++){
                    Time.delta = 1f;
                    Time.update();
                    spawned.update();
                }
            }catch(Throwable t){
                result.addIssue(ValidationResult.Severity.ERROR, "unit-test",
                    "单位 '" + unit.name + "' 崩溃: " + t.getClass().getSimpleName() + ": " + t.getMessage());
            }
        }
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
