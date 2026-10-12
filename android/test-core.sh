#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
mkdir -p build/test-classes
cp_path="libs/okhttp-jvm-5.5.0.jar:libs/okio-jvm-3.18.1.jar:libs/kotlin-stdlib-2.1.21.jar:libs/annotations-23.0.0.jar"
java -jar tooling/ecj.jar -8 -proc:none -encoding UTF-8 -cp "$cp_path" -d build/test-classes app/src/main/java/com/qianxia/backup/BackupCrypto.java app/src/main/java/com/qianxia/backup/FileOps.java app/src/main/java/com/qianxia/backup/DavClient.java tests/CoreTest.java
java -cp "build/test-classes:$cp_path" com.qianxia.backup.CoreTest | tee verification/core-tests.txt
