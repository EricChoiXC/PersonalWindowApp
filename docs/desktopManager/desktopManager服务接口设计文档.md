# 桌面管家服务接口设计文档

## 1. 概述

本文档定义桌面管家应用的所有 Service 层接口契约。本应用无数据库，所有持久化数据通过 JSON 配置文件存储。Service 层直接操作配置文件，不设 DAO 层。

## 2. 数据模型

### 2.1 GroupDo（分组数据对象）

```java
package com.personal.windows.desktopmanager.model;

/**
 * 分组数据对象，对应配置文件中的 group 节点
 */
public class GroupDo {
    /** 分组唯一标识，格式: "g-{UUID前8位}" */
    private String id;

    /** 分组名称，不可为空，最长 20 字符 */
    private String name;

    /** 分组排序序号，从 0 开始 */
    private int order;

    // getter/setter 省略
}
```

### 2.2 GroupVo（分组视图对象）

```java
package com.personal.windows.desktopmanager.model;

/**
 * 分组视图对象，用于 UI 层展示
 */
public class GroupVo {
    private String id;
    private String name;
    private int fileCount;   // 该分组下的文件数量（计算字段）

    // getter/setter 省略
}
```

### 2.3 DesktopFileVo（桌面文件视图对象）

```java
package com.personal.windows.desktopmanager.model;

/**
 * 桌面文件视图对象，用于 UI 列表展示
 */
public class DesktopFileVo {
    /** 文件在桌面上的绝对路径 */
    private String filePath;

    /** 文件名（含扩展名） */
    private String fileName;

    /** 文件扩展名（小写，不含点） */
    private String extension;

    /** 文件大小，单位字节，-1 表示目录 */
    private long fileSize;

    /** 是否为目录 */
    private boolean isDirectory;

    // getter/setter 省略
}
```

### 2.4 GroupDto（分组创建/更新 DTO）

```java
package com.personal.windows.desktopmanager.model;

/**
 * 分组创建/更新数据传输对象
 */
public class GroupDto {
    /** 分组名称，创建时必填，更新时选填 */
    private String name;

    // getter/setter 省略
}
```

### 2.5 AppConfigDto（应用配置 DTO）

```java
package com.personal.windows.desktopmanager.model;

/**
 * 应用全局配置数据传输对象
 */
public class AppConfigDto {
    /** 是否开机自启动 */
    private boolean autoStart;

    /** 主题：light / dark */
    private String theme;

    // getter/setter 省略
}
```

---

## 3. 服务接口

### 3.1 IConfigService - 配置服务

配置文件的读写与管理。配置文件路径：`%APPDATA%\PersonalDesktopManager\config.json`

```java
package com.personal.windows.desktopmanager.service;

public interface IConfigService {

    /**
     * 加载所有分组列表
     * @return 分组列表，配置不存在时返回仅包含默认"未命名"分组的列表
     */
    List<GroupDo> loadGroups();

    /**
     * 保存全部分组列表（覆盖写入）
     * @param groups 分组列表
     */
    void saveGroups(List<GroupDo> groups);

    /**
     * 加载文件→分组的映射关系
     * @return Map<文件绝对路径, 分组ID>，配置不存在时返回空 Map
     */
    Map<String, String> loadFileGroupMapping();

    /**
     * 保存文件→分组映射关系（覆盖写入）
     * @param mapping 文件路径→分组ID 映射
     */
    void saveFileGroupMapping(Map<String, String> mapping);

    /**
     * 加载应用全局配置
     * @return 应用配置，配置不存在时返回默认配置
     */
    AppConfigDto loadAppConfig();

    /**
     * 保存应用全局配置
     * @param config 应用配置
     */
    void saveAppConfig(AppConfigDto config);

    /**
     * 设置单个文件的所属分组
     * @param filePath 文件绝对路径
     * @param groupId  分组ID，传 null 表示移除映射
     */
    void setFileGroup(String filePath, String groupId);

    /**
     * 获取单个文件的所属分组ID
     * @param filePath 文件绝对路径
     * @return 分组ID，未分配时返回默认分组ID
     */
    String getFileGroup(String filePath);

    /**
     * 确保配置文件及目录存在，不存在则自动创建并写入默认配置
     */
    void ensureConfigExists();
}
```

#### 3.1.1 ConfigServiceImpl 实现要点

- 配置文件以 UTF-8 编码读写
- 使用 Jackson `ObjectMapper` 序列化/反序列化
- 默认配置文件内容：
  ```json
  {
    "groups": [{"id": "g-default", "name": "未命名", "order": 0}],
    "fileGroupMapping": {},
    "appConfig": {"autoStart": false, "theme": "light"}
  }
  ```
- 每次保存使用原子写入：先写临时文件，再替换原文件
- 读写异常抛出 `ConfigException`

---

### 3.2 IDesktopIconService - 桌面图标服务

