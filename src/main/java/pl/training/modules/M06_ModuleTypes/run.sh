#!/usr/bin/env bash
# M06 — Named, automatic, unnamed: same legacy JAR consumed two different ways.
set -euo pipefail
cd "$(dirname "$0")"

rm -rf out mods
mkdir -p out mods

echo "[1/4] build zoo.legacy as a PLAIN jar (no module-info)"
# Compile the legacy classes the old-fashioned way — no --module-source-path.
mkdir -p out/legacy
javac -d out/legacy zoo.legacy/zoo/legacy/LegacyHelper.java
jar --create --file mods/zoo.legacy.jar -C out/legacy .

echo "[2/4] build zoo.consumer.named (treats zoo.legacy as an AUTOMATIC module)"
javac -p mods -d out --module-source-path . -m zoo.consumer.named
jar --create --file mods/zoo.consumer.named.jar --module-version 1.0 \
    --main-class zoo.consumer.named.Named \
    -C out/zoo.consumer.named .

echo "[3/4] build zoo.consumer.classpath (no module-info → UNNAMED at runtime)"
javac -d out/cp -cp mods/zoo.legacy.jar zoo.consumer.classpath/zoo/consumer/classpath/Cp.java

echo "--- run zoo.consumer.named (legacy is AUTOMATIC) ---"
java -p mods -m zoo.consumer.named

echo
echo "--- run zoo.consumer.classpath (legacy is on -cp; consumer is UNNAMED) ---"
java -cp "mods/zoo.legacy.jar:out/cp" zoo.consumer.classpath.Cp

echo
echo "[4/4] inspect: --describe-module on the legacy JAR (note the [automatic] tag)"
java -p mods --describe-module zoo.legacy
