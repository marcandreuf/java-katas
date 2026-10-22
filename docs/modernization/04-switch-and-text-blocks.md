# 04. Switch expressions and text blocks

**Java 14** (switch expressions, preview in 12 and 13) · **Java 15** (text blocks, preview in 13
and 14) · concept stage 04 of [the concept order](00-start-here.md) · no build change

> **No safety net:** `SimpleThreads` has no tests. Write them first, in a separate commit,
> asserting what the code does today — bugs included. [`../testing-first.md`](../testing-first.md) says how, and why the order matters.

## What this stage is actually about

Two features that shipped a release apart and have one thing in common: each replaces a form
where **the mistake you were most likely to make was not a compile error.**

Five words, defined once:

- a **switch statement** *does* something for each case. It is the 1996 form, with
  `case X:` labels, and it **falls through**: after a case runs, control carries on into the next
  case unless you write `break`.
- a **switch expression** *produces a value*: `D r = switch (d) { ... };`. It uses arrow labels
  (`case UP -> DOWN;`), which never fall through, and it must be **exhaustive**: every possible
  input needs a case, or it doesn't compile.
- **`yield`** is how a case with a `{ block }` body hands back its value. `return` would leave the
  whole method, so it isn't allowed inside a switch expression.
- a **text block** is a multi-line string literal between `"""` delimiters.
- **incidental whitespace** is the indentation that is there only because the text block sits
  inside indented code. The compiler strips it, and **the position of the closing `"""` decides
  how much it strips.**

The rule for both features: **the new form moves a mistake from runtime to compile time.** A
forgotten `break` or a missing enum constant becomes a compile error. Indentation that used to be
hand-built with `"\n" +` on every line becomes something the compiler works out. Where it can't
do that, see the tabs trap in part two.

## The worked example

### The switch that already exists

**The 2014 code has no `switch` at all** — not one, in any file. Its branching is
`if`/`else if` over *conditions*: `CircularList.java:37` (`isLastElement(...)`),
`SumSearch.java:24` (`tmpSum < sumTarget`), `BinarySearch.java:23`. A switch can't help with any
of them, because it matches *values*, not conditions (and switching on conditions with a
`when` guard over a primitive is still preview; see [doc 21](21-unnamed-and-primitive-patterns.md)).
**That is the finding**: the old code has no switch to modernise, because nothing in it chooses
between a fixed set of values.

The one switch in the repo is new, `mousemaze/Direction.java:32-39`, written for
[DSA stage 11](../dsa/11-reachability.md):

    public Direction opposite() {
        return switch (this) {
            case UP -> DOWN;
            case RIGHT -> LEFT;
            case DOWN -> UP;
            case LEFT -> RIGHT;
        };
    }

Here is the same method in the 1996 form, with one `break` missing, as anyone could write it
(verified):

    Direction r = null;
    switch (this) {
        case UP:    r = DOWN;              // no break: falls into the next case
        case RIGHT: r = LEFT;  break;
        case DOWN:  r = UP;    break;
        case LEFT:  r = RIGHT; break;
    }
    return r;

    UP.opposite()        // LEFT. It compiles, and it's wrong.

Delete the `LEFT` case instead, and the statement still compiles: `LEFT.opposite()` returns
`null` (verified). Make either mistake in the expression form and it doesn't compile: there is no
fall-through to forget, and a missing case is `the switch expression does not cover all possible
input values`.

Three more rules, all verified:

- **Several labels can share a case**: `case UP, DOWN -> "vertical";`.
- **A case that needs more than one expression is a block, and ends with `yield`**:

      case LEFT, RIGHT -> {
          String s = name().toLowerCase();
          yield "horizontal:" + s;
      }

  Try `return` there instead: `attempt to return out of a switch expression`.
- **You can't mix the two label kinds** in one switch: `case UP -> 1; case DOWN: yield 2;` is
  `different case kinds used in the switch`.

One trap. Arrow labels on a switch *statement* still don't make it exhaustive.
`switch (d) { case UP -> ...; case DOWN -> ...; }` with no value produced compiles, and `LEFT`
does nothing (verified). Exhaustiveness comes with the *expression*, or with patterns
(doc 03), not with the arrows.

### The text block that doesn't exist yet

**Text blocks have no target in the old code either.** `src/main` calls `System.out.println` 48
times (`git grep -c "System.out.println" -- src/main`) and never builds a multi-line string. So
the exercise creates one, in part two. Here is how the margin works, each case verified, with
`·` for a space and `⏎` for a newline:

    String a = """
            usage: SimpleThreads [seconds]
              seconds  how long to wait
            """;
    // usage:·SimpleThreads·[seconds]⏎··seconds··how·long·to·wait⏎

The closing `"""` is lined up with the text, so all of that indentation is incidental and is
stripped. The two extra spaces on the second line are *not* incidental, because they go past the
margin, so they stay. And because the closing delimiter is on its own line, the string ends with
`⏎`.

    String b = """
            usage: SimpleThreads [seconds]
              seconds  how long to wait
        """;
    // ····usage:·SimpleThreads·[seconds]⏎······seconds··how·long·to·wait⏎

