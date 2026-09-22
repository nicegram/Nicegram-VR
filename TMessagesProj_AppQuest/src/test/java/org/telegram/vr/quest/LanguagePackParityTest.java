package org.telegram.vr.quest;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The English resources and the Russian upload artifact must stay the same set of keys, and
 * must agree about their format placeholders.
 *
 * <p>This is not a translation review — nothing here can tell good Russian from bad. It catches
 * the two failures that are silent and expensive: a key added on one side only, which reaches a
 * user as an English string in a Russian interface; and a placeholder that drifts, which reaches
 * them as a crash. {@code String.format("Записи уходят в %2$s", one)} throws
 * {@code MissingFormatArgumentException}, and it throws at the moment the screen draws, on a
 * device, in one language.
 *
 * <p>Why the Russian is not a {@code values-ru/} folder: the package strips every Android locale
 * ({@code localeFilters += ["zz"]}, {@code build.gradle:124}) because Telegram serves its own
 * language packs at runtime. A folder there is compiled, merged and dropped — it was tried and
 * it shipped nothing. The test asserts the folder has not come back, because a resource that
 * looks like it works is worse than an artifact that admits it must be uploaded.
 */
public class LanguagePackParityTest {

    private static final Pattern STRING =
            Pattern.compile("<string name=\"([^\"]+)\"([^>]*)>(.*?)</string>", Pattern.DOTALL);
    private static final Pattern PLURALS =
            Pattern.compile("<plurals name=\"([^\"]+)\">(.*?)</plurals>", Pattern.DOTALL);
    /** `%s`, `%d`, `%1$s`, `%2$d` — and not `%%`. */
    private static final Pattern PLACEHOLDER = Pattern.compile("%(?:(\\d+)\\$)?([sdf])");

    @Test
    public void everyKeyExistsOnBothSides() throws IOException {
        final Map<String, String> en = parse(read("TMessagesProj_AppQuest/src/main/res/values/strings_vr.xml"));
        final Map<String, String> ru = parse(read("language-pack/strings_vr.ru.xml"));

        final Set<String> missingRu = new TreeSet<>(en.keySet());
        missingRu.removeAll(ru.keySet());
        final Set<String> orphanRu = new TreeSet<>(ru.keySet());
        orphanRu.removeAll(en.keySet());

        if (!missingRu.isEmpty() || !orphanRu.isEmpty()) {
            final StringBuilder message = new StringBuilder("language-pack/strings_vr.ru.xml is out of step with the resources.\n");
            if (!missingRu.isEmpty()) {
                message.append("  no Russian for: ").append(String.join(", ", missingRu)).append('\n');
            }
            if (!orphanRu.isEmpty()) {
                message.append("  Russian for a key that no longer exists: ")
                        .append(String.join(", ", orphanRu)).append('\n');
            }
            message.append("  A key present on one side only reaches a Russian user as English.");
            fail(message.toString());
        }
        assertTrue("parsed no keys at all — the file moved or its shape changed", en.size() > 50);
    }

    @Test
    public void placeholdersAgree() throws IOException {
        final Map<String, String> en = parse(read("TMessagesProj_AppQuest/src/main/res/values/strings_vr.xml"));
        final Map<String, String> ru = parse(read("language-pack/strings_vr.ru.xml"));

        final List<String> mismatches = new ArrayList<>();
        for (Map.Entry<String, String> entry : en.entrySet()) {
            final String russian = ru.get(entry.getKey());
            if (russian == null) {
                continue; // reported by the other test; not reported twice
            }
            final Set<String> a = placeholders(entry.getValue());
            final Set<String> b = placeholders(russian);
            if (!a.equals(b)) {
                mismatches.add(entry.getKey() + "  en=" + a + "  ru=" + b);
            }
        }
        if (!mismatches.isEmpty()) {
            fail("These two sides disagree about their format arguments, which is a crash on the "
                    + "device at the moment the screen draws:\n  " + String.join("\n  ", mismatches));
        }
    }

    @Test
    public void theRussianResourceFolderHasNotComeBack() {
        final File values = new File(root(), "TMessagesProj_AppQuest/src/main/res/values-ru");
        assertFalse("values-ru/ is back in the headset module. It is compiled, merged, and then "
                + "dropped from the APK by localeFilters += [\"zz\"] — it ships nothing and it "
                + "looks like it works. The Russian belongs in language-pack/, uploaded to the "
                + "cloud language pack.", values.isDirectory());
    }

    private static Set<String> placeholders(String body) {
        final Set<String> out = new LinkedHashSet<>();
        final Matcher m = PLACEHOLDER.matcher(body);
        while (m.find()) {
            out.add(m.group(1) == null ? "%" + m.group(2) : "%" + m.group(1) + "$" + m.group(2));
        }
        return out;
    }

    /** Keys to their body. `translatable="false"` is skipped: it is not for the pack. */
    private static Map<String, String> parse(String xml) {
        final Map<String, String> out = new LinkedHashMap<>();
        final Matcher s = STRING.matcher(xml);
        while (s.find()) {
            if (s.group(2).contains("translatable=\"false\"")) {
                continue;
            }
            out.put(s.group(1), s.group(3));
        }
        final Matcher p = PLURALS.matcher(xml);
        while (p.find()) {
            out.put("plurals:" + p.group(1), p.group(2));
        }
        return out;
    }

    private static String read(String relative) throws IOException {
        final File file = new File(root(), relative);
        if (!file.isFile()) {
            throw new IOException("not found: " + file);
        }
        return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
    }

    /** Walks up from the working directory: Gradle runs from the module, an IDE may not. */
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
