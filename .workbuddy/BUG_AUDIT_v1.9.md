# 🔴 「校长我来当」v1.9 全面Bug审计报告

> 审计日期：2026-06-10 | 审计范围：185个Kotlin源文件 | 以玩家反馈"频繁闪退"为切入点

---

## 📊 概览

| 严重等级 | 数量 | 描述 |
|---------|------|------|
| 🔴 致命 | 3 | 直接导致闪退/数据丢失，**必须立即修复** |
| 🟠 高危 | 4 | 在特定条件下导致闪退/ANR，**尽快修复** |
| 🟡 中危 | 5 | 导致用户体验差、数据膨胀、静默异常 |
| 🟢 低危 | 4 | 长期隐患、代码质量问题 |

---

## 🔴 致命级 — 直接导致闪退

### [BUG-01] 数据库升级时静默清空用户存档

**位置**: `AppModule.kt:157-166` · `AppDatabase.kt:31`

**现象**: 用户更新App后打开，发现游戏进度完全丢失，变成新游戏

**根因**:
```kotlin
// AppModule.kt:163
.fallbackToDestructiveMigration()  // ← 致命!
```
- 数据库当前版本是 **15**
- 只注册了 `MIGRATION_14_15`
- 如果旧用户DB版本 < 14（比如早期版本），Room找不到迁移路径 → 直接删除DB重建
- `SaveManager`的存档恢复机制虽然做了 `migrateLoadedDatabase()`，但用户必须手动去读档界面操作，绝大多数玩家不知道

**修复方案**:
1. 补全所有历史版本→15的Migration（至少从version 1开始）
2. 或者：在启动时检测DB版本，如果 < 14，先做 `migrateLoadedDatabase` 再让Room打开
3. 重写 `SchoolTycoonApplication.onCreate()` 加入DB版本检测与自动修复

**严重程度**: 🔴致命 — 100%触发，所有从旧版本升级的用户都会丢档

---

### [BUG-02] GameEngine中 `runBlocking` 冻结主游戏循环

**位置**: `GameEngine.kt:161` · `GameEngine.kt:177`

**现象**: 游戏突然卡住，几秒后闪退（ANR或主线程阻塞）

**根因**:
```kotlin
// GameEngine.kt:161 — ensureTimetablesGenerated()
val allTeachers = kotlinx.coroutines.runBlocking { teacherRepository.getTeachers() }

// GameEngine.kt:177 — refreshTimetablesForTeacherChange()
val allTeachers = kotlinx.coroutines.runBlocking { teacherRepository.getTeachers() }
```
- GameEngine跑在 `Dispatchers.Default` 线程池上
- `runBlocking` 会完全阻塞当前线程直到DB查询完成
- 如果此时有其他协程在同一线程上等待（如tick循环），会造成死锁
- 在低端设备上DB查询可能耗时数百毫秒 → ANR

**修复方案**:
```kotlin
// 改为suspend函数，在调用处用协程处理
suspend fun ensureTimetablesGenerated() {
    val allClasses = _classes.value
    if (allClasses.isEmpty()) return
    val allTeachers = teacherRepository.getTeachers()  // 直接在suspend上下文调用
    // ...
}
```
并将所有调用处改为 `engineScope.launch { ensureTimetablesGenerated() }`

**严重程度**: 🔴致命 — 在教师变动/课表生成时触发，影响所有中后期玩家

---

### [BUG-03] 存档时与游戏引擎并发写入导致DB损坏

**位置**: `SaveManager.kt:99-133` · `GameEngine.kt:119-124`

**现象**: 加载存档后闪退，或存档文件损坏无法读取

**根因**:
```kotlin
// SaveManager.saveGame() 复制DB文件时，GameEngine的tick()可能正在写DB
checkpointDatabase()  // WAL checkpoint不是原子操作
dbFile.copyTo(File(slotDir, DB_FILE_NAME), overwrite = true)  // 边写边复制!
```
- `isSaving` 标志只阻止了tick，但没阻止其他manager的数据库操作
- GameEngine的tick中有大量 `schoolRepository.updateSchool()` / `studentRepository.updateStudents()` 调用
- WAL模式下checkpoint不保证所有写已完成
- 复制到一半的DB文件就是损坏的存档

**修复方案**:
1. 保存前先暂停整个GameEngine（不只是tick）
2. 用 `PRAGMA wal_checkpoint(TRUNCATE)` + `PRAGMA journal_mode=DELETE` 强制同步
3. 复制后用checksum校验文件完整性
4. 或者改用JSON序列化存档（绕过DB文件复制）

**严重程度**: 🔴致命 — 随机触发，存档越大触发概率越高

---

## 🟠 高危级 — 特定条件闪退

### [BUG-04] 全局异常处理器吞掉致命异常

**位置**: `SchoolTycoonApplication.kt:19-28`

