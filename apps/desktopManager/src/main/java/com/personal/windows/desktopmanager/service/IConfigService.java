package com.personal.windows.desktopmanager.service;

import com.personal.windows.desktopmanager.model.AppConfigDto;
import com.personal.windows.desktopmanager.model.GroupDo;

import java.util.List;
import java.util.Map;

public interface IConfigService {

    List<GroupDo> loadGroups();

    void saveGroups(List<GroupDo> groups);

    Map<String, String> loadFileGroupMapping();

    void saveFileGroupMapping(Map<String, String> mapping);

    AppConfigDto loadAppConfig();

    void saveAppConfig(AppConfigDto config);

    void setFileGroup(String filePath, String groupId);

    String getFileGroup(String filePath);

    void ensureConfigExists();
}
