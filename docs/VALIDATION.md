# 0.1.1 验证记录 · 2026-09-30

## 已通过

执行：`gradlew.bat testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest --console=plain`。

- 60项JVM/Robolectric测试通过，0失败、0错误；原41项回归仍通过，新增19项。
- 存储：草稿和完整建议（含判断、排序、警告）读写，清空RAM后恢复，重复写入真实替换、备份恢复、原子写入失败/文件损坏不虚报成功、历史保留、联系人解绑及清空范围。
- 联系人导入：多人、缺失姓名、空结果、原文依据校验、格式最多重试一次、网络异常传播、UTF-8/BOM/GB18030、文件及字数上限。
- 悬浮策略：实际应用窗口选择、键盘/系统/自有浮层不直接覆盖应用身份、应用切换及分屏焦点。
- Activity交互：暂停后保存、清空内存后新Activity恢复、聊天快捷保存联系人、无资料不请求模型、历史候选复制及打开记录时克隆会话。
- Android构建及仪器测试APK编译成功；Lint 0错误、26警告，包含上游资源、国际化、ABI定位等警告。
- minSDK30、target/compileSDK35，应用ID `com.ltrj.chatadvisor`，versionName0.1.1、versionCode2，原生库仅ARM64。
- APK签名与0.1.0一致，证书SHA256：`e81e9375004b9b8e35fb46036aaf57e76abccdbbdd27e8d7ff4dbc386cfe3e9b`；v2验签、16KB zipalign及diff格式检查通过。

## 仍待手机与真实模型复测

- 本轮ADB没有连接设备；未执行0.1.1原生仪器测试、覆盖安装、微信截图与悬浮窗完整流程。此前0.1.0雷电Android14的3项测试不计为0.1.1通过。
- 用户确认0.1.0手机上的回复生成可用；这不代表0.1.1联系人模型提取已通过真实接口。本轮没有使用真实API密钥或付费模型调用。
- 悬浮稳定性仍需记录手机/Android/微信版本，测试键盘、菜单、通知栏、锁屏/解锁、服务断开恢复和快速重复点击。
- 截图导入完整修复按用户要求暂缓；共享布局保护不替代实际导入及OCR验收。

新APK SHA256：`e45ed3ec2ec51b00016c832d1ab33d0fe4f37063e80acadfcf2dc0999245e3d4`。
