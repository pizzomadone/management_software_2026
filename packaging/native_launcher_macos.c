#include <mach-o/dyld.h>
#include <libgen.h>
#include <limits.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>

int main(int argc, char **argv) {
    char executable[PATH_MAX];
    uint32_t size = (uint32_t) sizeof(executable);
    if (_NSGetExecutablePath(executable, &size) != 0) {
        fprintf(stderr, "WareStat launcher: executable path is too long.\n");
        return 1;
    }

    char resolved[PATH_MAX];
    if (realpath(executable, resolved) == NULL) {
        perror("WareStat launcher realpath");
        return 1;
    }

    char dirbuf[PATH_MAX];
    strncpy(dirbuf, resolved, sizeof(dirbuf) - 1);
    dirbuf[sizeof(dirbuf) - 1] = '\0';
    char *dir = dirname(dirbuf);

    char core[PATH_MAX];
    snprintf(core, sizeof(core), "%s/WareStat-native", dir);

    if (chdir(dir) != 0) {
        perror("WareStat launcher chdir");
        return 1;
    }

    setenv("DYLD_LIBRARY_PATH", dir, 1);

    char sqliteProperty[PATH_MAX + 64];
    snprintf(sqliteProperty, sizeof(sqliteProperty),
             "-Dorg.sqlite.lib.exportPath=%s", dir);

    char **childArgv = calloc((size_t) argc + 2, sizeof(char *));
    if (childArgv == NULL) {
        fprintf(stderr, "WareStat launcher: out of memory.\n");
        return 1;
    }

    childArgv[0] = core;
    childArgv[1] = sqliteProperty;
    for (int i = 1; i < argc; ++i) {
        childArgv[i + 1] = argv[i];
    }
    childArgv[argc + 1] = NULL;

    execv(core, childArgv);
    perror("WareStat launcher execv");
    free(childArgv);
    return 1;
}
