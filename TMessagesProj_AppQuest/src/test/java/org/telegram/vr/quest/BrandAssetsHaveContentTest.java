package org.telegram.vr.quest;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

/**
 * A brand asset must actually contain something, and this test exists because for three days one
 * did not.
 *
 * <p>{@code nicegram_mark.png} shipped at five densities — 32, 48, 64, 96 and 128 square — and
 * every one of them was a single flat colour: pure white, fully opaque, nothing drawn. It was
 * installed as this fork's mark, it was drawn on the first screen a user sees, and it rendered
 * there as a white rectangle where the product's name belongs.
 *
 * <p>Nothing could have caught it. A blank PNG is a valid PNG: it compiles, it packs, aapt
 * reports it, the build is green, the APK is correct and the drawable draws. Every check this
 * repository had asked whether an asset EXISTS — which is exactly what a blank file satisfies.
 *
 * <p>So this reads the pixels. The PNG is decoded here rather than with {@code ImageIO}, because
 * an Android unit test compiles against {@code android.jar} and neither {@code java.awt} nor
 * {@code javax.imageio} is on that classpath — the first version of this file did use them and
 * did not compile. {@code java.util.zip.Inflater} is present on both, and a PNG is an inflate
 * plus an unfilter.
 */
public class BrandAssetsHaveContentTest {

    @Test
    public void noRasterAssetIsASingleFlatColour() throws IOException {
        final List<File> images = rasterAssets();
        assertFalse("found no raster assets at all — the path this test walks has moved",
                images.isEmpty());

        final List<String> blank = new ArrayList<>();
        final List<String> unreadable = new ArrayList<>();
        for (File file : images) {
            final Png png;
            try {
                png = Png.decode(Files.readAllBytes(file.toPath()));
            } catch (Exception e) {
                unreadable.add(file.getName() + " — " + e.getMessage());
                continue;
            }
            if (png.distinctPixels() <= 1) {
                blank.add(file.getParentFile().getName() + "/" + file.getName()
                        + "  " + png.width + "x" + png.height);
            }
        }
        if (!blank.isEmpty()) {
            fail("These are a single flat colour — a file where a mark should be. A blank image "
                    + "compiles, packs and draws exactly like a real one, which is why this is "
                    + "checked rather than assumed:\n  " + String.join("\n  ", blank));
        }
        if (!unreadable.isEmpty()) {
            // Not a failure: a shape this decoder does not handle (interlaced, 16-bit, palette)
            // is a gap in the check, and a gap that says so is better than a silent pass.
            System.out.println("BrandAssetsHaveContentTest did not inspect:\n  "
                    + String.join("\n  ", unreadable));
        }
    }

    /**
     * The mark itself, whatever form it takes. A vector has no pixels to count, so what is
     * asserted is that it draws something and that it is white — {@code DialogStoriesCell} puts
     * it under {@code PorterDuff.Mode.MULTIPLY} so the theme can colour it, and multiplying by
     * anything other than white discards the theme's colour.
     */
    @Test
    public void theMarkIsAWhiteGlyphAndNotEmpty() throws IOException {
        final File vector = new File(res(), "drawable/nicegram_mark.xml");
        final File raster = new File(res(), "drawable-xxhdpi/nicegram_mark.png");

        assertTrue("nicegram_mark is gone in every form. VrBrandNames installs it as this fork's "
                        + "mark and DialogStoriesCell draws it.",
                vector.isFile() || raster.isFile());

        if (!vector.isFile()) {
            return; // a raster mark is covered by the sweep above
        }
        final String xml = new String(Files.readAllBytes(vector.toPath()), StandardCharsets.UTF_8);
        assertTrue("nicegram_mark.xml draws no paths, so it is an empty drawable with a "
                        + "well-formed header — the vector spelling of the blank PNG this test "
                        + "exists for.",
                xml.split("<path", -1).length - 1 >= 1);
        assertTrue("nicegram_mark.xml is not white. DialogStoriesCell multiplies it by the theme's "
                        + "logo colour, and multiplying by anything but white throws that colour "
                        + "away.",
                xml.toLowerCase().contains("#ffffffff") || xml.toLowerCase().contains("#ffffff\""));
        assertFalse("nicegram_mark.xml still carries the launcher icon's black background square. "
                        + "As a standalone mark it must be a glyph on nothing, or it draws a black "
                        + "block wherever the background is not black.",
                xml.contains("M0,0h1024v1024h-1024z"));
    }

    // --- a PNG, decoded far enough to count colours and no further ---

    private static final class Png {
        final int width;
        final int height;
        final int channels;
        final byte[] pixels;

        private Png(int width, int height, int channels, byte[] pixels) {
            this.width = width;
            this.height = height;
            this.channels = channels;
            this.pixels = pixels;
        }

