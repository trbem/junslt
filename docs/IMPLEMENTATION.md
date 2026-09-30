# 工程说明

应用 ID 为 `com.ltrj.chatadvisor`；内部 Kotlin namespace 保留 `com.jev.probe`，用于复用 Jev 代码，不影响与 Jev 并存。无障碍服务为真实应用的 `com.jev.probe.capture.ChatCaptureService`，不注册上游伪装的系统服务类。

## 数据流

前台包名事件 → 手动悬浮球 → `ScreenCapture` → 内存截图 → `ReviewActivity` / `CropPreview` → `MlKitOcr` → 用户校对 → `AdvisorClient` → `AdvisorPipeline` → 结果页／`BubbleOverlay` → 仅复制候选。

截图最低间隔 1 秒；失败退避上限 30 秒，回调 watchdog 为 3 秒；截图取消／超时后恢复悬浮状态并丢弃迟到回调。标准服务截图被拒绝时，不自动尝试其他机制，提供导入或文本入口。

截图先裁剪。OCR 保留行矩形坐标与屏幕顺序；左、右提示只是候选说话人，居中的行为 unknown。用户须选择我／对方、删除时间等杂项、合并多行并确认。确认之后聊天最多 12000 字；所有行非空、所有说话人已映射，才允许发送。

## 模型与资源

Jev 使用原有选择／评分接口；生成模型使用 OpenAI-compatible chat completions 协议。Jev 是概率提示，不能成为已知事实。`AdvisorPipeline` 支持独立判断、生成、排序失败测试；生成失败直接报错，绝不补占位候选。格式错误只多请求一次。

`AdvisorResult` 有 emotion、facts、inferences、unknowns、recommendation、nextStep、stopCondition、三条 strategy/text 候选及可选概率、判断与警告。解析验证所有必需字段和恰好三条不同且非空候选；概率来自有效 Jev 排序，失败时为 null。

核心提示词常驻；`KnowledgeRouter` 选择 reply + 一个主题（安全优先）+ digital，最多三份参考。用户背景和聊天放入独立的证据 JSON，不能覆盖系统行为。按话题路由是启发式选择，不是语义检索。原始来源与许可随 assets 分发；参考内容为固定基线，不宣称在线验证法律或医疗信息。

## 会话与生命周期

`ManualSession` 仅在 RAM。新输入替换当前会话，取消旧 gate；每个 OCR／分析 callback 同时检查会话对象与请求 generation、Activity 生命周期。内容或目标修改会取消旧请求、清除结果并撤销确认。离开／销毁校对 Activity 会取消后台任务，不在后台偷偷继续展示结果。旋转后可复用仍在内存的输入和已有结果，未完成请求须重新触发；进程死亡后内容消失。

模型 HTTP 超时与状态由共享 `HttpJson` 处理；401 等不补格式重试，429／繁忙和传输重试沿用上游实现。原始错误响应不进入界面或日志。取消未必立即中止 socket，但旧结果绝不重新显示。

手动资料匹配沿用 Jev 本地知识库；分析路径强制不记历史。无账号、收费、支付、远程存储或自动关系档案。

## 构建与测试

JDK 17 / SDK 35 / Gradle 8.9 / AGP 8.7.3 / Kotlin 1.9.24。保留上游 ConversationSession 与 GuardedInputWriter 的回归测试；新增解析、校对、路由、gate、格式重试及分阶段失败测试。遗留适配器和旧 OverlayController 作为上游代码留存，未接入新手动服务，不代表这些自动采集或填入能力可用。

本机验收和真实模型效果必须与单元测试分开记录，不因解析及模拟返回成功而声称实际模型质量通过。见 DEVICE-ACCEPTANCE.md 和 VALIDATION.md。