控制桌面图标的显隐、桌面文件扫描与实时监控。

```java
package com.personal.windows.desktopmanager.service;

public interface IDesktopIconService {

    /**
     * 隐藏桌面上的所有图标
     * 通过修改注册表 HideIcons 键值实现
     * @throws DesktopIconException 注册表操作失败时抛出
     */
    void hideDesktopIcons();

    /**
     * 恢复显示桌面上的所有图标
     * 通过修改注册表 HideIcons 键值实现
     * @throws DesktopIconException 注册表操作失败时抛出
     */
    void showDesktopIcons();

    /**
     * 获取桌面目录路径
     * @return 用户桌面目录的绝对路径
     */
    String getDesktopPath();

    /**
     * 扫描桌面目录，获取所有非隐藏文件/文件夹列表
     * @return 桌面文件视图对象列表
     */
    List<DesktopFileVo> scanDesktopFiles();

    /**
     * 启动桌面文件夹监控
     * 当有新文件创建时，通过回调通知外部
     * @param onNewFile 新文件创建回调，参数为新建文件的 DesktopFileVo
     */
    void startFileWatcher(Consumer<DesktopFileVo> onNewFile);

    /**
     * 停止文件夹监控
     */
    void stopFileWatcher();

    /**
     * 隐藏单个文件/文件夹
     * 设置 Windows 文件隐藏属性
     * @param filePath 文件的绝对路径
     */
    void hideFile(String filePath);

    /**
     * 获取文件的系统图标显示名称
     * @param filePath 文件绝对路径
     * @return 图标资源标识或文件类型描述
     */
    String getFileIconKey(String filePath);
}
```

#### 3.2.1 DesktopIconServiceImpl 实现要点

- 桌面路径获取优先级：
  1. 读取注册表 `HKCU\Software\Microsoft\Windows\CurrentVersion\Explorer\User Shell Folders\Desktop`
  2. 回退到 `System.getProperty("user.home") + "\\Desktop"`
- 桌面图标显隐通过 JNA 调用 Windows API，详见技术方案 4.1 节
- 文件监控使用 `WatchService`，注册 `ENTRY_CREATE` 和 `ENTRY_MODIFY` 事件
- 新文件检测到后自动调用 `hideFile()` 设置隐藏属性
- 文件监控线程为守护线程，应用退出时自动终止
- 异常抛出 `DesktopIconException`

---

### 3.3 IGroupService - 分组管理服务

管理文件分组，提供分组 CRUD 与文件-分组关联操作。

```java
package com.personal.windows.desktopmanager.service;

public interface IGroupService {

    /**
     * 获取所有分组列表（含文件计数）
     * @return 分组视图对象列表，按 order 排序
     */
    List<GroupVo> listGroups();

    /**
     * 获取指定分组下的所有文件
     * @param groupId 分组ID
     * @return 文件视图对象列表
     */
    List<DesktopFileVo> listFilesByGroup(String groupId);

    /**
     * 创建新分组
     * @param dto 分组创建信息
     * @return 创建成功后的 GroupVo
     * @throws BusinessException 分组名重复时抛出
     */
    GroupVo createGroup(GroupDto dto);

    /**
     * 重命名分组
     * @param groupId 分组ID
     * @param dto     新名称
     * @return 更新后的 GroupVo
     * @throws BusinessException 分组名重复或分组不存在时抛出
     */
    GroupVo renameGroup(String groupId, GroupDto dto);

    /**
     * 删除分组
     * @param groupId 分组ID
     * @throws BusinessException 分组不存在或分组内仍有文件时抛出
     */
    void deleteGroup(String groupId);

    /**
     * 将文件移动到指定分组
     * @param filePath     文件绝对路径
     * @param targetGroupId 目标分组ID
     * @throws BusinessException 文件不存在或分组不存在时抛出
     */
    void moveFileToGroup(String filePath, String targetGroupId);

    /**
     * 刷新文件列表（重新扫描桌面并与配置文件同步）
     * - 新增的桌面文件自动归入默认分组
     * - 配置中已删除的文件自动清理
     * @return 当前选中分组的文件列表
     */
    List<DesktopFileVo> refreshFiles(String groupId);

    /**
     * 获取默认分组ID（"未命名"分组）
     * @return 默认分组ID
     */
    String getDefaultGroupId();
}
```

#### 3.3.1 GroupServiceImpl 实现要点

- 依赖 `IConfigService` 读写分组和文件映射
- 依赖 `IDesktopIconService` 扫描桌面文件
- `listFilesByGroup()` 逻辑：
  1. 从配置加载文件映射，筛选属于该分组的文件路径
  2. 对于每个文件路径，检查文件是否仍存在于桌面，过滤已删除文件
  3. 返回 `DesktopFileVo` 列表
- `createGroup()`：
  1. 检查名称是否重复（允许同名字分组？需求文档说"可重复"，所以允许）
  2. 生成唯一 ID
  3. 计算 order（最大值+1）
  4. 保存到配置
