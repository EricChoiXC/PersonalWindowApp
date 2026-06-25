package com.personal.windows.desktopmanager.service.impl;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import com.personal.windows.desktopmanager.exception.BusinessException;
import com.personal.windows.desktopmanager.model.DesktopFileVo;
import com.personal.windows.desktopmanager.model.GroupDo;
import com.personal.windows.desktopmanager.model.GroupDto;
import com.personal.windows.desktopmanager.model.GroupVo;
import com.personal.windows.desktopmanager.service.IConfigService;
import com.personal.windows.desktopmanager.service.IDesktopIconService;
import com.personal.windows.desktopmanager.service.IGroupService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GroupServiceImpl implements IGroupService {

    private static final Logger log = LoggerFactory.getLogger(GroupServiceImpl.class);

    private final IConfigService configService;
    private final IDesktopIconService desktopIconService;

    public GroupServiceImpl(IConfigService configService, IDesktopIconService desktopIconService) {
        this.configService = configService;
        this.desktopIconService = desktopIconService;
    }

    @Override
    public List<GroupVo> listGroups() {
        List<GroupDo> groups = configService.loadGroups();
        if (groups.isEmpty()) {
            String defaultId = getDefaultGroupId();
            GroupDo defaultGroup = new GroupDo(defaultId, "默认", 0);
            groups.add(defaultGroup);
            configService.saveGroups(groups);
            log.info("已自动创建默认分组: {}", defaultId);
        }

        Map<String, String> mapping = configService.loadFileGroupMapping();

        Map<String, Integer> fileCounts = computeFileCounts(mapping);

        List<DesktopFileVo> desktopFiles = desktopIconService.scanDesktopFiles();
        int unmappedCount = 0;
        for (DesktopFileVo file : desktopFiles) {
            if (!mapping.containsKey(file.getFilePath())) {
                unmappedCount++;
            }
        }
        if (unmappedCount > 0) {
            String defaultGroupId = getDefaultGroupId();
            fileCounts.merge(defaultGroupId, unmappedCount, Integer::sum);
        }

        return groups.stream()
                .sorted(Comparator.comparingInt(GroupDo::getOrder))
                .map(g -> new GroupVo(g.getId(), g.getName(),
                        fileCounts.getOrDefault(g.getId(), 0)))
                .collect(Collectors.toList());
    }

    @Override
    public List<DesktopFileVo> listFilesByGroup(String groupId) {
        Map<String, String> mapping = configService.loadFileGroupMapping();
        List<DesktopFileVo> allFiles = desktopIconService.scanDesktopFiles();

        Set<String> existingPaths = new HashSet<>();
        List<DesktopFileVo> result = new ArrayList<>();

        for (DesktopFileVo file : allFiles) {
            existingPaths.add(file.getFilePath());
            String fileGroupId = mapping.getOrDefault(file.getFilePath(), getDefaultGroupId());
            if (fileGroupId.equals(groupId)) {
                result.add(file);
            }
        }

        cleanupStaleMappings(existingPaths);

        return result;
    }

    @Override
    public GroupVo createGroup(GroupDto dto) {
        String name = dto.getName();
        if (name == null || name.isBlank()) {
            throw new BusinessException("分组名称不能为空");
        }
        if (name.length() > 20) {
            throw new BusinessException("分组名称不能超过20个字符");
        }

        List<GroupDo> groups = configService.loadGroups();

        boolean nameExists = groups.stream()
                .anyMatch(g -> g.getName().equals(name.trim()));
        if (nameExists) {
            throw new BusinessException("已存在同名分组");
        }

        int maxOrder = groups.stream().mapToInt(GroupDo::getOrder).max().orElse(-1);

        GroupDo newGroup = new GroupDo();
        newGroup.setId("g-" + UUID.randomUUID().toString().substring(0, 8));
        newGroup.setName(name.trim());
        newGroup.setOrder(maxOrder + 1);

        groups.add(newGroup);
        configService.saveGroups(groups);

        log.info("创建分组: {} ({})", newGroup.getName(), newGroup.getId());
        return new GroupVo(newGroup.getId(), newGroup.getName(), 0);
    }

    @Override
    public GroupVo renameGroup(String groupId, GroupDto dto) {
        String newName = dto.getName();
        if (newName == null || newName.isBlank()) {
            throw new BusinessException("分组名称不能为空");
        }
        if (newName.length() > 20) {
            throw new BusinessException("分组名称不能超过20个字符");
        }

        List<GroupDo> groups = configService.loadGroups();
        GroupDo target = groups.stream()
                .filter(g -> g.getId().equals(groupId))
                .findFirst()
                .orElseThrow(() -> new BusinessException("分组不存在"));

        target.setName(newName.trim());
        configService.saveGroups(groups);

        Map<String, String> mapping = configService.loadFileGroupMapping();
        int fileCount = countFilesInGroup(mapping, groupId);

        log.info("重命名分组: {} -> {}", groupId, newName);
        return new GroupVo(target.getId(), target.getName(), fileCount);
    }

    @Override
    public void deleteGroup(String groupId) {
        if (getDefaultGroupId().equals(groupId)) {
            throw new BusinessException("默认分组不可删除");
        }

        Map<String, String> mapping = configService.loadFileGroupMapping();
        int fileCount = countFilesInGroup(mapping, groupId);
        if (fileCount > 0) {
            throw new BusinessException("分组内仍有文件，无法删除");
        }

        List<GroupDo> groups = configService.loadGroups();
        boolean removed = groups.removeIf(g -> g.getId().equals(groupId));
        if (!removed) {
            throw new BusinessException("分组不存在");
        }

        configService.saveGroups(groups);
        log.info("删除分组: {}", groupId);
    }

    @Override
    public void moveFileToGroup(String filePath, String targetGroupId) {
        if (!Files.exists(Path.of(filePath))) {
            throw new BusinessException("文件不存在或已被删除");
        }

        List<GroupDo> groups = configService.loadGroups();
        boolean groupExists = groups.stream().anyMatch(g -> g.getId().equals(targetGroupId));
        if (!groupExists) {
            throw new BusinessException("目标分组不存在");
        }

        configService.setFileGroup(filePath, targetGroupId);
        log.info("文件移动分组: {} -> {}", filePath, targetGroupId);
    }

    @Override
    public List<DesktopFileVo> refreshFiles(String groupId) {
        List<DesktopFileVo> currentFiles = desktopIconService.scanDesktopFiles();
        Map<String, String> mapping = configService.loadFileGroupMapping();

        Set<String> existingPaths = new HashSet<>();
        boolean mappingChanged = false;

        for (DesktopFileVo file : currentFiles) {
            existingPaths.add(file.getFilePath());
            if (!mapping.containsKey(file.getFilePath())) {
                mapping.put(file.getFilePath(), getDefaultGroupId());
                mappingChanged = true;
            }
        }

        List<String> removedPaths = new ArrayList<>();
        for (String path : mapping.keySet()) {
            if (!existingPaths.contains(path)) {
                removedPaths.add(path);
            }
        }
        if (!removedPaths.isEmpty()) {
            for (String path : removedPaths) {
                mapping.remove(path);
            }
            mappingChanged = true;
        }

        if (mappingChanged) {
            configService.saveFileGroupMapping(mapping);
        }

        return currentFiles.stream()
                .filter(f -> groupId.equals(
                        mapping.getOrDefault(f.getFilePath(), getDefaultGroupId())))
                .collect(Collectors.toList());
    }

    @Override
    public String getDefaultGroupId() {
        List<GroupDo> groups = configService.loadGroups();
        if (groups.isEmpty()) {
            return "g-default";
        }
        return groups.get(0).getId();
    }

    @Override
    public void reorderGroups(java.util.List<String> orderedGroupIds) {
        List<GroupDo> groups = configService.loadGroups();
        java.util.Map<String, GroupDo> groupMap = new java.util.HashMap<>();
        for (GroupDo g : groups) {
            groupMap.put(g.getId(), g);
        }

        for (int i = 0; i < orderedGroupIds.size(); i++) {
            GroupDo g = groupMap.get(orderedGroupIds.get(i));
            if (g != null) {
                g.setOrder(i);
            }
        }

        groups.sort(Comparator.comparingInt(GroupDo::getOrder));
        configService.saveGroups(groups);
        log.info("分组排序已更新");
    }

    private Map<String, Integer> computeFileCounts(Map<String, String> mapping) {
        Map<String, Integer> counts = new HashMap<>();
        for (String groupId : mapping.values()) {
            counts.merge(groupId, 1, Integer::sum);
        }
        return counts;
    }

    private int countFilesInGroup(Map<String, String> mapping, String groupId) {
        int count = 0;
        for (String gid : mapping.values()) {
            if (gid.equals(groupId)) {
                count++;
            }
        }
        return count;
    }

    private void cleanupStaleMappings(Set<String> existingPaths) {
        Map<String, String> mapping = configService.loadFileGroupMapping();
        List<String> staleKeys = new ArrayList<>();
        for (String path : mapping.keySet()) {
            if (!existingPaths.contains(path) && !Files.exists(Path.of(path))) {
                staleKeys.add(path);
            }
        }
        if (!staleKeys.isEmpty()) {
            for (String key : staleKeys) {
                mapping.remove(key);
            }
            configService.saveFileGroupMapping(mapping);
            log.debug("清理过期映射: {} 条", staleKeys.size());
        }
    }
}
