package monitor.stage;

import arc.*;
import arc.graphics.*;
import arc.graphics.g2d.*;
import arc.graphics.gl.*;
import arc.struct.*;
import mindustry.*;
import mindustry.graphics.*;

import monitor.report.*;

import java.lang.reflect.*;

/**
 * S5: OpenGL 渲染管线监控（full 版独有）
 * 监控 GL 版本、Shader 编译状态、Atlas 加载
 */
public class GLRenderMonitor {

    private final MonitorReport report;

    public GLRenderMonitor(MonitorReport report) {
        this.report = report;
    }

    public void run() {
        System.out.println("[S5] GL 渲染管线监控启动");

        try {
            checkGLVersion();
            checkShaderCompilation();
            checkAtlasState();
        } catch (Exception e) {
            report.addIssue(MonitorReport.Severity.ERROR, "gl-monitor",
                "GL 监控失败: " + e.getMessage());
        }
    }

    private void checkGLVersion() {
        try {
            GLVersion ver = Core.graphics.getGLVersion();
            if (ver != null) {
                report.addIssue(MonitorReport.Severity.INFO, "gl-version",
                    "GL: " + ver.type + " " + ver.majorVersion + "." + ver.minorVersion);
            }
        } catch (Throwable t) {
            report.addIssue(MonitorReport.Severity.WARN, "gl-version",
                "无法获取 GL 版本: " + t.getMessage());
        }
    }

    private void checkShaderCompilation() {
        // 通过反射检查 Shaders 类的静态字段
        Class<?> shadersClass = Shaders.class;
        int compiled = 0;
        int failed = 0;
        int total = 0;

        for (Field field : shadersClass.getDeclaredFields()) {
            if (!Modifier.isStatic(field.getModifiers())) continue;
            if (!Shader.class.isAssignableFrom(field.getType())) continue;

            total++;
            try {
                field.setAccessible(true);
                Shader shader = (Shader) field.get(null);
                if (shader != null) {
                    // isCompiled is private, use reflection
                    Field isCompField = Shader.class.getDeclaredField("isCompiled");
                    isCompField.setAccessible(true);
                    boolean isComp = isCompField.getBoolean(shader);
                    if (isComp) {
                        compiled++;
                    } else {
                        failed++;
                        report.addIssue(MonitorReport.Severity.WARN, "shader",
                            "Shader 未编译: " + field.getName());
                    }
                }
            } catch (Throwable ignored) {}
        }

        report.addIssue(MonitorReport.Severity.INFO, "shader",
            "Shader 编译: " + compiled + "/" + total + " (" + failed + " 失败)");
    }

    private void checkAtlasState() {
        TextureAtlas atlas = Core.atlas;
        if (atlas != null) {
            report.addIssue(MonitorReport.Severity.INFO, "texture",
                "Atlas 已加载");
        } else {
            report.addIssue(MonitorReport.Severity.WARN, "texture",
                "Atlas 未初始化");
        }
    }
}
