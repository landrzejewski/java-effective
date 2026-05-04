#!/usr/bin/env bash
# M03 — qualified exports, requires transitive, opens.
# The script deliberately runs ONE compile that must fail (zoo.guest_bad) and
# checks that it fails for the right reason. Then it builds and runs the
# successful path.
set -euo pipefail
cd "$(dirname "$0")"

rm -rf out mods
mkdir -p out mods

echo "[1/6] compile zoo.animal.talks (the producer)"
javac -d out --module-source-path . -m zoo.animal.talks
jar --create --file mods/zoo.animal.talks.jar --module-version 1.0 \
    -C out/zoo.animal.talks .

echo "[2/6] compile zoo.staff (qualified-export friend; requires transitive)"
javac -p mods -d out --module-source-path . -m zoo.staff
jar --create --file mods/zoo.staff.jar --module-version 1.0 \
    --main-class zoo.staff.StaffApp \
    -C out/zoo.staff .

echo "[3/6] compile zoo.guest (gets talks transitively via staff)"
javac -p mods -d out --module-source-path . -m zoo.guest
jar --create --file mods/zoo.guest.jar --module-version 1.0 \
    --main-class zoo.guest.GuestApp \
    -C out/zoo.guest .

echo "[4/6] compile zoo.guest_bad (this MUST fail — qualified export blocks it)"
set +e
javac -p mods -d out --module-source-path . -m zoo.guest_bad 2> bad-compile.log
bad_status=$?
set -e
if [[ $bad_status -ne 0 ]] && grep -q "zoo.animal.talks.content" bad-compile.log; then
    echo "    ✓ compile failed as expected (qualified-exports enforcement works)"
    echo "    compiler said:"
    grep -E "error|is not visible" bad-compile.log | head -2 | sed 's/^/        /'
else
    echo "    ✗ unexpected: zoo.guest_bad compiled, qualified-exports broken"
    cat bad-compile.log
    exit 1
fi
rm -f bad-compile.log

echo "[5/6] run zoo.staff"
java -p mods -m zoo.staff

echo "[6/6] run zoo.guest"
java -p mods -m zoo.guest
