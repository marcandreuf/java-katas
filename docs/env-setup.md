# Environment setup

Everything here installs under `$HOME` through SDKMAN, which is already on this machine and
already owns your JDK: `JAVA_HOME` points at
`~/.sdkman/candidates/java/current`. **No `sudo` anywhere**, which matters because `sudo` on this
VM wants a password.

## What was missing

Checked 2026-10-15, before this document existed:

| | State |
| --- | --- |
| JDK | Temurin 25.0.4, via SDKMAN — fine |
| Maven | **not installed** (`mvn: command not found`) |
| `~/.m2` | **does not exist** |
| JDK 27 | not installed |
| Network to Maven Central | reachable (HTTP 200) |

So `mvn verify` from the README could not run at all, and `scripts/check-env.sh` would have told
you so: `Maven MISSING`, `Maven cache COLD`.

## 1. Install Maven

    sdk install maven 3.9.16

`sdk install <candidate> <version>` downloads into `~/.sdkman/candidates/maven/3.9.16`, symlinks
`~/.sdkman/candidates/maven/current` at it, and puts that on your `PATH` through the shell hook
SDKMAN added to your profile. The first install of a candidate becomes the default automatically.

**Why 3.9.16 and not `4.0.0-rc-7`.** `pom.xml` enforces `requireMavenVersion` 3.6.3 as a floor, so
anything 3.6.3+ satisfies the build. The 4.0.0 line is still release-candidate and changes the POM
model; you do not want a build tool that is itself an experiment while you are learning language
features. Pick the newest stable 3.x.

Check it:

    mvn -v

## 2. Fill the local repository

    cd /home/dev1/projects/java-katas
    ./scripts/warm-cache.sh

This creates `~/.m2/repository` and downloads JUnit, Hamcrest, commons-lang3 and — the part
`dependency:go-offline` misses — the plugin dependencies Surefire and the compiler plugin pull at
run time. The script runs a real build for exactly that reason.

Then confirm:

    ./scripts/check-env.sh

You want `JDK ok`, `Maven ok`, `Maven cache warm`.

> The README still spells these `./check-env.sh` and `./warm-cache.sh`. They moved to `scripts/`
> in commit `9b22a9c` and the README was not updated with them.

## 3. Install JDK 27 alongside 25

    sdk install java 27.0.0.0+35-tem

Temurin 27 is the LTS-track build matching the `temurin` distribution CI already uses. Installing a
second JDK does **not** change your default — SDKMAN only switches the first one it installs for a
candidate. Verify:

    sdk current java        # still 25.0.4-tem

### Switching between them

    sdk use java 27.0.0.0+35-tem     # this shell only, until you close it
    sdk use java 25.0.4-tem          # back

    sdk default java 27.0.0.0+35-tem # every new shell, permanently

**Use `sdk use`, not `sdk default`, while working through this track.** `java.release` in `pom.xml`
is 21 and most of the track targets that. You want 27 for one exercise at a time, in one shell,
not as the thing every build silently gets.

To check which one a shell is actually holding:

    java -version && echo "JAVA_HOME=$JAVA_HOME"

## 4. Working offline

Once step 2 has run while online:

    mvn -o verify

The `-o` flag forbids network access. No exercise in this track reaches the internet — the
`HttpClient` one starts a server inside the test JVM — so an offline build is the normal case, not
a degraded one.

## What needs which JDK

| Stages | JDK | Why |
| --- | --- | --- |
| Java 8 → 21 material | 25 (default) | All of it compiles at `--release 21`. No pom change. |
| Java 22 → 27 finalised features | 27 | `--release` has to be raised; see `docs/INDEX.md`. |
| Preview features | 27 + `--enable-preview` | Structured concurrency, lazy constants, primitive patterns. Not wired into `pom.xml` yet. |

A JDK can always compile for an **older** `--release` but never a newer one, which is why 27 is the
one to install and 21 stays the floor.
