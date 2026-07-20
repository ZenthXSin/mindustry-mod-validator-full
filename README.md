# Mindustry Mod Validator (Full Environment)

完整环境版模组验证器——直接跑 Mindustry 原端（`ClientLauncher`），通过 Arc SDL3 backend + LWJGL 提供完整 OpenGL 上下文，支持 shader 编译、资源加载等 headless 环境无法完成的验证。

## 与 Headless 版的区别

| 特性 | Headless 版 | 完整环境版 |
|------|-----------|-----------|
| Arc backend | `backend-headless`（无 GL） | `backend-sdl3`（完整 GL） |
| Shader 编译 | ❌ 不支持 | ✅ 完整支持 |
| 资源加载 | 部分（跳过贴图） | ✅ 完整（atlas, sprites） |
| 运行环境 | 纯 JVM | 需要 GL 上下文（xvfb+Mesa / 真实 GPU） |
| 跨平台 | ✅ | ✅（LWJGL natives 内置） |

## 跨平台支持

LWJGL 3.4.2 自动加载对应平台的 native 库：
- Linux x64 / ARM64
- Windows x64
- macOS x64 / ARM64

## 构建

```bash
./gradlew fatJar
```

输出：`build/libs/mindustry-mod-validator-full-1.0.0-all.jar`

## 运行

### Linux（无显示器服务器）

需要 `xvfb-run` + Mesa（llvmpipe 软件渲染）：

```bash
./run-full.sh <mod-path>
# 或手动：
LIBGL_ALWAYS_SOFTWARE=1 xvfb-run -a java -jar modvalidator-full.jar <mod-path>
```

### Windows / macOS（有 GPU）

```bash
java -jar modvalidator-full.jar <mod-path>
```

### 选项

- `--json`：JSON 格式输出
- `--output <file>`：写入文件
- `--help`：帮助

## 验证流程

1. 启动 SDL3 Application + ClientLauncher
2. 完整初始化：Vars → Assets → Atlas → Shaders → Content → Mods
3. `ClientLoadEvent` 触发后执行测试：
   - 方块测试：放置 → 60 tick（无限火力 + 时间推进）
   - 单位测试：生成 → 60 tick（AI 驱动移动）
4. 输出报告 + 退出

## 依赖

- Mindustry v159.5 + Arc d72b19223f
- LWJGL 3.4.2（SDL3 backend 自带）
- Java 17+

## License

与 Mindustry 相同（GPLv3）
