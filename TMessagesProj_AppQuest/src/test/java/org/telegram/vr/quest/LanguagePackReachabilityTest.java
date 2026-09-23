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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Every string this module draws must be reachable by the cloud language pack, and this test
 * exists because five of them were not.
 *
 * <p>The build strips every Android locale from the package — {@code localeFilters += ["zz"]},
 * {@code TMessagesProj_AppQuest/build.gradle:124} — because Telegram serves its own language
 * packs at runtime. So a {@code values-ru/} folder here is compiled, merged and then dropped,
 * and the ONLY channel by which this module's strings can ever become Russian is the pack,
 * which answers by resource ENTRY NAME through {@link org.telegram.messenger.LocaleController}.
 *
 * <p>A read through a {@code Context} never asks the pack. It returns the compiled English and
 * always will, however the pack is loaded — so a single {@code context.getString(...)} is not a
 * style slip, it is one string permanently untranslatable. Finding A-19 recorded that the
 * reading side was done; it was not, and the first-run screen was among the five that were
 * wrong (A-31).
 *
 * <p>This is a source-level check for the same reason {@code BrandSeamCoverageTest} is: the
 * defect is a call that is perfectly correct code and simply asks the wrong thing, which no
 * compiler reports and no test of the drawn result can reach without a device and a loaded pack.
 */
public class LanguagePackReachabilityTest {

    /**
     * A string read that cannot work for THIS module's resources.
     *
     * <p>Both halves are broken, for opposite reasons. A {@code Context} read never asks the
     * cloud language pack, so the string stays English for ever. And
     * {@code LocaleController.getString(int)} cannot resolve one of this module's ids: it maps
     * the id to a name hash and then asks the packaged localization asset for that hash, and
     * that asset is built from UPSTREAM's strings.xml. Ours were never in it, so every such
     * read produced the literal text {@code LOC_ERR:null} on screen (A-36).
     *
     * <p>{@link org.telegram.vr.quest.VrStrings} is the path that works: the pack by entry
     * NAME, then this module's own compiled string.
     */
    private static final Pattern CONTEXT_READ = Pattern.compile(
            "\\b(?:[A-Za-z_][A-Za-z0-9_]*+(?:\\(\\))?\\.)?getResources\\(\\)\\.getString\\("
            + "|\\b(?:context|mContext|activity|getContext\\(\\)|getParentActivity\\(\\)|this)"
            + "\\.getString\\(");

    /**
     * {@code LocaleController.getString(my.nicegram.vr.R.string.X)} — the read that put
     * {@code LOC_ERR:null} on screen, matched across LINE BREAKS.
     *
     * <p>The line-by-line version of this test passed a repository that still held two of them
     * (A-37), because both were ternaries wrapped over three lines and the opening call and the
     * resource sat on different lines. A guard that only sees one line at a time cannot see a
     * Java expression, which spans as many as the formatter wants.
     *
     * <p>{@code [^;]} bounds the match to a single statement, so a legitimate
     * {@code LocaleController.getString(R.string.Upstream)} earlier in a method cannot reach
     * forward and accuse an unrelated {@code my.nicegram.vr} reference after the semicolon.
     */
    private static final Pattern LOCALE_READ = Pattern.compile(
            "LocaleController\\.(?:getString|formatString)\\([^;]{0,300}?my\\.nicegram\\.vr\\.R\\.string");

    /**
     * The one file allowed to read strings through a Context, and the reason is in its own
     * header: {@code VrBrandNames} fills the map that {@code LocaleController} consults, so
     * asking {@code LocaleController} from inside it would recurse. Those strings are English
     * today by the same design, and P-18 fixes all of them together.
     */
    private static final String EXEMPT = "VrBrandNames.java";

    @Test
    public void noHeadsetStringIsReadThroughAContext() throws IOException {
        final List<String> offenders = new ArrayList<>();
        for (File file : sources()) {
            if (EXEMPT.equals(file.getName()) || "VrStrings.java".equals(file.getName())) {
                continue;  // VrStrings IS the correct path, and its javadoc quotes the wrong one
            }
            final String body =
                    new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
            final String[] lines = body.split("\n", -1);
            for (int i = 0; i < lines.length; i++) {
                final Matcher m = CONTEXT_READ.matcher(lines[i]);
                if (m.find()) {
                    offenders.add(file.getName() + ":" + (i + 1) + "  " + lines[i].trim());
                }
            }
            // The same text with every newline replaced by a space: indices are unchanged, so a
            // match still reports the line it started on, and a call wrapped over three lines is
            // now one string to the matcher.
            final Matcher wrapped = LOCALE_READ.matcher(body.replace('\n', ' '));
            while (wrapped.find()) {
                final int line = countLines(body, wrapped.start());
                offenders.add(file.getName() + ":" + line + "  "
                        + wrapped.group().replaceAll("\\s+", " "));
            }
        }
        if (!offenders.isEmpty()) {
            fail("These read a string through a Context, so the cloud language pack can never "
                    + "reach them, or cannot resolve them at all and put LOC_ERR:null on screen:\n  "
                    + String.join("\n  ", offenders)
                    + "\nUse VrStrings.get(resId) — the pack by name, then this module's own string.");
        }
    }

    @Test
    public void theSweepActuallyLookedAtSomething() throws IOException {
        // A silent pass over an empty file list is indistinguishable from a clean bill of health.
        final List<File> files = sources();
        assertTrue("found no headset sources to check — the path this test walks has moved",
                files.size() >= 15);
        boolean sawALocaleControllerRead = false;
        for (File file : files) {
            final String body = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
            if (body.contains("VrStrings.get(")) {
                sawALocaleControllerRead = true;
                break;
            }
        }
        assertTrue("no file reads a string through VrStrings at all, which means this "
                + "test is now checking a module that no longer draws strings", sawALocaleControllerRead);
    }

    private static int countLines(String body, int offset) {
        int line = 1;
        for (int i = 0; i < offset && i < body.length(); i++) {
            if (body.charAt(i) == '\n') {
                line++;
            }
        }
        return line;
    }

    /** Walks up from the working directory: Gradle runs from the module, an IDE may not. */
    private static List<File> sources() {
        final String relative = "TMessagesProj_AppQuest/src/main/java";
        File dir = new File(System.getProperty("user.dir"));
        for (int i = 0; i < 6 && dir != null; i++, dir = dir.getParentFile()) {
            final File candidate = new File(dir, relative);
            if (candidate.isDirectory()) {
                final List<File> out = new ArrayList<>();
                collect(candidate, out);
                return out;
            }
        }
        throw new IllegalStateException("could not find " + relative + " above "
                + System.getProperty("user.dir"));
    }

    private static void collect(File dir, List<File> out) {
        final File[] children = dir.listFiles();
        if (children == null) {
            return;
        }
        for (File child : children) {
            if (child.isDirectory()) {
                collect(child, out);
            } else if (child.getName().endsWith(".java")) {
                out.add(child);
            }
        }
    }
}
