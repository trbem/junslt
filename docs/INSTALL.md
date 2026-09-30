# 聊天军师 · Android

自用的聊天副驾：点击悬浮球识别当前屏幕，校对后生成关系分析与三条可复制回复。基于 [Jev 聊天助手](https://github.com/jev-chat/jev-chat-jarvis) 与 [狗头军师](https://github.com/shengjidaguai-china/goutoujunshi) 二次开发，不代表原作者出品。

## 安装和使用

要求 **Android 11 或更新系统、ARM64 手机**。安装交付目录中的 `chat-advisor-0.1.0-debug.apk`，这是使用开发签名的自用版本，可与原 Jev 同时安装。

1. 打开「聊天军师」→「模型接口设置」，填写接口并保存，可先运行连通测试。
2. 先从主页「粘贴聊天文本」试用，例如：
   ```text
   我：今天过得怎么样？
   对方：今天好累，不太想说话。
   ```
3. 校对每条消息的我／对方及顺序，选填关系和本轮目标。只有勾选背景资料时才使用手动保存的联系人与笔记。
4. 勾选校对确认，点击「确认并生成军师建议」。候选与军师分析分别显示；复制只复制候选正文。
5. 要使用悬浮入口，在主页开启无障碍「聊天军师」及悬浮窗权限，并按手机需要设置通知、自启动与电池不限制。
6. 打开微信的一对一聊天，点击「军师」球。图片上可以拖出裁剪区域，识别后删除时间／昵称等杂项，合并同一气泡的多行文本，再确认分析。
7. 复制候选，回到微信手动粘贴、审核并点击发送。也可从结果页返回聊天查看悬浮结果。长按悬浮球可导入截图、粘贴文本或查看本轮结果。

**微信直接截图尚需在具体手机和微信版本上验收。** 系统或微信拒绝截图时，会显示失败原因，并提供已有截图导入和手动文本入口。导入截图成功不代表微信直接截图已验证。上游曾报告微信风控并禁用了该功能；本项目使用真实名称的标准服务，不恢复旧自动读取适配器，不保证所有微信版本可截图。

## API 配置

军师生成使用 `/chat/completions` 兼容协议，判断和排序使用 Jev 专用协议，不能把任意普通聊天模型地址当作 Jev 判断接口。

| 路线 | Base URL 示例 | 模型示例 | 密钥 |
| --- | --- | --- | --- |
| 判断／排序 · OpenRouter | `https://openrouter.ai/api` | `typesafe/jev-1.13` | OpenRouter Key |
| 回复 · OpenRouter | `https://openrouter.ai/api/v1` | `deepseek/deepseek-chat-v3.1` | OpenRouter Key |
| 回复 · DeepSeek 官方 | `https://api.deepseek.com/v1` | `deepseek-chat` | DeepSeek Key |
| 回复 · 通义兼容 | `https://dashscope.aliyuncs.com/compatible-mode/v1` | `qwen-plus` | 对应地域和端点的 DashScope Key |

示例来自上游预设，具体可用模型、服务地址和费用以你所选服务商为准。选择预设后点击「应用所选预设」填入地址和模型。接口必须使用 HTTPS。

可只填写回复接口（例如自己的 DeepSeek Key）：Jev 未配置时，仍能获得军师分析与候选，界面明确标注判断、排序不可用，不显示概率。需要 Jev 时再填写判断接口。回复 Key 留空会继承判断 Key；两个服务商的 Key 通常不能互用。

每个测试按钮测试当前填写值，并使用匿名示例，不发送你的真实聊天。测试成功后仍需「保存接口设置」。本 App 没有中转服务器，模型按你账户的用量计费。

## 数据与功能边界

- 截图本机 OCR；确认后才上传文字及启用的背景到配置的模型接口。默认不写截图、聊天、结果或长期关系记忆到磁盘。
- 手动笔记和联系人、接口设置与密钥保存在 App 私有空间，系统备份关闭。截图、文本和结果在本轮内存中使用，清除或进程结束后消失；剪贴板按 Android 系统规则保留。
- 一对一聊天；不保证自动理解语音、表情、图片、引用消息或群聊说话人。OCR 左右位置只是提示，必须由用户校对。裁剪重新识别会替换当前消息，请先确认。
- 仅复制。没有自动读取新消息、自动分析、填入输入框或自动发送。停止助手不等于清除本轮内容，可在校对页明确清除。
- 结果包括已知事实、推测与未知；Jev 意图及排序概率都是模型提示，排序占比不是恋爱成功率。明确拒绝时不建议继续推进。

完整说明：[隐私说明](PRIVACY.md)、[验证记录](docs/VALIDATION.md)、[真机验收清单](docs/DEVICE-ACCEPTANCE.md)、[工程说明](docs/IMPLEMENTATION.md)。

## 本地构建

需要 JDK 17、Android SDK platform 35 / build-tools 35.0.0，Gradle 由仓库 wrapper 下载。

PowerShell（按机器实际路径设置环境）：

```powershell
$env:JAVA_HOME = 'D:\ltrj\.tools\java\jdk-17.0.20.1+1'
$env:ANDROID_HOME = 'D:\ltrj\.tools\android-sdk'
$env:GRADLE_USER_HOME = 'D:\ltrj\.tools\gradle-cache'
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug --console=plain
```

另一台机器请配置自己的 `local.properties`：`sdk.dir=实际SDK路径`。它不会进入源码交付。APK 输出为 `app/build/outputs/apk/debug/app-debug.apk`。

可运行 `scripts/build.ps1` 自动发现当前工作区的构建工具。可选 `-PqaAbi=x86_64` 仅用于模拟器 QA，交付 APK 默认仍为 ARM64。Release 签名必须自行通过 `CHAT_ADVISOR_KEYSTORE_PROPS` 指定仓库外的 properties 文件，不使用上游签名。

## 开源来源

- Jev 基线：`1af6f3d2d50df8ae1abd0c53790737cdaf45a0c3`，保留根目录 `LICENSE` 与 `NOTICE`。
- 狗头军师资料基线：`6db7354a4002dc7c448a9c87ffdad8132570c9d3`，许可位于 `app/src/main/assets/advisor/LICENSE-goutoujunshi.txt`。
- 完整上游规则作为参考随包保存；App 的行为内核经过即时回复适配，参考资料按场景最多载入三份，不安装 Codex Skill，也不依赖 Codex 运行。
- 上游说明归档在 `docs/upstream/`；App「关于」页也提供来源及许可全文。


## 0.1.0 发布补充

下载：[GitHub Release](https://github.com/trbem/junslt/releases/tag/v0.1.0)。用户反馈截图导入失败、悬浮不稳定；建议生成可用。聊天及建议仅在内存中，0.1.1 才计划增加持久化。完整现状见 [0.1.0 说明](releases/0.1.0.md)。
