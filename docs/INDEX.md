# Index: Java 8 → 26, in release order

The original track (docs `00`–`11`) is ordered by **concept**, smallest change first. That is the
right order for modernising this repo and the wrong order for reviewing a language. This index is
the second ordering: every developer-facing feature from Java 8 to Java 26, in the release it
landed, pointing at the document that teaches it.

**Read in Stage order. The file numbers are append-only** — `12` is the first stage because it was
written after `11`, not because it comes last. Nothing was renumbered, because the `@Disabled`
reasons in `src/test` name document filenames and renaming them silently breaks that link.

**Java 27 is deliberately not covered yet.** It is four final JEPs, three of which are JVM
defaults rather than anything you write, and they are noted in the rows below where they continue
a story. See [the last section](#java-27-not-yet-covered).

Status words:

| | |
| --- | --- |
| **doc NN** | has an exercise or written answers, written |
| read only | worth knowing, no exercise earns its keep |
| skipped | deliberately not taught, with the reason given |

---

## Java 8 (2014) — the release this repo predates

The repo contains **zero** lambdas, streams, `Optional` or `java.time`. One file uses `->`, and
this track added it. Java 8 is not revision here, it is new ground.

| Feature | Stage |
| --- | --- |
| Lambdas, functional interfaces, method references | **[doc 12](12-lambdas-and-method-references.md)** |
| Default, static and private interface methods | **[doc 12](12-lambdas-and-method-references.md)** |
| `java.time` | **[doc 13](13-java-time.md)** |
| `Map` default methods, `Comparator` combinators, `Collectors` | **[doc 14](14-map-defaults-and-collectors.md)** |
| `CompletableFuture` | **[doc 15](15-completable-future.md)** |
| Streams, `Optional` | [doc 09](09-streams-and-optional.md) |
| `StringJoiner`, `Base64`, `Arrays.parallelSort` | [doc 14](14-map-defaults-and-collectors.md), prompts |
| `LongAdder`, `StampedLock` | [doc 08](08-memory-model.md) + [doc 34](34-measuring-it-properly.md) |
| Repeatable annotations, type annotations | read only |
| Nashorn | skipped — removed in 15. Learning it is negative value. |

## Java 9 (2017)

| Feature | JEP | Stage |
| --- | --- | --- |
| Collection factories (`List.of`, `Map.of`) | 269 | **[doc 16](16-collection-factories.md)** |
| `Stream.takeWhile` / `dropWhile` / 3-arg `iterate` | 269 | **[doc 17](17-stream-and-optional-additions.md)** |
| `Optional.stream` / `or` / `ifPresentOrElse` | 269 | **[doc 17](17-stream-and-optional-additions.md)** |
| `@Deprecated(forRemoval)` | 277 | **[doc 30](30-upgrade-hazards.md)** |
| JDK internals encapsulated | 260 | **[doc 30](30-upgrade-hazards.md)** |
| Compact strings | 254 | **[doc 20](20-free-diagnostics.md)** |
| G1 becomes the default collector | 248 | **[doc 31](31-runtime-defaults.md)** |
| `jshell` | 222 | **[doc 25](25-tooling.md)** |
| JPMS | 261 | [doc 10](10-sequenced-collections-and-httpclient.md), paper; reframed in [doc 27](27-module-imports-and-compact-sources.md) |
| `VarHandle` | 193 | [doc 30](30-upgrade-hazards.md) |
| Effectively-final try-with-resources | 213 | [doc 18](18-files-strings-and-charsets.md) |
| `Flow`, `StackWalker`, Process API | 266, 259, 102 | read only |

## Java 10 (2018)

| Feature | JEP | Stage |
| --- | --- | --- |
| `var` | 286 | [doc 05](05-var.md) |
| `List.copyOf`, `Collectors.toUnmodifiable*` | — | **[doc 16](16-collection-factories.md)** |
| `Optional.orElseThrow()` | — | **[doc 17](17-stream-and-optional-additions.md)** |

## Java 11 (2018, LTS)

| Feature | JEP | Stage |
| --- | --- | --- |
| `HttpClient` | 321 | [doc 10](10-sequenced-collections-and-httpclient.md) |
| `String.isBlank` / `strip` / `lines` / `repeat` | — | **[doc 18](18-files-strings-and-charsets.md)** |
| `Files.readString` / `writeString` | — | **[doc 18](18-files-strings-and-charsets.md)** |
| Single-file source launch | 330 | **[doc 25](25-tooling.md)** |
| Flight Recorder becomes free | 328 | [doc 11](11-jvm-diagnostics.md), [doc 20](20-free-diagnostics.md) |
| Java EE and CORBA modules **removed** | 320 | **[doc 30](30-upgrade-hazards.md)** |
| `var` in lambda parameters | 323 | [doc 05](05-var.md) |
| Epsilon GC, ZGC (experimental) | 318, 333 | [doc 31](31-runtime-defaults.md) |

## Java 12–13 (2019)

| Feature | JEP | Stage |
| --- | --- | --- |
| `Collectors.teeing` | — | **[doc 17](17-stream-and-optional-additions.md)** |
| `String.indent` / `transform` | — | **[doc 18](18-files-strings-and-charsets.md)** |
| `CompactNumberFormat`, `Files.mismatch` | — | read only |

## Java 14 (2020)

| Feature | JEP | Stage |
| --- | --- | --- |
| Switch expressions (final) | 361 | [doc 04](04-switch-and-text-blocks.md) |
| **Helpful NullPointerExceptions** | 358 | **[doc 20](20-free-diagnostics.md)** |
| Records (preview) | 359 | [doc 01](01-records.md) |
| `@Serial` | — | [doc 30](30-upgrade-hazards.md) |

## Java 15 (2020)

| Feature | JEP | Stage |
| --- | --- | --- |
| Text blocks (final) | 378 | [doc 04](04-switch-and-text-blocks.md) |
| Sealed classes (preview) | 360 | [doc 02](02-sealed-types.md) |
| Nashorn **removed** | 372 | **[doc 30](30-upgrade-hazards.md)** |
| EdDSA | 339 | [doc 32](32-crypto.md) |

## Java 16 (2021)

| Feature | JEP | Stage |
| --- | --- | --- |
| Records (final) | 395 | [doc 01](01-records.md) |
| Pattern matching for `instanceof` (final) | 394 | [doc 03](03-pattern-matching.md) |
| `Stream.toList`, `Stream.mapMulti` | — | **[doc 16](16-collection-factories.md)**, [doc 09](09-streams-and-optional.md) |
| Internals strongly encapsulated by default | 396 | **[doc 30](30-upgrade-hazards.md)** |
| Unix domain sockets, `jpackage` | 380, 392 | read only |
| Vector API (incubator, 1st of 11) | 338 | read only — see the note at the end |

## Java 17 (2021, LTS)

| Feature | JEP | Stage |
| --- | --- | --- |
| Sealed classes (final) | 409 | [doc 02](02-sealed-types.md) |
| **`RandomGenerator` API** | 356 | **[doc 19](19-randomness-and-numerics.md)** |
| `--illegal-access` escape hatch **removed** | 403 | **[doc 30](30-upgrade-hazards.md)** |
| Security Manager deprecated | 411 | **[doc 30](30-upgrade-hazards.md)** |
| Context-specific deserialization filters | 415 | read only |

## Java 18 (2022)

| Feature | JEP | Stage |
| --- | --- | --- |
| **UTF-8 by default** | 400 | **[doc 18](18-files-strings-and-charsets.md)** |
| Simple Web Server (`jwebserver`) | 408 | **[doc 25](25-tooling.md)** |
| Code snippets in javadoc (`@snippet`) | 413 | [doc 25](25-tooling.md) |
| Finalization deprecated | 421 | **[doc 30](30-upgrade-hazards.md)** |

## Java 19–20 (2022–23) — the preview runway

| Feature | JEP | Stage |
| --- | --- | --- |
| Virtual threads (preview) | 425 | [doc 06](06-virtual-threads.md) |
| Structured concurrency (incubator) | 428, 437 | [doc 26](26-scoped-values-and-structured-concurrency.md) |
| Scoped values (incubator) | 429 | [doc 26](26-scoped-values-and-structured-concurrency.md) |
| Record patterns (preview) | 405 | [doc 03](03-pattern-matching.md) |

## Java 21 (2023, LTS) — where the original track stops

| Feature | JEP | Stage |
| --- | --- | --- |
| Virtual threads (final) | 444 | [doc 06](06-virtual-threads.md) |
| Sequenced collections | 431 | [doc 10](10-sequenced-collections-and-httpclient.md) |
| Pattern matching for `switch` (final) | 441 | [doc 03](03-pattern-matching.md) |
| Record patterns (final) | 440 | [doc 03](03-pattern-matching.md) |
| Generational ZGC | 439 | **[doc 31](31-runtime-defaults.md)** |
| `Math.clamp`, `StringBuilder.repeat` | — | **[doc 19](19-randomness-and-numerics.md)** |
| Key Encapsulation Mechanism API | 452 | **[doc 32](32-crypto.md)** |
| 32-bit x86 port deprecated | 449 | [doc 30](30-upgrade-hazards.md) |
| **String templates (preview)** | 430 | **skipped — withdrawn.** See [doc 33](33-preview-shelf.md). |

> **String templates are the one trap in this list.** Previewed in 21, again in 22, then withdrawn
> and removed from 23. Tutorials, courses and conference talks from 2024 still teach them as the
> future of string interpolation. They are not in Java 26 and there is no successor JEP.
> [Doc 33](33-preview-shelf.md) is about reading that signal.

## Java 22 (2024)

| Feature | JEP | Stage |
| --- | --- | --- |
| Unnamed variables and patterns (`_`) | 456 | **[doc 21](21-unnamed-and-primitive-patterns.md)** |
| Foreign Function & Memory API (final) | 454 | **[doc 23](23-foreign-function-and-memory.md)** |
| Stream Gatherers (preview) | 461 | **[doc 22](22-stream-gatherers.md)** |
| Class-File API (preview) | 457 | **[doc 24](24-class-file-api.md)** |
| Launch multi-file source programs | 458 | **[doc 25](25-tooling.md)** |
| Statements before `super(...)` (preview) | 447 | **[doc 28](28-flexible-constructor-bodies.md)** |

## Java 23 (2024)

| Feature | JEP | Stage |
| --- | --- | --- |
| Markdown documentation comments | 467 | **[doc 25](25-tooling.md)** |
| Generational ZGC becomes ZGC's only mode | 474 | **[doc 31](31-runtime-defaults.md)** |
| `sun.misc.Unsafe` memory access deprecated | 471 | **[doc 30](30-upgrade-hazards.md)** |
| Module import declarations (preview) | 476 | **[doc 27](27-module-imports-and-compact-sources.md)** |
| Primitive types in patterns (preview) | 455 | [doc 21](21-unnamed-and-primitive-patterns.md) |
| **String templates removed** | — | skipped — see above |

## Java 24 (2025)

| Feature | JEP | Stage |
| --- | --- | --- |
| **Stream Gatherers (final)** | 485 | **[doc 22](22-stream-gatherers.md)** |
| **Class-File API (final)** | 484 | **[doc 24](24-class-file-api.md)** |
| ML-KEM and ML-DSA | 496, 497 | **[doc 32](32-crypto.md)** |
| Ahead-of-time class loading & linking | 483 | **[doc 31](31-runtime-defaults.md)** |
| Security Manager permanently disabled | 486 | **[doc 30](30-upgrade-hazards.md)** |
| `sun.misc.Unsafe` memory access **warns** | 498 | **[doc 30](30-upgrade-hazards.md)** |
| JNI use restricted, with warnings | 472 | **[doc 23](23-foreign-function-and-memory.md)** |
| Compact object headers (experimental) | 450 | **[doc 31](31-runtime-defaults.md)** |
| Generational Shenandoah | 404 | [doc 31](31-runtime-defaults.md) |
| Key Derivation Function API (preview) | 478 | **[doc 32](32-crypto.md)** |
| Flexible constructor bodies (3rd preview) | 492 | **[doc 28](28-flexible-constructor-bodies.md)** |
| Module imports / simple source files (2nd preview) | 494, 495 | **[doc 27](27-module-imports-and-compact-sources.md)** |
| Late barrier expansion, images without JMODs | 475, 493 | read only |

## Java 25 (2025, LTS) — the biggest language batch since 21

| Feature | JEP | Stage |
| --- | --- | --- |
| **Scoped Values (final)** | 506 | **[doc 26](26-scoped-values-and-structured-concurrency.md)** |
| **Module Import Declarations (final)** | 511 | **[doc 27](27-module-imports-and-compact-sources.md)** |
| **Compact Source Files & Instance Main (final)** | 512 | **[doc 27](27-module-imports-and-compact-sources.md)** |
| **Flexible Constructor Bodies (final)** | 513 | **[doc 28](28-flexible-constructor-bodies.md)** |
| **Key Derivation Function API (final)** | 510 | **[doc 32](32-crypto.md)** |
| Structured Concurrency (5th preview) | 505 | **[doc 26](26-scoped-values-and-structured-concurrency.md)** |
| Stable Values (preview) | 502 | **[doc 33](33-preview-shelf.md)** |
| Compact object headers (product, opt-in) | 519 | **[doc 31](31-runtime-defaults.md)** |
| JFR CPU-time profiling / sampling / method timing | 509, 518, 520 | **[doc 31](31-runtime-defaults.md)** |
| AOT command-line ergonomics, method profiling | 514, 515 | **[doc 31](31-runtime-defaults.md)** |
| PEM encodings (preview) | 470 | [doc 32](32-crypto.md), [doc 33](33-preview-shelf.md) |
| 32-bit x86 port **removed** | 503 | **[doc 30](30-upgrade-hazards.md)** |
| Primitive types in patterns (3rd preview) | 507 | [doc 21](21-unnamed-and-primitive-patterns.md) |

## Java 26 (March 2026) — current for this track

| Feature | JEP | Stage |
| --- | --- | --- |
| **HTTP/3 for the HttpClient API** | 517 | **[doc 29](29-http3-and-the-httpclient.md)** |
| **Prepare to Make Final Mean Final** | 500 | **[doc 30](30-upgrade-hazards.md)** |
| **Applet API removed** | 504 | **[doc 30](30-upgrade-hazards.md)** |
| AOT object caching with any GC | 516 | **[doc 31](31-runtime-defaults.md)** |
| G1 throughput via reduced synchronization | 522 | **[doc 31](31-runtime-defaults.md)** |
| Structured concurrency (6th preview) | 525 | **[doc 26](26-scoped-values-and-structured-concurrency.md)** — the `Joiner` API changed again |
| Lazy Constants (2nd preview, renamed) | 526 | **[doc 33](33-preview-shelf.md)** |
| PEM encodings (2nd preview) | 524 | [doc 33](33-preview-shelf.md) |
| Primitive types in patterns (4th preview) | 530 | [doc 21](21-unnamed-and-primitive-patterns.md) |
| Vector API (11th incubator) | 529 | read only — see below |

> **The Vector API has incubated for eleven releases**, since Java 16, and has never been
> previewed. It is waiting on Project Valhalla and has no target release. Treat it the way you
> treat String templates: know it exists, know *why* it has not shipped, and do not build on it.

---

## The stages, in reading order

| Stage | Document | Releases |
| --- | --- | --- |
| 1 | [12 — Lambdas, functional interfaces, method references](12-lambdas-and-method-references.md) | 8, 9 |
| 2 | [13 — `java.time`](13-java-time.md) | 8 |
| 3 | [14 — Map defaults, comparators, collectors](14-map-defaults-and-collectors.md) | 8 |
| 4 | [15 — `CompletableFuture`](15-completable-future.md) | 8, 9, 12 |
| 5 | [16 — Collection factories and immutability](16-collection-factories.md) | 9, 10, 16 |
| 6 | [17 — Stream and `Optional` additions after 8](17-stream-and-optional-additions.md) | 9, 10, 12, 16 |
| 7 | [18 — Files, strings and charsets](18-files-strings-and-charsets.md) | 11, 12, 18 |
| 8 | [19 — Randomness and numerics](19-randomness-and-numerics.md) | 17, 21 |
| 9 | [20 — The diagnostics you get for free](20-free-diagnostics.md) | 9, 11, 14, 15 |
| 10 | [21 — Unnamed variables, and primitive patterns](21-unnamed-and-primitive-patterns.md) | 22, preview |
| 11 | [22 — Stream gatherers](22-stream-gatherers.md) | 22, 24 |
| 12 | [23 — Foreign function and memory](23-foreign-function-and-memory.md) | 22, 24 |
| 13 | [24 — The Class-File API](24-class-file-api.md) | 22, 24 |
| 14 | [25 — Tooling: jshell, source launch, jwebserver, markdown javadoc](25-tooling.md) | 9, 11, 18, 22, 23, 25 |
| 15 | [26 — Scoped values, and structured concurrency](26-scoped-values-and-structured-concurrency.md) | 25, preview |
| 16 | [27 — Module imports, compact source files, instance `main`](27-module-imports-and-compact-sources.md) | 23, 24, 25 |
| 17 | [28 — Flexible constructor bodies](28-flexible-constructor-bodies.md) | 22, 24, 25 |
| 18 | [29 — HTTP/3 and the HttpClient](29-http3-and-the-httpclient.md) | 26 |
| 19 | [30 — Upgrade hazards: deprecations and removals](30-upgrade-hazards.md) | 8–26 |
| 20 | [31 — Runtime defaults: GC, AOT, JFR](31-runtime-defaults.md) | 9–26 |
| 21 | [32 — Crypto: key derivation, post-quantum, PEM](32-crypto.md) | 21–26 |
| 22 | [33 — The preview shelf, and what withdrawal looks like](33-preview-shelf.md) | 25, 26 |
| 23 | [34 — Measuring it properly](34-measuring-it-properly.md) | — |

### What needs which JDK

| Stages | `--release` | Notes |
| --- | --- | --- |
| 1–9 | 21 | All of it compiles at the original floor. |
| 10–17, 21 | 22–25 | Covered by the current setting. |
| 18 | 26 | HTTP/3. `<java.release>` is now **26**, so this is a compiled exercise. |
| 15, 22 | 26 + preview | `mvn -Ppreview verify`. Never enable preview by default. |
| 12 | 26 + native access | Surefire already passes the flag; [doc 23](23-foreign-function-and-memory.md) says why. |

[`env-setup.md`](env-setup.md) has the commands, including installing JDK 27 alongside 25.

## Java 27, not yet covered

Java 27 (15 September 2026) shipped **nine JEPs and only four are final**, three of which are JVM
defaults rather than anything you write. Where they continue a story already in this track:

| Feature | JEP | Continues |
| --- | --- | --- |
| Compact object headers **by default** | 534 | [doc 31](31-runtime-defaults.md) — opt-in in 25, default in 27 |
| G1 as the default collector everywhere | 523 | [doc 31](31-runtime-defaults.md) |
| JFR in-process data redaction | 536 | [doc 31](31-runtime-defaults.md) |
| Post-quantum hybrid key exchange for TLS 1.3 | 527 | [doc 32](32-crypto.md) — the gap that doc ends on |
| Structured concurrency (7th preview) | 533 | [doc 26](26-scoped-values-and-structured-concurrency.md) |
| Lazy Constants (3rd preview) | 531 | [doc 33](33-preview-shelf.md) |
| Primitive types in patterns (5th preview) | 532 | [doc 21](21-unnamed-and-primitive-patterns.md) |
| PEM encodings (3rd preview) | 538 | [doc 33](33-preview-shelf.md) |

## What this index is not

It is not a feature checklist to tick off. The original track's rule holds and matters more here
than anywhere: **every document ends with "what got worse", and you fill it in.** Twenty-three
stages of features you can name is worth less than four you can argue about.
