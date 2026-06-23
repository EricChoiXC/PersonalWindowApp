package com.personal.windows.desktopmanager.service;

import com.personal.windows.desktopmanager.model.DesktopFileVo;

import java.util.List;
import java.util.function.Consumer;

public interface IDesktopIconService {

    void hideDesktopIcons();

    void showDesktopIcons();

    String getDesktopPath();

    List<DesktopFileVo> scanDesktopFiles();

    void startFileWatcher(Consumer<DesktopFileVo> onNewFile);

    void stopFileWatcher();

    void hideFile(String filePath);

    String getFileIconKey(String filePath);
}
