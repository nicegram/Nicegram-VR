package org.telegram.vr.quest;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Every edit this fork makes to SHARED upstream code must say, in a comment beside it, where the
 * decision behind it is written down.
 *
 * <h3>Why this is worth a test</h3>
 *
 * These lines live inside files upstream rewrites. When Telegram moves
 * {@code checkDisplaySize} or reshapes {@code appendMessage}, whoever resolves that conflict
 * sees one unexplained line in a thousand-line file and has every reason to drop it. Three of
 * this project's worst findings were exactly that shape — a registry that was right and a caller
 * that never asked it (A-26, A-31, A-36) — and the cheapest defence is that the call site names
 * the document.
 *
 * <p>So: each shared file that reaches into {@code org.telegram.vr} carries
 * {@code docs/vr-layer.md#seams} within a few lines of doing so. The anchor is checked to exist
 * too, because a comment pointing at a heading nobody wrote is worse than no comment.
 *
 * <p>This does not check that the document is <i>right</i> — {@code Tools/check_docs.py} does
 * what can be done there. It checks that the pointer is present, which is the part a merge
 * silently removes.
 */
public class SeamAnchorsTest {

    private static final String ANCHOR = "docs/vr-layer.md#seams";

    /** The shared files that reach into the seam, and the registry each one reads. */
    private static final Map<String, String> SEAM_CALLERS = new LinkedHashMap<>();

    static {
        SEAM_CALLERS.put("TMessagesProj/src/main/java/org/telegram/messenger/AndroidUtilities.java",
                "VrDisplay");
        SEAM_CALLERS.put("TMessagesProj/src/main/java/org/telegram/messenger/NotificationsController.java",
                "VrPolicy");
        SEAM_CALLERS.put("TMessagesProj/src/main/java/org/telegram/messenger/voip/VoIPService.java",
                "VrPolicy");
        SEAM_CALLERS.put("TMessagesProj/src/main/java/org/telegram/messenger/LocaleController.java",
                "VrBrand");
        SEAM_CALLERS.put("TMessagesProj/src/main/java/org/telegram/ui/LaunchActivity.java",
                "VrEntryPoints");
        SEAM_CALLERS.put("TMessagesProj/src/main/java/org/telegram/ui/NotificationsSettingsActivity.java",
                "VrEntryPoints");
        SEAM_CALLERS.put("TMessagesProj/src/main/java/org/telegram/ui/DialogsActivity.java",
                "VrEntryPoints");
        SEAM_CALLERS.put("TMessagesProj/src/main/java/org/telegram/ui/Components/ChatActivityEnterView.java",
                "VrEntryPoints");
    }

    @Test
    public void everySharedCallerNamesTheDocument() throws IOException {
        final List<String> silent = new ArrayList<>();
        for (Map.Entry<String, String> entry : SEAM_CALLERS.entrySet()) {
            final String body = read(entry.getKey());
            if (!body.contains("org.telegram.vr." + entry.getValue())) {
                silent.add(entry.getKey() + " no longer calls " + entry.getValue()
                        + " — either the seam moved or this list is stale");
            } else if (!body.contains(ANCHOR)) {
                silent.add(entry.getKey() + " calls " + entry.getValue()
                        + " with no comment naming " + ANCHOR);
            }
        }
        if (!silent.isEmpty()) {
            fail("A fork's edit to shared upstream code must say where its reason is written, or "
                    + "the next merge conflict resolves it away:\n  "
                    + String.join("\n  ", silent));
        }
    }

    @Test
    public void theAnchorItPointsAtExists() throws IOException {
        final String doc = read("docs/vr-layer.md");
        assertTrue("docs/vr-layer.md has no <a id=\"seams\"> for the call sites to point at",
                doc.contains("<a id=\"seams\"></a>"));
        // The section is a table of the registries; if it stops naming them it has been gutted.
        for (String registry : new String[]{"VrPolicy", "VrEntryPoints", "VrDisplay", "VrBrand"}) {
            assertTrue("docs/vr-layer.md#seams no longer describes " + registry,
                    doc.contains("`" + registry + "`"));
        }
    }

    /** Walks up from the working directory: Gradle runs from the module, an IDE may not. */
    private static String read(String relative) throws IOException {
        File dir = new File(System.getProperty("user.dir"));
        for (int i = 0; i < 6 && dir != null; i++, dir = dir.getParentFile()) {
            final File candidate = new File(dir, relative);
            if (candidate.isFile()) {
                return new String(Files.readAllBytes(candidate.toPath()), StandardCharsets.UTF_8);
            }
        }
        throw new IllegalStateException("could not find " + relative + " above "
                + System.getProperty("user.dir"));
    }
}
