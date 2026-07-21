package monitor.stage;

import arc.*;
import arc.struct.*;
import mindustry.*;
import mindustry.world.*;

import monitor.report.*;

import java.lang.reflect.*;
import java.util.*;

/**
 * S4: 运行时渲染监控（静态分析模式）
 * 在 headless 环境中检查渲染相关字段
 */
public class RenderPipelineMonitor {

    private final MonitorReport report;

    public RenderPipelineMonitor(MonitorReport report) {
        this.report = report;
    }

    public void run() {
        System.out.println("[S4] 运行时渲染监控启动");

        try {
            checkDrawBlockFields();
            checkGlowTextures();
            checkShaderReferences();
        } catch (Exception e) {
            report.addIssue(MonitorReport.Severity.ERROR, "render",
                "渲染监控失败: " + e.getMessage());
        }
    }

    private void checkDrawBlockFields() {
        Seq<Block> blocks = Vars.content.blocks().select(b -> b.minfo.mod != null);
        for (Block block : blocks) {
            try {
                // 检查 block.drawer（DrawBlock 子类实例）
                Field drawerField = findField(block.getClass(), "drawer");
                if (drawerField != null) {
                    drawerField.setAccessible(true);
                    Object drawer = drawerField.get(block);
                    if (drawer != null) {
                        checkDrawerFields(drawer, block.name);
                    }
                }

                // 检查 rotationDraw
                Field rotDrawField = findField(block.getClass(), "rotationDraw");
                if (rotDrawField != null) {
                    rotDrawField.setAccessible(true);
                    float val = rotDrawField.getFloat(block);
                    if (val < 0) {
                        report.addIssue(MonitorReport.Severity.WARN, "render",
                            "方块 " + block.name + " rotationDraw 为负值: " + val);
                    }
                }
            } catch (Throwable t) {
                report.addIssue(MonitorReport.Severity.WARN, "render",
                    "方块 " + block.name + " DrawBlock 检查异常: " + t.getMessage());
            }
        }
    }

    private void checkDrawerFields(Object drawer, String blockName) {
        Class<?> cls = drawer.getClass();
        String[] animFields = {"warmup", "progress", "heat", "curFrame", "frameSpeed"};
        for (String fieldName : animFields) {
            try {
                Field f = findField(cls, fieldName);
                if (f != null) {
                    f.setAccessible(true);
                    float val = f.getFloat(drawer);
                    if (Float.isNaN(val) || Float.isInfinite(val)) {
                        report.addIssue(MonitorReport.Severity.ERROR, "render",
                            String.format("[%s] DrawBlock.%s 值为非法: %s",
                                blockName, fieldName, val));
                    }
                }
            } catch (Throwable ignored) {}
        }
    }

    private void checkGlowTextures() {
        Seq<Block> blocks = Vars.content.blocks().select(b -> b.minfo.mod != null);
        for (Block block : blocks) {
            if (block.hasShadow) {
                String glowName = block.name + "-glow";
                try {
                    var region = Core.atlas.find(glowName);
                    if (region == null && block.emitLight) {
                        report.addIssue(MonitorReport.Severity.WARN, "render",
                            "方块 " + block.name + " emitLight=true 但缺少 -glow 贴图");
                    }
                } catch (Throwable ignored) {}
            }
        }
    }

    private void checkShaderReferences() {
        Seq<Block> blocks = Vars.content.blocks().select(b -> b.minfo.mod != null);
        for (Block block : blocks) {
            try {
                Field shaderField = findField(block.getClass(), "shader");
                if (shaderField != null) {
                    shaderField.setAccessible(true);
                    Object shader = shaderField.get(block);
                    if (shader != null) {
                        Field isCompiled = findField(shader.getClass(), "isCompiled");
                        if (isCompiled != null) {
                            isCompiled.setAccessible(true);
                            boolean compiled = isCompiled.getBoolean(shader);
                            if (!compiled) {
                                report.addIssue(MonitorReport.Severity.ERROR, "render",
                                    "方块 " + block.name + " 的 Shader 未编译成功");
                            }
                        }
                    }
                }
            } catch (Throwable t) {
                report.addIssue(MonitorReport.Severity.WARN, "render",
                    "方块 " + block.name + " Shader 检查异常: " + t.getMessage());
            }
        }
    }

    private Field findField(Class<?> cls, String name) {
        while (cls != null && cls != Object.class) {
            try {
                return cls.getDeclaredField(name);
            } catch (NoSuchFieldException e) {
                cls = cls.getSuperclass();
            }
        }
        return null;
    }
}
