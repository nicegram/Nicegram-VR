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
 * Every screen this module owns must put its content in something that scrolls.
 *
 * <h3>Why this is a rule and not a preference</h3>
 *
 * The panel is small and this build makes it smaller on purpose. A Quest 3 hands the app
 * 500x800 px at 200 dpi, which is 400x640 dp; {@code VrDensity.STEP_SCALE} then multiplies
 * density by up to 1.54 so that the interface is readable at arm's length, and at that step the
 * same panel is <b>260x415 dp</b>. A column of paragraphs and buttons that fits comfortably at
 * one step is off the bottom of the panel at another — and a fixed column has no way to reach
 * what is off the bottom.
 *
 * <p>Found the hard way (A-40): the phone-app offer overflowed on both axes, and the first-run
 * screen — the first screen of the product — put its dismiss buttons past the fold as soon as
 * the text ran to Russian length. Neither is visible in a screenshot taken at the default step.
 *
 * <p>The check is on the SOURCE because the defect is invisible to the compiler and needs a
 * device at a particular interface size to reproduce. A screen that is a list already scrolls;
 * one that builds a column must say so.
 */
public class ScrollableScreensTest {

    /** Anything that scrolls. A list IS a scrolling container; so is an explicit ScrollView. */
    private static final String[] SCROLLS = {
            "ScrollView", "RecyclerListView", "RecyclerView", "ListView", "NestedScroll"
    };

    @Test
    public void everyScreenCanReachItsOwnBottom() throws IOException {
        final List<String> fixed = new ArrayList<>();
        final List<File> screens = screens();
        assertTrue("found no screens to check — the path this test walks has moved",
                screens.size() >= 4);

        for (File screen : screens) {
            final String body =
                    new String(Files.readAllBytes(screen.toPath()), StandardCharsets.UTF_8);
            boolean scrolls = false;
            for (String container : SCROLLS) {
                if (body.contains(container)) {
                    scrolls = true;
                    break;
                }
            }
            if (!scrolls) {
                fixed.add(screen.getName());
            }
        }

        if (!fixed.isEmpty()) {
            fail("These screens lay their content out with no scrolling container, so whatever "
                    + "does not fit the panel cannot be reached — and at the largest interface "
                    + "step the panel is about 260x415 dp:\n  " + String.join("\n  ", fixed)
                    + "\nWrap the column in a ScrollView with setFillViewport(true).");
        }
    }

    /** The fragments of this module: a class that ends in Activity and extends BaseFragment. */
    private static List<File> screens() throws IOException {
        final String relative = "TMessagesProj_AppQuest/src/main/java/org/telegram/vr/quest";
        File dir = new File(System.getProperty("user.dir"));
        for (int i = 0; i < 6 && dir != null; i++, dir = dir.getParentFile()) {
            final File candidate = new File(dir, relative);
            if (candidate.isDirectory()) {
                final List<File> out = new ArrayList<>();
                final File[] children = candidate.listFiles();
                if (children != null) {
                    for (File child : children) {
                        if (!child.getName().endsWith("Activity.java")) {
                            continue;
                        }
                        final String body = new String(
                                Files.readAllBytes(child.toPath()), StandardCharsets.UTF_8);
                        if (body.contains("extends BaseFragment")) {
                            out.add(child);
                        }
                    }
                }
                return out;
            }
        }
        throw new IllegalStateException("could not find " + relative + " above "
                + System.getProperty("user.dir"));
    }
}
