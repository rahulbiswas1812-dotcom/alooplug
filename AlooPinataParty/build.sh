#!/usr/bin/env bash
# Usage:  ./build.sh          -> target/AlooPinataParty-2.0.0.jar        (Java 21 bytecode, runs on Java 21 AND 25)
#         ./build.sh java25   -> target/AlooPinataParty-2.0.0-java25.jar (needs JDK 25 to build, Java 25 server only)
set -euo pipefail
cd "$(dirname "$0")"
java -version 2>&1 | head -1
if [ "${1:-}" = "java25" ]; then
  mvn -B clean package -Pjava25
else
  mvn -B clean package
fi
echo; ls -1 target/*.jar
