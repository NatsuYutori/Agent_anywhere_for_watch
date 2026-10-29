# Agents Anywhere for Watch（OPPO Watch X2 / X3）

这是一个给 **OPPO Watch X 系列（466×466 圆形屏）** 用的 **遥控端** APK，基于上游
[`anywhere-labs/Agents-Anywhere`](../../) 的 Android 客户端源码构建。应用名就是
**Agents Anywhere for Watch**（`watch/src/main/res/values/strings.xml` 的 `app_name`）。

手表 **不执行** Agent 任务，只做遥控器：真正的 Agent 跑在装了 Connector 的电脑或手机上。
这个分工是上游本身的设计（`android/ARCHITECTURE.md`：手机负责控制，工作设备负责执行），
手表版只是把「控制」这一半做成适合圆屏的四个界面。

---

## 一、四个界面

登录成功后进入主界面，一共四页，**左右滑动切换**：

| 页面 | 内容 |
| --- | --- |
| **对话**（默认页） | 当前 Agent 的对话时间线 + 底部「输入框 + 发送」一行；**回合运行中这一行整体变成「终止」** |
| **工作区** | 多个 **工作目录**（项目）与其中的 **对话** 列表，点一下即切换并跳回对话页；**多设备可按设备过滤**，顶部有 **「＋ 新会话」** |
| **操作** | **接管/释放**、**切换模型**、**切换权限**、**待审批**按钮、重新读取模型与权限 |
| **账号** | 官方字体 wordmark「Agents Anywhere」+ **头像与昵称**、邮箱、角色、邮箱验证状态、**绑定/验证邮箱**、服务器地址、**退出登录** |

界面示意图（按源码 1:1 还原，**不是真机截图**）：
[`mockups/ui-preview.png`](mockups/ui-preview.png)，源文件 [`mockups/ui-preview.html`](mockups/ui-preview.html)。
v2 的三处改动另见 [`mockups/ui-preview-v2.png`](mockups/ui-preview-v2.png)
（源文件 [`mockups/ui-preview-v2.html`](mockups/ui-preview-v2.html)）。

### 交互上的几个取舍

- **对话没有气泡框**。圆屏只有约 165dp 宽，气泡的左右留白太贵，所以层次改用字号和颜色：
  代理正文 12sp 最亮 → 自己的发言 11sp **靠右对齐**、右侧一条强调色竖线 → 其余一律小字。
- **思考与工具调用默认折叠**成一行 9sp 灰字，点一下展开、再点收起；每行前面有一枚小图标
  （调用工具=锤子，调用命令行=终端，写文件=笔，搜索=放大镜，读=清单，取网页=刷新……）。
  被折叠的类型：思考、命令、工具调用、文件改动、子代理、诊断、产物、标记。
- **输入框和发送并排一行**贴在底部（整行 34dp 高），键盘不会挡住输入。输入框**永远可编辑**：
  运行时状态是轮询来的，早先让 `enabled` 跟着它每几秒翻转一次，手表输入法在编辑过程中被禁用
  会掉字、退格也会失去同步；现在 `enabled = true`，只灰化发送键。发送键 28dp、只画符号
  （发送=纸飞机，终止=方块），省下的宽度都给了输入框，输入字号 11sp 配 4dp 内边距 —— 默认的
  13sp + 9dp 内边距比这一行本身还高，字会被切掉。
- **回合运行中，输入行整个变成「终止」**：一个控件一个含义，不用先「终止并发送」；停下之后
  输入框立刻回来。判断依据是 `turnInFlight || runtime.status == Running`（`Waiting` /
   `WaitingApproval` 不挡输入）。终止是一个居中的圆形按钮（只画方块），不再是一条占满整行的长条。
- **进入对话页永远落在最新一条**：`scrollToItem`（瞬时而非动画）在列表还没测量时就能记下目标，
  而 `animateScrollToItem` 需要一个已经排好版的目标，页面刚重建的那一刻它可能什么也不做。
  新消息到达时只在「用户本来就在底部」时才跟随，往回翻看历史不会被拽回去。
- **思考中是一枚转动的圆环**（四分之一亮、四分之三暗）配 9sp 加粗的「思考中」；先前的三点呼吸
  动画在同一块地方看起来更像占位符。
