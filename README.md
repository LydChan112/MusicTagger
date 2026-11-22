# MusicTagger

一个 Material 3 风格的 Android 音乐标签编辑器：扫描本地音频 → 在线匹配歌曲信息 / 歌词 / 封面 → 写入文件标签。

> **AI 使用说明**：本项目在 AI 编程助手（DeepSeek）协作下完成，详见文末 [AI 使用说明](#ai-使用说明)。

## 功能

| 功能 | 说明 |
|---|---|
| 扫描音乐 | 用 SAF 选择文件夹（可持久授权），递归扫描 mp3 / flac / m4a / ogg / wav 等 |
| 基本信息 | 读取标题、艺术家、专辑、专辑艺术家、年份、音轨、流派、注释，以及格式、码率、采样率、时长、大小 |
| 按文件名搜索 | 标签为空白或不可靠时，从文件名解析「歌名 / 歌手」再搜索：支持 `01. 歌手 - 歌名`、`歌手《歌名》`、`歌名(歌手)`、下划线、全角破折号，自动去掉 `[FLAC]`、`320k`、`无损` 等噪音 |
| 一键匹配并写入 | 多音源并发搜索，按相似度取最佳结果，自动填信息、下封面、抓歌词并写回文件 |
| 多选批量 + 匹配结果页 | 批量为「先搜索、再看结果、后写入」：结果页列出每首歌的封面、匹配歌名/歌手/专辑/来源/匹配度 |
| 标签查重 | 已有标题+艺术家与候选一致 → 标「重复·跳过」不重写；同批两首匹配到同一首 → 标「列表内重复」；其余才写入 |
| 候选列表（带封面） | 点结果页任意一行可弹出带封面的候选列表，换一个立即重新判定 |
| 搜歌词 | 所选音源 → LRCLIB → 网易云 → QQ 音乐，逐级回退；支持 LRC 预览与手动编辑 |
| 歌词写入 | 歌词作为标签字段写进音频文件（MP3 的 USLT、FLAC/OGG 的 LYRICS、M4A 的 ©lyr），不生成额外的 `.lrc` 文件 |
| 搜封面 | 列表只用缩略图（约 10–40 KB）；写入时取最高分辨率，超过 500 KB 自动重编码压到 500 KB 以内 |
| 深色模式 | Material 3 DayNight，跟随系统，也可在菜单手动切换并记忆 |
| 设置 | 时长容差过滤（关闭 / ±3 / ±5 / ±10 / ±20 / ±30 秒）、搜索并行数（1–5）、下载并行数（1–8）、GitHub 项目入口、版本号 |

## 构建

需要 JDK 17 与 Android SDK 34：

```bash
git clone <this repo>
cd MusicTagger
./gradlew assembleDebug
# 产物：app/build/outputs/apk/debug/app-debug.apk
```

`local.properties` 里的 `sdk.dir` 指向本机 SDK。首次构建需要联网下载 AGP 与 AndroidX 依赖。

想用 GitHub Actions 自动构建，把 `ci/build.yml.example` 复制到 `.github/workflows/build.yml` 即可
（推送 workflow 文件要求 token 带 `Workflows` 权限）。

## 架构

```
app/src/main/java/com/musictagger/app/
├── net/Http.java              极简 HttpURLConnection 封装（gzip、超时、错误码）
├── model/                     Song、Track（在线候选）、Lyrics、MatchItem
├── provider/                  MusicProvider 接口 + 网易云 / 酷狗 / QQ 音乐 / iTunes / LRCLIB
│   ├── ProviderHub.java       并发搜索、相似度打分、来源健康探测、封面/歌词回退链
├── storage/SafStore.java      SAF 树扫描、缓存拷贝、写回原文件
├── tag/TagIO.java             jaudiotagger 读写标签与封面（FLAC 避开 java.awt）
├── service/                   AutoTagger（匹配 / 写入）、BatchSession
├── ui/                        MainActivity、EditActivity、MatchReviewActivity、候选弹窗
└── util/                      文件名解析、查重、格式识别、MP3 修复、封面压缩
```

几处值得说明的实现：

- **来源健康探测**：批量开始前并行探测全部音源，不通的源本轮跳过，避免每首歌都等一次超时。
- **MP3 修复**：遇到 `No audio header found` 时扫描第一个合法 MPEG 帧，裁掉前面损坏/伪造的 ID3 头。
- **格式识别**：按文件头（`fLaC` / `OggS` / `ID3` / MPEG 同步字…）判断真实容器，不轻信扩展名。
- **封面压缩**：写入前按 JPEG 质量 90→50、必要时缩边，硬上限 500 KB。

## 数据来源

全部为公开 Web 接口，无需登录、无需 AppKey：

- 网易云音乐：`/api/search/get`（搜索）、`/api/song/detail`（专辑图）、`/api/song/lyric`（歌词）
- QQ 音乐：`c.y.qq.com/soso/fcgi-bin/client_search_cp`（搜索）、`y.gtimg.cn`（专辑图）、`fcg_query_lyric_new`（base64 LRC）
- 酷狗：`mobilecdn.kugou.com/api/v3/search/song`、`/api/v3/album/info`
- iTunes Search API：封面与元数据兜底
- LRCLIB：带时间轴的歌词

## 免责声明

- 本项目仅用于**整理本地音乐文件**，不提供、不下载任何音频内容。
- 第三方接口均为非官方公开接口，可能随时变更或失效；项目与各音乐平台无任何关联。
- 请遵守你所在地区的法律与各平台服务条款，不要用于商业用途或批量抓取。

## AI 使用说明

本项目明确标注：**代码主要由 AI 编程助手生成，作者负责需求、取舍与验证。**

协作方式：

| 环节 | AI 参与 | 人工参与 |
|---|---|---|
| 需求拆解、功能取舍 | 提供方案与权衡 | 决定做什么 |
| Android 代码（UI / 网络 / 标签写入） | 生成 | 审阅、提出修改 |
| 在线接口调研 | 用脚本实测各接口可用性与返回格式 | — |
| 疑难定位 | 依据堆栈定位（如 `No audio header found`、批量卡住） | 提供真机现象 |
| 构建与发版 | 配置工具链、编译、打包 | 安装试用 |

已验证的部分：

- Gradle 构建通过（AGP 8.5.2 / Gradle 8.7 / JDK 17 / compileSdk 34）；
- 桌面单测：文件名解析 14 条、查重 8 条、MP3 头修复、歌词写入标签、标签读写回环，全部通过；
- 接口连通性：网易云 / 酷狗 / QQ 音乐 / iTunes / LRCLIB 均实测返回 200。

受环境限制未能自动验证：真机 UI 交互、SAF 写回、部分机型的兼容性。

如果你要基于本项目继续开发，请把 AI 生成的代码当作初稿对待：跑一遍测试，按自己的数据再验证一次。

## 许可证

[MIT](LICENSE)
