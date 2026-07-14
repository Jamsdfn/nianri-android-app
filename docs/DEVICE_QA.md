# 念日设备验收记录

日期：2026-07-14
用户目标设备：小米 15 Pro / HyperOS 3.0.304.0

## 状态定义

- `PASS / AUTOMATED`：可重复的单元、Robolectric 或 instrumentation 测试已通过。
- `PASS / EMULATOR`：在表格所列真实模拟器镜像完成安装或界面冒烟。
- `PENDING / PHYSICAL`：必须在用户的小米真机/HyperOS Launcher 上确认，当前不能写成通过。
- `PENDING / MANUAL TIME`：需要真实时间或重启等待，自动化只验证了计算与重建入口。

## Android 兼容矩阵

| 系统 | 镜像 | 安装/启动 | E2E | 通知与精确闹钟 | 系统变化恢复 |
|---|---|---|---|---|---|
| API 26 | `android-26 google_apis arm64-v8a` rev 3 | instrumentation 安装与运行 `PASS / EMULATOR`；主界面启动待补 | `PASS / AUTOMATED`（1/1，Android 8.0.0） | API 26 无运行时通知/精确闹钟特殊授权；调度逻辑为自动化覆盖 | 广播入口 `PASS / AUTOMATED`；全套结果见下方限制 |
| API 31 | `android-31 google_apis arm64-v8a` rev 11 | 安装与主界面冷启动 `PASS / EMULATOR`（Android 12，2566 ms） | `PASS / AUTOMATED`（1/1） | 精确闹钟设置 Intent 解析到系统 `AlarmsAndRemindersAppActivity`；AppOps 为 `Default mode: default`，缺权不降级逻辑 `PASS / AUTOMATED` | 广播入口 `PASS / AUTOMATED`；模拟器广播待验证 |
| API 36 | `android-36 google_apis arm64-v8a` rev 7 | `PASS / EMULATOR` | `PASS / AUTOMATED`（Task 10 场景 1/1） | 通知权限 shell 授权为 `allow`、撤销为 `ignore`，精确闹钟 Intent 解析到系统设置：`PASS / EMULATOR`；状态/09:00 计算 `PASS / AUTOMATED` | 日期/时间/时区/开机重建入口 `PASS / AUTOMATED` |
| API 37.1 | official `android-37.1 google_apis_ps16k arm64-v8a` rev 6 | 安装与主界面冷启动 `PASS / EMULATOR`（Android 17，3300 ms） | `PASS / AUTOMATED`（1/1） | 通用权限与调度逻辑 `PASS / AUTOMATED`；本镜像系统权限页未手工检查 | 广播入口 `PASS / AUTOMATED`；全套结果见下方限制 |

API 37.1 行记录的是 SDK Manager 实际发布的 Android 17 official 16 KB page-size 包名，不把它描述成 preview。未执行的项目保持“待验证”，不能由其他 API 的结果外推。

API 26 构建指纹：`google/sdk_gphone_arm64/generic_arm64:8.0.0/OSR1.180418.028.A3/10734686:userdebug/dev-keys`。

### 四版本全套 instrumentation 诊断

首次同时运行每台 73 项的结果：

- API 31：73/73 通过；API 36：73/73 通过。
- API 26：71/73 通过。一个用例调用 API 29 才存在的 `UiAutomation.adoptShellPermissionIdentity`，现已用 `@SdkSuppress(minSdkVersion = 29)` 明确测试能力边界；另一个用例真实发现 1.3 倍字体下 2×1 辅助标签“农历 ·”底部溢出 1 px。产品仅把该辅助标签从 8.5sp 调至 8sp，核心名称、天数和日期未改变。修复后的 API 26 定向复跑因设备命令审批通道断线而未执行，状态为“待验证”，不能写成 GREEN。
- API 37.1：30/73 通过，43 项 Compose UI 用例在测试初始化阶段同源失败：传递解析的 Espresso 3.5.0 反射调用已移除的 `InputManager.getInstance()`。依赖已显式升级到 Espresso 3.7.0；原失败类 `DetailScreenTest` 定向复跑 3/3 通过。完整 73 项复跑仍待设备命令重新授权。

上述 API 26/37 限制位于测试基础设施或尚待复验的 1 px 兼容修复，不推翻已经独立通过的 E2E、安装和冷启动结果，也不能被描述为“全矩阵全绿”。

## 自动化覆盖

Task 10 的 `EndToEndTest` 使用真实 Room repository、`DayMutationCoordinator`、日期计算/换算、首页投影和小部件 presentation，覆盖：

1. 创建以农历为倒计时基准、默认显示新历的日子；
2. 切换 App 日期显示后，本次实际新历日期和剩余天数均不变；
3. 置顶该日子并创建另一条新历日子；
4. 两个 widget ID 分别选择不同记录和不同显示历法；
5. 删除第一个小部件引用的记录后得到 `MissingDay`；
6. 原 widget ID 能改选剩余记录，另一个小部件选择不受影响。