- **表冠滚动**：表冠事件在 Activity 层就被拦下，一格约 30dp，只滚动当前页，不翻页，也不漏给页面里的滚动容器。
  机制和标定方法见下面「表冠」一节。
- **空态是一枚居中的图标**（对话页=空对话气泡，操作页=一排滑杆），每次划入该页重播一次淡入，
  不再写「还没有选择对话，向右滑…」那种提示句子。
- **操作页有主次**：顶部运行时 / 状态与用量是 7sp 标签 + 9sp **加粗**值（`WatchInfoRow(dense = true)`），
  设备在线时前面带一个**绿点**；主操作 34dp 全宽胶囊，次级 26dp，分节标题 10sp 且更淡。
- **操作页有用量一节**：`轮数 · 步数` 一行（轮=自己发的条数，步=命令/工具/文件/子代理的次数），
  下面是可点开的 tokens 一行，展开显示输入 / 输出 / **缓存命中** / 缓存写入 / 合计的精确数字。
  用量只从运行时自己写进事件里的字段解析（`input_tokens`、`cache_read_input_tokens`、
  `num_turns`……），逐条求和；若出现带 `num_turns` 的 result 总账则**以总账为准**；一个字段都
  没有时写明「这段对话里没有 token 统计」，而不是假装 0。账号页的所有信息行同样是 `dense`。
- **模型只显示当前选中的那一个**，配一枚星形图标；点一下在原地展开（高度 + 淡入动画）才出现
  其他模型，带档位的模型（low / high）继续折叠在它自己那一行里。
- **权限名翻译成中文并配图标**：只读=锁、仅工作区可写=笔、完全访问=警告、每次询问=人、
  自动审查=放大镜……运行时给的是英文（`Read Only`）或裸 id（`read-only`），两种都会匹配，
  认不出来的模式保留原文，免得把安全选项标错。
- **审批卡片能直接回答提问**（`WatchNoticeCard`）：运行时提问时选项**不在** `actions` 里，而是藏在
  `action.uiSchema` 的 `component = "inputRequest"` 表单里。旧卡片只画 `actions` 按钮，而提问的
  action 一律 `input.required = true`，正好被过滤掉，于是只剩「待审批」一个标题、外加一句「请在手机
  或电脑上完成」。现在会解析出题干与选项：单选点一个换一个、多选逐项开关、`allowCustom` 时多一个
  「其他（自己输入）」并就地展开输入框；`isComplete()` 为真才点亮「提交」，提交时用 `buildPayload()`
  把 `{"answers": {问题 id: {optionIds, customText}}}` 一并交给 `respondNotice`。
- **配色是灰阶**，只留两处刻意的颜色：**绿色 = 设备在线**，**红色 = 出错**（错误横幅前面是
  红色感叹号）。这两件事用灰色表达不出来。
- **启动图标**由 `ios-dark.png` 生成 48/72/96/144/192 五档 `mipmap/ic_launcher.png`；
  没有做成自适应图标，因为原图自带圆角方形与透明角，再套一层蒙版会被裁掉。
- **登录按钮是全屏最大的控件**（165dp 全宽 × 48dp 高），「注册」「服务器」缩成两个并排小椭圆。

### 圆屏适配

圆屏的可用宽度 **随竖直位置变化**：越靠近顶部/底部越窄，所以一行控件的宽度必须按它所在的
高度来定，否则角会被圆边切掉。这个规则做成了可计算的函数
（`ui/WatchRound.kt` 的 `maxRowWidth`）：

```
把一行控件（宽 w、高 h）的顶边放在距面板顶部 top 处，
它离圆心最远的角到圆心的距离是 √((w/2)² + dy²)，其中 dy = |top + h/2 − 116.5| + h/2
只要这个距离 ≤ 116.5dp − 5dp（留边），整行就完整可见。
```

面板按 466×466 / density 320 折算成 **233dp 正方形**，内接圆半径 **116.5dp**，
横向安全边距 `WatchSafeHorizontal = 34.dp`（内接正方形推出：`(466 − 466/√2)/2 ≈ 68px`）。
如果实测 `adb shell wm density` 不是 320，请按同样公式重算这些值。

