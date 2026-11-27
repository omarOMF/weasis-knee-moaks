#!/bin/bash
# Installs the Knee MOAKS Outline plugin (the version with smart outlines) for Weasis on this
# Mac and opens Weasis with it. The regular Knee MOAKS install is left as it is.
# Run it again any time to open Weasis with the plugin.
set -e

HERE="$(cd "$(dirname "$0")" && pwd)"
DEST="$HOME/Weasis-plugins/knee-moaks-outline"
mkdir -p "$DEST"

JAR="$(ls -t "$HERE"/weasis-knee-moaks-outline*.jar 2>/dev/null | head -1)"
if [ -n "$JAR" ]; then
  # Weasis keeps a cached copy of each plugin file name it has loaded, so every build has its
  # own name; remove the older ones.
  rm -f "$DEST"/weasis-knee-moaks-outline*.jar
  cp "$JAR" "$DEST/"
fi
NAME="$(ls -t "$DEST"/weasis-knee-moaks-outline*.jar 2>/dev/null | head -1)"
if [ -z "$NAME" ]; then
  echo "The plugin file weasis-knee-moaks-outline-*.jar was not found next to this script."
  exit 1
fi
NAME="$(basename "$NAME")"

cat > "$DEST/knee-moaks-outline.json" <<JSON
{
  "weasisPreferences": [
    {
      "code": "felix.auto.start.125",
      "value": "file://$DEST/$NAME",
      "description": "Knee MOAKS scoring panel with smart outlines"
    }
  ]
}
JSON

urlencode() {
  local s="$1" out="" c i
  for ((i = 0; i < ${#s}; i++)); do
    c="${s:i:1}"
    case "$c" in
      [a-zA-Z0-9.~_-]) out+="$c" ;;
      *) out+=$(printf '%%%02X' "'$c") ;;
    esac
  done
  printf '%s' "$out"
}

CONFIG="file://$DEST/knee-moaks-outline.json"
LINK="weasis://$(urlencode "\$weasis:config pro=\"felix.extended.config.properties $CONFIG\"")"

# A double-clickable shortcut for next time
LAUNCHER="$DEST/Open Weasis with Knee MOAKS Outline.command"
cat > "$LAUNCHER" <<SH
#!/bin/bash
open '$LINK'
SH
chmod +x "$LAUNCHER"

echo "Knee MOAKS Outline is installed in $DEST"
echo "Opening Weasis with the plugin (quit Weasis first if it is already open)..."
open "$LINK"
