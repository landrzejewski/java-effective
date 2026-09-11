#!/usr/bin/env bash
# M02 — exports + requires: build two modules where care -> feeding.
set -euo pipefail
cd "$(dirname "$0")"

rm -rf out mods
mkdir -p out mods

echo "[1/4] compile zoo.animal.feeding"
# Compile the producer module on its own — no module path needed yet.
javac -d out --module-source-path . -m zoo.animal.feeding

echo "[2/4] package zoo.animal.feeding into mods/"
jar --create \
    --file mods/zoo.animal.feeding.jar \
    --module-version 1.0 \
    -C out/zoo.animal.feeding .

echo "[3/4] compile zoo.animal.care (it requires feeding)"
# -p mods lets javac resolve `requires zoo.animal.feeding`.
javac -p mods -d out --module-source-path . -m zoo.animal.care

echo "[4/4] package + run zoo.animal.care"
jar --create \
    --file mods/zoo.animal.care.jar \
    --main-class zoo.animal.care.CareApp \
    --module-version 1.0 \
    -C out/zoo.animal.care .

# A single launcher invocation pulls both modules in via the module path.
java -p mods -m zoo.animal.care