| 控件 | 位置 | 实际宽度 | 余量 |
| --- | --- | --- | --- |
| 登录主按钮 | 顶部 132dp 起，高 48dp | **165dp（吃满全宽）** | 12.4dp |
| 登录页两个椭圆 | 顶部 186dp 起，高 26dp | **115.1dp**（每个 53.6dp） | 5.0dp |
| 对话页输入行 | 顶部 181dp 起，高 28dp | **120.9dp**（输入框 88.9dp，发送键 28dp） | ≥5dp |

这几个数字不是估的，`WatchRoundTest` 会断言它们确实落在圆内；登录页的内容正好在 233dp 内，
不滚动也不需要滚，另有 `trailingSpace` 让「滚到底」正好落在设计位置上。

- 键盘弹出时页面会整体上移（`adjustResize` + 纵向滚动），输入框不会被挡。

### 表冠

OPPO 的表冠在系统里是一个 **滚轮设备**（`dumpsys input` 里 `HaveVWheel: true`，
`WheelVelocityControlParameters` 的 `acceleration = 4.000`），所以轻轻一拨也会送来一个被固件
加速度放大过的滚动量。谁接了它，谁就会一次滑出去一大截 —— 在长对话里看起来就是「卡死」，
在其他页面则是「一下窜到顶又一下窜到底」。

所以整条链路只认「一格」，并且 **这一格滚动当前页面，不翻页**：

1. `WatchMainActivity.dispatchGenericMotionEvent` 在任何滚动容器之前拿到事件：只要带纵向滚动轴、
   又不是手指来源的 generic motion 就收下，把方向交给 `WatchRotary` 并 `return true` 吃掉事件。
   注意这里 **不排除鼠标来源** —— 这块表的表冠正是以滚轮指针的身份上报的，早期把它当鼠标排除掉，
   事件就整条漏给了页面里的列表，那才是「拨一格冲到底」的原因。
2. `WatchRotary` 只累计 **次数**（不看数值大小），对外是一个 `StateFlow<Int>`。`StateFlow` 自带
   conflation：滚动还在跑的时候连拨好几格，只会等空闲后收到最新总数，不会补播一串积压事件。
3. `WatchShell` 把「总数之差」换算成步数（单次最多 3 步），每一步固定 `CROWN_STEP_DP = 30dp`，
   按当前页分派给对应的滚动状态：对话页是 `LazyListState`，其余三页是各自的 `ScrollState`。
   **距离是固定的，只用系统给的符号** —— 系统报的数值已经被固件的 `acceleration = 4.000`
   放大过，跟着它走就是「拨一下窜到底」。
4. 方向在 `WatchShell` 里取反（`distance = -steps * crownStep`），不是在 `WatchRotary` 里：
   那个计数器就是「拨了几格」的流水账，本身不该带方向语义。
5. 一批（最多 3 格）合成**一个 `tween` 线性滑动**，时长 `CROWN_ANIM_MS_PER_STEP = 55ms` × 步数。
   早先每格各起一个 `spring`：每格都要先加速再收住，快速转动就是一顿一顿的；线性匀速才读作「连续」。

换固件或换表型时可以重新标定：

```bash
adb logcat -c && adb logcat -s WatchCrown
# 拨一格表冠，看输出：
# crown units=1.0 action=8 source=0x2002 device=4 accepted=true
```

`units` 是系统给的滚动量（被加速度放大，所以只取符号），`accepted=false` 表示这一路事件被判成
手指来源、没有吃下来 —— 那种情况下要放宽 `isCrownScroll()` 的判据。若表冠改发按键重复而不是滚轮
事件，logcat 里会先出现 `crown-as-key? code=…`（Activity 目前只记日志不消费，用来确认固件走的是哪条路）。

### 实时输出与对话状态

上游本来就实现了整套会话实时协议，而且 `feature/realtime/**` 就在手表模块的共享源码白名单里
（见第六节），所以**手表用的是和手机端同一个客户端**：`WatchSessionController.startRealtime()`
在对话页可见时订阅当前会话，`SessionRealtimeController` 负责票据、WebSocket、游标与断线重连，
事件回来交给 `SessionDetailController.applyRealtimeEvents()` 归约进同一个 `SessionDetailState`。
回复与工具调用因此是逐条推来的，而不是每次轮询一次性刷出来。

