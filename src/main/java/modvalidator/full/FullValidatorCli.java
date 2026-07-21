package modvalidator.full;

import arc.struct.*;
import arc.util.*;
import mindustry.ctype.*;
import mindustry.mod.*;

import java.util.*;

/**
 * CLI entry point for the full-environment mod validator.
 *
 * Usage:
 *   xvfb-run -a java -jar modvalidator-full.jar <mod-path>
 *   java -jar modvalidator-full.jar --help
 *
 * Cross-platform: LWJGL natives for Linux/Windows/Mac are bundled.
 * On headless servers, use xvfb-run + Mesa llvmpipe for software GL.
 */
public class FullValidatorCli {

    public static void main(String[] args){
        if(args.length == 0 || args[0].equals("--help") || args[0].equals("-h")){
            printHelp();
            return;
        }

        String modPath = null;
        boolean jsonOutput = false;
        String outputFile = null;
        String reportDir = null;

        for(int i = 0; i < args.length; i++){
            switch(args[i]){
                case "--json" -> jsonOutput = true;
                case "--output", "-o" -> {
                    if(i + 1 < args.length){
                        outputFile = args[++i];
                    }else{
                        System.err.println("错误: --output 需要指定文件路径");
                        System.exit(1);
                    }
                }
                case "--report", "-r" -> {
                    if(i + 1 < args.length){
                        reportDir = args[++i];
                    }else{
                        System.err.println("错误: --report 需要指定目录路径");
                        System.exit(1);
                    }
                }
                case "--help", "-h" -> {
                    printHelp();
                    return;
                }
                default -> {
                    if(!args[i].startsWith("-") && modPath == null){
                        modPath = args[i];
                    }else if(args[i].startsWith("-")){
                        System.err.println("未知选项: " + args[i]);
                        System.exit(1);
                    }
                }
            }
        }

        if(modPath == null){
            System.err.println("错误: 未指定模组路径。使用 --help 查看用法。");
            System.exit(1);
        }

        String testDataDir = System.getProperty("java.io.tmpdir") + "/modvalidator-full-data";

        long startTime = System.currentTimeMillis();
        FullTestEnvironment env = new FullTestEnvironment(testDataDir);
        FullValidator.ValidationResult result = new FullValidator.ValidationResult();
        result.modPath = modPath;

        try{
            System.out.println("正在验证模组（完整环境）: " + modPath);
            System.out.println("========================================");

            // Set up the test callback before initialization
            FullValidator validator = new FullValidator(env, result);
            env.setTestCallback(() -> validator.runAllTests());

            // Initialize full client environment (blocks until tests complete)
            env.initialize(modPath);

            result.loadTimeMs = System.currentTimeMillis() - startTime;

            // Output report
            String report = jsonOutput ? generateJsonReport(result) : generateTextReport(result);
            System.out.println(report);

            if(outputFile != null){
                java.nio.file.Files.writeString(java.nio.file.Path.of(outputFile), report);
                System.out.println("报告已写入: " + outputFile);
            }

            // Generate structured report directory
            if(reportDir != null){
                generateStructuredReport(result, env, reportDir);
            }

            System.exit(result.hasErrors() ? 1 : 0);

        }catch(Exception e){
            System.err.println("致命错误: " + e.getMessage());
            Throwable cause = e.getCause();
            while(cause != null){
                System.err.println("  Caused by: " + cause.getClass().getSimpleName() + ": " + cause.getMessage());
                cause = cause.getCause();
            }
            System.exit(2);
        }finally{
            env.exit();
        }
    }

    private static String generateTextReport(FullValidator.ValidationResult result){
        StringBuilder sb = new StringBuilder();
        sb.append("========================================\n");
        sb.append("  Mindustry 模组验证报告（完整环境）\n");
        sb.append("========================================\n\n");
        sb.append("模组: ").append(result.modName.isEmpty() ? "(未识别)" : result.modName).append("\n");
        sb.append("路径: ").append(result.modPath).append("\n");
        sb.append("加载耗时: ").append(result.loadTimeMs).append("ms\n");
        sb.append("状态: ").append(result.hasErrors() ? "失败" : "通过").append("\n\n");
        sb.append("--- Summary ---\n");
        sb.append("  错误: ").append(result.getErrorCount()).append("\n");
        sb.append("  警告: ").append(result.getWarnCount()).append("\n");
        sb.append("  信息: ").append(result.getInfoCount()).append("\n\n");
        sb.append("--- Issues ---\n");
        for(var issue : result.getIssues()){
            sb.append("  [").append(issue.severity()).append("] [").append(issue.category()).append("] ").append(issue.message()).append("\n");
        }
        sb.append("\n========================================\n");
        return sb.toString();
    }

