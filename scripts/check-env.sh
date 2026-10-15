#!/usr/bin/env bash
# Says whether this machine can build the katas offline. Run it before you disconnect.
set -uo pipefail

ok=0
say () { printf '%-28s %s\n' "$1" "$2"; }

# Must match <java.release> in pom.xml: a JDK can target an older release but never a newer one.
required=$(sed -n 's|.*<java.release>\([0-9]*\)</java.release>.*|\1|p' \
    "$(dirname "$0")/../pom.xml" | head -1)
required=${required:-26}

if command -v java >/dev/null 2>&1; then
    ver=$(java -version 2>&1 | head -1)
    major=$(java -version 2>&1 | head -1 | sed -E 's/.*"([0-9]+).*/\1/')
    if [[ "$major" =~ ^[0-9]+$ ]] && (( major >= required )); then
        say "JDK" "ok, $ver"
    else
        say "JDK" "TOO OLD, $ver (need $required or newer)"; ok=1
    fi
else
    say "JDK" "MISSING"; ok=1
fi

if command -v mvn >/dev/null 2>&1; then
    say "Maven" "ok, $(mvn -v 2>/dev/null | head -1)"
else
    say "Maven" "MISSING"; ok=1
fi

repo="${HOME}/.m2/repository"
if [[ -d "$repo/org/junit/jupiter" && -d "$repo/org/apache/maven/plugins/maven-surefire-plugin" ]]; then
    say "Maven cache" "warm"
else
    say "Maven cache" "COLD, run ./warm-cache.sh while online"; ok=1
fi

echo
if (( ok == 0 )); then
    echo "Ready. Offline builds: mvn -o verify"
else
    echo "Not ready. Fix the lines above."
fi
exit $ok
