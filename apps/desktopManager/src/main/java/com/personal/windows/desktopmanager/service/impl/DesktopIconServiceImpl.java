package com.personal.windows.desktopmanager.service.impl;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import com.personal.windows.desktopmanager.exception.DesktopIconException;
import com.personal.windows.desktopmanager.model.DesktopFileVo;
import com.personal.windows.desktopmanager.service.IDesktopIconService;
import com.personal.windows.desktopmanager.util.WindowsApiUtil;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DesktopIconServiceImpl implements IDesktopIconService {

    private static final Logger log = LoggerFactory.getLogger(DesktopIconServiceImpl.class);

    private static final String FALLBACK_DESKTOP_DIR =
            System.getProperty("user.home") + "\\Desktop";

    private final Path desktopPath;
    private final AtomicBoolean watcherRunning = new AtomicBoolean(false);
    private volatile WatchService watchService;
    private volatile Thread watcherThread;

    public DesktopIconServiceImpl() {
        this.desktopPath = Path.of(resolveDesktopPath());
        log.info("桌面路径: {}", desktopPath);
    }

    public DesktopIconServiceImpl(Path desktopPath) {
        this.desktopPath = desktopPath;
    }

    @Override
    public void hideDesktopIcons() {
        WindowsApiUtil.hideDesktopIcons();
    }

    @Override
    public void showDesktopIcons() {
        WindowsApiUtil.showDesktopIcons();
    }

    @Override
    public String getDesktopPath() {
        return desktopPath.toString();
    }

    @Override
    public List<DesktopFileVo> scanDesktopFiles() {
        List<DesktopFileVo> files = new ArrayList<>();
        try {
            if (!Files.isDirectory(desktopPath)) {
                throw new DesktopIconException("桌面目录不存在: " + desktopPath);
            }
            for (Path entry : Files.list(desktopPath).collect(Collectors.toList())) {
                try {
                    String name = entry.getFileName().toString().toLowerCase();
                    if (name.equals("desktop.ini") || name.equals("thumbs.db")) {
                        continue;
                    }
                    files.add(buildDesktopFileVo(entry));
                } catch (IOException e) {
                    log.warn("读取文件信息失败: {}", entry, e);
                }
            }
        } catch (IOException e) {
            throw new DesktopIconException("扫描桌面文件失败", e);
        }
        return files;
    }

    @Override
    public void startFileWatcher(Consumer<DesktopFileVo> onNewFile) {
        if (watcherRunning.getAndSet(true)) {
            log.warn("文件监控已在运行");
            return;
        }

        watcherThread = new Thread(() -> {
            try (WatchService ws = desktopPath.getFileSystem().newWatchService()) {
                watchService = ws;
                desktopPath.register(ws, StandardWatchEventKinds.ENTRY_CREATE);
                log.info("桌面文件监控已启动: {}", desktopPath);

                while (!Thread.currentThread().isInterrupted()) {
                    WatchKey key;
                    try {
                        key = ws.take();
                    } catch (InterruptedException e) {
                        break;
                    }

                    for (WatchEvent<?> event : key.pollEvents()) {
                        WatchEvent.Kind<?> kind = event.kind();
                        if (kind == StandardWatchEventKinds.ENTRY_CREATE) {
                            @SuppressWarnings("unchecked")
                            Path fileName = ((WatchEvent<Path>) event).context();
                            Path fullPath = desktopPath.resolve(fileName);
                            try {
                                Thread.sleep(200);
                            } catch (InterruptedException e) {
                                break;
                            }
                            try {
                                if (Files.exists(fullPath) && !Files.isHidden(fullPath)) {
                                    WindowsApiUtil.hideFile(fullPath.toString());
                                    DesktopFileVo vo = buildDesktopFileVo(fullPath);
                                    onNewFile.accept(vo);
                                    log.debug("新文件已加入: {}", fullPath);
                                }
                            } catch (IOException e) {
                                log.warn("处理新文件失败: {}", fullPath, e);
                            }
                        }
                    }
                    if (!key.reset()) {
                        log.warn("WatchKey 失效，停止监控");
                        break;
                    }
                }
            } catch (IOException e) {
                log.error("文件监控异常", e);
            } finally {
                watcherRunning.set(false);
                log.info("桌面文件监控已停止");
            }
        }, "FileWatcher-Thread");
        watcherThread.setDaemon(true);
        watcherThread.start();
    }

    @Override
    public void stopFileWatcher() {
        if (!watcherRunning.get()) {
            return;
        }
        watcherRunning.set(false);
        if (watcherThread != null) {
            watcherThread.interrupt();
        }
        try {
            if (watchService != null) {
                watchService.close();
            }
        } catch (IOException e) {
            log.warn("关闭 WatchService 失败", e);
        }
    }

    @Override
    public void hideFile(String filePath) {
        WindowsApiUtil.hideFile(filePath);
    }

    @Override
    public String getFileIconKey(String filePath) {
        Path path = Path.of(filePath);
        String fileName = path.getFileName().toString();
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex > 0) {
            return fileName.substring(dotIndex + 1).toLowerCase();
        }
        if (Files.isDirectory(path)) {
            return "folder";
        }
        return "file";
    }

    private String resolveDesktopPath() {
        String fromRegistry = WindowsApiUtil.getDesktopPathFromRegistry();
        if (fromRegistry != null && Files.isDirectory(Path.of(fromRegistry))) {
            return fromRegistry;
        }
        return FALLBACK_DESKTOP_DIR;
    }

    private DesktopFileVo buildDesktopFileVo(Path path) throws IOException {
        String fileName = path.getFileName().toString();
        boolean isDir = Files.isDirectory(path);
        long fileSize = isDir ? -1 : Files.size(path);
        String extension;
        if (isDir) {
            extension = "folder";
        } else {
            int dotIndex = fileName.lastIndexOf('.');
            extension = dotIndex > 0 ? fileName.substring(dotIndex + 1).toLowerCase() : "";
        }

        DesktopFileVo vo = new DesktopFileVo();
        vo.setFilePath(path.toString());
        vo.setFileName(fileName);
        vo.setExtension(extension);
        vo.setFileSize(fileSize);
        vo.setDirectory(isDir);
        return vo;
    }
}
