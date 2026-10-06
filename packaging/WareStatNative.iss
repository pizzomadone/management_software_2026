#define MyAppName "WareStat"
#ifndef MyAppVersion
  #define MyAppVersion "1.3"
#endif
#ifndef MySourceDir
  #define MySourceDir "..\\dist\\native\\windows"
#endif
#ifndef MyOutputDir
  #define MyOutputDir "..\\dist\\installers"
#endif
#ifndef MyIcon
  #define MyIcon "..\\resources\\icons\\warestat.ico"
#endif

[Setup]
AppId={{ee55bd31-e9b9-5503-bca5-9ac13d0501b1}
AppName={#MyAppName}
AppVersion={#MyAppVersion}
AppPublisher=WareStat
AppPublisherURL=https://www.warestat.com
AppSupportURL=https://www.warestat.com
DefaultDirName={localappdata}\\Programs\\WareStat
DefaultGroupName=WareStat
DisableProgramGroupPage=yes
PrivilegesRequired=lowest
ArchitecturesAllowed=x64
ArchitecturesInstallIn64BitMode=x64
OutputDir={#MyOutputDir}
OutputBaseFilename=WareStat-{#MyAppVersion}-windows-x64-setup
SetupIconFile={#MyIcon}
UninstallDisplayIcon={app}\\WareStat.exe
Compression=lzma2/max
SolidCompression=yes
WizardStyle=modern
CloseApplications=yes
RestartApplications=no
VersionInfoVersion={#MyAppVersion}.0
VersionInfoCompany=WareStat
VersionInfoDescription=WareStat Setup
VersionInfoProductName=WareStat
VersionInfoProductVersion={#MyAppVersion}
VersionInfoCopyright=Copyright 2026 WareStat

[Files]
Source: "{#MySourceDir}\\*"; DestDir: "{app}"; Flags: ignoreversion recursesubdirs createallsubdirs

[Icons]
Name: "{group}\\WareStat"; Filename: "{app}\\WareStat.exe"; WorkingDir: "{app}"
Name: "{autodesktop}\\WareStat"; Filename: "{app}\\WareStat.exe"; WorkingDir: "{app}"; Tasks: desktopicon

[Tasks]
Name: "desktopicon"; Description: "Create a desktop shortcut"; GroupDescription: "Additional icons:"; Flags: unchecked

[Run]
Filename: "{app}\\WareStat.exe"; Description: "Launch WareStat"; Flags: nowait postinstall skipifsilent
