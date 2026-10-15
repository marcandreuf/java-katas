# docs

One folder per learning track, plus the setup notes that are shared between them.

| | |
| --- | --- |
| [`modernization/`](modernization/) | **The modernization track.** Takes the 2014–2021 code in this repo from the idiom it was written in to the current one, and reviews the language from Java 8 to 26. Two orderings over the same 35 documents: [`00-start-here.md`](modernization/00-start-here.md) begins the concept order, [`INDEX.md`](modernization/INDEX.md) is the release order. |
| [`dsa/`](dsa/) | **The data structures and algorithms track.** Twelve stages, all written. [`INDEX.md`](dsa/INDEX.md) is the spine, [`research.md`](dsa/research.md) the findings it rests on — including a review against the OCP objectives and *Effective Java*. The thesis: almost none of the algorithms are load-bearing, almost all of the contracts are. |
| [`env-setup.md`](env-setup.md) | Shared. What a machine needs to build this repo, and the commands to get there. Not part of either track. |

## Why the source tree is not organised this way

`docs/` splits by track because that is where the tracks differ. **`src/` does not, for three
reasons:**

1. **Java directory structure is decided by package name**, not by topic. Moving a file into a
   track folder means renaming its package and rewriting every import that reaches it.
2. **Maven's layout is `src/main/java` and `src/test/java`.** Changing it means overriding
   `sourceDirectory` in the pom and fighting every tool that assumes the default.
3. **The tracks deliberately overlap on the same files.** `algorithm/FairWorkLoad.java` is
   modernization [doc 09](modernization/09-streams-and-optional.md) *and* a DSA stage.
   `MyArrayList` and `CircularList` are [doc 10](modernization/10-sequenced-collections-and-httpclient.md)
   *and* DSA. `algorithm/BinarySearch.java` is DSA, and its test is part of the modernization
   track's green baseline. A source tree split by track would have to duplicate those files, and
   then the two copies would drift.

The mapping from track to files lives in the documents instead, which is already how both tracks
work: **every document names the files it lands on.** New exercise code goes in a package named
for its topic — `functional`, `time`, `crypto`, `ffm` — not for the track that happens to use it.