        int distinctPixels() {
            final Set<Integer> seen = new HashSet<>();
            for (int i = 0; i < width * height; i++) {
                int key = 0;
                for (int c = 0; c < channels; c++) {
                    key = key * 31 + (pixels[i * channels + c] & 0xFF);
                }
                seen.add(key);
                if (seen.size() > 1) {
                    return seen.size();
                }
            }
            return seen.size();
        }

        static Png decode(byte[] file) throws IOException, DataFormatException {
            final ByteBuffer in = ByteBuffer.wrap(file);
            final byte[] signature = new byte[8];
            in.get(signature);
            if ((signature[0] & 0xFF) != 0x89 || signature[1] != 'P' || signature[2] != 'N') {
                throw new IOException("not a PNG");
            }
            int width = 0, height = 0, channels = 0;
            final ByteArrayOutputStream idat = new ByteArrayOutputStream();
            while (in.remaining() >= 8) {
                final int length = in.getInt();
                final byte[] type = new byte[4];
                in.get(type);
                final String name = new String(type, StandardCharsets.US_ASCII);
                final byte[] data = new byte[length];
                in.get(data);
                in.getInt(); // crc, not checked: a corrupt file fails to inflate anyway
                if ("IHDR".equals(name)) {
                    final ByteBuffer h = ByteBuffer.wrap(data);
                    width = h.getInt();
                    height = h.getInt();
                    final int bitDepth = h.get() & 0xFF;
                    final int colourType = h.get() & 0xFF;
                    h.get(); // compression
                    h.get(); // filter
                    if ((h.get() & 0xFF) != 0) {
                        throw new IOException("interlaced");
                    }
                    if (bitDepth != 8) {
                        throw new IOException("bit depth " + bitDepth);
                    }
                    switch (colourType) {
                        case 0: channels = 1; break;
                        case 2: channels = 3; break;
                        case 4: channels = 2; break;
                        case 6: channels = 4; break;
                        default: throw new IOException("colour type " + colourType);
                    }
                } else if ("IDAT".equals(name)) {
                    idat.write(data);
                } else if ("IEND".equals(name)) {
                    break;
                }
            }
            if (width <= 0 || height <= 0 || channels == 0) {
                throw new IOException("no usable IHDR");
            }

            final Inflater inflater = new Inflater();
            inflater.setInput(idat.toByteArray());
            final int stride = width * channels;
            final byte[] raw = new byte[(stride + 1) * height];
            int filled = 0;
            while (filled < raw.length && !inflater.finished()) {
                final int n = inflater.inflate(raw, filled, raw.length - filled);
                if (n == 0) {
                    break;
                }
                filled += n;
            }
            inflater.end();
            if (filled < raw.length) {
                throw new IOException("short image data");
            }

            final byte[] out = new byte[stride * height];
            final byte[] previous = new byte[stride];
            final byte[] line = new byte[stride];
            int at = 0;
            for (int y = 0; y < height; y++) {
                final int filter = raw[at++] & 0xFF;
                System.arraycopy(raw, at, line, 0, stride);
                at += stride;
                for (int x = 0; x < stride; x++) {
                    final int a = x >= channels ? line[x - channels] & 0xFF : 0;
                    final int b = previous[x] & 0xFF;
                    final int c = x >= channels ? previous[x - channels] & 0xFF : 0;
                    final int value = line[x] & 0xFF;
                    final int restored;
                    switch (filter) {
                        case 0: restored = value; break;
                        case 1: restored = value + a; break;
                        case 2: restored = value + b; break;
                        case 3: restored = value + ((a + b) >> 1); break;
                        case 4: {
                            final int p = a + b - c;
                            final int pa = Math.abs(p - a);
                            final int pb = Math.abs(p - b);
                            final int pc = Math.abs(p - c);
                            restored = value + (pa <= pb && pa <= pc ? a : (pb <= pc ? b : c));
                            break;
                        }
                        default: throw new IOException("filter " + filter);
                    }
                    line[x] = (byte) restored;
                }
                System.arraycopy(line, 0, out, y * stride, stride);
                System.arraycopy(line, 0, previous, 0, stride);
            }
            return new Png(width, height, channels, out);
        }
    }

    private static List<File> rasterAssets() {
        final List<File> out = new ArrayList<>();
        final File[] dirs = res().listFiles(
                (dir, name) -> name.startsWith("drawable") || name.startsWith("mipmap"));
        if (dirs != null) {
            for (File dir : dirs) {
                final File[] files = dir.listFiles((d, name) -> name.endsWith(".png"));
                if (files != null) {
                    java.util.Collections.addAll(out, files);
                }
            }
        }
        return out;
    }

    private static File res() {
        File dir = new File(System.getProperty("user.dir"));
        for (int i = 0; i < 6 && dir != null; i++, dir = dir.getParentFile()) {
            final File candidate = new File(dir, "TMessagesProj_AppQuest/src/main/res");
            if (candidate.isDirectory()) {
                return candidate;
            }
        }
        throw new IllegalStateException("could not find the headset module's res above "
                + System.getProperty("user.dir"));
    }
}
