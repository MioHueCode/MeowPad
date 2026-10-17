# MeowPad

一个本地优先的 Android Markdown 笔记应用。所有笔记以纯文本 + JSON 形式保存在本机存储，不上传任何服务器。

## 功能

- **Markdown 编辑**：实时分色标注（标题 / 加粗 / 斜体 / 代码 / 链接 / 引用），不改变原文
- **阅读 / 编辑双态**：一键切换，阅读模式内联渲染图片、视频、音频、文件附件，链接可点击
- **代码模式**：等宽字体 + 语法高亮 + 行号
- **AI 助手面板**：可配置任意 OpenAI 兼容接口（Base URL / Model / API Key），支持润色、续写、摘要，结果一键回填正文
- **组织方式**：文件夹、标签、置顶、收藏、自动分类（链接 / 图片 / 视频 / 文件 / 音频 / 文本）
- **数据管理**：回收站、备份、附件迁移、存储路径自定义
- **其他**：应用锁密码、离线工具箱、中英文切换、多套猫咪配色主题

## 技术栈

| 项 | 版本 |
|---|---|
| 语言 | Kotlin 2.0.21 |
| UI | Jetpack Compose（BOM 2024.09.00） |
| 构建 | AGP 8.7.3 / Gradle 8.14.2 |
| SDK | minSdk 26 · targetSdk 34 · compileSdk 34 |
| 应用版本 | 1.9.0（versionCode 9） |

## 构建

```bash
# Debug
./gradlew :app:assembleDebug

# Release（已启用 R8 代码压缩 + 资源裁剪，需自备签名配置）
./gradlew :app:assembleRelease
```

Release 构建默认不包含签名配置，请自行在 `app/build.gradle.kts` 中添加 `signingConfigs`，
或对产物使用 `apksigner` 签名。

## 数据存储

笔记与附件默认位于：

```
/storage/emulated/0/MeowPad/
├── notes/      # 笔记 JSON
├── images/
├── videos/
├── audios/
├── files/
├── trash/      # 回收站
└── backup/     # 备份
```

路径可在应用内「设置 → 数据」中修改。卸载应用不会删除这些文件。

## 开源协议

本项目采用 **GNU AGPL-3.0** 协议，详见 [LICENSE](LICENSE)。

选择 AGPL-3.0 的原因：项目内嵌了 `kmark` Markdown 解析器
（源自 JetBrains IntelliJ 平台，经 [Kori](https://github.com/MioHueCode) 工程整理），
且界面交互设计大量参考 Kori 项目，二者均为 AGPL-3.0 协议。
依据 AGPL 的传染性条款，衍生作品需以相同协议发布。

## 致谢

- [JetBrains IntelliJ Platform](https://github.com/JetBrains/intellij-community) — `kmark` Markdown 解析器
- [Kori](https://github.com/MioHueCode) — 编辑器交互与视觉设计参考
- [Kotlin](https://kotlinlang.org/) / [Jetpack Compose](https://developer.android.com/jetpack/compose)
