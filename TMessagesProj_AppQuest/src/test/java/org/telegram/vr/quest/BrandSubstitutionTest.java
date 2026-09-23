package org.telegram.vr.quest;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * What happens to a sentence the language pack has already translated.
 *
 * <p>The rule replaced a map of finished English sentences (A-39). Under that map a Russian
 * interface read "Update Nicegram VR" where it should have read "Обновить Nicegram VR", because
 * the translation the pack had just returned was thrown away along with the service's name.
 *
 * <p>These assertions are the whole rule. The map that decides WHICH ids it applies to is
 * reviewed by reading it; what it DOES to a string is checked here.
 */
public class BrandSubstitutionTest {

    private static final String EN_FALLBACK = "Update Nicegram VR";

    @Test
    public void theEnglishSentenceKeepsItsShape() {
        assertEquals("Update Nicegram VR",
                VrBrandNames.substitute("Update Telegram", EN_FALLBACK));
    }

    @Test
    public void aTranslatedSentenceStaysTranslated() {
        // The whole point: the Russian survives and only the name changes.
        assertEquals("Обновить Nicegram VR",
                VrBrandNames.substitute("Обновить Telegram", EN_FALLBACK));
    }

    @Test
    public void aCyrillicSpellingOfTheNameIsAlsoReplaced() {
        assertEquals("Обновить Nicegram VR",
                VrBrandNames.substitute("Обновить Телеграм", EN_FALLBACK));
    }

    @Test
    public void placeholdersSurvive() {
        assertEquals("%s Nicegram VR Cache",
                VrBrandNames.substitute("%s Telegram Cache", "%s Nicegram VR Cache"));
        assertEquals("Nicegram VR %1$s",
                VrBrandNames.substitute("Telegram %1$s", "Nicegram VR %1$s"));
    }

    @Test
    public void everyOccurrenceIsReplacedNotJustTheFirst() {
        // UpdateAppAlert names the app twice, and half a rename reads like a bug report.
        assertEquals("Sorry, your Nicegram VR app is out of date. Please update Nicegram VR.",
                VrBrandNames.substitute(
                        "Sorry, your Telegram app is out of date. Please update Telegram.",
                        "fallback"));
    }

    @Test
    public void aSentenceWithoutTheNameFallsBackRatherThanLying() {
        // A translation that transliterates the name some third way, or an upstream edit that
        // dropped it. English and true beats translated and wrong about which app is speaking.
        assertEquals(EN_FALLBACK, VrBrandNames.substitute("Обновить приложение", EN_FALLBACK));
    }

    @Test
    public void nothingResolvedFallsBack() {
        assertEquals(EN_FALLBACK, VrBrandNames.substitute(null, EN_FALLBACK));
        assertEquals(EN_FALLBACK, VrBrandNames.substitute("", EN_FALLBACK));
    }

    /**
     * Every id in the substitution list must be an upstream string that actually contains the
     * service's name — otherwise the substitution can never fire for it, the entry falls back
     * to English on every launch in every language, and the map claims a rename it does not do.
     *
     * <p>Measured on 23 September 2026: 36 of 36. This test is here so that an upstream merge
     * that rewords one of them says so, rather than quietly turning it back into a frozen
     * English sentence.
     */
    @Test
    public void everySubstitutionIdIsAStringThatNamesTheService() throws IOException {
        final Map<String, String> upstream = upstreamStrings();
        assertTrue("could not read upstream's strings.xml — the path this test walks has moved",
                upstream.size() > 1000);

        final List<String> unreachable = new ArrayList<>();
        final Matcher m = Pattern.compile("english\\.put\\(R\\.string\\.(\\w+),")
                .matcher(source("TMessagesProj_AppQuest/src/main/java/org/telegram/vr/quest/VrBrandNames.java"));
        int seen = 0;
        while (m.find()) {
            seen++;
            final String value = upstream.get(m.group(1));
            if (value == null) {
                unreachable.add(m.group(1) + " — no such string upstream any more");
            } else if (!value.contains("Telegram")) {
                unreachable.add(m.group(1) + " — upstream no longer names the service: " + value);
            }
        }
        assertTrue("found no substitution entries at all; the map has been restructured",
                seen >= 30);
        if (!unreachable.isEmpty()) {
            fail("These ids are listed for substitution but the substitution can never fire, so "
                    + "each falls back to frozen English in every language:\n  "
                    + String.join("\n  ", unreachable));
        }
    }

    private static Map<String, String> upstreamStrings() throws IOException {
        final Map<String, String> out = new HashMap<>();
        final Matcher m = Pattern.compile("<string name=\"([^\"]+)\"[^>]*>(.*?)</string>",
                Pattern.DOTALL).matcher(source("TMessagesProj/src/main/res/values/strings.xml"));
        while (m.find()) {
            out.put(m.group(1), m.group(2));
        }
        return out;
    }

    /** Walks up from the working directory: Gradle runs from the module, an IDE may not. */
    private static String source(String relative) throws IOException {
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

    /**
     * The version line is a REPLACEMENT, and its replacement must still carry the product's
     * name — which lives in {@code VrBrandNames.PRODUCT} everywhere else, so the one copy of
     * it that sits in a resource file is held against the constant here rather than trusted.
     */
    @Test
    public void theVersionStringStillNamesTheProduct() throws IOException {
        final String strings = source(
                "TMessagesProj_AppQuest/src/main/res/values/strings_vr.xml");
        final Matcher m = Pattern.compile(
                "<string name=\"vr_app_version\">([^<]+)</string>").matcher(strings);
        assertTrue("vr_app_version is gone; VrBrandNames still routes TelegramVersion to it",
                m.find());
        final String value = m.group(1);
        assertTrue("vr_app_version is \"" + value + "\", which does not name "
                        + VrBrandNames.PRODUCT,
                value.contains(VrBrandNames.PRODUCT));
        assertTrue("vr_app_version lost its %1$s and would print no version at all",
                value.contains("%1$s"));
    }
}
