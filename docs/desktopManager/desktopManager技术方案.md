# 桌面管家技术方案

## 1. 概述

桌面管家是一款 Windows 桌面图标管理工具，提供桌面文件自动隐藏、图标分组管理、系统托盘常驻等功能。本文档定义应用的技术架构、技术选型、模块设计与关键技术决策。

## 2. 技术选型

| 类别 | 选择 | 说明 |
|------|------|------|
| JDK | 17 LTS | 满足规范要求，提供稳定长期支持 |
| UI 框架 | JavaFX (OpenJFX) | FXML+CSS 分离布局与样式，MVC 分层清晰 |
| 系统托盘 | AWT SystemTray | Java 原生支持，无需额外依赖 |
| Windows API 调用 | JNA (Java Native Access) | 调用 Shell32/User32 实现桌面图标显隐控制 |
| 文件监控 | java.nio.file.WatchService | JDK 内置，无需三方依赖 |
| 配置存储 | JSON (Jackson/Gson) | 轻量结构化存储，存于 %APPDATA% |
| 日志 | SLF4J + Logback | 符合规范要求 |
| 构建工具 | Maven 3.8+ | 符合规范要求 |
| 打包 | jpackage + Inno Setup | jpackage 生成运行时镜像，Inno Setup 生成 Windows 安装包 (.exe) |

## 3. 架构设计

### 3.1 分层架构

```
┌──────────────────────────────────────────────────┐
│ UI 层 (ui/)                                       │
│ ├─ DesktopManagerApplication   应用入口           │
│ ├─ SystemTrayManager           系统托盘管理        │
│ ├─ IconManageController        图标管理窗口控制器  │
│ └─ GroupEditDialogController   分组编辑弹窗控制器  │
├──────────────────────────────────────────────────┤
│ Service 层 (service/)                             │
│ ├─ IDesktopIconService         桌面图标显隐/监控   │
│ │  └─ DesktopIconServiceImpl                      │
│ ├─ IGroupService               分组管理           │
│ │  └─ GroupServiceImpl                            │
│ └─ IConfigService              配置文件读写        │
│    └─ ConfigServiceImpl                           │
├──────────────────────────────────────────────────┤
│ Model 层 (model/)                                 │
│ ├─ DesktopFileDo / DesktopFileVo 桌面文件模型      │
│ ├─ GroupDo / GroupVo             分组模型          │
│ └─ AppConfigDto                  应用配置模型      │
├──────────────────────────────────────────────────┤
│ util/                                             │
│ ├─ WindowsApiUtil               JNA 封装          │
│ ├─ AppPathUtil                  路径工具          │
│ └─ JsonConfigUtil               JSON 配置工具      │
└──────────────────────────────────────────────────┘
```

> **注意**：本应用无数据库，不包含 DAO 层。所有持久化数据通过 JSON 配置文件存储。

### 3.2 模块划分

| 模块 | 职责 | 核心类 |
|------|------|--------|
| 应用入口 | 启动初始化、环境检查、自启动注册 | `DesktopManagerApplication` |
| 系统托盘 | 托盘图标创建、左/右键菜单响应 | `SystemTrayManager` |
| 图标管理窗口 | 桌面文件列表展示、分组管理、拖拽交互 | `IconManageController` |
| 桌面图标服务 | 桌面文件显隐控制、文件夹监控 | `DesktopIconServiceImpl` |
| 分组服务 | 分组增删改查、文件移入移出 | `GroupServiceImpl` |
| 配置服务 | 配置文件读写、分组与文件映射持久化 | `ConfigServiceImpl` |
| 工具类 | Windows API 封装、路径获取、JSON 解析 | 各 util 类 |

## 4. 关键技术方案

### 4.1 桌面图标显隐控制

Windows 桌面图标显隐通过修改注册表 + 广播系统设置变更实现：

1. 修改注册表键值：
   - 路径：`HKCU\Software\Microsoft\Windows\CurrentVersion\Explorer\Advanced`
   - 键名：`HideIcons`
   - 值：`1` = 隐藏，`0` = 显示

2. 广播 `WM_SETTINGCHANGE` 消息使 Explorer 立即生效：
   - 通过 JNA 调用 `User32.INSTANCE.SendMessageTimeout` 广播 `WM_SETTINGCHANGE`

3. 方案封装为 `WindowsApiUtil`，提供 `hideDesktopIcons()` / `showDesktopIcons()` 静态方法

4. 应用退出时恢复桌面图标显示

```java
// 关键 JNA 接口映射
interface User32 extends com.sun.jna.Library {
    User32 INSTANCE = Native.load("user32", User32.class);
    LRESULT SendMessageTimeoutW(HWND hWnd, int msg, WPARAM wParam, LPARAM lParam, int fuFlags, int uTimeout, IntByReference lpdwResult);
}
```

### 4.2 桌面文件夹监控

使用 `java.nio.file.WatchService` 监控用户桌面目录：

- 监控路径：`System.getProperty("user.home") + "\\Desktop"`
- 监控事件：`ENTRY_CREATE`、`ENTRY_MODIFY`
- 新文件创建后自动调用隐藏逻辑
- 监控线程独立运行，使用后台线程避免阻塞 UI

### 4.3 桌面文件扫描

应用首次启动时全量扫描桌面目录：

