package monitor.report;

import java.util.*;
import java.util.stream.*;

/**
 * 报告生成器 — 输出文本或 JSON 格式
 */
public class ReportGenerator {

    private final MonitorReport report;

    public ReportGenerator(MonitorReport report) {
        this.report = report;
    }

    public String toText() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== Mod Deep Monitor 报告 ===\n");
        sb.append("模组路径: ").append(report.modPath).append("\n");
        sb.append("检测时间: ").append(new Date(report.timestamp)).append("\n");
        sb.append("\n");

        // 按严重级别分组
        Map<MonitorReport.Severity, List<MonitorReport.Issue>> bySeverity = report.getIssues().stream()
            .collect(Collectors.groupingBy(i -> i.severity));

        for (MonitorReport.Severity sev : MonitorReport.Severity.values()) {
            List<MonitorReport.Issue> list = bySeverity.getOrDefault(sev, Collections.emptyList());
            if (list.isEmpty()) continue;

            sb.append(String.format("[%s] %d 条\n", sev, list.size()));
            for (MonitorReport.Issue issue : list) {
                sb.append(String.format("  [%s] %s\n", issue.category, issue.message));
            }
            sb.append("\n");
        }

        sb.append(String.format("总计: ERROR=%d, WARN=%d, INFO=%d\n",
            report.getErrorCount(), report.getWarnCount(), report.getInfoCount()));

        return sb.toString();
    }

    public String toJson() {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append(String.format("  \"modPath\": \"%s\",\n", escapeJson(report.modPath)));
        sb.append(String.format("  \"timestamp\": %d,\n", report.timestamp));
        sb.append(String.format("  \"summary\": {\"errors\": %d, \"warnings\": %d, \"info\": %d},\n",
            report.getErrorCount(), report.getWarnCount(), report.getInfoCount()));
        sb.append("  \"issues\": [\n");

        List<MonitorReport.Issue> issues = report.getIssues();
        for (int i = 0; i < issues.size(); i++) {
            MonitorReport.Issue issue = issues.get(i);
            sb.append("    {\n");
            sb.append(String.format("      \"severity\": \"%s\",\n", issue.severity));
            sb.append(String.format("      \"category\": \"%s\",\n", escapeJson(issue.category)));
            sb.append(String.format("      \"message\": \"%s\"\n", escapeJson(issue.message)));
            sb.append("    }");
            if (i < issues.size() - 1) sb.append(",");
            sb.append("\n");
        }

        sb.append("  ]\n}");
        return sb.toString();
    }

    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
    }
}