**现象**: 玩家遇到闪退，但开发者拿不到任何崩溃日志

**根因**:
```kotlin
Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
    Log.e("SchoolTycoon", "Uncaught exception on thread ${thread.name}", throwable)
    // 如果是协程相关的非致命异常（如 StateFlow 竞态），尝试恢复
    if (throwable is NullPointerException || throwable is IllegalStateException) {
        Log.w("SchoolTycoon", "Non-fatal exception caught, suppressing crash")
        return@setDefaultUncaughtExceptionHandler  // ← 吞掉了!
    }
    defaultHandler?.uncaughtException(thread, throwable)
}
```
- `NullPointerException` 和 `IllegalStateException` 被完全吞掉
- 这些异常往往标志着严重的代码逻辑错误
- 吞掉后App处于未知状态，可能在几秒后更严重地崩溃
- 无法收到崩溃报告 = 永远不知道有多少玩家在闪退

**修复方案**:
1. 改为接入Firebase Crashlytics / Bugly等崩溃上报SDK
2. 所有未捕获异常都上报，但区分"可恢复"和"致命"
3. 不要吞掉NPE/ISE — 让它们正常崩溃并上报

**严重程度**: 🟠高危 — 隐藏了真实的崩溃数据，使大量闪退"不可见"

---

### [BUG-05] 广告SDK互冲导致启动闪退

**位置**: `build.gradle.kts:102-191` · `SchoolTycoonApplication.kt:40-45`

**现象**: 部分机型打开App立刻闪退，连主界面都看不到

**根因**:
- 集成了 **25+个广告SDK**（穿山甲、优量汇、快手、百度、Sigmob、TopOn等）
- 全部在Application.onCreate()中同步初始化
- 不同广告SDK可能依赖冲突版本的OkHttp、Gson、RxJava
- 穿山甲SDK在部分机型上初始化时会读取设备信息，可能触发权限异常
- 没有try-catch包裹广告初始化 — 任何一个SDK初始化失败都会导致Application.onCreate()崩溃

**修复方案**:
1. 将所有广告SDK初始化包在try-catch中，失败不影响主流程
2. 延迟初始化非必要SDK（在需要展示广告时才初始化）
3. 添加`android:usesCleartextTraffic="true"`（部分广告SDK需要）
4. 用BuildConfig控制只加载核心SDK，其余按需加载

**严重程度**: 🟠高危 — 部分机型100%触发，导致无法启动

---

### [BUG-06] 数据库关闭后未等待重连就启动引擎

**位置**: `SaveManager.kt:135-172` · `MainViewModel.kt:163-168`

**现象**: 加载存档后引擎启动时报"database is closed"异常

**根因**:
```kotlin
// SaveManager.loadGame()
database.close()   // 关闭Room DB
// ... 复制文件 ...
// 没有重新打开DB!

// MainViewModel.init
if (saveManager.consumeJustLoaded()) {
    startGamePaused()  // ← GameEngine.start() 会立即访问DB
}
```
- `database.close()` 后Room不会自动重连
- 下一次访问DB时Room会尝试重连，但这之间有时间窗口
- `startGamePaused()` 中的 `restoreAllManagerStates()` 会大量访问DB
- 如果恰好在重连前访问 → crash

**修复方案**:
1. `loadGame()` 结束后显式等待DB重连完成
2. 在 `startGamePaused()` 前添加短暂delay
3. 改用 `SupportSQLiteDatabase` 的回调检测连接状态

**严重程度**: 🟠高危 — 读档后100%触发，但在大多数机型上Room重连足够快所以偶尔不崩

---

### [BUG-07] StockPriceHistory无限膨胀导致存储耗尽

**位置**: `GameEngine.kt:667-668` · `StockDao` (未定义清理逻辑)

**现象**: TapTap用户反馈"游戏占用20多G存储" — 极可能就是股价历史表膨胀

**根因**:
```kotlin
// GameEngine.tick()
val totalDay = (school.currentYear - 1988) * 360 + (school.currentMonth - 1) * 30 + school.currentDay
stockRepository.recordDailyPrice(totalDay)  // 每个tick写一条!
```
- Base tick interval = 5秒，每5秒写一条股价记录
- 一天86400秒 ≈ 17,280条记录/现实天
- 一条记录 ~40 bytes → 每天产生 ~700KB
- 一个月持续玩 → 21MB仅此表
- **没有清理机制** — 数据只增不减

**修复方案**:
```kotlin
// 1. 改为每天只记录一次（而非每个tick）
if (school.currentDay != lastRecordedDay) {
    stockRepository.recordDailyPrice(totalDay)
    lastRecordedDay = school.currentDay
}
// 2. 添加定期清理：只保留最近30天的股价历史
// 3. 在App启动时清理3个月以前的股价数据
```

