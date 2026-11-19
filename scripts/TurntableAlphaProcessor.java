import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.stream.IntStream;

/**
 * Extracts clean RGBA transparent PNGs from raw studio turntable and door-animation frames:
 * - Dynamic resolution support (e.g. 640x360 turntable, 768x432 door sequences)
 * - Removes exterior black backdrop, turntable floor ring, wall glare, and (in doors mode) floor puddle reflections
 * - Preserves black tires, wheel arches, rear diffuser, tinted glass, open cabin interior, and roof antenna
 */
public class TurntableAlphaProcessor {
    public static void main(String[] args) throws Exception {
        if (args.length >= 2 && "--preview-doors".equals(args[0])) {
            renderPreview(args[1]);
            return;
        }
        if (args.length < 3) {
            System.err.println("Usage: TurntableAlphaProcessor <rawDir> <docsOutDir> <assetsOutDir> [count] [--doors <prefix> <hflip>]");
            System.exit(1);
        }
        File rawDir = new File(args[0]);
        File docsDir = new File(args[1]);
        File assetsDir = new File(args[2]);
        int count = args.length >= 4 ? Integer.parseInt(args[3]) : 180;
        boolean doorsMode = args.length >= 5 && "--doors".equals(args[4]);
        String prefix = doorsMode && args.length >= 6 ? args[5] : "turntable";
        boolean hflip = doorsMode && args.length >= 7 && "true".equalsIgnoreCase(args[6]);

        docsDir.mkdirs();
        assetsDir.mkdirs();

        IntStream.range(0, count).parallel().forEach(k -> {
            try {
                File inFile = new File(rawDir, String.format("raw_%03d.png", k));
                BufferedImage raw = ImageIO.read(inFile);
                if (hflip) raw = flipHorizontal(raw);
                BufferedImage cut = removeStudioBg(raw, doorsMode);
                String outName = doorsMode
                        ? String.format("%s_%02d.png", prefix, k)
                        : String.format("turntable_%03d.png", k);
                ImageIO.write(cut, "png", new File(docsDir, outName));
                ImageIO.write(cut, "png", new File(assetsDir, outName));
            } catch (Exception e) {
                throw new RuntimeException("Failed frame " + k, e);
            }
        });
    }

