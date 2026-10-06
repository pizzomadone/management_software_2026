#define UNICODE
#define _UNICODE
#include <windows.h>
#include <wchar.h>
#include <stdlib.h>

static void show_error(const wchar_t *message) {
    MessageBoxW(NULL, message, L"WareStat", MB_OK | MB_ICONERROR);
}

int WINAPI wWinMain(HINSTANCE instance, HINSTANCE previous, PWSTR commandLine, int showCommand) {
    (void) instance;
    (void) previous;
    (void) commandLine;
    (void) showCommand;

    wchar_t launcherPath[32768];
    DWORD length = GetModuleFileNameW(NULL, launcherPath, (DWORD)(sizeof(launcherPath) / sizeof(launcherPath[0])));
    if (length == 0 || length >= (DWORD)(sizeof(launcherPath) / sizeof(launcherPath[0]))) {
        show_error(L"Unable to determine the WareStat installation directory.");
        return 1;
    }

    wchar_t *slash = wcsrchr(launcherPath, L'\\');
    if (slash == NULL) {
        show_error(L"Invalid WareStat installation path.");
        return 1;
    }
    *slash = L'\0';
    const wchar_t *appDir = launcherPath;

    if (!SetCurrentDirectoryW(appDir)) {
        show_error(L"Unable to enter the WareStat installation directory.");
        return 1;
    }

    wchar_t corePath[32768];
    if (_snwprintf_s(corePath, _countof(corePath), _TRUNCATE,
                     L"%ls\\WareStat-native.exe", appDir) < 0) {
        show_error(L"WareStat installation path is too long.");
        return 1;
    }

    wchar_t childCommand[65536];
    if (_snwprintf_s(childCommand, _countof(childCommand), _TRUNCATE,
                     L"\"%ls\" \"-Djava.home=%ls\" \"-Dorg.sqlite.lib.exportPath=%ls\"",
                     corePath, appDir, appDir) < 0) {
        show_error(L"WareStat command line is too long.");
        return 1;
    }

    STARTUPINFOW startup;
    PROCESS_INFORMATION process;
    ZeroMemory(&startup, sizeof(startup));
    ZeroMemory(&process, sizeof(process));
    startup.cb = sizeof(startup);

    if (!CreateProcessW(corePath, childCommand, NULL, NULL, FALSE, 0, NULL, appDir, &startup, &process)) {
        wchar_t message[512];
        _snwprintf_s(message, _countof(message), _TRUNCATE,
                     L"Unable to start WareStat (Windows error %lu).", GetLastError());
        show_error(message);
        return 1;
    }

    CloseHandle(process.hThread);
    WaitForSingleObject(process.hProcess, INFINITE);

    DWORD exitCode = 1;
    GetExitCodeProcess(process.hProcess, &exitCode);
    CloseHandle(process.hProcess);
    return (int) exitCode;
}
