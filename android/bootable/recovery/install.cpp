/*
 * Copyright (C) 2007 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#include <ctype.h>
#include <errno.h>
#include <fcntl.h>
#include <limits.h>
#include <string.h>
#include <sys/stat.h>
#include <sys/wait.h>
#include <unistd.h>

#include <chrono>
#include <string>
#include <vector>

#include <android-base/parseint.h>
#include <android-base/stringprintf.h>
#include <android-base/strings.h>

#include "common.h"
#include "error_code.h"
#include "install.h"
#include "minui/minui.h"
#include "minzip/SysUtil.h"
#include "minzip/Zip.h"
#include "mtdutils/mounts.h"
#include "mtdutils/mtdutils.h"
#include "roots.h"
#include "ui.h"
#include "verifier.h"
#include "bootloader.h"
#include "cutils/android_reboot.h"

extern RecoveryUI* ui;

#define ASSUMED_UPDATE_BINARY_NAME  "META-INF/com/google/android/update-binary"
#define PUBLIC_KEYS_FILE "/res/keys"
static constexpr const char* METADATA_PATH = "META-INF/com/android/metadata";

// Default allocation of progress bar segments to operations
static const int VERIFICATION_PROGRESS_TIME = 60;
static const float VERIFICATION_PROGRESS_FRACTION = 0.25;
static const float DEFAULT_FILES_PROGRESS_FRACTION = 0.4;
static const float DEFAULT_IMAGE_PROGRESS_FRACTION = 0.1;

static const char* RECOVERY_OTA_SIGN_FILE = "/cache/recovery/last_ota_sign";
static const char* RECOVERY_OTA_STAGE_FILE = "/cache/last_ota_stage";
static const char* SYSRECOVERY = "/dev/block/by-name/sysrecovery";
static bool LastAbort = false;

//power-down protection for incremental ota process.
//Check if the last power-down event  damges the
static int check_last_abort(const char *path){
	//EOCD(End of central directory record) is in the end of the Archive package,
	//which is used to identify the end of data.
	//Each Zip has only one EOCD record

	//the first 22 bytes are EOCD header,the 20-22 bytes are the comment length(n),
	//the 22 to 22 + n bytes are the Zip comment zone
	//
	//An Archive with a whole-file signature will end in six bytes:
	//(2-byte signature start) $ff $ff (2-byte comment size)

	const int FOOTER_SIZE = 6;
	const int EOCD_HEADER_SIZE = 22;
	unsigned char footer[FOOTER_SIZE];
	LastAbort = false;

	FILE * file_path;
	if((file_path = fopen(path,"rb")) == NULL){
		LOGE("failed to open %s (%s)\n", path, strerror(errno));
		return -1;
	}

	if(fseek(file_path, -FOOTER_SIZE, SEEK_END) != 0){
		LOGE("failed to seek in %s (%s)\n", path, strerror(errno));
		fclose(file_path);
		return -1;
	}

	if(fread(footer, 1, FOOTER_SIZE, file_path) != FOOTER_SIZE){
		LOGE("failed to read footer in %s (%s)\n", path, strerror(errno));
		fclose(file_path);
		return -1;
	}

	size_t comment_size = footer[4] + (footer[5] << 8);
	size_t eocd_size = EOCD_HEADER_SIZE + comment_size;

	if(fseek(file_path, -eocd_size, SEEK_END) != 0){
		LOGE("failed to seek in %s (%s)\n", path, strerror(errno));
		fclose(file_path);
		return -1;
	}

	unsigned char *eocd = NULL;

	if((eocd = (unsigned char *)malloc(eocd_size)) == NULL){
		LOGE("malloc for EOCD record failed\n");
		fclose(file_path);
		return -1;
	}

	if(fread(eocd, 1, eocd_size, file_path) != eocd_size){
		LOGE("failed to read eocd from %s (%s)\n", path, strerror(errno));
		free(eocd);
		fclose(file_path);
		return -1;
	}

	fclose(file_path);

	FILE *file_sign;
	if((file_sign = fopen(RECOVERY_OTA_SIGN_FILE,"rb")) != NULL ){
		unsigned char *eocd_old = (unsigned char *)malloc(eocd_size);
		if((eocd_old = (unsigned char *)malloc(eocd_size)) == NULL ){
			LOGE("malloc for EOCD old record failed\n");
			fclose(file_sign);
			free(eocd);
			return -1;
		}

		if(fread(eocd_old, 1, eocd_size, file_sign) != eocd_size){
			LOGE("not the same ota signature because the size is different\n");
			fclose(file_sign);
			free(eocd_old);
		}
		else{
			//if both the eocd are the same,then needn't create last_ota_sign
			if(memcmp(eocd, eocd_old, eocd_size) == 0){
				LOGE("Same ota signature\n");
				FILE * file_stage;
				if((file_stage = fopen(RECOVERY_OTA_STAGE_FILE, "rb")) != NULL){
					char buf[512] = {0};
					int size = fread(buf, 1, 512, file_stage);
					if(strlen(buf) == 0){
						LOGE("No content in %s\n",RECOVERY_OTA_STAGE_FILE);
					}
					else{
						if(strcmp("passCheck",buf) != 0){
							LOGE("last ota doen't pass Check\n");
						}
						else{
							LastAbort = true;
							LOGE("last ota pass Check\n");
						}
					}
					fclose(file_stage);
				}
				free(eocd_old);
				free(eocd);
				return 0;
			}
			else{
				LOGE("Not the same ota signature\n");
				free(eocd_old);
			}
		}
	}

	mkdir("/cache/recovery",0755);
	if((file_sign = fopen(RECOVERY_OTA_SIGN_FILE,"w+b")) == NULL){
		int err = errno;
		LOGE("errno %d , %s\n", err, strerror(err));
		return -1;
	}

	size_t done = 0;
	while(done < eocd_size){
		int wrote = fwrite(eocd + done, 1, eocd_size - done, file_sign);
		if(wrote < 0){
			fclose(file_sign);
			free(eocd);
			return -1;
		}
		done += wrote;
	}

	fflush(file_sign);
	fsync(fileno(file_sign));
	free(eocd);
	fclose(file_sign);
	return 0;
}

//clear the files and handle the power-down event happens last time
static int handle_last_abort(int result){
	unlink(RECOVERY_OTA_SIGN_FILE);
	unlink(RECOVERY_OTA_STAGE_FILE);

	if(access(SYSRECOVERY,R_OK) == 0){
		LOGE("sysrecovery exists\n");
	}
	else{
		LOGE("sysrecovery doesn't exist\n");
	}
	if(LastAbort && (access(SYSRECOVERY,R_OK) == 0) && (result != INSTALL_SUCCESS)){
		LOGE("sysrecovery is needed\n");

		struct bootloader_message bm;
		memset(&bm, 0, sizeof(bm));
		strlcpy(bm.command, "boot-recovery", sizeof(bm.command));
		strlcpy(bm.recovery, "sysrecovery", sizeof(bm.recovery));
		set_bootloader_message(&bm);

		ensure_path_unmounted("/data");
		format_volume("/data");

		ensure_path_unmounted("/cache");
		format_volume("/cache");

		android_reboot(ANDROID_RB_RESTART, 0, NULL);
	}
	return 0;
}



// This function parses and returns the build.version.incremental
static int parse_build_number(std::string str) {
    size_t pos = str.find("=");
    if (pos != std::string::npos) {
        std::string num_string = android::base::Trim(str.substr(pos+1));
        int build_number;
        if (android::base::ParseInt(num_string.c_str(), &build_number, 0)) {
            return build_number;
        }
    }

    LOGE("Failed to parse build number in %s.\n", str.c_str());
    return -1;
}

// Read the build.version.incremental of src/tgt from the metadata and log it to last_install.
static void read_source_target_build(ZipArchive* zip, std::vector<std::string>& log_buffer) {
    const ZipEntry* meta_entry = mzFindZipEntry(zip, METADATA_PATH);
    if (meta_entry == nullptr) {
        LOGE("Failed to find %s in update package.\n", METADATA_PATH);
        return;
    }

    std::string meta_data(meta_entry->uncompLen, '\0');
    if (!mzReadZipEntry(zip, meta_entry, &meta_data[0], meta_entry->uncompLen)) {
        LOGE("Failed to read metadata in update package.\n");
        return;
    }

    // Examples of the pre-build and post-build strings in metadata:
    // pre-build-incremental=2943039
    // post-build-incremental=2951741
    std::vector<std::string> lines = android::base::Split(meta_data, "\n");
    for (const std::string& line : lines) {
        std::string str = android::base::Trim(line);
        if (android::base::StartsWith(str, "pre-build-incremental")){
            int source_build = parse_build_number(str);
            if (source_build != -1) {
                log_buffer.push_back(android::base::StringPrintf("source_build: %d",
                        source_build));
            }
        } else if (android::base::StartsWith(str, "post-build-incremental")) {
            int target_build = parse_build_number(str);
            if (target_build != -1) {
                log_buffer.push_back(android::base::StringPrintf("target_build: %d",
                        target_build));
            }
        }
    }
}

// If the package contains an update binary, extract it and run it.
static int
try_update_binary(const char* path, ZipArchive* zip, bool* wipe_cache,
                  std::vector<std::string>& log_buffer, int retry_count)
{
    read_source_target_build(zip, log_buffer);

    const ZipEntry* binary_entry =
            mzFindZipEntry(zip, ASSUMED_UPDATE_BINARY_NAME);
    if (binary_entry == NULL) {
        mzCloseZipArchive(zip);
        return INSTALL_CORRUPT;
    }

    const char* binary = "/tmp/update_binary";
    unlink(binary);
    int fd = creat(binary, 0755);
    if (fd < 0) {
        mzCloseZipArchive(zip);
        LOGE("Can't make %s\n", binary);
        return INSTALL_ERROR;
    }
    bool ok = mzExtractZipEntryToFile(zip, binary_entry, fd);
    close(fd);
    mzCloseZipArchive(zip);

    if (!ok) {
        LOGE("Can't copy %s\n", ASSUMED_UPDATE_BINARY_NAME);
        return INSTALL_ERROR;
    }

    int pipefd[2];
    pipe(pipefd);

    // When executing the update binary contained in the package, the
    // arguments passed are:
    //
    //   - the version number for this interface
    //
    //   - an fd to which the program can write in order to update the
    //     progress bar.  The program can write single-line commands:
    //
    //        progress <frac> <secs>
    //            fill up the next <frac> part of of the progress bar
    //            over <secs> seconds.  If <secs> is zero, use
    //            set_progress commands to manually control the
    //            progress of this segment of the bar.
    //
    //        set_progress <frac>
    //            <frac> should be between 0.0 and 1.0; sets the
    //            progress bar within the segment defined by the most
    //            recent progress command.
    //
    //        firmware <"hboot"|"radio"> <filename>
    //            arrange to install the contents of <filename> in the
    //            given partition on reboot.
    //
    //            (API v2: <filename> may start with "PACKAGE:" to
    //            indicate taking a file from the OTA package.)
    //
    //            (API v3: this command no longer exists.)
    //
    //        ui_print <string>
    //            display <string> on the screen.
    //
    //        wipe_cache
    //            a wipe of cache will be performed following a successful
    //            installation.
    //
    //        clear_display
    //            turn off the text display.
    //
    //        enable_reboot
    //            packages can explicitly request that they want the user
    //            to be able to reboot during installation (useful for
    //            debugging packages that don't exit).
    //
    //   - the name of the package zip file.
    //
    //   - an optional argument "retry" if this update is a retry of a failed
    //   update attempt.
    //

    const char** args = (const char**)malloc(sizeof(char*) * 6);
    args[0] = binary;
    args[1] = EXPAND(RECOVERY_API_VERSION);   // defined in Android.mk
    char* temp = (char*)malloc(10);
    sprintf(temp, "%d", pipefd[1]);
    args[2] = temp;
    args[3] = (char*)path;
    args[4] = retry_count > 0 ? "retry" : NULL;
    args[5] = NULL;

    pid_t pid = fork();
    if (pid == 0) {
        umask(022);
        close(pipefd[0]);
        execv(binary, (char* const*)args);
        fprintf(stdout, "E:Can't run %s (%s)\n", binary, strerror(errno));
        _exit(-1);
    }
    close(pipefd[1]);

    *wipe_cache = false;
    bool retry_update = false;

    char buffer[1024];
    FILE* from_child = fdopen(pipefd[0], "r");
    while (fgets(buffer, sizeof(buffer), from_child) != NULL) {
        char* command = strtok(buffer, " \n");
        if (command == NULL) {
            continue;
        } else if (strcmp(command, "progress") == 0) {
            char* fraction_s = strtok(NULL, " \n");
            char* seconds_s = strtok(NULL, " \n");

            float fraction = strtof(fraction_s, NULL);
            int seconds = strtol(seconds_s, NULL, 10);

            ui->ShowProgress(fraction * (1-VERIFICATION_PROGRESS_FRACTION), seconds);
        } else if (strcmp(command, "set_progress") == 0) {
            char* fraction_s = strtok(NULL, " \n");
            float fraction = strtof(fraction_s, NULL);
            ui->SetProgress(fraction);
        } else if (strcmp(command, "ui_print") == 0) {
            char* str = strtok(NULL, "\n");
            if (str) {
                ui->PrintOnScreenOnly("%s", str);
            } else {
                ui->PrintOnScreenOnly("\n");
            }
            fflush(stdout);
        } else if (strcmp(command, "wipe_cache") == 0) {
            *wipe_cache = true;
        } else if (strcmp(command, "clear_display") == 0) {
            ui->SetBackground(RecoveryUI::NONE);
        } else if (strcmp(command, "enable_reboot") == 0) {
            // packages can explicitly request that they want the user
            // to be able to reboot during installation (useful for
            // debugging packages that don't exit).
            ui->SetEnableReboot(true);
        } else if (strcmp(command, "retry_update") == 0) {
            retry_update = true;
        } else if (strcmp(command, "log") == 0) {
            // Save the logging request from updater and write to
            // last_install later.
            log_buffer.push_back(std::string(strtok(NULL, "\n")));
        } else {
            LOGE("unknown command [%s]\n", command);
        }
    }
    fclose(from_child);

    int status;
    waitpid(pid, &status, 0);
    if (retry_update) {
        return INSTALL_RETRY;
    }
    if (!WIFEXITED(status) || WEXITSTATUS(status) != 0) {
        LOGE("Error in %s\n(Status %d)\n", path, WEXITSTATUS(status));
        return INSTALL_ERROR;
    }

    return INSTALL_SUCCESS;
}

static int
really_install_package(const char *path, bool* wipe_cache, bool needs_mount,
                       std::vector<std::string>& log_buffer, int retry_count)
{
    ui->SetBackground(RecoveryUI::INSTALLING_UPDATE);
    ui->Print("Finding update package...\n");
    // Give verification half the progress bar...
    ui->SetProgressType(RecoveryUI::DETERMINATE);
    ui->ShowProgress(VERIFICATION_PROGRESS_FRACTION, VERIFICATION_PROGRESS_TIME);
    LOGI("Update location: %s\n", path);

    // Map the update package into memory.
    ui->Print("Opening update package...\n");

    if (path && needs_mount) {
        if (path[0] == '@') {
            ensure_path_mounted(path+1);
        } else {
            ensure_path_mounted(path);
        }
    }

    MemMapping map;
    if (sysMapFile(path, &map) != 0) {
        LOGE("failed to map file\n");
        return INSTALL_CORRUPT;
    }

    // Load keys.
    std::vector<Certificate> loadedKeys;
    if (!load_keys(PUBLIC_KEYS_FILE, loadedKeys)) {
        LOGE("Failed to load keys\n");
        return INSTALL_CORRUPT;
    }
    LOGI("%zu key(s) loaded from %s\n", loadedKeys.size(), PUBLIC_KEYS_FILE);

    // Verify package.
    ui->Print("Verifying update package...\n");
    auto t0 = std::chrono::system_clock::now();
    int err = verify_file(map.addr, map.length, loadedKeys);
    std::chrono::duration<double> duration = std::chrono::system_clock::now() - t0;
    ui->Print("Update package verification took %.1f s (result %d).\n", duration.count(), err);
    if (err != VERIFY_SUCCESS) {
        LOGE("signature verification failed\n");
        log_buffer.push_back(android::base::StringPrintf("error: %d", kZipVerificationFailure));

        sysReleaseMap(&map);
        return INSTALL_CORRUPT;
    }

    //power-down protection
    if(check_last_abort(path) != 0){
        LOGE("check last abort for power-down failed\n");
    }

    // Try to open the package.
    ZipArchive zip;
    err = mzOpenZipArchive(map.addr, map.length, &zip);
    if (err != 0) {
        LOGE("Can't open %s\n(%s)\n", path, err != -1 ? strerror(err) : "bad");
        log_buffer.push_back(android::base::StringPrintf("error: %d", kZipOpenFailure));

        sysReleaseMap(&map);
        return INSTALL_CORRUPT;
    }

    // Verify and install the contents of the package.
    ui->Print("Installing update...\n");
    if (retry_count > 0) {
        ui->Print("Retry attempt: %d\n", retry_count);
    }
    ui->SetEnableReboot(false);
    int result = try_update_binary(path, &zip, wipe_cache, log_buffer, retry_count);
    ui->SetEnableReboot(true);
    ui->Print("\n");

    sysReleaseMap(&map);

    return result;
}

int
install_package(const char* path, bool* wipe_cache, const char* install_file,
                bool needs_mount, int retry_count)
{
    modified_flash = true;
    auto start = std::chrono::system_clock::now();

    FILE* install_log = fopen_path(install_file, "w");
    if (install_log) {
        fputs(path, install_log);
        fputc('\n', install_log);
    } else {
        LOGE("failed to open last_install: %s\n", strerror(errno));
    }
    int result;
    std::vector<std::string> log_buffer;
    if (setup_install_mounts() != 0) {
        LOGE("failed to set up expected mounts for install; aborting\n");
        result = INSTALL_ERROR;
    } else {
        result = really_install_package(path, wipe_cache, needs_mount, log_buffer, retry_count);
    }
    if (install_log != nullptr) {
        fputc(result == INSTALL_SUCCESS ? '1' : '0', install_log);
        fputc('\n', install_log);
        std::chrono::duration<double> duration = std::chrono::system_clock::now() - start;
        int count = static_cast<int>(duration.count());
        // Report the time spent to apply OTA update in seconds.
        fprintf(install_log, "time_total: %d\n", count);
        fprintf(install_log, "retry: %d\n", retry_count);

        for (const auto& s : log_buffer) {
            fprintf(install_log, "%s\n", s.c_str());
        }

        fclose(install_log);
    }

    handle_last_abort(result);

    return result;
}
