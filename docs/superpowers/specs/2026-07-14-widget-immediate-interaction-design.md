# 小部件即时切换与稳定配置设计

## 目标

- 点击小部件底部日期文字或其右侧切换图标，立即切换新历/农历展示。
- 切换只改变展示历法，不改变日子的倒计时基准。
- 任一小部件切换后，App 与所有绑定同一日子的小部件立即读取同一状态。
- 新建或编辑小部件选择日子并保存后，桌面在配置页结束前完成重绘，不再依赖退出重进或后续系统刷新。

## 根因

当前联动事务会立即更新 Room，但 `AndroidWidgetInstanceUpdater` 只发送
`ACTION_APPWIDGET_UPDATE` 广播便返回。更关键的是，2×1/2×2 的 `provideGlance` 在
`provideContent` 外读取 Room 并捕获 `WidgetModel`；活跃 Glance 会话收到更新后仍可能重绘旧模型。
因此数据库和 App 已经正确，小部件外观却不变；首次配置也会看起来像没有选中。

## 方案

保留按日子统一的 `appDisplay` 数据模型与现有独立日期点击区。将实例更新器改为：

1. 通过 `AppWidgetManager` 找到实例对应的 provider。
2. 每次更新重新从 Room 生成最新 `WidgetModel`，写入该实例的 Glance Preferences 状态。
3. 2×1/2×2 的组合只读取 `currentState<Preferences>()`，不再捕获首次加载的模型。
4. 写入状态后调用并等待对应 `GlanceAppWidget.update` 完成；配置页随后才设置成功并结束。
5. 未绑定实例继续使用包内广播作为安全回退，外部 provider 仍拒绝更新。

`WidgetConfigActivity` 使用空 `taskAffinity` 并从最近任务中排除。它向桌面返回 `RESULT_OK` 后正常
`finish()`，自然回到启动它的小米桌面；不额外发送会干扰 AppWidget 配置协议的 HOME Intent。

日期文字与切换图标继续位于同一条横向铺满的点击区域；主要内容区打开 App，两个动作不重叠。

## 验证

- 单元测试证明已绑定的 2×1/2×2 实例走直接更新且不会再发送延迟广播。
- 控制器测试证明一次切换更新日子状态及全部同日实例。
- 配置测试证明一次选择和保存即可绑定目标日子。
- 布局测试证明日期与图标仍共享独立、全宽切换热区。
- 真实 `AppWidgetHost` 测试证明活跃宿主中的可见日期控件在一次联动后立即替换。
- 运行 JVM、Lint、API 26/31/36/37.1 设备矩阵，并覆盖安装到小米 15 Pro 真机。
