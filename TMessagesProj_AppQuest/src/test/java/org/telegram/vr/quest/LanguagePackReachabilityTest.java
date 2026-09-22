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

    /** A string read that bypasses the pack. The receiver may be any expression ending in a name. */
    private static final Pattern CONTEXT_READ = Pattern.compile(
            "\\b(?:[A-Za-z_][A-Za-z0-9_]*+(?:\\(\\))?\\.)?getResources\\(\\)\\.getString\\("
            + "|\\b(?:context|mContext|activity|getContext\\(\\)|getParentActivity\\(\\)|this)"
            + "\\.getString\\(");

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
            if (EXEMPT.equals(file.getName())) {
                continue;
            }
            final String[] lines =
                    new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8).split("\n", -1);
            for (int i = 0; i < lines.length; i++) {
                final Matcher m = CONTEXT_READ.matcher(lines[i]);
                if (m.find()) {
                    offenders.add(file.getName() + ":" + (i + 1) + "  " + lines[i].trim());
                }
            }
        }
        if (!offenders.isEmpty()) {
            fail("These read a string through a Context, so the cloud language pack can never "
                    + "reach them and they stay English in every language:\n  "
                    + String.join("\n  ", offenders)
                    + "\nUse LocaleController.getString(resId) — it needs no Context.");
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
            if (body.contains("LocaleController.getString(")) {
                sawALocaleControllerRead = true;
                break;
            }
        }
        assertTrue("no file reads a string through LocaleController at all, which means this "
                + "test is now checking a module that no longer draws strings", sawALocaleControllerRead);
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