- **游标**：`detail.realtime.cursor` 就是恢复点；`onCursorAdvanced` 用 `laterEventCursor` 取较大值。
- **缺口**：`onSnapshotRequired`（socket 无法重建漏掉的事件）和 `onRuntimeRefreshRequired`
  都会退回 `refreshDetail()`，也就是原来的轮询快照。
- **兜底**：轮询一直开着（对话页与操作页每 3 秒一次）。老服务端不开 WebSocket、或者票据被拒，
  页面不会退化，只是不显示「实时」。
- **生命周期**：进对话页 `startRealtime`，离开就 `stopRealtime()`（另有一个 `DisposableEffect`
  兜底）。手表会激进冻结后台，替一个看不见的页面握着长连接只是耗电。

对话页右上角那一个词就是状态出口（不额外占行）：

| 显示 | 含义 |
| --- | --- |
| **实时**（绿） | WebSocket 已连上，正在推事件 |
| **重连中…**（黄） | 掉线后在退避重连（`realtime.recovering` / `reconnectAttempt > 0`） |
| **运行失败**（红） | `runtime.status == Error` |
| **已阻塞**（红） | `runtime.status == Blocked` |
| **设备离线**（黄） | `runtime.status == Disconnected` |
| 其余 | 会话自身的状态文案（原来的行为） |

运行时发来的 `notification` 类通知（额度超限、连接丢失、回合失败等）不是状态码，而是有文字的，
所以它们以 `WatchStatusNotice` 细带显示在对话上方（最多两条），`severity` 为 `error/critical` 变红、
`warning/warn` 变黄，其余灰。它们来自 `RuntimeNotice.openNotification`，也就是「还没被撤回」的那些。

默认的物理排列（从左到右）是：

```
[0] 工作区   [1] 对话(启动页)   [2] 操作   [3] 账号
```

也就是说：

- **手指往右拖 → 工作区**（多个工作目录和对话）
- **手指往左拖 → 操作**（接管 / 切换模型 / 切换权限 / 审批）
- **再往左拖一次 → 账号**（邮箱、昵称、退出登录）

> **想镜像所有滑动方向？** 只改一个地方：`android/watch/src/main/java/com/agentsanywhere/app/watch/ui/WatchShell.kt`
> 里的 `WatchPageOrder` 列表，把它整行反过来即可（启动页会自动跟着算，不用改别的）：
> `listOf(WatchPage.Account, WatchPage.Actions, WatchPage.Conversation, WatchPage.Sidebar)`。

### 多设备与新建会话

一个账号可以挂多台工作设备（笔记本、台式机、手机各跑一个 Connector，`/connectors` 各是一个
`AgentDevice`）。上游手机端有设备选择器，手表之前没有，于是把所有设备的会话混在一张表里：
两台机器都有 `~/work` 时会出现两行同名目录，既看不出谁是谁，也没法只看一台。

**设备过滤**（`WatchSessionController.deviceFilterId`）：

- `null` 表示全部设备；`visibleProjects` / `visibleSessions` 在外面就被过滤好，页面本身不做筛选。
- 选择器只在**设备多于一台**时出现（一台设备没有可切换的对象，而这一行要占 233dp 面板的两行）。
  每台设备带一个 4dp 状态点：在线绿、离线灰。
- 选「全部设备」且设备多于一台时，目录行后面会补上设备名 —— 由 `buildSidebarGroups(projects, sessions,
  deviceNamesById)` 拼进 `detail`，设备名按 **connectorId** 匹配（不是 project id），空白设备名会被
  trim 掉，不留悬空的 ` · `。
- 被过滤的设备在别处被解绑时，`refreshSessions` 会把过滤器复位成「全部设备」；切换设备会收起当前
  展开的目录（同一个目录名在不同设备上是两个不同的目录）。

**新建会话**（`WatchNewSessionState` + `WatchNewSessionSheet`）：

手机端是四步向导（设备 → 运行时 → 目录 → 首句）。手表压成**一个面板**：每个选择都是一个已有合理
默认值的胶囊，用户只需要写第一句话。

- 默认设备 = 当前过滤的设备，否则第一台在线设备；改设备会重新拉运行时与目录。
- 运行时来自 `DevicesApi.listDeviceRuntimes`，再用上游的 `activeNewSessionRuntimes` 过滤
  （`configured && active && status == Running`）。没启动的会明确说「要先在手机或电脑上启动」，
  和「一台都没配置」分开提示。