    static void renderPreview(String rawDirPath) throws Exception {
        String[] names = {"fr_19.png", "rr_19.png", "fr_rr_19.png", "trunk_19.png"};
        BufferedImage preview = new BufferedImage(384 * 2, 216 * 4, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = preview.createGraphics();
        for (int idx = 0; idx < names.length; idx++) {
            BufferedImage raw = ImageIO.read(new File(rawDirPath, names[idx]));
            BufferedImage cut = removeStudioBg(raw, true);
            int col = idx % 2;
            int row = idx / 2;
            g.setColor(new Color(0x05, 0x05, 0x07));
            g.fillRect(col * 384, row * 216, 384, 216);
            g.drawImage(cut, col * 384, row * 216, 384, 216, null);

            g.setColor(new Color(0x1A, 0x6B, 0x8A));
            g.fillRect(col * 384, (row + 2) * 216, 384, 216);
            g.drawImage(cut, col * 384, (row + 2) * 216, 384, 216, null);
        }
        g.dispose();
        ImageIO.write(preview, "png", new File("/tmp/doors_preview.png"));
    }

    static BufferedImage flipHorizontal(BufferedImage src) {
        int w = src.getWidth(), h = src.getHeight();
        BufferedImage dst = new BufferedImage(w, h, src.getType() == 0 ? BufferedImage.TYPE_INT_ARGB : src.getType());
        Graphics2D g = dst.createGraphics();
        g.drawImage(src, w, 0, 0, h, 0, 0, w, h, null);
        g.dispose();
        return dst;
    }

    static BufferedImage removeStudioBg(BufferedImage src, boolean doorsMode) {
        final int W = src.getWidth();
        final int H = src.getHeight();
        final double scale = W / 640.0;
        final int coreReach = Math.max(18, (int) Math.round(24 * scale));
        final int lowerBandY = (int) Math.round(H * 0.54);
        final int floorGlareY = (int) Math.round(H * 0.64);

        int[] pixels = new int[W * H];
        src.getRGB(0, 0, W, H, pixels, 0, W);

        int[] maxRgb = new int[W * H];
        boolean[] redFloorGlare = new boolean[W * H];
        boolean[] brightCore = new boolean[W * H];
        for (int i = 0; i < W * H; i++) {
            int p = pixels[i];
            int r = (p >> 16) & 0xFF;
            int gr = (p >> 8) & 0xFF;
            int b = p & 0xFF;
            int m = Math.max(r, Math.max(gr, b));
            maxRgb[i] = m;
            int x = i % W;
            int y = i / W;
            boolean inFloorZone = y >= floorGlareY || ((x < (int) (W * 0.22) || x > (int) (W * 0.78)) && y >= (int) (H * 0.58));
            boolean isSaturatedRedFloor = doorsMode && inFloorZone && (r - Math.max(gr, b) >= 8) && Math.max(gr, b) <= 95;
            redFloorGlare[i] = isSaturatedRedFloor;
            if (isSaturatedRedFloor) {
                brightCore[i] = false;
            } else {
                brightCore[i] = (m >= 38) || (r - gr >= 12 && r >= 26);
            }
        }

        int[] distToCore = new int[W * H];
        Arrays.fill(distToCore, 999);
        ArrayDeque<Integer> q = new ArrayDeque<>(W * H / 4);
        for (int i = 0; i < W * H; i++) {
            if (brightCore[i]) {
                distToCore[i] = 0;
                q.add(i);
            }
        }
        while (!q.isEmpty()) {
            int cur = q.poll();
            int d = distToCore[cur];
            if (d >= coreReach) continue;
            int x = cur % W, y = cur / W;
            if (x > 0 && distToCore[cur - 1] > d + 1) { distToCore[cur - 1] = d + 1; q.add(cur - 1); }
            if (x < W - 1 && distToCore[cur + 1] > d + 1) { distToCore[cur + 1] = d + 1; q.add(cur + 1); }
            if (y > 0 && distToCore[cur - W] > d + 1) { distToCore[cur - W] = d + 1; q.add(cur - W); }
            if (y < H - 1 && distToCore[cur + W] > d + 1) { distToCore[cur + W] = d + 1; q.add(cur + W); }
        }

        boolean[] isBg = new boolean[W * H];
        q.clear();
        for (int x = 0; x < W; x++) {
            seedBg(x, 0, W, lowerBandY, scale, isBg, maxRgb, distToCore, redFloorGlare, q);
            seedBg(x, H - 1, W, lowerBandY, scale, isBg, maxRgb, distToCore, redFloorGlare, q);
        }
        for (int y = 0; y < H; y++) {
            seedBg(0, y, W, lowerBandY, scale, isBg, maxRgb, distToCore, redFloorGlare, q);
            seedBg(W - 1, y, W, lowerBandY, scale, isBg, maxRgb, distToCore, redFloorGlare, q);
        }
        while (!q.isEmpty()) {
            int cur = q.poll();
            int x = cur % W, y = cur / W;
            if (x > 0 && !isBg[cur - 1] && isBgCandidate(x - 1, y, cur - 1, lowerBandY, scale, maxRgb, distToCore, redFloorGlare)) { isBg[cur - 1] = true; q.add(cur - 1); }
            if (x < W - 1 && !isBg[cur + 1] && isBgCandidate(x + 1, y, cur + 1, lowerBandY, scale, maxRgb, distToCore, redFloorGlare)) { isBg[cur + 1] = true; q.add(cur + 1); }
            if (y > 0 && !isBg[cur - W] && isBgCandidate(x, y - 1, cur - W, lowerBandY, scale, maxRgb, distToCore, redFloorGlare)) { isBg[cur - W] = true; q.add(cur - W); }
            if (y < H - 1 && !isBg[cur + W] && isBgCandidate(x, y + 1, cur + W, lowerBandY, scale, maxRgb, distToCore, redFloorGlare)) { isBg[cur + W] = true; q.add(cur + W); }
        }

        // Keep ONLY largest connected component (wipes stray glare blobs & floor spots)
        boolean[] fg = new boolean[W * H];
        for (int i = 0; i < W * H; i++) fg[i] = !isBg[i];

        int[] compId = new int[W * H];
        int cid = 0, bestCid = 0, bestCount = 0;
        int[] queueArr = new int[W * H];
        for (int i = 0; i < W * H; i++) {
            if (fg[i] && compId[i] == 0) {
                cid++;
                int head = 0, tail = 0;
                queueArr[tail++] = i;
                compId[i] = cid;
                while (head < tail) {
                    int cur = queueArr[head++];
                    int x = cur % W, y = cur / W;
                    if (x > 0 && fg[cur - 1] && compId[cur - 1] == 0) { compId[cur - 1] = cid; queueArr[tail++] = cur - 1; }
                    if (x < W - 1 && fg[cur + 1] && compId[cur + 1] == 0) { compId[cur + 1] = cid; queueArr[tail++] = cur + 1; }
                    if (y > 0 && fg[cur - W] && compId[cur - W] == 0) { compId[cur - W] = cid; queueArr[tail++] = cur - W; }
                    if (y < H - 1 && fg[cur + W] && compId[cur + W] == 0) { compId[cur + W] = cid; queueArr[tail++] = cur + W; }
                }
                if (tail > bestCount) { bestCount = tail; bestCid = cid; }
            }
        }
        for (int i = 0; i < W * H; i++) fg[i] = (compId[i] == bestCid);

        // Seal wheel wells & diagonal wheel-to-fender arches, plus horizontal close for black rear diffuser
        int closeRad = Math.max(12, (int) Math.round(16 * scale));
        int closeMinY = (int) Math.round(H * 0.46);
        boolean[] closed = morphCloseLower(fg, W, H, closeRad, closeMinY);
        for (int i = 0; i < W * H; i++) {
            if (closed[i]) fg[i] = true;
        }
        int diffY0 = (int) Math.round(H * 0.55);
        int diffY1 = (int) Math.round(H * 0.79);
        int diffSpan = Math.max(40, (int) Math.round(65 * scale));
        int marginX = Math.max(20, (int) Math.round(30 * scale));
        boolean[] hSealed = fg.clone();
        for (int y = diffY0; y <= diffY1; y++) {
            for (int x = marginX; x < W - marginX; x++) {
                int i = y * W + x;
                if (fg[i] || redFloorGlare[i]) continue;
                boolean hasL = false, hasR = false;
                for (int dx = 1; dx <= diffSpan; dx++) { if (x - dx >= 0 && fg[i - dx]) { hasL = true; break; } }
                if (hasL) {
                    for (int dx = 1; dx <= diffSpan; dx++) { if (x + dx < W && fg[i + dx]) { hasR = true; break; } }
                }
                if (hasL && hasR) hSealed[i] = true;
            }
        }
        fg = hSealed;

        // Feather outer silhouette up to 7px for smooth anti-aliased blending without black rim
        int featherMax = Math.max(6, (int) Math.round(8 * scale));
        int[] distFromBg = new int[W * H];
        Arrays.fill(distFromBg, 99);
        q.clear();
        for (int i = 0; i < W * H; i++) {
            if (!fg[i]) {
                distFromBg[i] = 0;
                q.add(i);
            }
        }
        while (!q.isEmpty()) {
            int cur = q.poll();
            int d = distFromBg[cur];
            if (d >= featherMax) continue;
            int x = cur % W, y = cur / W;
            if (x > 0 && distFromBg[cur - 1] > d + 1) { distFromBg[cur - 1] = d + 1; q.add(cur - 1); }
            if (x < W - 1 && distFromBg[cur + 1] > d + 1) { distFromBg[cur + 1] = d + 1; q.add(cur + 1); }
            if (y > 0 && distFromBg[cur - W] > d + 1) { distFromBg[cur - W] = d + 1; q.add(cur - W); }
            if (y < H - 1 && distFromBg[cur + W] > d + 1) { distFromBg[cur + W] = d + 1; q.add(cur + W); }
        }

        int[] out = new int[W * H];
        for (int i = 0; i < W * H; i++) {
            if (!fg[i]) {
                out[i] = 0x00000000;
            } else {
                int p = pixels[i];
                int d = distFromBg[i];
                int m = maxRgb[i];
                int alpha = 255;
                if (d < featherMax && m < 26) {
                    int edgeRamp = Math.max(0, Math.min(255, (m - 5) * 14));
                    int innerStart = Math.max(4, featherMax - 3);
                    int innerBoost = (d >= innerStart) ? ((d - innerStart + 1) * 55) : 0;
                    alpha = Math.min(255, Math.max(edgeRamp, innerBoost > 0 ? Math.max(edgeRamp, Math.min(255, m * 8 + innerBoost)) : edgeRamp));
                }
                out[i] = (alpha == 0) ? 0x00000000 : ((alpha << 24) | (p & 0x00FFFFFF));
            }
        }
        BufferedImage dst = new BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB);
        dst.setRGB(0, 0, W, H, out, 0, W);
        return dst;
    }

