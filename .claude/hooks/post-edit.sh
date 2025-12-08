#!/bin/bash

# Post-edit hook - Run after code edits are made
# This hook performs additional validation and formatting

# Check if this is a Scala file
if [[ "$1" == *.scala ]]; then
    echo "Post-edit validation for: $1"

    # Try to run scalafmt if available
    if command -v scalafmt > /dev/null 2>&1; then
        echo "Running scalafmt on $1..."
        scalafmt "$1"
    else
        echo "Note: scalafmt not available - consider installing for automatic formatting"
    fi

    # Quick compile check for the specific file
    echo "Verifying compilation..."
    if mvn scala:compile -q > /dev/null 2>&1; then
        echo "✓ Post-edit compilation successful"
    else
        echo "⚠ Warning: Compilation issues detected"
        mvn scala:compile
    fi
fi

exit 0