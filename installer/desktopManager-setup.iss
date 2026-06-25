; --- 桌面管家 Inno Setup 安装脚本 ---
; 用法：先运行 build-installer.ps1，再用 ISCC.exe 编译本脚本

#define MyAppName "DesktopManager"
#define MyAppNameCN "桌面管家"
#define MyAppVersion "1.1.0"
#define MyAppPublisher "Personal"
#ifndef MyAppSourceDir
  #define MyAppSourceDir "..\apps\desktopManager\target\dist"
#endif
#define MyAppSource "{#MyAppSourceDir}\DesktopManager"
#define MyAppOutput "."

[Setup]
AppId={{B8F4A123-5678-4DEF-9012-3456789ABCDE}}
AppName={#MyAppNameCN}
AppVersion={#MyAppVersion}
AppPublisher={#MyAppPublisher}
DefaultDirName={autopf}\{#MyAppNameCN}
DefaultGroupName={#MyAppNameCN}
AllowNoIcons=yes
OutputDir={#MyAppOutput}
OutputBaseFilename=DesktopManager-Setup-{#MyAppVersion}
Compression=lzma2
SolidCompression=yes
WizardStyle=modern
ArchitecturesInstallIn64BitMode=x64compatible
PrivilegesRequired=none
UninstallDisplayName={#MyAppNameCN}
UninstallDisplayIcon={app}\DesktopManager.exe

[Languages]
Name: "english"; MessagesFile: "compiler:Default.isl"

[Files]
; 复制 jpackage 生成的完整运行时镜像（含 EXE、app/、runtime/）
Source: "{#MyAppSource}\*"; DestDir: "{app}"; Flags: ignoreversion recursesubdirs createallsubdirs

[Icons]
Name: "{group}\{#MyAppNameCN}"; Filename: "{app}\DesktopManager.exe"; WorkingDir: "{app}"
Name: "{commondesktop}\{#MyAppNameCN}"; Filename: "{app}\DesktopManager.exe"; WorkingDir: "{app}"; Tasks: desktopicon

[Tasks]
Name: "desktopicon"; Description: "Create a desktop shortcut"; GroupDescription: "Additional icons:"

[Run]
Filename: "{app}\DesktopManager.exe"; Description: "Launch {#MyAppNameCN}"; Flags: nowait postinstall skipifsilent