    static boolean[] morphCloseLower(boolean[] fg, int W, int H, int rad, int minY) {
        int[] dFg = new int[W * H];
        Arrays.fill(dFg, 999);
        ArrayDeque<Integer> q = new ArrayDeque<>(W * H / 3);
        for (int i = 0; i < W * H; i++) {
            if (fg[i]) { dFg[i] = 0; q.add(i); }
        }
        while (!q.isEmpty()) {
            int cur = q.poll();
            int d = dFg[cur];
            if (d >= rad) continue;
            int x = cur % W, y = cur / W;
            if (x > 0 && dFg[cur - 1] > d + 1) { dFg[cur - 1] = d + 1; q.add(cur - 1); }
            if (x < W - 1 && dFg[cur + 1] > d + 1) { dFg[cur + 1] = d + 1; q.add(cur + 1); }
            if (y > 0 && dFg[cur - W] > d + 1) { dFg[cur - W] = d + 1; q.add(cur - W); }
            if (y < H - 1 && dFg[cur + W] > d + 1) { dFg[cur + W] = d + 1; q.add(cur + W); }
        }
        boolean[] dilated = new boolean[W * H];
        for (int i = 0; i < W * H; i++) dilated[i] = (dFg[i] <= rad);

        int[] dBg = new int[W * H];
        Arrays.fill(dBg, 999);
        q.clear();
        for (int i = 0; i < W * H; i++) {
            if (!dilated[i]) { dBg[i] = 0; q.add(i); }
        }
        while (!q.isEmpty()) {
            int cur = q.poll();
            int d = dBg[cur];
            if (d >= rad) continue;
            int x = cur % W, y = cur / W;
            if (x > 0 && dBg[cur - 1] > d + 1) { dBg[cur - 1] = d + 1; q.add(cur - 1); }
            if (x < W - 1 && dBg[cur + 1] > d + 1) { dBg[cur + 1] = d + 1; q.add(cur + 1); }
            if (y > 0 && dBg[cur - W] > d + 1) { dBg[cur - W] = d + 1; q.add(cur - W); }
            if (y < H - 1 && dBg[cur + W] > d + 1) { dBg[cur + W] = d + 1; q.add(cur + W); }
        }
        boolean[] out = new boolean[W * H];
        for (int y = minY; y < H; y++) {
            for (int x = 0; x < W; x++) {
                int i = y * W + x;
                out[i] = (dBg[i] >= rad);
            }
        }
        return out;
    }

    static void seedBg(int x, int y, int W, int lowerBandY, double scale,
                       boolean[] isBg, int[] maxRgb, int[] distToCore, boolean[] redFloorGlare, ArrayDeque<Integer> q) {
        int i = y * W + x;
        if (!isBg[i] && isBgCandidate(x, y, i, lowerBandY, scale, maxRgb, distToCore, redFloorGlare)) {
            isBg[i] = true;
            q.add(i);
        }
    }

    static boolean isBgCandidate(int x, int y, int i, int lowerBandY, double scale,
                                 int[] maxRgb, int[] distToCore, boolean[] redFloorGlare) {
        if (redFloorGlare[i]) return true;
        int d = distToCore[i];
        int m = maxRgb[i];
        int wheelProtDist = Math.max(6, (int) Math.round(8 * scale));
        int farDist = Math.max(12, (int) Math.round(16 * scale));
        int midDist = Math.max(7, (int) Math.round(9 * scale));
        int nearDist = Math.max(2, (int) Math.round(3 * scale));
        if (y >= lowerBandY && d <= wheelProtDist) return false;
        if (d >= farDist)  return m <= 65;
        if (d >= midDist)  return m <= 24;
        if (d >= nearDist) return m <= 10;
        return m <= 7;
    }
}
