package com.personal.windows.desktopmanager.model;

public class DesktopFileVo {

    private String filePath;

    private String fileName;

    private String extension;

    private long fileSize;

    private boolean isDirectory;

    public DesktopFileVo() {
    }

    public DesktopFileVo(String filePath, String fileName, String extension, long fileSize, boolean isDirectory) {
        this.filePath = filePath;
        this.fileName = fileName;
        this.extension = extension;
        this.fileSize = fileSize;
        this.isDirectory = isDirectory;
    }

    public String getFilePath() {
        return filePath;
    }

    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getExtension() {
        return extension;
    }

    public void setExtension(String extension) {
        this.extension = extension;
    }

    public long getFileSize() {
        return fileSize;
    }

    public void setFileSize(long fileSize) {
        this.fileSize = fileSize;
    }

    public boolean isDirectory() {
        return isDirectory;
    }

    public void setDirectory(boolean directory) {
        isDirectory = directory;
    }

    @Override
    public String toString() {
        return fileName;
    }
}
