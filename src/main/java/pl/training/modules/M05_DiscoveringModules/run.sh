#!/usr/bin/env bash
# M05 — Discovering modules: build, run, then introspect with the JDK tools.
set -euo pipefail
cd "$(dirname "$0")"

rm -rf out mods
mkdir -p out mods

echo "[1/2] build zoo.inventory"
javac -d out --module-source-path . -m zoo.inventory
jar --create --file mods/zoo.inventory.jar --module-version 1.0 \
    --main-class zoo.inventory.InventoryApp \
    -C out/zoo.inventory .

echo "[2/2] run zoo.inventory"
java -p mods -m zoo.inventory

echo
echo "=== java --list-modules (with -p mods) — first few + our module ==="
# Limit the output so this script does not flood the screen.
java -p mods --list-modules | grep -E '^(zoo\.|java\.base|java\.sql|java\.logging|java\.net\.http|java\.xml)' | head -10

echo
echo "=== java -p mods --describe-module zoo.inventory ==="
java -p mods --describe-module zoo.inventory

echo
echo "=== java -p mods --describe-module java.sql ==="
# The JDK module's own description.
java --describe-module java.sql | head -8

echo
echo "=== java -p mods --show-module-resolution -m zoo.inventory (top 6 lines) ==="
java -p mods --show-module-resolution -m zoo.inventory 2>&1 | head -6 || true

echo
echo "=== jdeps mods/zoo.inventory.jar ==="
jdeps --module-path mods mods/zoo.inventory.jar