Move the closing `"""` four spaces left and **the margin moves with it**: every line keeps four
more spaces. The closing delimiter is the ruler.

    String c = """
            usage: SimpleThreads [seconds]""";
    // usage:·SimpleThreads·[seconds]          (no trailing newline)

And three small rules:

- **Trailing spaces on a line are stripped** (`abc···` comes out as `abc`). To keep one, end the
  line with `\s`.
- **A `\` at the end of a line joins it to the next one**: `one \` + `line` gives `one·line`.
- **The opening `"""` must be followed by a line break.** `"""hello"""` on one line is
  `illegal text block open delimiter sequence, missing line terminator`.

## Part one: switches, on code that has none

There is nothing in the old code to convert, so this part is a pair of experiments on
`Direction.opposite()`. **Do them in a scratch copy, or revert them afterwards.** The method
belongs to DSA stage 11, and it is already in its final form.

1. **Write it the 1996 way and break it.** Rewrite `opposite()` as a switch statement with colon
   labels. Leave out one `break`, then delete one case. It compiles both times. Write down which
   input now gives a wrong answer, and how a test would have to be written to catch it. (It would
   need to call `opposite()` on every constant, which is the test nobody writes until after the
   bug.)
2. **Put the expression back and repeat the same two mistakes.** There is no `break` to forget,
   and the deleted case is a compile error. **That compile error is what you are learning.** The
   switch expression is not shorter syntax. It is the switch statement with its two most common
   bugs taken away.
3. **Add a `default` branch to the expression, and delete a case again.** It compiles: the
   `default` covers it. Doc 02 made the same point about sealed types: **`default` switches
   exhaustiveness off.** On an enum you own, leave it out.

## Part two: a text block, on `SimpleThreads`

`concurrency/SimpleThreads.java:25-35` takes one optional argument: how many seconds to wait
before giving up on its worker thread. Passing anything that isn't an integer prints
`Argument must be an integer.` to `stderr` and exits with status 1 (verified with `abc` and with
`1.5`). It never says *which* argument, or what the number means. That's the gap a usage message
fills.

It is concurrency code, so per [`../testing-first.md`](../testing-first.md) you test the part that
isn't concurrent. The message is that part.

Three commits, in this order:

1. **Extract and characterize.** Move the message into a `static String usage()` (or a constant),
   leave `main` printing it, and write a test asserting it is exactly
   `"Argument must be an integer."`. Green, and nothing has changed yet.
2. **Write the usage message as a text block**, and change the assertion in the same commit,
   because changing the message is the point of the commit. Say so in the commit message. **Write
   the expected value in the test as ordinary `"..." + "\n"` concatenation, not as another text
   block**: two text blocks with the same indentation mistake compare equal. A character-for-
   character comparison against a plain string is the cheapest way to see where the closing
   delimiter put your margin.
3. **Decide whether the message ends with a newline**, and make it say so. `System.err.println`
   adds one, so a text block whose closing `"""` is on its own line prints a blank line after
   itself. Pick one: close on the last line of text, or switch to `print`.

### The tabs trap, which this file will spring on you

`SimpleThreads.java` is indented with **tabs**. Most editors insert **spaces** when you type
inside a string. Text-block stripping counts whitespace *characters*, and one tab counts as one
character, the same as one space. So a text block whose closing `"""` sits after two tabs, with
content lines indented by spaces, gives you a different margin from the one you see (verified, `→`
for a tab):

    →usage:·SimpleThreads·[seconds]⏎
    ··········seconds··how·long·to·wait⏎

Plain `javac` says nothing. The Maven build passes `-Xlint:all` (`pom.xml:80`), which includes
`-Xlint:text-blocks`, so `mvn compile` prints
`warning: [text-blocks] inconsistent white space indentation` (verified with
`javac -Xlint:text-blocks`). **It is a warning, not
an error, so read the build output.** Then make the indentation in that block all tabs or all
spaces.

## `Door.toString()`, from the switch side

[Doc 03](03-pattern-matching.md) already decided this one: a ternary. From this side the reason
is the same: `switch` on a `boolean` needs primitive patterns, which are preview at `--release 26`.
The only way to get a switch expression into `Door` is to replace the `boolean` with an enum,
which [doc 02](02-sealed-types.md) argued is not worth it at two states. **Nothing to change
here.** The point is that neither feature in this doc is a reason on its own to restructure a
class.

## What got worse

- _(fill this in)_
- _(fill this in)_

Prompts: when does `formatted()` on a text block read worse than plain concatenation? What does a
text block do to a `git blame` on the line you changed, and to a diff when someone re-indents the
method around it? A switch expression with block bodies and `yield`: at what size would you rather
have an `if`/`else`? The 1996 fall-through was occasionally intended (two labels sharing code):
the arrow form gives you `case A, B ->` for that, so is there anything the old form could do that
the new one can't?
