package com.personal.windows.desktopmanager.service.impl;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import com.personal.windows.desktopmanager.exception.ConfigException;
import com.personal.windows.desktopmanager.model.AppConfigDto;
import com.personal.windows.desktopmanager.model.GroupDo;
import com.personal.windows.desktopmanager.service.IConfigService;
import com.personal.windows.desktopmanager.util.AppPathUtil;
import com.personal.windows.desktopmanager.util.JsonConfigUtil;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ConfigServiceImpl implements IConfigService {

    private static final Logger log = LoggerFactory.getLogger(ConfigServiceImpl.class);

    private static final String DEFAULT_GROUP_ID = "g-default";
    private static final String DEFAULT_GROUP_NAME = "未命名";

    private final ObjectMapper objectMapper;
    private final Path configFile;

    private ConfigData cachedConfig;

    public ConfigServiceImpl() {
        this(AppPathUtil.getConfigFilePath());
    }

    public ConfigServiceImpl(Path configFile) {
        this.configFile = configFile;
        this.objectMapper = new ObjectMapper();
        AppPathUtil.ensureAppDataDir();
        ensureConfigExists();
        loadCache();
    }

    private void loadCache() {
        try {
            String content = Files.readString(configFile);
            cachedConfig = objectMapper.readValue(content, ConfigData.class);
        } catch (Exception e) {
            cachedConfig = createDefaultConfig();
            log.warn("加载配置缓存失败，使用默认配置", e);
        }
    }

    @Override
    public synchronized List<GroupDo> loadGroups() {
        return deepCopyGroups(cachedConfig.groups);
    }

    @Override
    public synchronized void saveGroups(List<GroupDo> groups) {
        cachedConfig.groups = deepCopyGroups(groups);
        persistConfig();
    }

    @Override
    public synchronized Map<String, String> loadFileGroupMapping() {
        return new HashMap<>(cachedConfig.fileGroupMapping);
    }

    @Override
    public synchronized void saveFileGroupMapping(Map<String, String> mapping) {
        cachedConfig.fileGroupMapping = new HashMap<>(mapping);
        persistConfig();
    }

    @Override
    public synchronized AppConfigDto loadAppConfig() {
        AppConfigDto source = cachedConfig.appConfig;
        AppConfigDto dto = new AppConfigDto();
        dto.setAutoStart(source.isAutoStart());
        dto.setTheme(source.getTheme());
        return dto;
    }

    @Override
    public synchronized void saveAppConfig(AppConfigDto config) {
        cachedConfig.appConfig = config;
        persistConfig();
    }

    @Override
    public synchronized void setFileGroup(String filePath, String groupId) {
        if (groupId == null) {
            cachedConfig.fileGroupMapping.remove(filePath);
        } else {
            cachedConfig.fileGroupMapping.put(filePath, groupId);
        }
        persistConfig();
    }

    @Override
    public synchronized String getFileGroup(String filePath) {
        return cachedConfig.fileGroupMapping.getOrDefault(filePath, getDefaultGroupId());
    }

    @Override
    public void ensureConfigExists() {
        try {
            if (!Files.exists(configFile)) {
                AppPathUtil.ensureAppDataDir();
                ConfigData defaultConfig = createDefaultConfig();
                String json = objectMapper.writerWithDefaultPrettyPrinter()
                        .writeValueAsString(defaultConfig);
                Files.writeString(configFile, json);
                log.info("默认配置文件已创建: {}", configFile);
            }
        } catch (Exception e) {
            throw new ConfigException("创建默认配置文件失败", e);
        }
    }

    public String getDefaultGroupId() {
        List<GroupDo> groups = loadGroups();
        if (groups.isEmpty()) {
            return DEFAULT_GROUP_ID;
        }
        return groups.get(0).getId();
    }

    synchronized void removeFilesNotInSet(java.util.Set<String> existingFilePaths) {
        cachedConfig.fileGroupMapping.keySet().removeIf(
                fp -> !existingFilePaths.contains(fp));
        persistConfig();
    }

    private void persistConfig() {
        try {
            String json = objectMapper.writerWithDefaultPrettyPrinter()
                    .writeValueAsString(cachedConfig);
            Path tempFile = configFile.resolveSibling(configFile.getFileName() + ".tmp");
            Files.writeString(tempFile, json);
            Files.move(tempFile, configFile,
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                    java.nio.file.StandardCopyOption.ATOMIC_MOVE);
        } catch (Exception e) {
            throw new ConfigException("写入配置文件失败", e);
        }
    }

    private ConfigData createDefaultConfig() {
        ConfigData config = new ConfigData();
        List<GroupDo> groups = new ArrayList<>();
        groups.add(new GroupDo(DEFAULT_GROUP_ID, DEFAULT_GROUP_NAME, 0));
        config.groups = groups;
        config.fileGroupMapping = new HashMap<>();
        config.appConfig = new AppConfigDto();
        return config;
    }

    private List<GroupDo> deepCopyGroups(List<GroupDo> source) {
        List<GroupDo> copy = new ArrayList<>();
        for (GroupDo g : source) {
            copy.add(new GroupDo(g.getId(), g.getName(), g.getOrder()));
        }
        return copy;
    }

    public static class ConfigData {
        public List<GroupDo> groups;
        public Map<String, String> fileGroupMapping;
        public AppConfigDto appConfig;
    }
}
