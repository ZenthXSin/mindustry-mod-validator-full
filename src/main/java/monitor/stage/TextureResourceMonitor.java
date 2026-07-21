package monitor.stage;

import arc.*;
import arc.graphics.g2d.*;
import arc.struct.*;
import mindustry.*;
import mindustry.entities.bullet.*;
import mindustry.type.*;
import mindustry.world.*;

import monitor.report.*;

import java.util.*;

import static mindustry.Vars.*;

/**
 * S3: 贴图资源验证
 * 检测所有 mod 内容的贴图是否存在
 */
public class TextureResourceMonitor {

    private final MonitorReport report;
    private final Set<String> missingSprites = new LinkedHashSet<>();
    private final Set<String> checkedPaths = new HashSet<>();

    public TextureResourceMonitor(MonitorReport report) {
        this.report = report;
    }

    public void run() {
        System.out.println("[S3] 贴图资源验证启动");

        try {
            checkBlockTextures();
            checkUnitTextures();
            checkBulletTextures();
            checkItemTextures();
            checkLiquidTextures();
        } catch (Exception e) {
            report.addIssue(MonitorReport.Severity.ERROR, "texture",
                "贴图验证失败: " + e.getMessage());
        }

        System.out.println("[S3] 贴图验证完成，缺失精灵数: " + missingSprites.size());
    }

    private void checkBlockTextures() {
        Seq<Block> blocks = content.blocks().select(b -> b.minfo.mod != null);
        for (Block block : blocks) {
            checkRegion(block.name + "-icon", "block", block.name);
            checkRegion(block.name + "-preview", "block", block.name);

            String[] suffixes = {"-top", "-glow", "-team", "-heat", "-light", "-rotator",
                "-bottom", "-mid", "-cap", "-edge", "-weave"};
            for (String suffix : suffixes) {
                checkRegion(block.name + suffix, "block", block.name);
            }
        }
    }

    private void checkUnitTextures() {
        Seq<UnitType> units = content.units().select(u -> u.minfo.mod != null);
        for (UnitType unit : units) {
            checkRegion(unit.name, "unit", unit.name);
            checkRegion(unit.name + "-preview", "unit", unit.name);
            checkRegion(unit.name + "-cell", "unit", unit.name);
            checkRegion(unit.name + "-leg-front", "unit", unit.name);
            checkRegion(unit.name + "-leg-back", "unit", unit.name);
            checkRegion(unit.name + "-base", "unit", unit.name);

            for (Weapon weapon : unit.weapons) {
                checkRegion(weapon.name, "weapon", unit.name);
                checkRegion(weapon.name + "-preview", "weapon", unit.name);
                checkRegion(unit.name + "-" + weapon.name, "weapon", unit.name);
            }
        }
    }

    private void checkBulletTextures() {
        Seq<BulletType> bullets = content.bullets().select(b -> b.minfo.mod != null);
        for (BulletType bullet : bullets) {
            String name = bullet.toString();
            checkRegion(name, "bullet", name);
            checkRegion(name + "-preview", "bullet", name);
        }
    }

    private void checkItemTextures() {
        Seq<Item> items = content.items().select(i -> i.minfo.mod != null);
        for (Item item : items) {
            checkRegion(item.name, "item", item.name);
        }
    }

    private void checkLiquidTextures() {
        Seq<Liquid> liquids = content.liquids().select(l -> l.minfo.mod != null);
        for (Liquid liquid : liquids) {
            checkRegion(liquid.name, "liquid", liquid.name);
        }
    }

    private void checkRegion(String name, String contentType, String contentName) {
        if (checkedPaths.add(name)) {
            try {
                TextureRegion region = Core.atlas.find(name);
                if (region == null || region.texture == null) {
                    missingSprites.add(name);
                    report.addIssue(MonitorReport.Severity.WARN, "missing-sprite",
                        String.format("[%s/%s] 缺失贴图: %s", contentType, contentName, name));
                }
            } catch (Throwable t) {
                report.addIssue(MonitorReport.Severity.WARN, "texture-check-error",
                    String.format("[%s/%s] 检查贴图 %s 时出错: %s",
                        contentType, contentName, name, t.getMessage()));
            }
        }
    }
}