- 目录来自上游 `workspaceOptionsFor`：该设备已有会话的 cwd 去重，加上 Home directory。
- 创建走 `SessionsController.createAndStartSession` —— 目录解析、缺项目时建项目、`create-and-start`、
  以及「网络失败但可能已创建」的对账，全都复用上游那一份实现，手表只持有草稿。
- 成功后刷新列表并直接进入新会话。
- 面板是**盖在 pager 之上的 overlay**，不是第五页：放弃它要点「取消」，不响应背景点击（圆屏太小，
  滚动时误触会丢掉刚写好的句子），打开期间 pager 的滑动手势被禁用，表冠也改由面板接管。

**限制**：首句是单行输入（`WatchTextField` 目前 `singleLine = true`，真要多行得给它加参数），
不支持附件，也不做「先建空会话再决定运行时」。

---

## 二、登录与注册（和上游协议一致）

- **登录 = 邮箱 + 密码，没有二次验证。**
  上游 `server/agent_server/api/auth.py` 的 `auth_login` 只校验密码，源码注释也写明
  「Until 2FA exists…」。所以手表上 **不存在** 收验证码登录这一步。
- 密码 **明文不出手表**：本地用 PBKDF2-HMAC-SHA256 派生校验值再上传，
  参数与 Web 端、服务端完全一致：
  `base64url(PBKDF2(password, salt, 120000, dkLen=32))`，salt 按 UTF-8 字符参与哈希。
  实现见 `watch/data/PasswordVerifier.kt`，并有对照 OpenSSL 的单元测试。
- **注册需要邮箱验证码**（6 位数字，10 分钟有效，重发间隔 60 秒）。
  官方云端 `/api/v2/auth/config` 返回 `emailVerificationRequired: true`、
  `registrationOpen: true`，所以点「注册新账号」→「发送验证码」→ 填验证码 + 密码即可。
  若连的是自建服务器且不需要验证码，界面会自动提示并允许留空。
- 「服务器设置」按钮可以改成自建服务器地址（默认官方云端 `https://web.agents-anywhere.com`）。
- **绑定 / 验证邮箱**（在账号页）：如果账号还没有邮箱（例如手机端用 OAuth 创建的账号），
  账号页会出现「绑定邮箱」按钮；如果邮箱存在但未验证，则显示「验证邮箱」。
  两者都是同一个流程：`POST /auth/email-code {purpose: "bind"}` → 填 6 位验证码 →
  `PUT /auth/me/email`。注意服务端的 bind 验证码只要求「已登录 + 服务器启用了邮箱验证」，
  **不要求** 注册开关打开，所以注册关闭的服务器也能绑定邮箱。

### 协议核对结果

这几个端点的字段名和参数是**逐个对着服务端源码核对过**的（不是猜的）：

| 手表发出 | 服务端模型 |
| --- | --- |
| `POST /auth/password-salt {email}` | `AuthPasswordSaltRequest` → `{salt, serverTime}` |
| `POST /auth/login {email, passwordVerifier, passwordSalt}` | `AuthRequest` → `AuthResponse` |
| `POST /auth/register {email, passwordVerifier, passwordSalt, displayName?, code?}` | `AuthRequest` → `AuthResponse` |
| `POST /auth/email-code {email, purpose}` | `EmailCodeRequest{"register"｜"bind"}` → `{expiresIn, retryAfter}` |
| `PUT /auth/me/email {email, code}` | `BindEmailRequest` → `AuthMeResponse` |

最关键的一条：服务端 `core/auth.py` 的 `hash_password()` 是

```python
digest = hashlib.pbkdf2_hmac("sha256", password.encode("utf-8"), salt.encode("utf-8"), 120000)
base64.urlsafe_b64encode(digest).decode("ascii").rstrip("=")
```

而 `verify_password_verifier()` 是拿客户端上传的 verifier 和存储值**逐字节比较**，
所以 base64 变体（urlsafe、去掉 `=`）和 32 字节长度必须完全一致，否则登录永远失败。
手表端 `PasswordVerifier.kt` 与它逐项对齐：PBKDF2-HMAC-SHA256、120000 轮、dkLen 32、
salt 用 UTF-8 字符串字节、`Base64.getUrlEncoder().withoutPadding()`。

