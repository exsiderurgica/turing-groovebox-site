#!/bin/sh
set -eu
mkdir -p build
${CC:-cc} -std=c11 -O2 -Wall -Wextra -Werror -pedantic src/ttgfx.c tests/test_fx.c -lm -o build/test_fx
${CC:-cc} -std=c11 -O2 -Wall -Wextra -Werror -pedantic src/spis_probe.c tests/test_spis_probe.c -o build/test_spis_probe
./build/test_fx
./build/test_spis_probe