    private static String generateJsonReport(FullValidator.ValidationResult result){
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("  \"modName\": \"").append(escapeJson(result.modName)).append("\",\n");
        sb.append("  \"modPath\": \"").append(escapeJson(result.modPath)).append("\",\n");
        sb.append("  \"loadTimeMs\": ").append(result.loadTimeMs).append(",\n");
        sb.append("  \"status\": \"").append(result.hasErrors() ? "failed" : "passed").append("\",\n");
        sb.append("  \"errors\": ").append(result.getErrorCount()).append(",\n");
        sb.append("  \"warnings\": ").append(result.getWarnCount()).append(",\n");
        sb.append("  \"info\": ").append(result.getInfoCount()).append(",\n");
        sb.append("  \"issues\": [\n");
        var issues = result.getIssues();
        for(int i = 0; i < issues.size(); i++){
            var issue = issues.get(i);
            sb.append("    {\"severity\": \"").append(issue.severity())
              .append("\", \"category\": \"").append(escapeJson(issue.category()))
              .append("\", \"message\": \"").append(escapeJson(issue.message())).append("\"}");
            if(i < issues.size() - 1) sb.append(",");
            sb.append("\n");
        }
        sb.append("  ]\n}\n");
        return sb.toString();
    }

    private static String escapeJson(String s){
        if(s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
    }

    /**
     * Generate a structured report directory with normalized files.
     * <mod-name>/
     *   summary.json          — 总览（模组名、版本、加载时间、错误统计）
     *   issues.json           — 全部问题列表
     *   issues-by-severity/   — 按严重级别分文件
     *     error.json
     *     warning.json
     *     info.json
     *   issues-by-category/   — 按类型分文件
     *     content-parse.json
     *     block-test.json
     *     unit-test.json
     *     shader.json
     *     ...
     *   content/              — 注册的模组内容清单
     *     blocks.json
     *     units.json
     *     items.json
     *     liquids.json
     *     bullets.json
     *   logs/                 — 原始日志
     *     full.log
     *     errors.log
     *     warnings.log
     */
    private static void generateStructuredReport(FullValidator.ValidationResult result, FullTestEnvironment env, String reportDir){
        try {
            java.nio.file.Path dir = java.nio.file.Path.of(reportDir);
            java.nio.file.Files.createDirectories(dir);

            // summary.json
            StringBuilder summary = new StringBuilder();
            summary.append("{\n");
            summary.append("  \"modName\": \"").append(escapeJson(result.modName)).append("\",\n");
            summary.append("  \"modPath\": \"").append(escapeJson(result.modPath)).append("\",\n");
            summary.append("  \"loadTimeMs\": ").append(result.loadTimeMs).append(",\n");
            summary.append("  \"testTimeMs\": ").append(result.testTimeMs).append(",\n");
            summary.append("  \"status\": \"").append(result.hasErrors() ? "failed" : "passed").append("\",\n");
            summary.append("  \"errors\": ").append(result.getErrorCount()).append(",\n");
            summary.append("  \"warnings\": ").append(result.getWarnCount()).append(",\n");
            summary.append("  \"info\": ").append(result.getInfoCount()).append("\n");
            summary.append("}\n");
            java.nio.file.Files.writeString(dir.resolve("summary.json"), summary.toString());

            // issues.json — all issues
            StringBuilder allIssues = new StringBuilder();
            allIssues.append("[\n");
            var issues = result.getIssues();
            for(int i = 0; i < issues.size(); i++){
                var issue = issues.get(i);
                allIssues.append("  {\"severity\": \"").append(issue.severity())
                    .append("\", \"category\": \"").append(escapeJson(issue.category()))
                    .append("\", \"message\": \"").append(escapeJson(issue.message())).append("\"}");
                if(i < issues.size() - 1) allIssues.append(",");
                allIssues.append("\n");
            }
            allIssues.append("]\n");
            java.nio.file.Files.writeString(dir.resolve("issues.json"), allIssues.toString());

            // issues-by-severity/
            java.nio.file.Path sevDir = dir.resolve("issues-by-severity");
            java.nio.file.Files.createDirectories(sevDir);
            for(var sev : FullValidator.ValidationResult.Severity.values()){
                StringBuilder sb = new StringBuilder();
                sb.append("[\n");
                var list = issues.stream().filter(i -> i.severity() == sev).toList();
                for(int i = 0; i < list.size(); i++){
                    var issue = list.get(i);
                    sb.append("  {\"category\": \"").append(escapeJson(issue.category()))
                        .append("\", \"message\": \"").append(escapeJson(issue.message())).append("\"}");
                    if(i < list.size() - 1) sb.append(",");
                    sb.append("\n");
                }
                sb.append("]\n");
                java.nio.file.Files.writeString(sevDir.resolve(sev.name().toLowerCase() + ".json"), sb.toString());
            }

            // issues-by-category/
            java.nio.file.Path catDir = dir.resolve("issues-by-category");
            java.nio.file.Files.createDirectories(catDir);
            var categories = issues.stream().map(i -> i.category()).distinct().toList();
            for(String cat : categories){
                StringBuilder sb = new StringBuilder();
                sb.append("[\n");
                var list = issues.stream().filter(i -> i.category().equals(cat)).toList();
                for(int i = 0; i < list.size(); i++){
                    var issue = list.get(i);
                    sb.append("  {\"severity\": \"").append(issue.severity())
                        .append("\", \"message\": \"").append(escapeJson(issue.message())).append("\"}");
                    if(i < list.size() - 1) sb.append(",");
                    sb.append("\n");
                }
                sb.append("]\n");
                String fileName = cat.replaceAll("[^a-zA-Z0-9_-]", "_") + ".json";
                java.nio.file.Files.writeString(catDir.resolve(fileName), sb.toString());
            }

            // content/ — mod content inventory
            java.nio.file.Path contentDir = dir.resolve("content");
            java.nio.file.Files.createDirectories(contentDir);
            writeContentList(contentDir, "blocks", env, mindustry.ctype.ContentType.block);
            writeContentList(contentDir, "units", env, mindustry.ctype.ContentType.unit);
            writeContentList(contentDir, "items", env, mindustry.ctype.ContentType.item);
            writeContentList(contentDir, "liquids", env, mindustry.ctype.ContentType.liquid);
            writeContentList(contentDir, "bullets", env, mindustry.ctype.ContentType.bullet);

            // logs/
            java.nio.file.Path logDir = dir.resolve("logs");
            java.nio.file.Files.createDirectories(logDir);
            java.nio.file.Files.writeString(logDir.resolve("full.log"), String.join("\n", env.getAllLogs()));
            java.nio.file.Files.writeString(logDir.resolve("errors.log"), String.join("\n", env.getErrorLogs()));
            java.nio.file.Files.writeString(logDir.resolve("warnings.log"), String.join("\n", env.getWarnLogs()));

            System.out.println("结构化报告已生成: " + dir.toAbsolutePath());

        } catch(Exception e){
            System.err.println("生成结构化报告失败: " + e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private static void writeContentList(java.nio.file.Path dir, String name, FullTestEnvironment env, mindustry.ctype.ContentType type){
        try {
            arc.struct.Seq<? extends Content> contents = env.getContent(type);
            StringBuilder sb = new StringBuilder();
            sb.append("[\n");
            for(int i = 0; i < contents.size; i++){
                var c = contents.get(i);
                String cName = (c instanceof mindustry.ctype.UnlockableContent uc) ? uc.name : c.toString();
                sb.append("  {\"name\": \"").append(escapeJson(cName)).append("\", \"type\": \"").append(c.getClass().getSimpleName()).append("\"}");
                if(i < contents.size - 1) sb.append(",");
                sb.append("\n");
            }
            sb.append("]\n");
            java.nio.file.Files.writeString(dir.resolve(name + ".json"), sb.toString());
        } catch(Exception e){
            // skip
        }
    }

    private static void printHelp(){
        System.out.println("Mindustry 模组验证器（完整环境）— 动态 JSON/JS 内容测试工具");
        System.out.println();
        System.out.println("用法:");
        System.out.println("  xvfb-run -a java -jar modvalidator-full.jar <模组路径> [选项]");
        System.out.println();
        System.out.println("参数:");
        System.out.println("  <模组路径>       模组文件(.zip/.jar)或目录的路径");
        System.out.println();
        System.out.println("选项:");
        System.out.println("  --json           以 JSON 格式输出报告");
        System.out.println("  --output, -o     将报告写入文件");
        System.out.println("  --report, -r     生成结构化报告到指定目录");
        System.out.println("  --help, -h       显示此帮助信息");
        System.out.println();
        System.out.println("跨平台说明:");
        System.out.println("  LWJGL natives 已内置（Linux/Windows/Mac）");
        System.out.println("  无显示器服务器需用 xvfb-run + Mesa 软件渲染");
        System.out.println();
        System.out.println("退出码:");
        System.out.println("  0  验证通过");
        System.out.println("  1  验证失败（发现错误）");
        System.out.println("  2  致命错误");
    }
}
