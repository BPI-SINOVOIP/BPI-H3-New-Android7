// Copyright 2006-2008 the V8 project authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include <errno.h>
#include <signal.h>
#include <stdio.h>

#include "include/libplatform/libplatform.h"
#include "src/assembler.h"
#include "src/base/platform/platform.h"
#include "src/flags.h"
#include "src/list.h"
#include "src/snapshot/natives.h"
#include "src/snapshot/serialize.h"


using namespace v8;

class SnapshotWriter {
 public:
  SnapshotWriter() : fp_(NULL), startup_blob_file_(NULL) {}

  ~SnapshotWriter() {
    if (fp_) fclose(fp_);
    if (startup_blob_file_) fclose(startup_blob_file_);
  }

  void SetSnapshotFile(const char* snapshot_file) {
    if (snapshot_file != NULL) fp_ = GetFileDescriptorOrDie(snapshot_file);
  }

  void SetStartupBlobFile(const char* startup_blob_file) {
    if (startup_blob_file != NULL)
      startup_blob_file_ = GetFileDescriptorOrDie(startup_blob_file);
  }

  void WriteSnapshot(v8::StartupData blob) const {
    i::Vector<const i::byte> blob_vector(
        reinterpret_cast<const i::byte*>(blob.data), blob.raw_size);
    MaybeWriteSnapshotFile(blob_vector);
    MaybeWriteStartupBlob(blob_vector);
  }

 private:
  void MaybeWriteStartupBlob(const i::Vector<const i::byte>& blob) const {
    if (!startup_blob_file_) return;

    size_t written = fwrite(blob.begin(), 1, blob.length(), startup_blob_file_);
    if (written != static_cast<size_t>(blob.length())) {
      i::PrintF("Writing snapshot file failed.. Aborting.\n");
      exit(1);
    }
  }

  void MaybeWriteSnapshotFile(const i::Vector<const i::byte>& blob) const {
    if (!fp_) return;

    WriteFilePrefix();
    WriteData(blob);
    WriteFileSuffix();
  }

  void WriteFilePrefix() const {
    fprintf(fp_, "// Autogenerated snapshot file. Do not edit.\n\n");
    fprintf(fp_, "#include \"src/v8.h\"\n");
    fprintf(fp_, "#include \"src/base/platform/platform.h\"\n\n");
    fprintf(fp_, "#include \"src/snapshot/snapshot.h\"\n\n");
    fprintf(fp_, "namespace v8 {\n");
    fprintf(fp_, "namespace internal {\n\n");
  }

  void WriteFileSuffix() const {
    fprintf(fp_, "const v8::StartupData* Snapshot::DefaultSnapshotBlob() {\n");
    fprintf(fp_, "  return &blob;\n");
    fprintf(fp_, "}\n\n");
    fprintf(fp_, "}  // namespace internal\n");
    fprintf(fp_, "}  // namespace v8\n");
  }

  void WriteData(const i::Vector<const i::byte>& blob) const {
    fprintf(fp_, "static const byte blob_data[] __attribute__((aligned(8))) = {\n");
    WriteSnapshotData(blob);
    fprintf(fp_, "};\n");
    fprintf(fp_, "static const int blob_size = %d;\n", blob.length());
    fprintf(fp_, "static const v8::StartupData blob =\n");
    fprintf(fp_, "{ (const char*) blob_data, blob_size };\n");
  }

  void WriteSnapshotData(const i::Vector<const i::byte>& blob) const {
    for (int i = 0; i < blob.length(); i++) {
      if ((i & 0x1f) == 0x1f) fprintf(fp_, "\n");
      if (i > 0) fprintf(fp_, ",");
      fprintf(fp_, "%u", static_cast<unsigned char>(blob.at(i)));
    }
    fprintf(fp_, "\n");
  }

  FILE* GetFileDescriptorOrDie(const char* filename) {
    FILE* fp = base::OS::FOpen(filename, "wb");
    if (fp == NULL) {
      i::PrintF("Unable to open file \"%s\" for writing.\n", filename);
      exit(1);
    }
    return fp;
  }

  FILE* fp_;
  FILE* startup_blob_file_;
};


char* GetExtraCode(char* filename) {
  if (filename == NULL || strlen(filename) == 0) return NULL;
  ::printf("Embedding extra script: %s\n", filename);
  FILE* file = base::OS::FOpen(filename, "rb");
  if (file == NULL) {
    fprintf(stderr, "Failed to open '%s': errno %d\n", filename, errno);
    exit(1);
  }
  fseek(file, 0, SEEK_END);
  size_t size = ftell(file);
  rewind(file);
  char* chars = new char[size + 1];
  chars[size] = '\0';
  for (size_t i = 0; i < size;) {
    size_t read = fread(&chars[i], 1, size - i, file);
    if (ferror(file)) {
      fprintf(stderr, "Failed to read '%s': errno %d\n", filename, errno);
      exit(1);
    }
    i += read;
  }
  fclose(file);
  return chars;
}


int main(int argc, char** argv) {
  // By default, log code create information in the snapshot.
  i::FLAG_log_code = true;
  i::FLAG_logfile_per_isolate = false;

  // Print the usage if an error occurs when parsing the command line
  // flags or if the help flag is set.
  int result = i::FlagList::SetFlagsFromCommandLine(&argc, argv, true);
  if (result > 0 || (argc != 1 && argc != 2) || i::FLAG_help) {
    ::printf("Usage: %s --startup_src=... --startup_blob=... [extras]\n",
             argv[0]);
    i::FlagList::PrintHelp();
    return !i::FLAG_help;
  }

  i::CpuFeatures::Probe(true);
  V8::InitializeICU();
  v8::Platform* platform = v8::platform::CreateDefaultPlatform();
  v8::V8::InitializePlatform(platform);
  v8::V8::Initialize();

  {
    SnapshotWriter writer;
    if (i::FLAG_startup_src) writer.SetSnapshotFile(i::FLAG_startup_src);
    if (i::FLAG_startup_blob) writer.SetStartupBlobFile(i::FLAG_startup_blob);
    char* extra_code = GetExtraCode(argc == 2 ? argv[1] : NULL);
    StartupData blob = v8::V8::CreateSnapshotDataBlob(extra_code);
    CHECK(blob.data);
    writer.WriteSnapshot(blob);
    delete[] extra_code;
    delete[] blob.data;
  }

  V8::Dispose();
  V8::ShutdownPlatform();
  delete platform;
  return 0;
}
