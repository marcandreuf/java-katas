# java-katas

Exercises in Java, written between 2014 and 2021, plus a **modernization track** that takes them
from the idiom they were written in to the current one.

The old code is the point. It is real code with real habits in it: pre-diamond generics, hand-rolled
`finally` blocks, `wait`/`notifyAll`, swallowed `InterruptedException`, and a couple of genuine bugs
that survived because nothing tested them. Rewriting that is a more honest exercise than rewriting
something built to be rewritten.

## The track

`docs/` has one document per concept. Each names the files here that carry the old idiom, says what
to change, and asks what got worse afterwards.

**Start with [`docs/modernization/00-start-here.md`](docs/modernization/00-start-here.md).** It covers the one thing that has to
happen before anything else: the repo does not currently compile.

| # | Concept |
| --- | --- |
| [00](docs/modernization/00-start-here.md) | Start here, and make it build |
| [01](docs/modernization/01-records.md) | Records |
| [02](docs/modernization/02-sealed-types.md) | Sealed types and exhaustiveness |
| [03](docs/modernization/03-pattern-matching.md) | Pattern matching |
| [04](docs/modernization/04-switch-and-text-blocks.md) | Switch expressions and text blocks |
| [05](docs/modernization/05-var.md) | `var`, and the diamond |
| [06](docs/modernization/06-virtual-threads.md) | Virtual threads |
| [07](docs/modernization/07-classic-concurrency.md) | Classic concurrency |
| [08](docs/modernization/08-memory-model.md) | The memory model |
| [09](docs/modernization/09-streams-and-optional.md) | Streams and Optional |
| [10](docs/modernization/10-sequenced-collections-and-httpclient.md) | Sequenced collections, HttpClient |
| [11](docs/modernization/11-jvm-diagnostics.md) | JVM production diagnostics |
| [12](docs/modernization/12-lambdas-and-method-references.md) | Lambdas, functional interfaces, method references |
| [13](docs/modernization/13-java-time.md) | `java.time` |
| [14](docs/modernization/14-map-defaults-and-collectors.md) | `Map` defaults, comparators, collectors |
| [15](docs/modernization/15-completable-future.md) | `CompletableFuture` |
| [16](docs/modernization/16-collection-factories.md) | Collection factories and immutability |
| [17](docs/modernization/17-stream-and-optional-additions.md) | Stream and `Optional` additions after 8 |
| [18](docs/modernization/18-files-strings-and-charsets.md) | Files, strings and charsets |
| [19](docs/modernization/19-randomness-and-numerics.md) | Randomness and numerics |
| [20](docs/modernization/20-free-diagnostics.md) | The diagnostics you get for free |
| [21](docs/modernization/21-unnamed-and-primitive-patterns.md) | Unnamed variables, and primitive patterns |
| [22](docs/modernization/22-stream-gatherers.md) | Stream gatherers |
| [23](docs/modernization/23-foreign-function-and-memory.md) | Foreign function and memory |
| [24](docs/modernization/24-class-file-api.md) | The Class-File API |
| [25](docs/modernization/25-tooling.md) | Tooling: jshell, source launch, jwebserver, markdown javadoc |
| [26](docs/modernization/26-scoped-values-and-structured-concurrency.md) | Scoped values, structured concurrency |
| [27](docs/modernization/27-module-imports-and-compact-sources.md) | Module imports, compact source files |
| [28](docs/modernization/28-flexible-constructor-bodies.md) | Flexible constructor bodies |
| [29](docs/modernization/29-http3-and-the-httpclient.md) | HTTP/3 and the HttpClient |
| [30](docs/modernization/30-upgrade-hazards.md) | Upgrade hazards: deprecations and removals |
| [31](docs/modernization/31-runtime-defaults.md) | Runtime defaults: GC, AOT, JFR |
| [32](docs/modernization/32-crypto.md) | Crypto: key derivation, post-quantum, PEM |
| [33](docs/modernization/33-preview-shelf.md) | The preview shelf |
| [34](docs/modernization/34-measuring-it-properly.md) | Measuring it properly |

**One concept per commit.** The diff is the deliverable.

**No test, no rewrite.** Twenty of the thirty-two classes that predate the track have never had a
test, and fifteen of the documents above tell you to change one of them. The first commit on those
files is a test you write, asserting what the code does today — bugs included.
[`docs/testing-first.md`](docs/testing-first.md) is the procedure and the coverage map.

## Reviewing the language, Java 8 to 27

The table above is ordered by **concept**, smallest change first, which is the right order for
modernising this repo. **[`docs/modernization/INDEX.md`](docs/modernization/INDEX.md) is the second ordering**: every
developer-facing feature from Java 8 to Java 27, in the release it landed, pointing at the
document that teaches it — and saying plainly which ones are not taught and why.

Start there if the goal is reviewing the language rather than fixing this code. Documents 12
onwards are written in that release order; the file numbers are append-only and carry no reading
order of their own. **Coverage runs to Java 26**; Java 27 is four final JEPs, three of them JVM
defaults, and the index says where each one continues a story already here.

Setting up a machine to build this, including a second JDK for the post-21 material, is
[`docs/env-setup.md`](docs/env-setup.md). **Maven is not assumed to be installed.**

## A second track: data structures and algorithms

[`docs/dsa/`](docs/dsa/) is a separate track with its own index, arguing that **almost none of the
algorithms are load-bearing and almost all of the contracts are.** It does not rewrite old code;
it writes the test that was never there, and most of the time that test fails. Twelve stages, all
written.

The sharpest example is already in this repo: `algorithm/BinarySearch.java` cannot find five of the
twelve elements in its own test fixture, and its eight tests are green.

## Running it

    ./scripts/check-env.sh              # JDK present? cache warm?
    mvn verify                  # everything
    mvn -q -Dtest=BinarySearchTest test   # one test

**The build is green, and the outstanding work is the disabled tests:**

    grep -rn "@Disabled" src/test

Every one of them carries a reason naming the document that owns it. Starting an exercise means
deleting that one annotation, which turns a silent gap into a failing test. Commit the deletion
together with the fix, so the diff shows the debt being taken on and paid in the same change.

**No bare `@Disabled`.** A skip without a reason is how `FairWorkloadTest` stayed switched off from
2014 to 2026.

**Targets Java 26.** It was 21 — the floor virtual threads, sequenced collections and record
patterns need — and the release-ordered track raised it: 22 for the FFM API and unnamed patterns,
24 for stream gatherers and the class-file API, 25 for scoped values, module imports and flexible
constructor bodies, 26 for HTTP/3 in the HttpClient. Docs 00–11 and stages 1–9 still only
need 21.

The GitHub Actions workflow is **deactivated**: it runs only on manual dispatch. The header of
`.github/workflows/build.yml` says how to switch it back on.

Preview features are off by default and should stay that way — a class compiled with the preview
flag refuses to load on any other JDK version. Two stages need them:

    mvn -Ppreview verify

## Working offline

Run this **once, while online**:

    ./scripts/warm-cache.sh

It fills `~/.m2` so that `mvn -o verify` works with the network off. `dependency:go-offline` alone
misses plugin dependencies, which is why the script also runs a real build. No exercise here reaches
the internet: the `HttpClient` one starts a server inside the test JVM.

## Licence

MIT, except for eight files derived from the Oracle Java Tutorials which remain under Oracle's
BSD-3 licence. [`NOTICE`](NOTICE) summarises it; [`licenses/oracle-bsd-3.txt`](licenses/oracle-bsd-3.txt)
holds the licence text and the list of files. Those eight carry a two-line pointer to it rather
than an inline copy, which is what keeps the redistribution condition satisfied.
