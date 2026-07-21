package monitor.report;

import java.util.*;

/**
 * 监控报告 — 汇总所有阶段的检测结果
 */
public class MonitorReport {

    public String modPath;
    public String modName;
    public long timestamp = System.currentTimeMillis();

    private final List<Issue> issues = new ArrayList<>();

    public enum Severity { INFO, WARN, ERROR, CRITICAL }

    public void addIssue(Severity severity, String category, String message) {
        issues.add(new Issue(severity, category, message));
    }

    public List<Issue> getIssues() { return Collections.unmodifiableList(issues); }

    public int getErrorCount() {
        return (int) issues.stream().filter(i -> i.severity == Severity.ERROR || i.severity == Severity.CRITICAL).count();
    }

    public int getWarnCount() {
        return (int) issues.stream().filter(i -> i.severity == Severity.WARN).count();
    }

    public int getInfoCount() {
        return (int) issues.stream().filter(i -> i.severity == Severity.INFO).count();
    }

    public static class Issue {
        public final Severity severity;
        public final String category;
        public final String message;

        public Issue(Severity severity, String category, String message) {
            this.severity = severity;
            this.category = category;
            this.message = message;
        }
    }
}
