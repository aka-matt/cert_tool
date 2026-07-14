#!/bin/sh
# Cert Tool launcher (Linux / macOS).
#
# Usage: cert-tool.sh
#
# Locates the runnable JAR next to this script, picks the JavaFX classifier JARs
# matching the host OS from lib/, and launches the app via the Java module path
# so JavaFX loads as named modules (which is what its internal LauncherImpl
# requires).

set -eu

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
JAR="$SCRIPT_DIR/app-0.1.0-SNAPSHOT.jar"
LIB="$SCRIPT_DIR/lib"

if [ ! -f "$JAR" ]; then
    echo "Error: $JAR not found. Run \`./mvnw -pl app package\` first." >&2
    exit 1
fi

case "$(uname -s)" in
    Linux*)   PLATFORM="linux" ;;
    Darwin*)  PLATFORM="mac"   ;;
    MINGW*|MSYS*|CYGWIN*)
        echo "Error: this is the Linux/macOS launcher. Use cert-tool.bat on Windows." >&2
        exit 1
        ;;
    *)
        echo "Error: unsupported OS: $(uname -s)" >&2
        exit 1
        ;;
esac

# Collect the JavaFX classifier JARs for this platform.
JFX_JARS=""
for j in "$LIB"/javafx-base-17.0.11-"$PLATFORM".jar \
         "$LIB"/javafx-controls-17.0.11-"$PLATFORM".jar \
         "$LIB"/javafx-graphics-17.0.11-"$PLATFORM".jar \
         "$LIB"/javafx-fxml-17.0.11-"$PLATFORM".jar; do
    if [ -f "$j" ]; then
        JFX_JARS="$JFX_JARS:$j"
    fi
done
JFX_JARS="${JFX_JARS#:}"

exec java \
    --module-path "$JFX_JARS" \
    --add-modules javafx.base,javafx.controls,javafx.graphics,javafx.fxml \
    -jar "$JAR" \
    "$@"
