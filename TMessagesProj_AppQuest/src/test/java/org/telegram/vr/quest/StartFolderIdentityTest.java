package org.telegram.vr.quest;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/**
 * The startup folder must be remembered by the id that survives a restart, and matched by the
 * one that does not — and the two are one word apart.
 *
 * <p>{@code MessagesController.DialogFilter} carries both:
 *
 * <pre>
 *   public int id;                                 // the server's filter id — persistent
 *   public int localId = dialogFilterPointer++;    // a process counter (MessagesController:1295)
 * </pre>
 *
 * <p>{@code FilterTabsView} calls {@code localId} its <em>stable</em> id, which is true within a
 * session and false across one. So the selection needs both, in different places: the setting
 * stores {@code id}, and the tab is then found by that row's {@code localId}. Swap them and the
 * client opens a different folder on every launch — quietly, plausibly, and the folder list gets
 * the blame.
 *
 * <p>Nothing in a build or a unit test of this module can see that, because both fields are ints
 * and both compile. What can see it is the call, so the call is what this asserts. Same shape as
 * {@code BrandSeamCoverageTest} and for the same reason.
 */
public class StartFolderIdentityTest {

    private static final String BEGIN = "VrEntryPoints.startupFilterPending()";
    private static final String END = "if (updateCurrentTab) {";

    @Test
    public void theRememberedIdIsThePersistentOne() throws IOException {
        final String block = startupBlock();

        assertTrue("the startup-folder block no longer compares the PERSISTENT DialogFilter.id. "
                        + "localId is a process counter and remembering it opens a different "
                        + "folder on every launch.\n---\n" + block,
                block.contains(".id == wanted"));

        assertFalse("the startup-folder block compares localId, which is dialogFilterPointer++ "
                        + "and means nothing after a restart.\n---\n" + block,
                block.contains("localId == ") || block.contains("== localId"));
    }

    @Test
    public void theTabIsStillFoundByTheSessionId() throws IOException {
        final String block = startupBlock();
        assertTrue("the tab is no longer selected by localId. FilterTabsView keys its tabs on "
                        + "that value, so this is the one place it is the right one.\n---\n" + block,
                block.contains("selectTabWithStableId(filters.get(a).localId)"));
    }

    @Test
    public void theSelectionIsAOneShot() throws IOException {
        final String block = startupBlock();
        assertTrue("the one-shot is no longer disarmed, so every chat-list rebuild would snap the "
                        + "user back to the startup folder they deliberately left.\n---\n" + block,
                block.contains("markStartupFilterApplied()"));
        assertTrue("the block no longer waits for real folders to load. Before they do, `filters` "
                        + "holds only the default tab and the one-shot is spent on nothing.\n---\n" + block,
                block.contains("filters.size() > 1"));
    }

    private static String startupBlock() throws IOException {
        final File file = new File(root(), "TMessagesProj/src/main/java/org/telegram/ui/DialogsActivity.java");
        final String source = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
        final int begin = source.indexOf(BEGIN);
        if (begin < 0) {
            throw new IOException("DialogsActivity no longer calls " + BEGIN
                    + " — the startup folder is not wired at all, so the setting does nothing.");
        }
        final int end = source.indexOf(END, begin);
        if (end < 0) {
            throw new IOException("could not find the end of the startup-folder block");
        }
        return source.substring(begin, end);
    }

    private static File root() {
        File dir = new File(System.getProperty("user.dir"));
        for (int i = 0; i < 6 && dir != null; i++, dir = dir.getParentFile()) {
            if (new File(dir, "TMessagesProj_AppQuest/build.gradle").isFile()) {
                return dir;
            }
        }
        throw new IllegalStateException("could not find the project root above "
                + System.getProperty("user.dir"));
    }
}
