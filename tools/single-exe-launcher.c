#define UNICODE
#define _UNICODE
#define WIN32_LEAN_AND_MEAN

#include <windows.h>
#include <stdint.h>
#include <string.h>
#include <wchar.h>

#define MAGIC "TTTONIE_ALBUM_PAYLOAD_V1"
#define MAGIC_SIZE (sizeof(MAGIC) - 1)
#define COPY_BUFFER_SIZE (1024 * 1024)

static unsigned char copy_buffer[COPY_BUFFER_SIZE];

static void show_error(const wchar_t *message) {
    MessageBoxW(NULL, message, L"tttonie album maker", MB_OK | MB_ICONERROR);
}

static BOOL create_directory_if_needed(const wchar_t *path) {
    return CreateDirectoryW(path, NULL) || GetLastError() == ERROR_ALREADY_EXISTS;
}

static BOOL run_and_wait(const wchar_t *command) {
    STARTUPINFOW startup = { .cb = sizeof(startup) };
    PROCESS_INFORMATION process = {0};
    wchar_t command_copy[32768];
    DWORD exit_code = 1;

    if (wcslen(command) >= (sizeof(command_copy) / sizeof(command_copy[0]))) {
        return FALSE;
    }
    wcscpy(command_copy, command);

    if (!CreateProcessW(NULL, command_copy, NULL, NULL, FALSE, CREATE_NO_WINDOW,
            NULL, NULL, &startup, &process)) {
        return FALSE;
    }

    WaitForSingleObject(process.hProcess, INFINITE);
    GetExitCodeProcess(process.hProcess, &exit_code);
    CloseHandle(process.hThread);
    CloseHandle(process.hProcess);
    return exit_code == 0;
}

static BOOL launch_application(const wchar_t *application_path) {
    STARTUPINFOW startup = { .cb = sizeof(startup) };
    PROCESS_INFORMATION process = {0};
    wchar_t command[32768];

    if (swprintf(command, sizeof(command) / sizeof(command[0]), L"\"%ls\"", application_path) < 0) {
        return FALSE;
    }
    if (!CreateProcessW(application_path, command, NULL, NULL, FALSE, 0,
            NULL, NULL, &startup, &process)) {
        return FALSE;
    }

    CloseHandle(process.hThread);
    CloseHandle(process.hProcess);
    return TRUE;
}

static uint64_t read_uint64_le(const unsigned char *bytes) {
    uint64_t value = 0;
    for (int index = 7; index >= 0; --index) {
        value = (value << 8) | bytes[index];
    }
    return value;
}

