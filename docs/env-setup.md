# Environment setup

Everything here installs under `$HOME` through SDKMAN, which is already on this machine and
already owns your JDK: `JAVA_HOME` points at
`~/.sdkman/candidates/java/current`. **No `sudo` anywhere**, which matters because `sudo` on this
VM wants a password.

## What was missing

Checked 2026-10-15, before this document existed, and where it stands now:

| | State then | State now |
| --- | --- | --- |
| JDK | Temurin 25.0.4 | **Temurin 26.0.2.1**, the default |
| Maven | **not installed** (`mvn: command not found`) | 3.9.16, via SDKMAN |
| `~/.m2` | **did not exist** | warm |
| Network to Maven Central | reachable (HTTP 200) | reachable |

So `mvn verify` from the README could not run at all, and `scripts/check-env.sh` would have said
so: `Maven MISSING`, `Maven cache COLD`. **Both are fixed.** The steps below are kept because
they are what a new machine needs.

`check-env.sh` now reads the required JDK out of `<java.release>` in `pom.xml` rather than
hard-coding 21, so it fails on a JDK that cannot build the repo instead of passing it.

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

## 3. The JDK

`<java.release>` in `pom.xml` is **26**, so you need a JDK of **26 or newer** to build this at
all. A JDK compiles for any older `--release` and never a newer one.

    sdk install java 26.0.2+1.1-tem
    sdk default java 26.0.2+1.1-tem
    sdk current java

Stage 18 (HTTP/3, JEP 517) is the one that needs 26 specifically; everything before it would
build on 25.

### Looking further ahead

Java 27 shipped in September 2026 and this track does not cover it yet. To try the rows in
[`INDEX.md`](INDEX.md) that mention it — compact object headers on by default, post-quantum
TLS — install it as a second JDK:

    sdk install java 27.0.0.0+35-tem

Installing a second JDK does **not** change your default; SDKMAN only switches the first one it
installs for a candidate. Then:

    sdk use java 27.0.0.0+35-tem     # this shell only, until you close it
    sdk use java 26.0.2+1.1-tem      # back

    sdk default java 27.0.0.0+35-tem # every new shell, permanently

**Use `sdk use`, not `sdk default`, for that.** You want the newer JDK for one experiment at a
time, in one shell, not as the thing every build silently gets.

## 4. Working offline

Once step 2 has run while online:

    mvn -o verify

The `-o` flag forbids network access. No exercise in this track reaches the internet — the
`HttpClient` one starts a server inside the test JVM — so an offline build is the normal case, not
a degraded one.

## What needs which JDK

| Stages | JDK | Why |
| --- | --- | --- |
| Java 8 → 21 material | any 26+ | All of it compiles at `--release 21`. |
| Java 22 → 26 finalised features | **26** | What `<java.release>` is set to. HTTP/3 (stage 18) is the one that needs 26 specifically. |
| Preview features | 26 + the preview flag | `mvn -Ppreview verify`. The profile follows the active JDK, so it works without editing the pom. |

A JDK can always compile for an **older** `--release` but never a newer one, which is why the
JDK you run has to be at least `<java.release>`.


