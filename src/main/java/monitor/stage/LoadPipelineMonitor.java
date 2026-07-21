package monitor.stage;

import arc.struct.*;
import mindustry.*;
import mindustry.ctype.*;
import mindustry.ctype.UnlockableContent;

import monitor.core.*;
import monitor.report.*;

import java.util.*;

import static mindustry.Vars.*;

/**
 * S2: 加载管线监控
 * 在 Content 生命周期的各阶段抓取字段快照，对比差异，检测异常
 * 适配 full 版：直接使用 Vars.* 环境
 */
public class LoadPipelineMonitor {

    private final MonitorReport report;
    private final ContentLifecycleHook lifecycleHook = new ContentLifecycleHook();

    public LoadPipelineMonitor(MonitorReport report) {
        this.report = report;
    }

    public void run() {
        System.out.println("[S2] 加载管线监控启动");

        try {
            captureAllContent();
            analyzeResults();
        } catch (Exception e) {
            report.addIssue(MonitorReport.Severity.ERROR, "pipeline",
                "加载管线监控失败: " + e.getMessage());
        }
    }

    private void captureAllContent() {
        // 第一遍：收集所有异常
        List<String[]> allAnomalies = new ArrayList<>();
        Map<String, Integer> nullFieldCount = new HashMap<>();
        Map<String, Integer> negativeFieldCount = new HashMap<>();

        for (String type : ContentLifecycleHook.CONTENT_TYPES) {
            ContentType contentType = parseContentType(type);
            if (contentType == null) continue;

            Seq<Content> contents = content.getBy(contentType);
            for (Content c : contents) {
                if (c.minfo.mod == null) continue;

                lifecycleHook.capture(c, "final");

                String contentName = getContentName(c);
                List<ContentLifecycleHook.Anomaly> anomalies =
                    lifecycleHook.detectAnomalies(c.getClass().getSimpleName(), contentName);
                for (ContentLifecycleHook.Anomaly a : anomalies) {
                    allAnomalies.add(new String[]{type, contentName, a.phase, a.field, a.message, a.type});
                    // 统计 NULL_VALUE 类型的字段出现次数
                    if ("NULL_VALUE".equals(a.type)) {
                        nullFieldCount.merge(a.field, 1, Integer::sum);
                    }
                    // 统计 NEGATIVE_VALUE 类型的字段出现次数
                    if ("NEGATIVE_VALUE".equals(a.type)) {
                        negativeFieldCount.merge(a.field, 1, Integer::sum);
                    }
                }
            }
        }

        // 第二遍：输出，过滤高频 null/负值 字段（>=4 个 Content 的同名字段触发则标记为误判）
        int filtered = 0;
        for (String[] a : allAnomalies) {
            boolean isFalsePositive = false;
            // null 误报过滤
            if ("NULL_VALUE".equals(a[5])) {
                Integer count = nullFieldCount.get(a[3]);
                if (count != null && count >= 4) {
                    isFalsePositive = true;
                }
            }
            // 负值误报过滤
            if ("NEGATIVE_VALUE".equals(a[5])) {
                Integer count = negativeFieldCount.get(a[3]);
                if (count != null && count >= 4) {
                    isFalsePositive = true;
                }
            }
            if (!isFalsePositive) {
                report.addIssue(MonitorReport.Severity.WARN, "content-anomaly",
                    String.format("[%s/%s] %s: %s = %s", a[0], a[1], a[2], a[3], a[4]));
            } else {
                filtered++;
            }
        }

        System.out.println("[S2] 已抓取 " + lifecycleHook.getAllKeys().size() + " 个 Content 快照, 过滤误判 " + filtered + " 条");
    }

    private void analyzeResults() {
        for (String key : lifecycleHook.getAllKeys()) {
            String[] parts = key.split(":");
            if (parts.length != 2) continue;
            Map<String, Map<String, FieldSnapshot.ValueDiff>> diffs =
                lifecycleHook.analyzeDiffs(parts[0], parts[1]);
            for (Map.Entry<String, Map<String, FieldSnapshot.ValueDiff>> entry : diffs.entrySet()) {
                for (Map.Entry<String, FieldSnapshot.ValueDiff> diff : entry.getValue().entrySet()) {
                    if ("CHANGED".equals(diff.getValue().type)) {
                        report.addIssue(MonitorReport.Severity.INFO, "field-diff",
                            String.format("%s [%s]: %s: %s -> %s",
                                key, entry.getKey(), diff.getKey(),
                                diff.getValue().before != null ? diff.getValue().before.getRepr() : "null",
                                diff.getValue().after != null ? diff.getValue().after.getRepr() : "null"));
                    }
                }
            }
        }
        System.out.println("[S2] 分析完成");
    }

    private String getContentName(Content c) {
        if (c instanceof UnlockableContent uc) return uc.name;
        return c.toString();
    }

    private ContentType parseContentType(String type) {
        return switch (type) {
            case "block" -> ContentType.block;
            case "unit" -> ContentType.unit;
            case "item" -> ContentType.item;
            case "liquid" -> ContentType.liquid;
            case "bullet" -> ContentType.bullet;
            case "status" -> ContentType.status;
            default -> null;
        };
    }
}
