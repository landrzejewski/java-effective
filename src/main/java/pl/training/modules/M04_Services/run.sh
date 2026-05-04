#!/usr/bin/env bash
# M04 — Services: API + 2 providers + 1 consumer.
set -euo pipefail
cd "$(dirname "$0")"

rm -rf out mods
mkdir -p out mods

build_module() {
    local name=$1
    local main=${2:-}
    javac -p mods -d out --module-source-path . -m "$name"
    if [[ -n $main ]]; then
        jar --create --file "mods/$name.jar" --module-version 1.0 \
            --main-class "$main" \
            -C "out/$name" .
    else
        jar --create --file "mods/$name.jar" --module-version 1.0 \
            -C "out/$name" .
    fi
}

echo "[1/4] zoo.tours.api  (the SPI)"
build_module zoo.tours.api

echo "[2/4] zoo.tours.guide (provides QuietTour)"
build_module zoo.tours.guide

echo "[3/4] zoo.tours.adventure (provides AdventureTour)"
build_module zoo.tours.adventure

echo "[4/4] zoo.visitor (uses Tour) — and run it"
build_module zoo.visitor zoo.visitor.VisitorApp

echo "--- ServiceLoader output ---"
java -p mods -m zoo.visitor
