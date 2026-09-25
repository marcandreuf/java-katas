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

**Start with [`docs/00-start-here.md`](docs/00-start-here.md).** It covers the one thing that has to
happen before anything else: the repo does not currently compile.

| # | Concept |
| --- | --- |
| [00](docs/00-start-here.md) | Start here, and make it build |
| [01](docs/01-records.md) | Records |
| [02](docs/02-sealed-types.md) | Sealed types and exhaustiveness |
| [03](docs/03-pattern-matching.md) | Pattern matching |
| [04](docs/04-switch-and-text-blocks.md) | Switch expressions and text blocks |
| [05](docs/05-var.md) | `var`, and the diamond |
| [06](docs/06-virtual-threads.md) | Virtual threads |
| [07](docs/07-classic-concurrency.md) | Classic concurrency |
| [08](docs/08-memory-model.md) | The memory model |
| [09](docs/09-streams-and-optional.md) | Streams and Optional |
| [10](docs/10-sequenced-collections-and-httpclient.md) | Sequenced collections, HttpClient |
| [11](docs/11-jvm-diagnostics.md) | JVM production diagnostics |

**One concept per commit.** The diff is the deliverable.

## Running it

    ./check-env.sh              # JDK present? cache warm?
    mvn verify                  # everything
    mvn -q -Dtest=BinarySearchTest test   # one test

**The build is green, and the outstanding work is the disabled tests:**

    grep -rn "@Disabled" src/test

Every one of them carries a reason naming the document that owns it. Starting an exercise means
deleting that one annotation, which turns a silent gap into a failing test. Commit the deletion
together with the fix, so the diff shows the debt being taken on and paid in the same change.

**No bare `@Disabled`.** A skip without a reason is how `FairWorkloadTest` stayed switched off from
2014 to 2026.

Targets Java 21, which is the floor that virtual threads, sequenced collections and record patterns
need. CI builds on 21 and 25 so the code stays forward-compatible; raise `<java.release>` in
`pom.xml` once every machine that builds this has a 25 JDK.

## Working offline

Run this **once, while online**:

    ./warm-cache.sh

It fills `~/.m2` so that `mvn -o verify` works with the network off. `dependency:go-offline` alone
misses plugin dependencies, which is why the script also runs a real build. No exercise here reaches
the internet: the `HttpClient` one starts a server inside the test JVM.

## Licence

MIT, except for eight files derived from the Oracle Java Tutorials which remain under Oracle's
BSD-3 licence. See [`NOTICE`](NOTICE) for the list.
