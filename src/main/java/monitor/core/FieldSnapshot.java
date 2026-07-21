package monitor.core;

import java.lang.reflect.*;
import java.util.*;

/**
 * 字段快照 — 记录某一时刻对象的所有字段值
 * 基于 mod-tools FieldUtils 的反射机制
 */
public class FieldSnapshot {

    /** 目标对象类名 */
    public final String className;
    /** 目标对象 identityHashCode */
    public final int objectId;
    /** 快照时间戳 */
    public final long timestamp;
    /** 快照阶段（构造/init/postInit/loadIcon/load） */
    public final String phase;
    /** 字段名 -> 字段值 */
    public final Map<String, SnapshotValue> fields = new LinkedHashMap<>();
    /** 异常的字段 */
    public final List<String> errors = new ArrayList<>();

    public FieldSnapshot(Object obj, String phase) {
        this.className = obj.getClass().getName();
        this.objectId = System.identityHashCode(obj);
        this.timestamp = System.currentTimeMillis();
        this.phase = phase;
        capture(obj);
    }

    private void capture(Object obj) {
        Class<?> cls = obj.getClass();
        while (cls != null && cls != Object.class) {
            for (Field field : cls.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers())) continue;
                try {
                    field.setAccessible(true);
                    Object value = field.get(obj);
                    String key = cls.getSimpleName() + "." + field.getName();
                    fields.put(key, new SnapshotValue(field.getType(), value));
                } catch (Throwable e) {
                    errors.add(field.getName() + ": " + e.getMessage());
                }
            }
            cls = cls.getSuperclass();
        }
    }

    /**
     * 对比两个快照，返回差异字段
     */
    public Map<String, ValueDiff> diff(FieldSnapshot other) {
        Map<String, ValueDiff> diffs = new LinkedHashMap<>();
        for (Map.Entry<String, SnapshotValue> entry : fields.entrySet()) {
            String key = entry.getKey();
            SnapshotValue myVal = entry.getValue();
            SnapshotValue otherVal = other.fields.get(key);
            if (otherVal == null) {
                diffs.put(key, new ValueDiff(myVal, null, "REMOVED"));
            } else if (!Objects.equals(myVal.getRepr(), otherVal.getRepr())) {
                diffs.put(key, new ValueDiff(myVal, otherVal, "CHANGED"));
            }
        }
        for (String key : other.fields.keySet()) {
            if (!fields.containsKey(key)) {
                diffs.put(key, new ValueDiff(null, other.fields.get(key), "ADDED"));
            }
        }
        return diffs;
    }

    public static class SnapshotValue {
        private final Class<?> type;
        private final Object value;

        public SnapshotValue(Class<?> type, Object value) {
            this.type = type;
            this.value = value;
        }

        public Class<?> getType() { return type; }
        public Object getValue() { return value; }

        public String getRepr() {
            if (value == null) return "null";
            if (type.isArray()) {
                if (value instanceof int[] a) return Arrays.toString(a);
                if (value instanceof float[] a) return Arrays.toString(a);
                if (value instanceof long[] a) return Arrays.toString(a);
                if (value instanceof boolean[] a) return Arrays.toString(a);
                if (value instanceof Object[] a) return Arrays.deepToString(a);
                return value.toString();
            }
            if (value instanceof String s) return "\"" + s + "\"";
            return value.toString();
        }
    }

    public static class ValueDiff {
        public final SnapshotValue before;
        public final SnapshotValue after;
        public final String type;

        public ValueDiff(SnapshotValue before, SnapshotValue after, String type) {
            this.before = before;
            this.after = after;
            this.type = type;
        }
    }
}
