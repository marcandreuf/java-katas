package org.mandfer.practices.io;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.MalformedInputException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Disabled("Exercise: docs/modernization/18-files-strings-and-charsets.md. Delete this line to begin.")
class TextFilesTest {

    /** U+2000 EN QUAD: whitespace to Unicode, invisible to trim(). */
    private static final String EN_QUAD_PADDED = " abc ";

    @TempDir
    Path dir;

    @Test
    void reads_a_whole_utf8_file() throws IOException {
        Path file = Files.writeString(dir.resolve("ok.txt"), "café\nbar\n");

        assertEquals("café\nbar\n", TextFiles.readAll(file));
    }

    /**
     * The charset lesson. These bytes are valid ISO-8859-1 and NOT valid UTF-8.
     * Files.readString is strict -- it throws rather than substituting, which is the opposite
     * of what new String(bytes, UTF_8) does, and the reason a corrupt file sometimes fails
     * loudly and sometimes produces a replacement character three screens later.
     */
    @Test
    void reading_latin1_bytes_as_utf8_throws_rather_than_corrupting() throws IOException {
        Path file = dir.resolve("latin1.txt");
        Files.write(file, "café".getBytes(StandardCharsets.ISO_8859_1));

        assertThrows(MalformedInputException.class, () -> TextFiles.readAll(file));
        assertEquals("café", TextFiles.readAllWith(file, StandardCharsets.ISO_8859_1));
    }

    @Test
    void non_blank_lines_drops_empty_and_whitespace_only_lines() {
        assertEquals(List.of("a", "b"), TextFiles.nonBlankLines("a\n\nb\n   \n"));
    }

    /** lines() knows about \n, \r\n and \r. split("\n") knows about one of the three. */
    @Test
    void lines_handles_windows_terminators() {
        assertEquals(List.of("a", "b"), TextFiles.nonBlankLines("a\r\nb\r\n"));
    }

    @Test
    void strip_removes_unicode_whitespace() {
        assertEquals("abc", TextFiles.stripped(EN_QUAD_PADDED));
    }

    /** trim() uses the 1995 rule -- codepoints <= U+0020 -- so U+2000 is not whitespace to it. */
    @Test
    void trim_leaves_unicode_whitespace_in_place() {
        assertEquals(EN_QUAD_PADDED, TextFiles.trimmed(EN_QUAD_PADDED));
        assertEquals(5, TextFiles.trimmed(EN_QUAD_PADDED).length());
    }

    @Test
    void strip_and_trim_agree_about_plain_spaces() {
        assertEquals("abc", TextFiles.stripped("  abc  "));
        assertEquals("abc", TextFiles.trimmed("  abc  "));
    }

    @Test
    void banner_underlines_the_title_to_its_own_width() {
        assertEquals("Title\n-----", TextFiles.banner("Title"));
    }
}
