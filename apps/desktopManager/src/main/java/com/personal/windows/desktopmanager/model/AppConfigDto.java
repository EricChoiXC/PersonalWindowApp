package com.personal.windows.desktopmanager.model;

public class AppConfigDto {

    private boolean autoStart;

    private String theme;

    public AppConfigDto() {
        this.autoStart = false;
        this.theme = "light";
    }

    public boolean isAutoStart() {
        return autoStart;
    }

    public void setAutoStart(boolean autoStart) {
        this.autoStart = autoStart;
    }

    public String getTheme() {
        return theme;
    }

    public void setTheme(String theme) {
        this.theme = theme;
    }
}
