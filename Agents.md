# AGENTS

本文件用于约束本仓库内的人类开发者与 AI Agent 的协作方式。除非有明确说明，所有实现、设计、补文档与重构任务，均以 [docs/common/开发规范.md](docs/common/开发规范.md) 为最高基线；本文件负责将其转换为当前仓库可直接执行的规则。

## 1. 仓库结构

当前仓库为 Java Windows 桌面应用 项目，目录职责如下：

```text
PersonalWindowApp/
├─ docs/                     # 项目文档
│  ├─ common/                # 公共规范与项目说明
│  ├─ {应用名}/              # 各应用的开发文档
│  └─ ...
├─ apps/                     # 代码文件夹
│  ├─ {应用名}/              # 各应用的开发代码（Maven/Gradle 模块）
│  │  ├─ src/main/java/com/personal/windows/{app}/
│  │  │  ├─ ui/             # UI 层（Stage/Scene/View/Controller）
│  │  │  ├─ service/        # 业务逻辑层（Service 接口与实现）
│  │  │  ├─ dao/            # 数据访问层（DAO 接口与实现）
│  │  │  ├─ model/          # 领域模型（DO/VO/DTO）
│  │  │  ├─ config/         # 配置类
│  │  │  ├─ enums/          # 枚举常量
│  │  │  └─ util/           # 通用工具类
│  │  ├─ src/main/resources/ # 资源文件（FXML/CSS/图片/配置）
│  │  └─ ...
├─ AGENTS.md                 # 仓库级协作与开发约束
└─ README.md
```

统一包路径：

```text
com.personal.windows.{app}
```

## 2. 文档优先级与冲突处理

### 2.1 基线文档

- 仓库总规范基线：`docs/common/开发规范.md`
- 系统文档目录：`docs/{应用名}`
- 每个应用至少应包含：
    - `{应用名}业务逻辑.md` / `{应用名}需求文档.md`
    - `{应用名}技术方案.md` / `{应用名}UI设计文档.md`
    - `{应用名}服务接口设计文档.md`
    - `{应用名}数据库设计.md`
    - 如需执行拆分，再补 `{应用名}开发任务清单.md`

### 2.2 冲突优先级

当不同文档对同一事项描述冲突时，按"冲突项归属"判定最高标准：

- 业务流程、角色权限鉴权、异常处理：需求/业务逻辑文档优先
- 窗口布局、控件交互、页面跳转：UI 设计文档优先
- 服务接口、模块契约、事件总线：服务接口设计文档优先
- 表结构、字段、索引、外键约束：数据库设计文档优先

若模块文档未覆盖某项约束，则回退到 [docs/common/开发规范.md](docs/common/开发规范.md)。

## 3. 架构与框架约定

### 3.1 技术栈

- JDK：17+
- UI 框架：JavaFX（推荐）或 Swing
- 构建工具：Maven（推荐）或 Gradle
- 数据库：SQLite / H2 / HSQLDB（嵌入式数据库，随应用部署）
- 日志：SLF4J + Logback / Log4j2
- 打包：jpackage / launch4j / exe4j

### 3.2 分层架构

桌面应用采用类 MVC / MVP 分层：

```
UI 层 (ui/)          ← 窗口/FXML/View/Controller，仅负责界面渲染与用户交互
  ↓ 调用
Service 层 (service/) ← 业务逻辑，可独立单元测试
  ↓ 调用
DAO 层 (dao/)         ← 数据库访问，封装 SQL / ORM 操作
  ↓ 操作
Model 层 (model/)     ← 数据对象（DO/VO/DTO），跨层传递
```

约束：

- UI 层不得直接访问 DAO 层，也不得包含业务逻辑
- DAO 层仅负责数据存取，不得包含业务判断
- Service 层是唯一可编排复杂业务流程的分层

## 4. 命名规范

### 4.1 包名

- 全小写，按模块语义命名，如 `app.todo`、`app.note`、`app.reminder`
- 子包按照分层名称命名：`ui`、`service`、`dao`、`model`、`config`、`enums`、`util`