- 使用 `java.nio.file.Files.list()` 遍历桌面目录
- 过滤隐藏文件、系统文件
- 将文件路径、名称等元信息加载到内存模型

### 4.4 配置文件设计

配置文件存放路径：`%APPDATA%\PersonalDesktopManager\config.json`

```json
{
  "groups": [
    {
      "id": "g-001",
      "name": "未命名",
      "order": 0
    },
    {
      "id": "g-002",
      "name": "工作",
      "order": 1
    }
  ],
  "fileGroupMapping": {
    "C:\\Users\\xxx\\Desktop\\report.xlsx": "g-002",
    "C:\\Users\\xxx\\Desktop\\notes.txt": "g-001"
  },
  "appConfig": {
    "autoStart": true,
    "theme": "light"
  }
}
```

- `groups`：分组列表，每个分组有唯一 ID、名称、排序
- `fileGroupMapping`：文件路径 → 分组 ID 的映射
- `appConfig`：应用全局配置

### 4.5 自启动实现

Windows 自启动通过注册表实现：

- 路径：`HKCU\Software\Microsoft\Windows\CurrentVersion\Run`
- 键名：`PersonalDesktopManager`
- 键值：应用 exe 或 jar 的启动命令

通过 JNA 操作注册表，首次运行时询问用户是否启用。

### 4.6 拖拽实现

使用 JavaFX 原生拖拽事件：

- 文件条目设置 `setOnDragDetected` 开始拖拽
- 分组区域设置 `setOnDragOver` / `setOnDragDropped` 接收拖放
- 拖拽成功后更新文件-分组映射并刷新 UI

## 5. 线程模型

| 线程 | 用途 | 说明 |
|------|------|------|
| JavaFX Application Thread | UI 渲染与交互 | 所有 UI 操作必须在此线程 |
| FileWatcher Thread | 桌面文件监控 | 独立守护线程，检测文件变更 |
| Config IO Thread | 配置文件读写 | 使用 CompletableFuture 异步执行 |

- FileWatcher 线程检测到文件变化后，通过 `Platform.runLater()` 通知 UI 更新
- 配置文件读写通过异步任务执行，避免阻塞 UI

## 6. 数据流

```
桌面文件夹 ──WatchService──→ DesktopIconServiceImpl ──→ Platform.runLater() ──→ UI 更新
                                    │
                                    ├──→ GroupServiceImpl（文件-分组映射）
                                    │
                                    └──→ ConfigServiceImpl（持久化到 JSON）
```

## 7. 异常处理

| 异常场景 | 处理方式 |
|---------|---------|
| 注册表读写失败 | 日志记录 + 用户提示（可能需要管理员权限） |
| 配置文件损坏 | 使用默认配置重建，记录 WARN 日志 |
| 桌面目录不存在 | 使用默认路径尝试，失败则提示用户手动设置 |
| 文件监控失败 | 降级为手动刷新模式，记录 ERROR 日志 |
| JNA 加载失败 | 提示用户系统不兼容，优雅退出 |

## 8. 打包方案

1. Maven 编译构建 jar
2. jpackage 生成运行时镜像（含精简 JRE + 应用 JAR + 依赖）
3. Inno Setup 将运行时镜像打包为 Windows .exe 安装包
4. 安装包包含：
   - 应用主程序（jpackage 生成的 EXE 启动器）
   - 精简 JRE（通过 jpackage 自动处理）
   - 应用依赖（JavaFX、JNA 等）
   - 开始菜单快捷方式、桌面快捷方式
   - 卸载程序

## 9. 依赖清单

```xml
<!-- Maven 核心依赖 -->
<dependencies>
    <!-- JavaFX -->
    <dependency>
        <groupId>org.openjfx</groupId>
        <artifactId>javafx-controls</artifactId>
        <version>17.0.13</version>
    </dependency>
    <dependency>
        <groupId>org.openjfx</groupId>
        <artifactId>javafx-fxml</artifactId>
        <version>17.0.13</version>
    </dependency>

    <!-- JNA（Windows API 调用） -->
    <dependency>
        <groupId>net.java.dev.jna</groupId>
        <artifactId>jna</artifactId>
        <version>5.14.0</version>
    </dependency>
    <dependency>
        <groupId>net.java.dev.jna</groupId>
        <artifactId>jna-platform</artifactId>
        <version>5.14.0</version>
    </dependency>

    <!-- JSON 处理 -->
    <dependency>
        <groupId>com.fasterxml.jackson.core</groupId>
        <artifactId>jackson-databind</artifactId>
        <version>2.17.2</version>
    </dependency>

    <!-- 日志 -->
    <dependency>
        <groupId>org.slf4j</groupId>
        <artifactId>slf4j-api</artifactId>
        <version>2.0.13</version>
    </dependency>
    <dependency>
        <groupId>ch.qos.logback</groupId>
        <artifactId>logback-classic</artifactId>
        <version>1.5.6</version>
    </dependency>

    <!-- 测试 -->
    <dependency>
        <groupId>org.junit.jupiter</groupId>
        <artifactId>junit-jupiter</artifactId>
        <version>5.10.3</version>
        <scope>test</scope>
    </dependency>
    <dependency>
        <groupId>org.mockito</groupId>
        <artifactId>mockito-core</artifactId>
        <version>5.12.0</version>
        <scope>test</scope>
    </dependency>
</dependencies>
```