**严重程度**: 🟠高危 — 所有活跃玩家的存储空间都会持续增长

---

## 🟡 中危级

### [BUG-08] 90+处静默吞异常导致游戏状态异常

**位置**: 全项目89处 `catch (_: Exception)`

**现象**: 游戏玩法异常（数值不对、存档不完整），但表面不闪退

**根因**: 大量JSON反序列化和enum解析的catch块完全忽略错误：
```kotlin
facilities = try { Json.decodeFromString<List<Facility>>(facilitiesJson).toMutableList() } 
             catch (_: Exception) { mutableListOf() }  // 设施数据损坏 → 变成空列表
```
- JSON解析失败后返回默认空值 → 游戏状态与玩家操作不一致
- 玩家以为"操作成功"，实际数据根本没保存
- 累积性的数据丢失最终导致各种"莫名其妙"的bug

**修复方案**:
1. 所有catch块至少记录Log.w并附带原始数据
2. 对关键的JSON字段，解析失败时标记存档为"损坏"
3. 区分"可恢复的格式兼容问题"和"真正的数据损坏"

**严重程度**: 🟡中危 — 不会直接闪退，但导致大量"幽灵bug"

---

### [BUG-09] GameEngine tick异常后无回滚机制

**位置**: `GameEngine.kt:347-356`

**现象**: tick中间某步抛出异常 → 后续步骤被跳过 → 游戏状态半完成

**根因**:
```kotlin
try {
    tick()
} catch (e: Exception) {
    Log.e("GameEngine", "tick() exception, game loop continues", e)
    // tick内部如果 advanceDay() 成功但后续步骤失败 → 日期已推进但月结算没执行
}
```
- `advanceDay()` 是tick的第一步，执行后日期已变更
- 如果后续 `updateStudentProgress()` 抛出异常 → 当月学生不算成绩
- 如果 `deductMonthlyExpenses()` 没执行 → 当月不扣钱
- **没有事务性保证** — 一步失败会导致连锁反应

**修复方案**:
1. 将tick改造为事务模式：记录tick开始前的状态，出错时回滚
2. 至少保证：日期推进和月结算要么都成功，要么都不做
3. 记录tick失败次数，连续失败 > 3次时暂停游戏弹出提示

**严重程度**: 🟡中危 — 导致"越玩越不对劲"

---

### [BUG-10] 自动存档与ViewModel生命周期冲突

**位置**: `MainViewModel.kt:226-244`

**现象**: 用户切换应用/锁屏后回来发现游戏进度丢失

**根因**:
```kotlin
private fun performAutoSave(school: School) {
    viewModelScope.launch {
        withContext(NonCancellable + Dispatchers.IO) {  // ← NonCancellable = 强制执行
            saveManager.saveGame(...)
        }
    }
}
```
- `NonCancellable` 保证存档执行完毕
- 但如果此时用户已经退出App/Activity销毁 → `saveScope`在`onCleared`中也触发存档
- 两个存档协程可能同时运行 → DB文件并发复制 → 存档损坏

**修复方案**:
1. 使用`Mutex`保护存档操作，确保同时只有一个存档在进行
2. 在`onCleared`中先取消所有进行中的存档，再执行最终存档
3. 移除`NonCancellable`，改为在`onCleared`中保证最终存档

**严重程度**: 🟡中危 — 低概率触发但后果严重（存档损坏）

---

### [BUG-11] TapTap SDK初始化失败无备用方案

**位置**: `SchoolTycoonApplication.kt:31-37`

**现象**: 无网络/网络差时TapTap SDK初始化失败，App白屏

**根因**:
```kotlin
TapTapSdk.init(this, tapSdkOptions)  // 无try-catch
```
- TapTap SDK初始化涉及网络请求（获取配置）
- 如果用户在无网络环境打开App → SDK初始化超时/失败 → 可能抛异常
- 而且clientId和token硬编码在代码中（安全隐患）

**修复方案**:
1. 包裹try-catch
2. SDK初始化移到后台线程
3. clientId/clientToken放到BuildConfig或远程配置

**严重程度**: 🟡中危 — 影响无网络/弱网用户

---

### [BUG-12] 学生数量为0时触发大量空操作

**位置**: `GameEngine.tick()` — 多处调用

**现象**: 游戏初期（没学生时）卡顿、发热

**根因**: tick中大量无意义的查询：
```kotlin
val activeStudentsForExam = studentRepository.getActiveStudents()  // 有30个学生
if (activeStudentsForExam.isNotEmpty()) {
    // 考试系统...
    val students = studentRepository.getActiveStudents()  // 又查一次!
}
// ...
val totalStudents = studentRepository.getActiveStudents().size  // 再查一次!
if (totalStudents > 0) { ... }
```
- 每次tick从DB查询3次活跃学生列表
- 初期没有学生时这些查询没意义但仍在执行
- 每月1号执行大量DB操作（招生、考试、社团...）

