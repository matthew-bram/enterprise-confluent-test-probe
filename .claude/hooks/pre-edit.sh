#!/bin/bash

# Pre-edit hook - Run before accepting code edits
# This hook validates that code changes will compile successfully

# Check if this is a Scala file
if [[ "$1" == *.scala ]]; then
    echo "Validating Scala file: $1"

    # Check if the file compiles (main sources only due to test compilation issues)
    if mvn scala:compile -q > /dev/null 2>&1; then
        echo "✓ Compilation check passed"
        exit 0
    else
        echo "✗ Compilation check failed - please fix compilation errors"
        mvn scala:compile
        exit 1
    fi
fi

# For other file types, allow the edit
exit 0