现有自动化还覆盖本地 09:00 计算、14/7/3 独立请求码、过去时间跳过、通知/精确闹钟权限状态、重启/日期/时间/时区广播、每日审计，以及默认/1.15/1.3/2.0 字体下的 RemoteViews 边界。

真实“上午 9:00 到点收到通知”、整机重启后的 OEM 投递和 HyperOS 省电策略需要真实时间与真机观察，目前是 `PENDING / MANUAL TIME`，不能用计算测试代替。

## 验收图片

| 文件 | 来源与结论 |
|---|---|
| `docs/qa/home.png` | API 36 AVD 上安装 debug APK、创建农历基准记录后的真实首页与新历展示。不是设计稿。 |
| `docs/qa/edit-lunar.png` | API 36 AVD 上真实 App 新建页，已选择“农历基准”；不是设计稿。 |
| `docs/qa/widget-2x1.png` | API 36 `GlanceRemoteViews` 在最小 `110×40dp` 的真实渲染；不是 Launcher 截图。 |
| `docs/qa/widget-2x2.png` | API 36 `GlanceRemoteViews` 在最小 `110×110dp` 的真实渲染；不是 Launcher 截图。 |
| `docs/qa/widget-missing.png` | API 36 `GlanceRemoteViews` 的删除引用恢复态；不是 Launcher 截图。 |

没有生成或伪造 HyperOS Launcher 截图。Launcher 所有的卡片外尺寸、系统显示的“念日”标签及点击交互必须以下一节的真机记录为准。

## 小米 15 Pro / HyperOS 3.0.304.0 真机清单

当前 ADB 仅连接 Android 模拟器，未连接用户的小米 15 Pro。以下全部保持 `PENDING / PHYSICAL`：

| 检查项 | 状态 | 真机步骤 |
|---|---|---|
| 无线配对、重连、覆盖安装 | `PENDING / PHYSICAL` | 按 `docs/DEVELOPMENT.md` 执行 `adb pair`、`adb connect`、`adb install -r`，确认 `adb devices -l` 出现真实设备且非 `offline`。 |
| 2×1 比例与系统标签 | `PENDING / PHYSICAL` | 在 HyperOS 桌面添加 2×1，与用户截图“主卧灯”占格和高度并排比较；确认卡片下方由系统显示“念日”，两行文字不裁切。 |
| 2×2 比例与系统标签 | `PENDING / PHYSICAL` | 与用户截图“天气”小部件占格比较；确认名称、倒数、完整日期、紧凑切换和卡片下方“念日”。 |
| 独立选择与展示 | `PENDING / PHYSICAL` | 添加两个实例，分别选择不同日子和显示历法；点日期只改变写法，剩余天数和目标不变。 |
| 删除与换选 | `PENDING / PHYSICAL` | 删除被一个实例引用的日子，确认显示“这个日子已删除 / 点按选择其他日子”；点按后在原桌面位置改选。 |
| 字体放大 | `PENDING / PHYSICAL` | 系统字体依次默认、较大、最大；确认核心名称/倒数/日期不裁切，辅助标签允许隐藏。 |
| 通知和闹钟权限 | `PENDING / PHYSICAL` | 三个提醒全关时不请求；开启后依次检查通知权限和“闹钟和提醒”；拒绝/撤销显示“提醒未生效”，重新授权后恢复。 |
| HyperOS 省电策略 | `PENDING / PHYSICAL` | 在应用电池设置查看“不限制”选项和通知说明；产品不强制自启动。记录实际设置。 |
| 重启、日期与时区 | `PENDING / PHYSICAL` | 至少配置一条提醒及两个小部件，重启并切换时区/日期后确认倒数、小部件与闹钟计划重建。恢复自动日期/时区。 |
| 上午 9:00 到点投递 | `PENDING / MANUAL TIME` | 配置能在 14/7/3 天窗口命中的记录，保持本地时间自然运行到 09:00；记录通知时间、日期文字和特殊调整说明。 |

## 安全与交付物

- merged manifest 审计未发现 `android.permission.INTERNET` 或 `android.permission.USE_EXACT_ALARM`；应用自身仅声明通知、精确闹钟和开机恢复能力。
- 交付 APK 是 debug APK，不是商店签名 release 包。
- APK：`app/build/outputs/apk/debug/app-debug.apk`
- Task 10 已测试实现提交：`5a2e2c8`（后续仅追加本行证据引用）
- 当前 debug APK SHA-256：`482209f003160a362b90b28a8ae42df3568dd20aac33167ee70c5094003b795f`
- 最终 clean gate：`PENDING`。测试依赖/8sp 修复后的 `assembleDebug` 已通过；之后的 clean unit/lint/assemble 与设备复跑均被审批通道断线阻止，因此此处不虚报完成。