### 4.2 类命名

**UI 层**：
- 主窗口：`{App}Application` 或 `MainApp`
- Stage/Scene 控制器：`{页面名}Controller`
- 自定义控件：`{控件名}View` / `{控件名}Component`
- FXML 文件与对应 Controller 同名，如 `TodoList.fxml` ↔ `TodoListController.java`

**Service 层**：
- Service 接口：`I{业务名}Service`
- Service 实现：`{业务名}ServiceImpl`

**DAO 层**：
- DAO 接口：`I{业务名}Dao`
- DAO 实现：`{业务名}DaoImpl`

**Model 层**：
- 数据库实体对象：`{业务名}Do` 或 `{业务名}Entity`
- 视图 / 展示对象：`{业务名}Vo`
- 数据传输对象：`{业务名}Dto`
- 模型文件存放于 `model/` 目录下，可按类型进一步分子目录

**其他**：
- 配置类：`{功能}Config`
- 枚举类：使用明确业务语义命名，存放于 `enums/` 包
- 工具类：`{功能}Util` 或 `{功能}Utils`，存放于 `util/` 包

### 4.3 资源文件命名

- FXML 文件：与对应 Controller 同名，如 `Setting.fxml`
- CSS 文件：小写连字符命名，如 `main-style.css`、`dark-theme.css`
- 图片 / 图标：`{用途}-{尺寸}.{格式}`，如 `app-icon-64.png`
- 配置文件：`application.properties` / `config.properties`

### 4.4 数据库命名

- 表名：全小写，下划线分隔，如 `todo_item`、`note_category`
- 字段名：全小写，下划线分隔，如 `created_time`、`is_deleted`
- 主键统一使用自增 ID，字段名 `id`
- 公共字段（建议每表包含）：`created_time`、`updated_time`

### 4.5 文档命名

- 系统文档统一放在 `docs/{系统名}/`
- 文档文件名采用中文业务名 + 文档类型
- 标准文件名：
    - `{应用名}业务逻辑.md` / `{应用名}需求文档.md`
    - `{应用名}技术方案.md` / `{应用名}UI设计文档.md`
    - `{应用名}服务接口设计文档.md`
    - `{应用名}数据库设计.md`
    - `{应用名}开发任务清单.md`

## 5. 编码规则

- 所有文件统一使用 `UTF-8` 格式进行编码
- 不允许在 UI Controller 和 DAO 中编写业务逻辑
- 复杂业务流程必须在 Service 中拆分为可读的子方法
- 公共逻辑优先复用，禁止复制粘贴形成多份实现
- 不允许在 UI 层直接创建数据库连接或执行 SQL
- 耗时操作（IO、网络、复杂计算）必须放在后台线程执行，不得阻塞 UI 线程（JavaFX Application Thread / Swing EDT）
- 桌面应用本地文件读写使用 `java.nio.file`，避免硬编码路径
- 新增窗口或页面时，必须同时检查 FXML/CSS 资源路径、Controller 绑定关系是否符合设计文档
- 新增数据库对象时，必须同时检查字段前缀、公共字段、迁移脚本命名是否符合规范
- 不同后缀且不同功能的类文件应分开存放在不同子目录中，禁止混放
- 文档、代码、数据库脚本三者不一致时，不得默认以"现有代码"覆盖设计，必须回到文档优先级规则判断

### 通用约束

新增模块前，应先补充设计文档并确认其包名、表结构与分层职责。

## 6. Agent 执行准则

AI Agent 在本仓库工作时默认遵循以下流程：

1. 先读 `docs/common/开发规范.md` 与本次涉及模块文档
2. 再确认代码应落在哪个应用模块、哪个分层目录
3. 实现前核对窗口跳转关系、服务接口契约、表结构与命名
4. 实现后同步检查文档、代码、数据库脚本是否一致

若本文件与 [docs/common/开发规范.md](docs/common/开发规范.md) 的明确条款冲突，以后者为准；若本文件未覆盖某项实现细节，按模块设计文档和相邻现有代码风格继续补齐。
