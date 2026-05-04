#!/usr/bin/env bash
# M01 — Hello Module: build and run the smallest possible named module.
set -euo pipefail
cd "$(dirname "$0")"

# Clean previous outputs.
rm -rf out mods
mkdir -p out mods

echo "[1/3] javac — compile the module into out/zoo.animal.feeding/"
# --module-source-path tells javac where to find the per-module source roots.
# -m names the module(s) to compile.
javac -d out --module-source-path . -m zoo.animal.feeding

echo "[2/3] jar — package out/zoo.animal.feeding/ into mods/zoo.animal.feeding.jar"
jar --create \
    --file mods/zoo.animal.feeding.jar \
    --main-class zoo.animal.feeding.Task \
    --module-version 1.0 \
    -C out/zoo.animal.feeding .

echo "[3/3] java — run the module from the module path"
# -p mods         : module path
# -m moduleName   : main module to launch (uses the --main-class baked into the JAR)
java -p mods -m zoo.animal.feeding
