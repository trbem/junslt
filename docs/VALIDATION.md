# 验证记录 · 2026-09-30

## 已通过

执行：`gradlew.bat testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest --console=plain`。

- 构建成功；Android 11+（minSdk 30）、target／compile SDK 35。
- 交付包应用 ID：`com.ltrj.chatadvisor`；应用名称：聊天军师；版本：0.1.0。
- 交付 APK 仅包含 `arm64-v8a` 的 ML Kit 原生库；APK Signature Scheme v2 验签通过。
- 41 项 JVM 测试全部通过，0 失败、0 错误。其中 4 项 Robolectric Android Activity 交互测试；其余覆盖结构化结果、格式重试、校对、OCR 顺序与裁剪坐标、资料路由、取消与失败降级，以及 16 项上游会话／输入写入组件回归测试。
- Robolectric 测试实际创建主页、设置页与校对页，验证身份、未确认／未知说话人拦截、复制仅含候选正文、内容编辑撤销确认与旧结果，以及消息上移下移／删除。它们使用匿名固定样例，不调用模型。
- Android Lint：0 错误，24 警告。警告包括部分上游未使用资源／兼容代码、中文界面字符串国际化、仅 ARM64 定位及程序化 View 提示；未将警告当作全部清除。
- 后续在雷电 Android 14（API 34，型号 25102RKBEC）安装原 ARM64 APK，3 项 Android 仪器测试通过，使用匿名固定样例；未运行微信流程。
- `git diff --check` 通过。

## 尚未验证

- 开发侧没有连接用户手机。雷电通用界面测试已通过，但模拟器无法使用微信，不能替代微信兼容性验收。用户手机型号、Android 和微信版本未提供。
- 微信直接截图、悬浮球完整手机流程、已有截图导入及原生 ML Kit OCR 需要真机验证。JVM 的裁剪坐标和校对逻辑测试不能替代实际 OCR。
- 开发侧未配置真实模型密钥、未执行付费模型调用。用户自行在手机测试后反馈建议生成逻辑可用；不据此声称全部模型路线及场景已通过。
- 截图拒绝／空识别／模型超时的备用入口已实现，具体平台行为与实际服务商故障仍需实测。

交付包 SHA256：`F3FB9FE6DA0CDC9017C989D5B2D8E7A6BB4F53AD9B803C4218F05AF1E43EB9CD`。

## 用户手机反馈

建议生成可用；悬浮窗经常消失，导入聊天截图不可用，聊天与建议未持久保存，联系人缺少快捷导入和模型辅助文本导入。上述问题仍存在于 0.1.0，下一版本计划见 ROADMAP.md。
