package com.personal.windows.desktopmanager.model;

public class GroupVo {

    private String id;

    private String name;

    private int fileCount;

    public GroupVo() {
    }

    public GroupVo(String id, String name, int fileCount) {
        this.id = id;
        this.name = name;
        this.fileCount = fileCount;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getFileCount() {
        return fileCount;
    }

    public void setFileCount(int fileCount) {
        this.fileCount = fileCount;
    }

    @Override
    public String toString() {
        return name + " (" + fileCount + ")";
    }
}
