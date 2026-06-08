#!/usr/bin/env bash
# Extract and display a Minecraft source file from a NeoForge MDG project's generated sources jar.
# The sources jar contains decompiled Java with Mojang official (human-readable) names.
#
# Reads from build/moddev/artifacts/neoforge-{version}-sources.jar (primary, vanilla MC)
# Auto-switches to merged jar for NeoForge classes (net.neoforged.*) or if not found in sources.
#
# Usage: decompile.sh <class-name>
# Examples:
#   decompile.sh net.minecraft.world.item.ItemStack
#   decompile.sh net/minecraft/world/item/ItemStack
#   decompile.sh ItemStack
#   decompile.sh ItemStack.Builder              (inner class, extracts outer file)
#   decompile.sh net.neoforged.neoforge.event.Event

set -euo pipefail

CLASS_INPUT="$1"

# ---------- 1. Find project root ----------
find_project_root() {
    local dir
    dir="$(pwd)"
    while [[ "$dir" != "/" && "$dir" != "" ]]; do
        if [[ -f "$dir/gradle.properties" ]]; then
            echo "$dir"
            return 0
        fi
        dir="$(dirname "$dir")"
    done
    echo ""
}

PROJECT_ROOT="$(find_project_root)"
if [[ -z "$PROJECT_ROOT" ]]; then
    echo "ERROR: Not in a NeoForge mod project." >&2
    exit 1
fi

# ---------- 2. Find the jars ----------
# Location: build/moddev/artifacts/neoforge-{version}-sources.jar
ARTIFACTS_DIR="$PROJECT_ROOT/build/moddev/artifacts"

if [[ ! -d "$ARTIFACTS_DIR" ]]; then
    echo "ERROR: Artifacts directory not found at: $ARTIFACTS_DIR" >&2
    echo "       Run './gradlew build' or import the project in your IDE first." >&2
    exit 1
fi

NEO_VERSION=$(grep -E '^neo_version=' "$PROJECT_ROOT/gradle.properties" | cut -d= -f2 | tr -d '[:space:]')

SOURCES_JAR="$ARTIFACTS_DIR/neoforge-${NEO_VERSION}-sources.jar"
MERGED_JAR="$ARTIFACTS_DIR/neoforge-${NEO_VERSION}-merged.jar"

# Fallback: find any versioned jar if exact version not found
if [[ ! -f "$SOURCES_JAR" ]]; then
    SOURCES_JAR=$(find "$ARTIFACTS_DIR" -maxdepth 1 -name "neoforge-*-sources.jar" 2>/dev/null | head -1 || echo "")
fi
if [[ ! -f "$MERGED_JAR" ]]; then
    MERGED_JAR=$(find "$ARTIFACTS_DIR" -maxdepth 1 -name "neoforge-*-merged.jar" 2>/dev/null | head -1 || echo "")
fi

# Pick jar: NeoForge classes go to merged jar, everything else prefers sources
pick_jar() {
    local class_name="$1"
    # NeoForge classes are only in the merged jar
    if [[ "$class_name" == net.neoforged* ]] || [[ "$class_name" == net/neoforged* ]]; then
        echo "merged"
        return
    fi
    # Prefer sources jar for vanilla Minecraft classes
    if [[ -f "$SOURCES_JAR" ]]; then
        echo "sources"
    elif [[ -f "$MERGED_JAR" ]]; then
        echo "merged"
    else
        echo ""
    fi
}

class_in_jar() {
    local jar="$1"
    local path="$2"
    unzip -Z1 "$jar" 2>/dev/null | grep -qxF "$path" && return 0
    # case-insensitive fallback
    unzip -Z1 "$jar" 2>/dev/null | grep -qi "^${path}$" && return 0
    return 1
}

# ---------- 3. Convert class name to file path ----------
class_to_path() {
    local name="$1"
    # Strip inner class suffix ($Foo or .Foo) for file lookup
    name="${name%%\$*}"
    name="${name//.//}"
    echo "${name}.java"
}

find_in_jar() {
    local jar="$1"
    local query="$2"
    unzip -Z1 "$jar" 2>/dev/null | grep -i "/${query}\.java$" || true
}

search_file() {
    local short_name="$1"
    local jar="$2"
    find_in_jar "$jar" "$short_name"
}

# ---------- 4. Resolve file path ----------
JAR_TYPE=$(pick_jar "$CLASS_INPUT")
if [[ "$JAR_TYPE" == "merged" ]]; then
    USE_JAR="$MERGED_JAR"
else
    USE_JAR="$SOURCES_JAR"
fi

if [[ -z "$USE_JAR" || ! -f "$USE_JAR" ]]; then
    echo "ERROR: No sources or merged jar found in $ARTIFACTS_DIR" >&2
    echo "       Run './gradlew build' first." >&2
    exit 1
fi

