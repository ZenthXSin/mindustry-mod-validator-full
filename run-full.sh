#!/bin/bash
# Run the full-environment mod validator on headless Linux servers.
# Requires: xvfb-run, Mesa (llvmpipe software rendering)
#
# Usage: ./run-full.sh <mod-path> [--json] [--output <file>]

export LIBGL_ALWAYS_SOFTWARE=1
export MESA_GL_VERSION_OVERRIDE=3.3
export GALLIUM_DRIVER=llvmpipe

exec xvfb-run -a -s "-screen 0 1280x720x24" \
  java -jar "$(dirname "$0")/build/libs/mindustry-mod-validator-full-1.0.0-all.jar" "$@"
