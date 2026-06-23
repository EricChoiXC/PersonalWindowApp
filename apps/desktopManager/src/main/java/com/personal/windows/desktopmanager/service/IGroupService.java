package com.personal.windows.desktopmanager.service;

import com.personal.windows.desktopmanager.model.DesktopFileVo;
import com.personal.windows.desktopmanager.model.GroupDto;
import com.personal.windows.desktopmanager.model.GroupVo;

import java.util.List;

public interface IGroupService {

    List<GroupVo> listGroups();

    List<DesktopFileVo> listFilesByGroup(String groupId);

    GroupVo createGroup(GroupDto dto);

    GroupVo renameGroup(String groupId, GroupDto dto);

    void deleteGroup(String groupId);

    void moveFileToGroup(String filePath, String targetGroupId);

    List<DesktopFileVo> refreshFiles(String groupId);

    String getDefaultGroupId();
}