---

## 三、构建

### 环境

| 组件 | 版本 |
| --- | --- |
| JDK | 17（本项目验证用 Temurin 17.0.20.1） |
| Android SDK | `platforms;android-36`、`build-tools;36.0.0`、`platform-tools` |
| Gradle | 8.10.2（wrapper 自带） |
| AGP / Kotlin | 8.7.3 / 2.2.0 |

`android/local.properties` 里的 `sdk.dir` 需要指向本机 SDK（本仓库中已指向
`D:\android-build\sdk`，该文件被 git 忽略，可自行修改）。

### 命令

在 `android/` 目录下：

```powershell
# 调试包
.\gradlew.bat :watch:assembleDebug

# 正式包（需要 android/watch/keystore.properties，见下一节）
.\gradlew.bat :watch:assembleRelease

# 校验密码派生算法、登录报文格式、圆屏几何与图标映射
.\gradlew.bat :watch:testDebugUnitTest

# 确认没有影响手机版
.\gradlew.bat :app:assembleDebug
```

### 测试覆盖了什么

`watch/src/test/` 下共 73 个测试（`.\gradlew.bat :watch:testDebugUnitTest`）：

| 测试类 | 数量 | 覆盖 |
| --- | --- | --- |
| `PasswordVerifierTest` | 6 | PBKDF2 参数、32 字节长度、base64url 去 padding、随机盐形态，并对照 **OpenSSL 参考向量** |
| `WatchAuthApiTest` | 5 | 用 MockWebServer 起真实 HTTP，断言**登录/注册/发码的报文格式** |
| `WatchRoundTest` | 12 | **圆屏几何**：哪些行会超出圆形显示范围、登录页是否需要滚动、输入行会不会被圆边切掉 |
| `SidebarGroupsTest` | 6 | 工作目录与对话的分组、未归档对话的归属、选中项所在分组 |
| `SidebarDeviceLabelsTest` | 9 | 多设备时目录行带设备名、同路径的两台设备不再读成同一行、按 connectorId 而非 project id 匹配、空白设备名不留悬空分隔符、未归属组永不贴设备名、加标签不改变顺序 |
| `WatchModelGroupsTest` | 7 | 带档位的模型折叠成一行、不带档位的不折叠、当前档位回填 |
| `WatchToolIconTest` | 6 | 工具名 → 图标（命令行 / 写文件 / 搜索 / 网络 / 子代理）的映射 |
| `WatchRotaryTest` | 3 | 表冠一格算一格，拨动幅度不影响步数，0 不计数 |
| `WatchPermissionTest` | 5 | 权限英文名与裸 id 都翻成中文，认不出的保留原文，只读不会被认成可写 |
| `WatchUsageTest` | 8 | 轮数/步数的数法、逐条用量求和、result 总账覆盖逐条、没有用量时不假装是 0、K/M 与千分位格式 |
| `WatchInputRequestTest` | 6 | 提问的选项从 `uiSchema` 里解析出来、没有表单时不抢 `actions`、选完才可提交、自定义文本单独成立、提交载荷的形状 |

`WatchRoundTest` 把「控件不会被圆边切掉」变成可验证的断言而不是目测，例如：

- 同样 165dp 宽的一行，放在竖直中部算通过、放到靠近底部算不通过（证明公式确实在起作用）
- 登录页四行（邮箱、密码、主按钮、椭圆行）各自的最远角都在圆内
- 椭圆行**必须**比内容宽度窄，且把它按内容宽度算就**必须**判定为超出——不然这个几何就没有意义
- 登录页内容总高 ≤ 233dp（不需要滚动），且 `trailingSpace` 正好等于剩余空间
- 对话页输入行在它被钉住的位置上仍然在圆内，输入框剩余宽度 ≥ 80dp
- 输入行比页面其他行更窄（留白更大），保证它比普通行更难被圆边蹭到

`WatchAuthApiTest` 不是自说自话的单元测试，它锁定的是服务端契约：

