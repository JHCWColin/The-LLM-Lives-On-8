# The LLM Lives On 8 —— 墨水屏上的离线大模型

让一台 **1GB 内存、32 位 ARM（Cortex-A7）、Android 8.1** 的小米多看电纸书跑起来一个**本地英文对话大模型**。
纯离线、零联网，模型体量恰好卡在这台低配设备的极限上。

> 目标不是「它能帮你做什么」，而是「它真的能在这种设备上跑起来，并具备正常的多轮对话能力」。

## ✨ 特性

- **离线本地推理**：模型内置，不依赖任何网络或云端服务。
- **恰好能跑的模型**：SmolLM2-135M-Instruct，Q4_K_M 4bit 量化，约 101MB，运行时峰值内存约 250–350MB。
- **多轮上下文**：支持 ChatML 模板与最近几轮对话上下文。
- **墨水屏优化界面**：黑白高对比、大字号、无动画、流式输出节流刷新，减少残影。
- **双 ABI**：同时内置 `armeabi-v7a`（32 位，本机）与 `arm64-v8a` 库。

## 🚀 快速开始

从 [Releases](../../releases) 下载最新 `EInkChat.apk`（已签名），直接安装到设备即可：

```bash
# 方式一：ADB 安装（推荐，需开启 USB 调试）
adb install EInkChat.apk

# 方式二：拷贝到设备存储后，用文件管理器点击安装
```

首次启动会自动解压模型并加载（约 1–2 分钟），状态栏显示 `Loading model… → Ready` 后即可输入英文对话。

## 📦 设备与选型

| 项目 | 参数 |
|------|------|
| 设备 | 小米多看电纸书（2019 款） |
| SoC | 全志 Allwinner B300，四核 ARM **Cortex-A7** @1.8GHz（**32 位 ARMv7-A**） |
| 内存 | 1GB（32-bit DDR） |
| 存储 | 16GB |
| 系统 | Android 8.1（API 27） |
| 屏幕 | 6 寸 212ppi 电子墨水屏 |

选型结论：**SmolLM2-135M-Instruct**（Apache-2.0），Q4_K_M 量化。
135M 参数 + 4bit 量化是「1GB RAM、32 位 ARM」条件下刚好能跑的平衡点；
备选 SmolLM2-360M / Qwen2.5-0.5B 内存风险过高。

## ⚡ 性能预期

Cortex-A7 是 2011 年的低功耗小核，预期 **1–4 token/s**，一句话回复约 10–40 秒。
这是「能跑起来、能正常对话」的水平，而非流畅体验。

## 🛠 从源码构建

### 环境要求

- Linux + JDK 17/20
- Android SDK（compileSdk 34、build-tools 34.0.0）
- Android NDK r25c
- CMake ≥ 3.16

### 步骤

```bash
# 1. 下载内置模型（约 101MB，国内自动走 hf-mirror 镜像）
cd app && ./download_model.sh && cd ..

# 2. 交叉编译 libllama.so（armeabi-v7a + arm64-v8a）
cd app && ./build_native.sh /path/to/android-ndk-r25c /path/to/llamacpp && cd ..

# 3. 打包并签名 APK
cd app && ./build_apk.sh /path/to/gradle-8.7/bin/gradle /path/to/sdk && cd ..
```

## 📁 目录结构

```
├── app/                        # Android 应用源码
│   ├── app/src/main/java/com/ereader/chat/   # Java（Activity / JNI 封装 / 适配器）
│   ├── app/src/main/res/                      # 墨水屏黑白界面资源
│   ├── app/src/main/assets/models/            # 内置模型（download_model.sh 获取）
│   ├── app/src/main/jniLibs/                  # 预编译 libllama.so（build_native.sh 生成）
│   ├── jni/
│   │   ├── llama_jni.cpp                      # llama.cpp 的 JNI 绑定
│   │   └── CMakeLists.txt                     # 交叉编译配置
│   ├── build_native.sh                        # 编译原生库
│   ├── build_apk.sh                           # 打包签名 APK
│   └── download_model.sh                      # 下载模型
└── llamacpp/                  # llama.cpp 精简源码（含 32 位 ARM 修复）
```

## 🔧 针对 32 位 ARM（Cortex-A7）的关键修复

新版 llama.cpp 对 32 位 ARM 存在两处兼容缺陷，本仓库已修复（重新同步上游时需重新应用）：

1. `ggml/src/ggml-cpu/llamafile/sgemm.cpp`：fp16 加载函数缺少
   `__ARM_FEATURE_FP16_VECTOR_ARITHMETIC` 保护，在 32 位 ARM 上错误使用了
   ARMv8.2 的 fp16 指令（`vld1q_f16` 等）。已改为：有 fp16 特性时用向量指令，
   否则用标量转换。
2. `src/llama-mmap.cpp`：Android bionic 中 `posix_madvise`/`POSIX_MADV_*` 需要
   `_GNU_SOURCE` 且 API ≥ 23。已在文件顶部加 `#define _GNU_SOURCE`，编译平台设为 `android-23`。

## 📄 许可

- 应用代码：MIT（见 [LICENSE](LICENSE)）
- llama.cpp：MIT（见 `llamacpp/LICENSE`）
- SmolLM2 模型：Apache-2.0

## ⚠️ 免责声明

小模型能力有限，回复可能不准确或重复，属正常现象。本项目仅用于本地离线对话演示。
