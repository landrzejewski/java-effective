#!/usr/bin/env bash
# Runs every JPMS training topic in sequence and prints a summary.
# A topic is "green" if its run.sh exits 0.
set -uo pipefail
cd "$(dirname "$0")"

topics=(M01_HelloModule M02_ExportsRequires M03_ModuleDeclaration
        M04_Services M05_DiscoveringModules M06_ModuleTypes)

declare -a results
for t in "${topics[@]}"; do
    echo
    echo "=================================================================="
    echo " Running $t"
    echo "=================================================================="
    if bash "$t/run.sh"; then
        results+=("✓  $t")
    else
        results+=("✗  $t")
    fi
done

echo
echo "=================================================================="
echo " Summary"
echo "=================================================================="
for line in "${results[@]}"; do echo "  $line"; done

# exit non-zero if any topic failed
for line in "${results[@]}"; do
    [[ $line == ✗* ]] && exit 1
done
exit 0