- `deleteGroup()`：
  1. 检查分组内文件计数为 0
  2. 不允许删除默认分组（"未命名"）
  3. 从配置中移除
- `moveFileToGroup()`：
  1. 更新配置文件中的文件映射
  2. 异步保存配置
- 数据校验异常抛出 `BusinessException`

---

## 4. 异常定义

### 4.1 异常类

```java
package com.personal.windows.desktopmanager.exception;

/**
 * 业务异常，用于 Service 层统一抛出
 */
public class BusinessException extends RuntimeException {
    public BusinessException(String message) {
        super(message);
    }
    public BusinessException(String message, Throwable cause) {
        super(message, cause);
    }
}

/**
 * 配置异常，配置文件读写失败时抛出
 */
public class ConfigException extends RuntimeException {
    public ConfigException(String message, Throwable cause) {
        super(message, cause);
    }
}

/**
 * 桌面图标操作异常，JNA/注册表操作失败时抛出
 */
public class DesktopIconException extends RuntimeException {
    public DesktopIconException(String message, Throwable cause) {
        super(message, cause);
    }
}
```

### 4.2 业务异常码

| 异常信息 | 触发条件 |
|---------|---------|
| `分组名称已存在` | createGroup/renameGroup 时名称重复 |
| `分组不存在` | 操作的目标 groupId 在配置中未找到 |
| `分组内仍有文件，无法删除` | deleteGroup 时 fileCount > 0 |
| `默认分组不可删除` | 尝试删除"未命名"分组 |
| `文件不存在或已被删除` | moveFileToGroup 时文件路径无效 |
| `桌面目录访问失败` | 桌面路径不存在或无法读取 |
| `配置文件读写失败` | JSON 解析异常或文件 IO 异常 |

---

## 5. 服务间依赖关系

```
GroupServiceImpl
  ├──→ IConfigService（读写分组/映射配置）
  └──→ IDesktopIconService（扫描桌面文件）

DesktopIconServiceImpl
  └──→ WindowsApiUtil（JNA 调用）
       ├──→ User32（注册表操作、窗口消息广播）
       └──→ Advapi32（注册表读写）

ConfigServiceImpl
  └──→ Jackson ObjectMapper（JSON 序列化/反序列化）
```

## 6. 配置文件契约

### 6.1 配置文件路径

`{APPDATA}\PersonalDesktopManager\config.json`

### 6.2 配置文件结构

```json
{
  "groups": [
    {
      "id": "g-a1b2c3d4",
      "name": "未命名",
      "order": 0
    }
  ],
  "fileGroupMapping": {
    "C:\\Users\\Eric\\Desktop\\report.xlsx": "g-a1b2c3d4"
  },
  "appConfig": {
    "autoStart": true,
    "theme": "light"
  }
}
```

### 6.3 字段约束

| 字段 | 类型 | 必填 | 约束 |
|------|------|------|------|
| `groups[].id` | String | 是 | 唯一，格式 `g-{8位随机}` |
| `groups[].name` | String | 是 | 最长 20 字符 |
| `groups[].order` | int | 是 | >= 0 |
| `fileGroupMapping` | `Map<String, String>` | 否 | Key: 文件绝对路径，Value: 分组 ID |
| `appConfig.autoStart` | boolean | 否 | 默认 false |
| `appConfig.theme` | String | 否 | "light" | "dark"，默认 "light" |

---

## 7. 单元测试要点

### 7.1 IGroupService 测试

| 测试方法 | 场景 | 预期 |
|---------|------|------|
| `listGroups_init_returnsDefaultGroup` | 首次启动 | 返回仅含"未命名"的列表 |
| `createGroup_validName_returnsGroupVo` | 创建分组 | 返回包含 id/name 的 GroupVo |
| `deleteGroup_hasFiles_throwsException` | 删除非空分组 | 抛出 BusinessException |
| `deleteGroup_empty_success` | 删除空分组 | 分组从列表中移除 |
| `moveFileToGroup_valid_changeMapping` | 移动文件 | 文件映射更新 |

### 7.2 IConfigService 测试

| 测试方法 | 场景 | 预期 |
|---------|------|------|
| `ensureConfigExists_noFile_createsDefault` | 配置文件不存在 | 自动创建含默认分组的配置文件 |
| `loadGroups_valid_returnsList` | 正常加载 | 正确解析 JSON |
| `saveGroups_persistsCorrectly` | 保存再加载 | 数据一致 |

### 7.3 IDesktopIconService 测试

| 测试方法 | 场景 | 预期 |
|---------|------|------|
| `scanDesktopFiles_returnsFiles` | 扫描桌面 | 返回非隐藏文件列表 |
| `getDesktopPath_returnsValidPath` | 获取桌面路径 | 返回有效且存在的目录路径 |