- 请求**确实**走 `/api/v2/...` 命名空间，且服务端正常返回 JSON 时**不会**回退到 legacy 路由
- 报文字段名与 `core/models.py` 的 `AuthRequest` / `AuthResponse` 等一一对应
- 客户端算出的 `passwordVerifier` 等于**服务端实现**对固定向量 `("secret123","testsalt")`
  的输出 `ya57mR6uyioAY1JHvwbtpNr5BPfIBI7As7AeYNhG8wQ`（服务端是逐字节比较这个串的）
- 服务端返回 `401 {"detail":"invalid credentials"}` 时，`ApiException` 的
  `statusCode=401`、`message="invalid credentials"` 恰好命中中文映射表的 key
- 注册用的是**客户端随机盐**（22 字符、无 `=`），且 `displayName`/`code` 为空时不下发该字段

因此即使没有真机，"密码能通过服务端校验"这条链路也是被验证过的。

产物：

- `android/watch/build/outputs/apk/debug/watch-debug.apk`
- `android/watch/build/outputs/apk/release/watch-release.apk`

也可以直接用脚本（自动设置 `JAVA_HOME` / `ANDROID_HOME` / `GRADLE_USER_HOME`）：

```powershell
.\scripts\build-and-install.cmd                    # 构建 debug 并安装到已连接的手表
.\scripts\build-and-install.cmd -Variant Release
.\scripts\build-and-install.cmd -SkipInstall       # 只构建
```

> 用 `.cmd` 而不是 `.ps1`：本机 PowerShell 的执行策略默认禁止运行脚本
> （`在此系统上禁止运行脚本`），`.cmd` 包了一层 `-ExecutionPolicy Bypass`，
> 不用改系统设置。要直接跑 `.ps1` 就自己加
> `powershell -ExecutionPolicy Bypass -File .\scripts\build-and-install.ps1`。
>
> 另外这三个 `.ps1` 都保存为 **带 BOM 的 UTF-8**。PowerShell 5.1 在没有 BOM 时
> 会按 ANSI(GBK) 解码源码，里面的中文会变成乱码并直接导致**语法错误**——
> 本项目踩过一次，改脚本时请注意保持 BOM。

### 本机网络注意

本机 JVM **直连** `dl.google.com` / `repo1.maven.org` 是通的，所以依赖解析不需要代理。
唯一例外是 **Gradle 发行包本身**：`services.gradle.org` 会 302 到 `github.com`，
而 github.com 在本机被解析成 TUN 的 fake-IP 地址，JVM 连不上。
只有首次下载 wrapper 时才需要本地代理，配置写在
`D:\android-build\gradle-home\gradle.properties`（默认注释掉）：

```
# systemProp.https.proxyHost=127.0.0.1
# systemProp.https.proxyPort=7890
```

**没有修改** 仓库里被跟踪的 `android/gradle.properties`。
另外这台机器内存只有 16 GB，`:app` 编译时如果同时起 Gradle daemon 和独立的 Kotlin
daemon 容易被系统杀掉（表现为 `Gradle build daemon disappeared unexpectedly`）。
该文件里因此用了较保守的堆设置和 `kotlin.compiler.execution.strategy=in-process`。

---

## 四、签名

`android/watch/keystore.properties`（已被 `android/watch/.gitignore` 忽略）：

```properties
storeFile=D:/android-build/agents-anywhere-watch.jks
storePassword=<本机生成>
keyAlias=agentsanywhere-watch
keyPassword=<本机生成>
```

> ⚠️ **路径写正斜杠 `/`**（或双反斜杠 `\\`）。
> `.properties` 里 Java 的 `Properties.load()` 会把单个 `\` 当转义符吃掉，
> `D:\android-build\x.jks` 会静默变成 `D:android-buildx.jks`，
> 报错是「Keystore file ... not found」。本项目踩过一次。

`storeFile` 可以是绝对路径，也可以相对于 `android/` 目录。想换成自己的签名：

```powershell
keytool -genkeypair -v -keystore my-watch.jks -alias my-watch `
  -keyalg RSA -keysize 4096 -validity 10950
```

然后照着上面改 `keystore.properties`。**keystore 和密码请自己保管**，
丢失后无法用新包覆盖安装（需要先卸载，会清掉登录状态）。

---

## 五、安装到手表