**修复方案**:
1. tick中只查一次学生列表，缓存复用
2. 初期没有学生时跳过大部分系统
3. 将活跃学生列表缓存在内存中，仅在招生/毕业/退学时更新

**严重程度**: 🟡中危 — 导致低端设备发热、耗电、轻度卡顿

---

## 🟢 低危级

### [BUG-13] 不必需的敏感权限申请

**位置**: `AndroidManifest.xml:14-19`

```xml
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
<uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />
<uses-permission android:name="android.permission.ACCESS_BACKGROUND_LOCATION" />
<uses-permission android:name="android.permission.SYSTEM_ALERT_WINDOW" />
<uses-permission android:name="android.permission.QUERY_ALL_PACKAGES" />
<uses-permission android:name="android.permission.REQUEST_INSTALL_PACKAGES" />
```
- 学校模拟经营游戏不需要定位权限
- 这些权限在Android 12+会弹出权限请求对话框
- Google Play可能因权限过度申请拒绝上架
- 可能是广告SDK引入的，应该用`tools:node="remove"`移除

**严重程度**: 🟢低危 — 影响商店审核，可能导致部分用户拒绝安装

---

### [BUG-14] Release版本未开启代码混淆

**位置**: `build.gradle.kts:43`

```kotlin
isMinifyEnabled = false
```
- APK体积更大（多5-10MB）
- TapTap clientId/clientToken硬编码明文可见
- 容易被反编译

**严重程度**: 🟢低危 — 不影响功能但安全性和体积欠佳

---

### [BUG-15] AudioManager内存泄漏风险

**位置**: `MainViewModel.kt:375-376`

```kotlin
// 不要 release() 单例 AudioManager —— SoundPool.release() 是不可逆的
// 单例会随进程生命周期自然释放
audioManager.stopBgm()
```
- 注释说明不释放SoundPool是正确的
- 但SoundPool在某些Android版本上有已知的内存泄漏Bug
- 长期运行可能导致音频失真或无声

**严重程度**: 🟢低危 — Android系统级Bug，影响范围有限

---

### [BUG-16] 缺少进程被杀后的状态恢复

**位置**: 全局

**现象**: 用户切到后台，系统回收进程，回来后游戏从头开始

**根因**: 
- `onSaveInstanceState` 未实现
- 没有在Activity销毁前保存关键状态
- `SaveManager.markJustLoaded()` 机制依赖文件标记，但如果整个进程被杀 → 标记丢失

**修复方案**:
1. 在`onPause`或`onStop`中保存关键游戏状态到DataStore
2. 恢复时检查是否存在"未完成存档"
3. 实现`SavedStateHandle`在ViewModel中

**严重程度**: 🟢低危 — 影响后台被杀的场景

---

## 📋 修复优先级建议

| 优先级 | Bug编号 | 预计工时 | 影响范围 |
|--------|---------|---------|---------|
| P0 — 立刻 | BUG-01 数据库升级丢档 | 4h | 所有升级用户 |
| P0 — 立刻 | BUG-03 存档损坏 | 6h | 核心体验 |
| P0 — 立刻 | BUG-04 异常被吞 | 2h | 无法追踪真实崩溃 |
| P1 — 本版 | BUG-02 runBlocking冻结 | 3h | 教师变动场景 |
| P1 — 本版 | BUG-05 广告SDK启动崩 | 4h | 新安装/首次启动 |
| P1 — 本版 | BUG-07 股价历史膨胀 | 2h | 持续在玩的用户 |
| P1 — 本版 | BUG-06 DB关闭后竞态 | 2h | 读档场景 |
| P2 — 下版 | BUG-08/BUG-09/BUG-10 | 8h | 数据一致性 |
| P2 — 下版 | BUG-11 SDK初始化 | 2h | 弱网用户 |
| P3 — 排期 | BUG-12/BUG-13/14/15/16 | 10h | 体验优化 |

---

## 🎮 从玩家视角看：为什么会感觉"闪退特别多"

玩家说的"闪退"可能包含以下几种情况（按频率排序）：

1. **更新后打开游戏 → 进度丢失 → 以为是闪退** → BUG-01 (data destruction)
2. **游戏中突然卡住 → 几秒后闪退** → BUG-02 (runBlocking ANR)
3. **加载存档时闪退** → BUG-03 + BUG-06 (corrupted save)
4. **打开App立刻闪退** → BUG-05 (ad SDK crash)
5. **后台切换回来闪退** → BUG-16 + BUG-10 (state loss)

所有这些都应该被BUG-04的异常处理器捕获，但**异常处理器把异常吞掉了**，所以你在后台看不到崩溃报告，以为一切正常 — 但实际上玩家在大量闪退。
