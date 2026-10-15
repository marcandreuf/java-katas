# 11. JVM production diagnostics

**No TODOs and no tests.** The deliverable is the written answers below. Everything works offline
against a process on your own machine.

## The workload

`concurrency/forkjoin/ForkBlur.java` is the target: a fork/join blur over a `BufferedImage`. It
allocates hard, splits across the common pool, and is CPU-bound, which makes it a better profiling
subject than anything synthetic. Give it a large enough image that it runs for a minute.

`concurrency/MyDeadlock.java` is the second subject, for thread dumps specifically.

## Run it

    mvn -q clean package
    java -cp target/classes org.mandfer.practices.concurrency.forkjoin.ForkBlur

## The command list

| Question | Command |
| --- | --- |
| What is this process doing right now | `jcmd <pid> Thread.print` |
| What is on the heap | `jcmd <pid> GC.heap_info` then `jcmd <pid> GC.class_histogram` |
| Record a flight | `jcmd <pid> JFR.start name=lab settings=profile duration=60s filename=lab.jfr` |
| Check on it | `jcmd <pid> JFR.check` |
| What flags is it running with | `jcmd <pid> VM.flags` |
| What does it think its limits are | `jcmd <pid> VM.info`, look for the container section |
| Heap dump for later | `jcmd <pid> GC.heap_dump /tmp/lab.hprof` |

Open `lab.jfr` in JDK Mission Control if you have it. If not, `jfr summary lab.jfr` and
`jfr print --events ObjectAllocationSample lab.jfr` ship with the JDK and work from a terminal.

## Write the answers here

1. **Which allocation site dominates in `ForkBlur`?** _(fill in)_ It should be findable from the
   JFR allocation events without reading the source.
2. **How many threads does the common pool create here, and what decided that number?** _(fill in)_
3. **Run `MyDeadlock` until it hangs. What does the thread dump call that state, and where in the
   output is the cycle?** _(fill in)_
4. **Container-aware limits**: run it under `docker run --memory=256m` and write down what
   `Runtime.maxMemory()` reports and why. _(fill in)_
5. **The explain-it version**: answer _"how would you debug a slow service in production"_ in four
   sentences, naming the tool you reach for first and what it would have to show you before you
   reached for the second. _(fill in)_