1. 手表：**设置 → 关于/版本信息 → 连点版本号 7 次** 打开开发者模式。
2. **设置 → 开发者选项 → 打开「USB 调试」**。
3. 用 **充电线** 把手表接到电脑（OPPO 手表一般 **不支持** 无线 ADB）。
4. `adb devices` 应能看到设备（手表上会弹「允许调试」）。
5. 安装：

```powershell
adb install -r android\watch\build\outputs\apk\debug\watch-debug.apk
```

或直接跑 `.\scripts\build-and-install.cmd`。

安装后如果桌面找不到图标，在手表应用列表里找 **「Agents Anywhere 手表版」**。

### 界面自查

```powershell
.\scripts\screenshot.cmd
```

脚本会依次抓取四个界面的截图（`adb shell screencap`），并把 `wm size` /
`wm density` / `ro.product.model` 一起写进输出目录，方便按第五节重算安全边距。

### 打不开 / 闪退时

```powershell
.\scripts\diagnose.cmd
```

会启动 App、等待若干秒，然后检查**进程是否还活着**、Activity 是否 resumed，
并把 logcat（含崩溃栈）导出到 `android/watch/diagnostics/`。
`crash-*.txt` 里已经过滤出 `FATAL EXCEPTION` / `ANR` 和本应用的日志。

---

## 六、这个模块怎么复用上游源码

`android/watch` **没有复制、也没有移动** 任何上游文件。`watch/build.gradle.kts` 里有一个
`syncSharedSources` 任务，把上游 `app/src/main/java` 中白名单内的包在构建时同步到
`watch/build/generated/sharedSources`，再作为源码目录参与编译：

```
api/  model/  config/  feature/{auth,devices,realtime,sessiondetail,sessions}
ui/designsystem/Theme.kt
```

有意排除：`MainActivity`/`app`（手机导航）、`ui/screens`（手机 UI）、
`feature/terminal`（依赖 Termux）、`feature/update`（手机自升级）。

> 为什么用 `Sync` 而不是 `sourceSets.include(...)`？
> AGP 8.7 的 `AndroidSourceDirectorySet` 只有 `srcDir`/`srcDirs`，**没有** include/exclude；
> 在 `android {}` 里写 `include(...)` 会静默解析成 `Project.include`（那是「包含子项目」），
> 结果所有上游文件都会被编译。这是实测踩过的坑。

上游被改动的只有 **一行**：`android/settings.gradle.kts` 里加了 `include(":watch")`。

`namespace` 仍复用 `com.agentsanywhere.app`（以便共享代码能引用同一个 `BuildConfig`），
但 `applicationId` 是独立的 `com.agentsanywhere.app.watch`，可以和手机版共存。

---

## 七、已知限制

- **实时输出已经接上 WebSocket**（`SessionRealtimeController`），但**轮询仍是兜底**：对话页与操作页
  每 3 秒 `refreshDomains` 一次，socket 不可用时整页行为不变，只是右上角不显示「实时」。
  真机上的订阅是否被服务端接受尚未验证（手表没连上 adb）。
- **需要填写内容的审批**（`RuntimeNoticeAction.input.required == true`）在操作页渲染成
  `WatchNoticeCard` 里的输入表单：选项（单选/多选）、可选的「其他（自己输入）」、以及 `uiSchema`
  里解析出来的问题都可以在手表上填完提交（`WatchInputRequestTest` 覆盖解析与提交载荷）。
  没有表单、也没有可点动作的通知，才会提示改用手机或电脑。
- **切换模型/权限需要先接管会话**（和手机版一致，未接管时按钮不可点）。
- 手表端不显示附件、图片、diff 细节；时间线里命令/工具调用会压缩成一行。
- 模板/斜杠命令（`/runtime/commands`）暂未做成按钮，只在时间线里显示。
- **头像**只读 `/auth/me` 里的 `avatar`：`data:` 前缀按 base64 解，`http(s)` 才走网络（OkHttp），
  其他形态（例如 file://）不显示，退回首字母。没有引入 Coil —— 为 24dp 的圆多背近 1MB 不划算。
- **新建会话**只能写一句单行文字，不带附件，也必须选一个已经在运行的运行时；首句发送后会话才真正创建，
  中途退出不会留下空会话。
- **设备过滤**是纯前端的视图筛选：`sessions` 接口仍然一次返回该账号所有设备的会话，设备很多时
  首屏数据量会变大，分页游标也只对「全部设备」有意义。
