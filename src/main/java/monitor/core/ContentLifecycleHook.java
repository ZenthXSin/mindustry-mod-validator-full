package monitor.core;

import arc.struct.*;
import mindustry.ctype.*;
import mindustry.ctype.UnlockableContent;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Content 生命周期钩子
 * 在 Content 的各个阶段（构造/init/postInit/loadIcon/load）抓取字段快照
 *
 * 原理：通过 Arc 的 ContentLoader 事件机制，在每个阶段结束时触发快照
 */
public class ContentLifecycleHook {

    /** 每个 Content 的快照序列：阶段 -> 快照 */
    private final Map<String, Map<String, FieldSnapshot>> snapshots = new ConcurrentHashMap<>();
    /** 阶段顺序 */
    public static final String[] PHASES = {"construct", "init", "postInit", "loadIcon", "load"};
    /** 监控的 Content 类型 */
    public static final String[] CONTENT_TYPES = {"block", "unit", "item", "liquid", "bullet", "status"};

    /**
     * 记录一个 Content 在某个阶段的快照
     */
    public void capture(Content content, String phase) {
        try {
            String key = content.getClass().getSimpleName() + ":" + getContentName(content);
            snapshots.computeIfAbsent(key, k -> new LinkedHashMap<>());
            snapshots.get(key).put(phase, new FieldSnapshot(content, phase));
        } catch (Throwable t) {
            // 快照失败不应阻断加载
        }
    }

    private static String getContentName(Content c) {
        if (c instanceof UnlockableContent uc) return uc.name;
        return c.toString();
    }

    /**
     * 获取某个 Content 的所有阶段快照
     */
    public Map<String, FieldSnapshot> getSnapshots(String className, String name) {
        String key = className + ":" + name;
        return snapshots.getOrDefault(key, Collections.emptyMap());
    }

    /**
     * 获取所有已记录的 Content 键
     */
    public Set<String> getAllKeys() {
        return Collections.unmodifiableSet(snapshots.keySet());
    }

    /**
     * 分析某个 Content 的阶段间差异
     */
    public Map<String, Map<String, FieldSnapshot.ValueDiff>> analyzeDiffs(String className, String name) {
        Map<String, FieldSnapshot> snaps = getSnapshots(className, name);
        Map<String, Map<String, FieldSnapshot.ValueDiff>> result = new LinkedHashMap<>();

        FieldSnapshot prev = null;
        String prevPhase = null;
        for (String phase : PHASES) {
            FieldSnapshot current = snaps.get(phase);
            if (current != null && prev != null) {
                result.put(prevPhase + " -> " + phase, prev.diff(current));
            }
            prev = current;
            prevPhase = phase;
        }
        return result;
    }

    /**
     * 检测异常字段值（null 必填字段、越界数值等）
     */
    public List<Anomaly> detectAnomalies(String className, String name) {
        List<Anomaly> anomalies = new ArrayList<>();
        Map<String, FieldSnapshot> snaps = getSnapshots(className, name);

        for (Map.Entry<String, FieldSnapshot> entry : snaps.entrySet()) {
            String phase = entry.getKey();
            FieldSnapshot snap = entry.getValue();

            for (Map.Entry<String, FieldSnapshot.SnapshotValue> field : snap.fields.entrySet()) {
                String fieldName = field.getKey();
                FieldSnapshot.SnapshotValue val = field.getValue();

                // 检测 null 值（可能是未初始化的必填字段）
                if (val.getValue() == null) {
                    anomalies.add(new Anomaly(phase, fieldName, "NULL_VALUE",
                        "字段值为 null，可能未初始化"));
                }

                // 检测负数值（容量、血量等不应为负）
                if (val.getValue() instanceof Number n) {
                    if (n.doubleValue() < 0 && isNonNegativeField(fieldName)) {
                        anomalies.add(new Anomaly(phase, fieldName, "NEGATIVE_VALUE",
                            "字段值为负数: " + n));
                    }
                }
            }

            // 检测快照错误
            for (String error : snap.errors) {
                anomalies.add(new Anomaly(phase, error, "SNAPSHOT_ERROR", error));
            }
        }
        return anomalies;
    }

    private boolean isNonNegativeField(String fieldName) {
        return fieldName.contains("health") || fieldName.contains("capacity")
            || fieldName.contains("damage") || fieldName.contains("range")
            || fieldName.contains("speed") || fieldName.contains("reload");
    }

    public static class Anomaly {
        public final String phase;
        public final String field;
        public final String type;
        public final String message;

        public Anomaly(String phase, String field, String type, String message) {
            this.phase = phase;
            this.field = field;
            this.type = type;
            this.message = message;
        }
    }
}
