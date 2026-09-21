package org.telegram.vr.quest;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 * The rename seam must cover every path that resolves a string, and this test exists because it
 * did not.
 *
 * <p>{@code VrBrand.rename} was installed in {@code LocaleController.getStringInternal},
 * described in its own comment as "the one point every drawn string passes through". It was not:
 * {@code formatString} and {@code formatSpannable} repeat the cloud-pack-then-resource lookup
 * <b>inline</b> and never call it, so seven renames — including the version line on the settings
 * screen and the banner on an active call — were written, compiled and never shown (A-26).
 *
 * <p>No compiler can report a rename that simply never runs, and no unit test of this module can
 * either: the defect lives in an upstream file, in a method this module does not call. So the
 * check is on the SOURCE. Every method that asks the language pack for a value must also pass
 * that value through the seam before returning it. When an upstream merge adds an eighth lookup
 * path, this fails and names it.
 */
public class BrandSeamCoverageTest {

    /** The call that means "a string is being resolved here". */
    private static final String LOOKUP = "localizationExternal.getByResNameOrResId";

    /** The call that means "and it passes through the brand". */
    private static final String SEAM = "VrBrand.rename";

    @Test
    public void everyResolutionPathPassesThroughTheBrandSeam() throws IOException {
        final String source = new String(
                Files.readAllBytes(localeController().toPath()), StandardCharsets.UTF_8);

        final List<String> uncovered = new ArrayList<>();
        for (String method : methodBodies(source)) {
            if (method.contains(LOOKUP) && !method.contains(SEAM)) {
                uncovered.add(firstLine(method));
            }
        }

        if (!uncovered.isEmpty()) {
            fail("These methods in LocaleController resolve a string and never pass it through "
                    + SEAM + ", so every rename they touch is silently inert:\n  "
                    + String.join("\n  ", uncovered));
        }
    }

    @Test
    public void theSeamIsActuallyPresent() throws IOException {
        final String source = new String(
                Files.readAllBytes(localeController().toPath()), StandardCharsets.UTF_8);
        assertTrue("LocaleController no longer mentions " + SEAM + " at all — the rename seam has "
                + "been lost, probably to an upstream merge.", source.contains(SEAM));
        assertTrue("LocaleController no longer resolves strings the way this test understands; "
                + "the marker '" + LOOKUP + "' is gone and this check is now blind.",
                source.contains(LOOKUP));
    }

    /**
     * Splits the file on the brace depth that methods live at. Crude on purpose: a real parser
     * here would be a second thing to maintain, and the only question asked of each chunk is
     * whether two literal strings both appear in it.
     */
    private static List<String> methodBodies(String source) {
        final List<String> bodies = new ArrayList<>();
        final StringBuilder current = new StringBuilder();
        int depth = 0;
        for (int i = 0; i < source.length(); i++) {
            final char c = source.charAt(i);
            current.append(c);
            if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth <= 1) {
                    bodies.add(current.toString());
                    current.setLength(0);
                }
            }
        }
        bodies.add(current.toString());
        return bodies;
    }

    private static String firstLine(String body) {
        for (String line : body.split("\n")) {
            final String trimmed = line.trim();
            if (trimmed.contains("(") && !trimmed.startsWith("//") && !trimmed.startsWith("*")) {
                return trimmed;
            }
        }
        return body.substring(0, Math.min(80, body.length())).trim();
    }

    /**
     * Walks up from the working directory rather than hardcoding a depth: Gradle runs tests from
     * the module directory and an IDE may not.
     */
    private static File localeController() {
        final String relative =
                "TMessagesProj/src/main/java/org/telegram/messenger/LocaleController.java";
        File dir = new File(System.getProperty("user.dir"));
        for (int i = 0; i < 6 && dir != null; i++, dir = dir.getParentFile()) {
            final File candidate = new File(dir, relative);
            if (candidate.isFile()) {
                return candidate;
            }
        }
        throw new IllegalStateException("could not find " + relative + " above "
                + System.getProperty("user.dir"));
    }
}
