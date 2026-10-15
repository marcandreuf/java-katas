package org.mandfer.practices.io;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.util.List;

/**
 * Reading a text file without a hand-rolled finally block, and the two string methods that
 * look identical and are not.
 *
 * The rewrite target is collections/RandomFileLines.java, which has four separate problems
 * stacked up -- a finally that throws NPE if the open failed, a catch that logs and rethrows,
 * an off-by-one that never picks the last line, and a capacity guess ArrayList does not need.
 * docs/09-streams-and-optional.md owns the off-by-one; this document owns the file handling
 * and the charset. See docs/18-files-strings-and-charsets.md.
 *
 * TODO 1: readAll with Files.readString (Java 11). One line, no finally, no reader variable.
 *         Then look at RandomFileLines:48-60 and count what you deleted.
 * TODO 2: readAllWith -- the same, with the charset named explicitly. Files.readString defaults
 *         to UTF-8 and ALWAYS has; new FileReader(f) used the platform default until Java 18
 *         changed it (JEP 400). The test writes bytes in one charset and reads them in
 *         another so you can see what that difference actually produced.
 * TODO 3: nonBlankLines with String.lines() (Java 11) and isBlank(). Note lines() handles all
 *         three line terminators and split("\n") does not.
 * TODO 4: stripped and trimmed. trim() and strip() differ on exactly one class of input and
 *         the test uses it. Work out which one you have been using by accident.
 * TODO 5: banner with String.repeat (Java 11).
 */
public final class TextFiles {

    private TextFiles() {
    }

    /** The whole file as a string, decoded as UTF-8. */
    public static String readAll(Path path) throws IOException {
        throw new UnsupportedOperationException("TODO 1");
    }

    /** The whole file as a string, decoded as the given charset. */
    public static String readAllWith(Path path, Charset charset) throws IOException {
        throw new UnsupportedOperationException("TODO 2");
    }

    /** Every line that has something on it, in order. */
    public static List<String> nonBlankLines(String text) {
        throw new UnsupportedOperationException("TODO 3");
    }

    /** Leading and trailing whitespace removed, by the Unicode definition of whitespace. */
    public static String stripped(String text) {
        throw new UnsupportedOperationException("TODO 4");
    }

    /** Leading and trailing whitespace removed, by the 1995 definition. */
    public static String trimmed(String text) {
        throw new UnsupportedOperationException("TODO 4");
    }

    /** The title, then a rule of '-' the same width as the title. */
    public static String banner(String title) {
        throw new UnsupportedOperationException("TODO 5");
    }
}