int WINAPI wWinMain(HINSTANCE instance, HINSTANCE previous, PWSTR command_line, int show_command) {
    wchar_t executable_path[MAX_PATH];
    wchar_t local_app_data[MAX_PATH];
    wchar_t base_directory[MAX_PATH];
    wchar_t cache_directory[MAX_PATH];
    wchar_t zip_path[MAX_PATH];
    wchar_t application_path[MAX_PATH];
    wchar_t extraction_command[32768];
    HANDLE source = INVALID_HANDLE_VALUE;
    HANDLE zip_file = INVALID_HANDLE_VALUE;
    LARGE_INTEGER source_size;
    LARGE_INTEGER footer_position;
    FILETIME created, accessed, written;
    unsigned char footer[MAGIC_SIZE + sizeof(uint64_t)];
    uint64_t payload_size;
    uint64_t remaining;
    DWORD transferred;

    (void) instance;
    (void) previous;
    (void) command_line;
    (void) show_command;

    if (!GetModuleFileNameW(NULL, executable_path, MAX_PATH)) {
        show_error(L"Unable to locate the application executable.");
        return 1;
    }
    source = CreateFileW(executable_path, GENERIC_READ, FILE_SHARE_READ, NULL,
            OPEN_EXISTING, FILE_ATTRIBUTE_NORMAL, NULL);
    if (source == INVALID_HANDLE_VALUE || !GetFileSizeEx(source, &source_size)
            || source_size.QuadPart <= (LONGLONG) sizeof(footer)
            || !GetFileTime(source, &created, &accessed, &written)) {
        show_error(L"Unable to read the embedded application package.");
        goto fail;
    }

    footer_position.QuadPart = source_size.QuadPart - sizeof(footer);
    if (!SetFilePointerEx(source, footer_position, NULL, FILE_BEGIN)
            || !ReadFile(source, footer, sizeof(footer), &transferred, NULL)
            || transferred != sizeof(footer)
            || memcmp(footer, MAGIC, MAGIC_SIZE) != 0) {
        show_error(L"This executable does not contain a valid application package.");
        goto fail;
    }

    payload_size = read_uint64_le(footer + MAGIC_SIZE);
    if (payload_size == 0 || payload_size > (uint64_t) source_size.QuadPart - sizeof(footer)) {
        show_error(L"The embedded application package is corrupted.");
        goto fail;
    }

    if (!GetEnvironmentVariableW(L"LOCALAPPDATA", local_app_data, MAX_PATH)) {
        if (!GetTempPathW(MAX_PATH, local_app_data)) {
            show_error(L"Unable to find a directory for the application runtime.");
            goto fail;
        }
    }
    if (swprintf(base_directory, MAX_PATH, L"%ls\\tttonie_album_maker", local_app_data) < 0
            || !create_directory_if_needed(base_directory)
            || swprintf(cache_directory, MAX_PATH, L"%ls\\%llu-%lu", base_directory,
                (unsigned long long) source_size.QuadPart, written.dwLowDateTime) < 0
            || !create_directory_if_needed(cache_directory)
            || swprintf(application_path, MAX_PATH,
                L"%ls\\tttonie_album_maker\\tttonie_album_maker.exe", cache_directory) < 0) {
        show_error(L"Unable to prepare the application runtime directory.");
        goto fail;
    }

    if (GetFileAttributesW(application_path) == INVALID_FILE_ATTRIBUTES) {
        if (swprintf(zip_path, MAX_PATH, L"%ls\\package.zip", cache_directory) < 0) {
            show_error(L"Unable to prepare the embedded application package.");
            goto fail;
        }
        footer_position.QuadPart = source_size.QuadPart - sizeof(footer) - (LONGLONG) payload_size;
        if (!SetFilePointerEx(source, footer_position, NULL, FILE_BEGIN)) {
            show_error(L"Unable to read the embedded application package.");
            goto fail;
        }
        zip_file = CreateFileW(zip_path, GENERIC_WRITE, 0, NULL, CREATE_ALWAYS,
                FILE_ATTRIBUTE_NORMAL, NULL);
        if (zip_file == INVALID_HANDLE_VALUE) {
            show_error(L"Unable to write the embedded application package.");
            goto fail;
        }
        remaining = payload_size;
        while (remaining > 0) {
            DWORD chunk_size = remaining > COPY_BUFFER_SIZE ? COPY_BUFFER_SIZE : (DWORD) remaining;
            if (!ReadFile(source, copy_buffer, chunk_size, &transferred, NULL) || transferred != chunk_size
                    || !WriteFile(zip_file, copy_buffer, chunk_size, &transferred, NULL) || transferred != chunk_size) {
                show_error(L"Unable to unpack the embedded application package.");
                goto fail;
            }
            remaining -= chunk_size;
        }
        CloseHandle(zip_file);
        zip_file = INVALID_HANDLE_VALUE;

        if (swprintf(extraction_command, sizeof(extraction_command) / sizeof(extraction_command[0]),
                L"powershell.exe -NoProfile -NonInteractive -ExecutionPolicy Bypass -Command \"Expand-Archive -LiteralPath '%ls' -DestinationPath '%ls' -Force\"",
                zip_path, cache_directory) < 0 || !run_and_wait(extraction_command)) {
            show_error(L"Unable to unpack the application runtime. Windows PowerShell is required.");
            goto fail;
        }
    }

    if (!launch_application(application_path)) {
        show_error(L"Unable to start tttonie album maker.");
        goto fail;
    }
    CloseHandle(source);
    return 0;

fail:
    if (zip_file != INVALID_HANDLE_VALUE) {
        CloseHandle(zip_file);
    }
    if (source != INVALID_HANDLE_VALUE) {
        CloseHandle(source);
    }
    return 1;
}
