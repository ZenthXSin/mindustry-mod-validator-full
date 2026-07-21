# Mindustry Mod Validator (Full Environment)

完整环境版模组验证器——直接跑 Mindustry 原端（`ClientLauncher`），通过 Arc SDL3 backend 提供完整 OpenGL 上下文，支持 shader 编译、资源加载等 headless 环境无法完成的验证。

## 与 Headless 版的区别

| 特性 | Headless 版 (`mindustry-mod-validator`) | 完整环境版 |
|------|-----------|-----------|
| Arc backend | `backend-headless`（无 GL） | `backend-sdl3`（完整 GL） |
| Shader 编译 | ❌ 崩溃（`Core.gl == null`） | ✅ 完整支持 |
| 资源加载 | 部分（跳过贴图） | ✅ 完整（atlas, sprites） |
| 运行环境 | 纯 JVM | 需要 GL 上下文（xvfb+Mesa / 真实 GPU） |
| 验证能力 | 仅内容注册错误 | 内容注册 + shader 编译 + 运行时行为 |

## 项目结构

完整环境版由三个独立仓库组成：

| 仓库 | 作用 |
|------|------|
| [mindustry-mod-validator-full](https://github.com/ZenthXSin/mindustry-mod-validator-full) | 验证器主项目（本仓库） |
| [arc-natives](https://github.com/ZenthXSin/arc-natives) | Arc 预编译 native 库（全平台） |
| [mindustry-assets](https://github.com/ZenthXSin/mindustry-assets) | Mindustry 完整资源（shaders/sprites.aatls/fonts 等） |

## 运行环境要求

### Linux（无显示器服务器）

需要 `xvfb-run` + Mesa（llvmpipe 软件渲染）：

```bash
# Ubuntu/Debian 安装依赖
sudo apt install xvfb mesa-utils libegl1-mesa-dev libgl1-mesa-dri

# 运行
./run-full.sh <mod-path>
```

### Windows / macOS（有 GPU）

```bash
java -jar modvalidator-full.jar <mod-path>
```

## 构建

```bash
./gradlew fatJar
```

输出：`build/libs/mindustry-mod-validator-full-1.0.0-all.jar`

**注意**：构建前需要先构建 `arc-natives` 和 `mindustry-assets`，或者将它们的 jar 放到 `../arc-natives/build/libs/` 和 `../mindustry-assets/build/libs/` 目录下。

## 运行选项

```bash
java -jar modvalidator-full.jar <mod-path> [选项]
```

- `--json`：JSON 格式输出
- `--output <file>`：将报告写入文件
- `--help`：显示帮助

## 验证流程

1. 启动 SDL3 Application + ClientLauncher（完整初始化）
2. 加载所有资源：Vars → Atlas → Shaders → Content → Mods
3. `ClientLoadEvent` 触发后执行动态测试：
   - **方块测试**：放置 → 60 tick（无限火力 + 时间推进）
   - **单位测试**：生成 → 60 tick（AI 驱动移动）
4. 输出报告 + 退出

## 验证报告示例

```
模组: voidshield
路径: /path/to/VoidShield.jar
加载耗时: 42909ms
状态: 通过

--- Summary ---
  错误: 0
  警告: 0
  信息: 1
```

## 已验证模组

| 模组 | 状态 | 备注 |
|------|------|------|
| Aeronautics | ✅ 通过 | 完整加载 + 60 tick 测试 |
| VoidShield | ✅ 通过 | 含 shader 编译（default/heat/test） |

## 技术架构

```
┌─────────────────────────────────────────┐
│         mindustry-mod-validator-full     │
│  ┌─────────────────────────────────┐    │
│  │  FullTestEnvironment            │    │
│  │  - SDL3 Application             │    │
│  │  - ClientLauncher.setup()       │    │
│  │  - ClientLoadEvent → run tests  │    │
│  └─────────────────────────────────┘    │
│  ┌─────────────────────────────────┐    │
│  │  FullValidator                  │    │
│  │  - testBlocks(): 60 tick        │    │
│  │  - testUnits(): 60 tick + AI    │    │
│  └─────────────────────────────────┘    │
└─────────────────────────────────────────┘
           │ depends on           │ depends on
           ▼                      ▼
┌──────────────────┐    ┌──────────────────┐
│   arc-natives    │    │ mindustry-assets │
│ (libarc64.so +   │    │ (shaders/sprites │
│  freetype + etc) │    │  /fonts/bundles) │
└──────────────────┘    └──────────────────┘
```

## 依赖版本

| 依赖 | 版本 | 来源 |
|------|------|------|
| Mindustry | v159.5 | JitPack |
| Arc | d72b19223f | JitPack |
| LWJGL | 3.4.2-SNAPSHOT | Sonatype Snapshots |
| Rhino | e74ac129d2 | JitPack |
| Java | 17+ | — |

## 已知限制

- **内存占用**：完整客户端约 2-4GB（headless 版约 500MB）
- **加载时间**：首次加载约 30-45 秒（资源解压 + atlas 构建）
- **软件渲染**：无 GPU 服务器依赖 Mesa llvmpipe，性能低但功能完整
- **联网**：会尝试获取服务器列表（约 5-10 秒），不影响验证结果

## License

与 Mindustry 相同（GPLv3）