# Short name search
if [[ "$CLASS_INPUT" != *"."* && "$CLASS_INPUT" != *"/"* && "$CLASS_INPUT" != *'$'* ]]; then
    SHORT_NAME="$CLASS_INPUT"
    # Search in chosen jar first
    MATCHES=$(search_file "$SHORT_NAME" "$USE_JAR")
    MATCH_COUNT=$(echo "$MATCHES" | grep -c . || true)
    # If not found and we're using sources, also try merged jar
    if [[ "$MATCH_COUNT" -eq 0 && "$JAR_TYPE" == "sources" && -f "$MERGED_JAR" ]]; then
        MATCHES=$(search_file "$SHORT_NAME" "$MERGED_JAR")
        MATCH_COUNT=$(echo "$MATCHES" | grep -c . || true)
        if [[ "$MATCH_COUNT" -ge 1 ]]; then
            USE_JAR="$MERGED_JAR"
            JAR_TYPE="merged"
        fi
    fi
    if [[ "$MATCH_COUNT" -eq 0 ]]; then
        echo "ERROR: No source file found with name '${SHORT_NAME}.java'" >&2
        exit 1
    elif [[ "$MATCH_COUNT" -gt 1 ]]; then
        echo "Multiple matches for '${SHORT_NAME}':" >&2
        echo "$MATCHES" | while read -r line; do echo "  $line" >&2; done
        echo "Please re-run with a full class name." >&2
        exit 1
    fi
    FILE_PATH="$MATCHES"
else
    FILE_PATH=$(class_to_path "$CLASS_INPUT")
    # If result has no package path (e.g. inner class dot notation "ItemStack.Builder"),
    # search the jar for the full path
    if [[ "$FILE_PATH" != *"/"* ]]; then
        SHORT_NAME="${FILE_PATH%.java}"
        MATCHES=$(search_file "$SHORT_NAME" "$USE_JAR")
        MATCH_COUNT=$(echo "$MATCHES" | grep -c . || true)
        if [[ "$MATCH_COUNT" -eq 0 && "$JAR_TYPE" == "sources" && -f "$MERGED_JAR" ]]; then
            MATCHES=$(search_file "$SHORT_NAME" "$MERGED_JAR")
            MATCH_COUNT=$(echo "$MATCHES" | grep -c . || true)
            if [[ "$MATCH_COUNT" -ge 1 ]]; then
                USE_JAR="$MERGED_JAR"
                JAR_TYPE="merged"
            fi
        fi
        if [[ "$MATCH_COUNT" -ge 1 ]]; then
            FILE_PATH=$(echo "$MATCHES" | head -1)
        fi
    fi
fi

# Verify file exists in chosen jar; if not, try the other jar
if ! class_in_jar "$USE_JAR" "$FILE_PATH"; then
    if [[ "$JAR_TYPE" == "sources" && -f "$MERGED_JAR" ]]; then
        if class_in_jar "$MERGED_JAR" "$FILE_PATH"; then
            USE_JAR="$MERGED_JAR"
            JAR_TYPE="merged"
        fi
    elif [[ "$JAR_TYPE" == "merged" && -f "$SOURCES_JAR" ]]; then
        if class_in_jar "$SOURCES_JAR" "$FILE_PATH"; then
            USE_JAR="$SOURCES_JAR"
            JAR_TYPE="sources"
        fi
    fi
fi

if ! class_in_jar "$USE_JAR" "$FILE_PATH"; then
    # Last resort: strip inner class from dot notation
    BASE_NAME="${CLASS_INPUT##*.}"
    FOUND=$(unzip -Z1 "$USE_JAR" 2>/dev/null | grep -i "/${BASE_NAME}\.java$" | grep -v '[$]' | head -1 || true)
    if [[ -z "$FOUND" && "$JAR_TYPE" == "sources" && -f "$MERGED_JAR" ]]; then
        FOUND=$(unzip -Z1 "$MERGED_JAR" 2>/dev/null | grep -i "/${BASE_NAME}\.java$" | grep -v '[$]' | head -1 || true)
        if [[ -n "$FOUND" ]]; then
            USE_JAR="$MERGED_JAR"
            JAR_TYPE="merged"
        fi
    fi
    if [[ -z "$FOUND" ]]; then
        echo "ERROR: '${FILE_PATH}' not found in any jar." >&2
        echo "       Check the class name." >&2
        exit 1
    fi
    FILE_PATH="$FOUND"
fi

# ---------- 5. Extract and print ----------
MC_VERSION=$(grep -E '^minecraft_version=' "$PROJECT_ROOT/gradle.properties" | cut -d= -f2 | tr -d '[:space:]')

echo "--- Minecraft ${MC_VERSION} (NeoForge ${NEO_VERSION}) ---"
echo "Source:  ${FILE_PATH}"
echo "Jar:     $(basename "$USE_JAR") (${JAR_TYPE})"
echo ""

unzip -p "$USE_JAR" "$FILE_PATH" 2>/dev/null || {
    echo "ERROR: Failed to extract ${FILE_PATH} from $(basename "$USE_JAR")." >&2
    exit 1
}
