import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.stream.IntStream;

/**
 * Extracts clean RGBA transparent turntable PNGs from raw 640x360 studio frames:
 * - Removes exterior black backdrop, turntable floor ring, and stray spotlight glare
 * - Preserves black tires, wheel arches, rear diffuser, tinted glass, and roof antenna
 * - Smoothly feathers outer dark shadow pixels (distFromBg <= 6) to eliminate jagged black halos
 */
public class TurntableAlphaProcessor {
    static final int W = 640, H = 360;

    public static void main(String[] args) {
        if (args.length < 3) {
            System.err.println("Usage: TurntableAlphaProcessor <rawDir> <docsOutDir> <assetsOutDir> [count]");
            System.exit(1);
        }
        File rawDir = new File(args[0]);
        File docsDir = new File(args[1]);
        File assetsDir = new File(args[2]);
        int count = args.length >= 4 ? Integer.parseInt(args[3]) : 180;

        docsDir.mkdirs();
        assetsDir.mkdirs();

        IntStream.range(0, count).parallel().forEach(k -> {
            try {
                File inFile = new File(rawDir, String.format("raw_%03d.png", k));
                BufferedImage raw = ImageIO.read(inFile);
                BufferedImage cut = removeStudioBg(raw);
                String outName = String.format("turntable_%03d.png", k);
                ImageIO.write(cut, "png", new File(docsDir, outName));
                ImageIO.write(cut, "png", new File(assetsDir, outName));
            } catch (Exception e) {
                throw new RuntimeException("Failed frame " + k, e);
            }
        });
    }

    static BufferedImage removeStudioBg(BufferedImage src) {
        int[] pixels = new int[W * H];
        src.getRGB(0, 0, W, H, pixels, 0, W);

        int[] maxRgb = new int[W * H];
        boolean[] brightCore = new boolean[W * H];
        for (int i = 0; i < W * H; i++) {
            int p = pixels[i];
            int r = (p >> 16) & 0xFF;
            int gr = (p >> 8) & 0xFF;
            int b = p & 0xFF;
            int m = Math.max(r, Math.max(gr, b));
            maxRgb[i] = m;
            brightCore[i] = (m >= 38) || (r - gr >= 12 && r >= 26);
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
            if (d >= 24) continue;
            int x = cur % W, y = cur / W;
            if (x > 0 && distToCore[cur - 1] > d + 1) { distToCore[cur - 1] = d + 1; q.add(cur - 1); }
            if (x < W - 1 && distToCore[cur + 1] > d + 1) { distToCore[cur + 1] = d + 1; q.add(cur + 1); }
            if (y > 0 && distToCore[cur - W] > d + 1) { distToCore[cur - W] = d + 1; q.add(cur - W); }
            if (y < H - 1 && distToCore[cur + W] > d + 1) { distToCore[cur + W] = d + 1; q.add(cur + W); }
        }

        // Flood-fill background from outer edges with lower-band wheel/diffuser protection
        boolean[] isBg = new boolean[W * H];
        q.clear();
        for (int x = 0; x < W; x++) {
            seedBg(x, 0, isBg, maxRgb, distToCore, q);
            seedBg(x, H - 1, isBg, maxRgb, distToCore, q);
        }
        for (int y = 0; y < H; y++) {
            seedBg(0, y, isBg, maxRgb, distToCore, q);
            seedBg(W - 1, y, isBg, maxRgb, distToCore, q);
        }
        while (!q.isEmpty()) {
            int cur = q.poll();
            int x = cur % W, y = cur / W;
            if (x > 0 && !isBg[cur - 1] && isBgCandidate(x - 1, y, cur - 1, maxRgb, distToCore)) { isBg[cur - 1] = true; q.add(cur - 1); }
            if (x < W - 1 && !isBg[cur + 1] && isBgCandidate(x + 1, y, cur + 1, maxRgb, distToCore)) { isBg[cur + 1] = true; q.add(cur + 1); }
            if (y > 0 && !isBg[cur - W] && isBgCandidate(x, y - 1, cur - W, maxRgb, distToCore)) { isBg[cur - W] = true; q.add(cur - W); }
            if (y < H - 1 && !isBg[cur + W] && isBgCandidate(x, y + 1, cur + W, maxRgb, distToCore)) { isBg[cur + W] = true; q.add(cur + W); }
        }

        // Keep ONLY largest connected component (wipes stray wall glare / floor spots)
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
        boolean[] closed = morphCloseLower(fg, 16, 165);
        for (int i = 0; i < W * H; i++) {
            if (closed[i]) fg[i] = true;
        }
        boolean[] hSealed = fg.clone();
        for (int y = 200; y <= 285; y++) {
            for (int x = 30; x < W - 30; x++) {
                int i = y * W + x;
                if (fg[i]) continue;
                boolean hasL = false, hasR = false;
                for (int dx = 1; dx <= 65; dx++) { if (x - dx >= 0 && fg[i - dx]) { hasL = true; break; } }
                if (hasL) {
                    for (int dx = 1; dx <= 65; dx++) { if (x + dx < W && fg[i + dx]) { hasR = true; break; } }
                }
                if (hasL && hasR) hSealed[i] = true;
            }
        }
        fg = hSealed;

        // Distance from transparent background up to 8px for crisp anti-aliased silhouette feathering
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
            if (d >= 8) continue;
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
                if (d <= 7 && m < 26) {
                    // Feather outer black shadow shell smoothly into transparency (zero jagged dark outline)
                    int edgeRamp = Math.max(0, Math.min(255, (m - 5) * 14));
                    int innerBoost = (d >= 5) ? ((d - 4) * 55) : 0;
                    alpha = Math.min(255, Math.max(edgeRamp, innerBoost > 0 ? Math.max(edgeRamp, Math.min(255, m * 8 + innerBoost)) : edgeRamp));
                }
                out[i] = (alpha == 0) ? 0x00000000 : ((alpha << 24) | (p & 0x00FFFFFF));
            }
        }
        BufferedImage dst = new BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB);
        dst.setRGB(0, 0, W, H, out, 0, W);
        return dst;
    }

    static boolean[] morphCloseLower(boolean[] fg, int rad, int minY) {
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

    static void seedBg(int x, int y, boolean[] isBg, int[] maxRgb, int[] distToCore, ArrayDeque<Integer> q) {
        int i = y * W + x;
        if (!isBg[i] && isBgCandidate(x, y, i, maxRgb, distToCore)) {
            isBg[i] = true;
            q.add(i);
        }
    }

    static boolean isBgCandidate(int x, int y, int i, int[] maxRgb, int[] distToCore) {
        int d = distToCore[i];
        int m = maxRgb[i];
        if (y >= 195 && d <= 8) return false;
        if (d >= 16) return m <= 65;
        if (d >= 9)  return m <= 24;
        if (d >= 3)  return m <= 10;
        return m <= 7;
    }
}
