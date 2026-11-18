import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.awt.image.ConvolveOp;
import java.awt.image.Kernel;
import java.io.File;

public class SliceAndEnhanceSprites {

    public static void main(String[] args) throws Exception {
        String projectRoot = "/Users/otnansirk/Bangsa-Digital/RioSense";
        File inputFile = new File(projectRoot + "/docs/mockups/Kia_Rio_sprite_sheet_grid_b49ba01a.jpg");
        File docsOutDir = new File(projectRoot + "/docs/car_sprites");
        File veoOutDir = new File(projectRoot + "/docs/veo_frames");
        File assetsOutDir = new File(projectRoot + "/app/src/main/assets/car_sprites");
        docsOutDir.mkdirs();
        veoOutDir.mkdirs();
        assetsOutDir.mkdirs();

        BufferedImage sheet = ImageIO.read(inputFile);
        int imgW = sheet.getWidth();   // 1376
        int imgH = sheet.getHeight();  // 768
        int cellW = imgW / 4;          // 344
        int cellH = imgH / 3;          // 256

        // 1. Export 16:9 (1280x720) Ready-to-Upload Veo Frame Files in docs/veo_frames/
        BufferedImage closedRight16x9 = extractAndEnhanceCell(sheet, 0, 1, cellW, cellH, false, 1280, 720);
        BufferedImage closedFront16x9 = extractAndEnhanceCell(sheet, 1, 1, cellW, cellH, false, 1280, 720);
        BufferedImage closedLeft16x9  = extractAndEnhanceCell(sheet, 0, 1, cellW, cellH, true,  1280, 720);

        BufferedImage openFr16x9      = extractAndEnhanceCell(sheet, 2, 0, cellW, cellH, false, 1280, 720);
        BufferedImage openRr16x9      = extractAndEnhanceCell(sheet, 2, 2, cellW, cellH, true,  1280, 720);
        BufferedImage openFrRr16x9    = combineFrontAndRearOpen(openRr16x9, openFr16x9, false);

        BufferedImage openFl16x9      = extractAndEnhanceCell(sheet, 2, 0, cellW, cellH, true,  1280, 720);
        BufferedImage openRl16x9      = extractAndEnhanceCell(sheet, 2, 2, cellW, cellH, false, 1280, 720);
        BufferedImage openFlRl16x9    = combineFrontAndRearOpen(openRl16x9, openFl16x9, true);

        ImageIO.write(closedRight16x9, "png", new File(veoOutDir, "01_kanan_belakang_tertutup_16x9.png"));
        ImageIO.write(closedFront16x9, "png", new File(veoOutDir, "02_depan_kiri_tertutup_16x9.png"));
        ImageIO.write(closedLeft16x9,  "png", new File(veoOutDir, "03_kiri_belakang_tertutup_16x9.png"));

        ImageIO.write(openFr16x9,      "png", new File(veoOutDir, "04_pintu_FR_depan_kanan_terbuka_16x9.png"));
        ImageIO.write(openRr16x9,      "png", new File(veoOutDir, "05_pintu_RR_belakang_kanan_terbuka_16x9.png"));
        ImageIO.write(openFrRr16x9,    "png", new File(veoOutDir, "06_pintu_KANAN_SEMUA_terbuka_16x9.png"));

        ImageIO.write(openFl16x9,      "png", new File(veoOutDir, "07_pintu_FL_depan_kiri_terbuka_16x9.png"));
        ImageIO.write(openRl16x9,      "png", new File(veoOutDir, "08_pintu_RL_belakang_kiri_terbuka_16x9.png"));
        ImageIO.write(openFlRl16x9,    "png", new File(veoOutDir, "09_pintu_KIRI_SEMUA_terbuka_16x9.png"));

        System.out.println("✅ All 9 Veo 16:9 reference frames saved in docs/veo_frames/!");
    }

    private static BufferedImage combineFrontAndRearOpen(
        BufferedImage rearOpenImg,
        BufferedImage frontOpenImg,
        boolean isLeftSide
    ) {
        int w = rearOpenImg.getWidth();
        int h = rearOpenImg.getHeight();
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        double splitX = isLeftSide ? (w * 0.40) : (w * 0.60);
        double feather = 32.0;

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                double t = smoothstep(splitX - feather, splitX + feather, x);
                double useFront = isLeftSide ? (1.0 - t) : t;
                int rgbRear = rearOpenImg.getRGB(x, y);
                int rgbFront = frontOpenImg.getRGB(x, y);
                out.setRGB(x, y, lerpRgb(rgbRear, rgbFront, useFront));
            }
        }
        return out;
    }

    private static int lerpRgb(int c1, int c2, double t) {
        double a = Math.max(0.0, Math.min(1.0, t));
        int r1 = (c1 >> 16) & 0xFF, g1 = (c1 >> 8) & 0xFF, b1 = c1 & 0xFF;
        int r2 = (c2 >> 16) & 0xFF, g2 = (c2 >> 8) & 0xFF, b2 = c2 & 0xFF;
        int r = clamp((int) Math.round(r1 + (r2 - r1) * a));
        int g = clamp((int) Math.round(g1 + (g2 - g1) * a));
        int b = clamp((int) Math.round(b1 + (b2 - b1) * a));
        return (0xFF << 24) | (r << 16) | (g << 8) | b;
    }

    private static double smoothstep(double e0, double e1, double x) {
        double t = Math.max(0.0, Math.min(1.0, (x - e0) / (e1 - e0)));
        return t * t * (3.0 - 2.0 * t);
    }

    private static BufferedImage extractAndEnhanceCell(
        BufferedImage sheet,
        int row,
        int col,
        int cellW,
        int cellH,
        boolean flipHorizontal,
        int outWidth,
        int outHeight
    ) {
        int insetX = 6;
        int insetY = 12;
        int w = cellW - insetX * 2;
        int h = cellH - insetY * 2;

        BufferedImage sub = sheet.getSubimage(col * cellW + insetX, row * cellH + insetY, w, h);
        BufferedImage cleaned = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);

        int minX = w, maxX = 0, minY = h, maxY = 0;

        for (int y = 0; y < h; y++) {
            int edgeDistY = Math.min(y, h - 1 - y);
            double fadeY = Math.min(1.0, edgeDistY / 16.0);

            for (int x = 0; x < w; x++) {
                int edgeDistX = Math.min(x, w - 1 - x);
                double fadeX = Math.min(1.0, edgeDistX / 16.0);
                double edgeFactor = fadeX * fadeY;

                int rgb = sub.getRGB(x, y);
                int r = (rgb >> 16) & 0xFF;
                int g = (rgb >> 8) & 0xFF;
                int b = rgb & 0xFF;

                if (r + g + b > 95) {
                    if (x < minX) minX = x;
                    if (x > maxX) maxX = x;
                    if (y < minY) minY = y;
                    if (y > maxY) maxY = y;
                }

                double luma = (r + g + b) / 3.0;
                double redness = Math.max(0, r - (g + b) * 0.5);
                double crush;
                if (luma < 13.0 && redness < 8.0) {
                    crush = 0.0;
                } else if (luma < 26.0 && redness < 12.0) {
                    crush = (luma - 13.0) / 13.0;
                } else {
                    crush = 1.0;
                }

                double wgt = crush * edgeFactor;
                int nr = clamp((int) Math.round(5.0 + (r - 5.0) * wgt));
                int ng = clamp((int) Math.round(5.0 + (g - 5.0) * wgt));
                int nb = clamp((int) Math.round(7.0 + (b - 7.0) * wgt));
                cleaned.setRGB(x, y, (0xFF << 24) | (nr << 16) | (ng << 8) | nb);
            }
        }

        double scale = 2.45;
        double carCenterX = (minX + maxX) * 0.5 * scale;
        double carCenterY = (minY + maxY) * 0.5 * scale;

        BufferedImage canvas = new BufferedImage(outWidth, outHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2 = canvas.createGraphics();
        g2.setColor(new Color(5, 5, 7));
        g2.fillRect(0, 0, outWidth, outHeight);

        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        AffineTransform at = new AffineTransform();
        if (flipHorizontal) {
            at.translate(outWidth, 0);
            at.scale(-1.0, 1.0);
        }
        double drawX = outWidth * 0.5 - carCenterX;
        double drawY = outHeight * 0.50 - carCenterY;
        at.translate(drawX, drawY);
        at.scale(scale, scale);

        g2.drawImage(cleaned, at, null);
        g2.dispose();

        float[] sharpenMatrix = {
             0.0f, -0.18f,  0.0f,
            -0.18f, 1.72f, -0.18f,
             0.0f, -0.18f,  0.0f
        };
        Kernel kernel = new Kernel(3, 3, sharpenMatrix);
        ConvolveOp op = new ConvolveOp(kernel, ConvolveOp.EDGE_NO_OP, null);
        BufferedImage sharpened = new BufferedImage(outWidth, outHeight, BufferedImage.TYPE_INT_RGB);
        op.filter(canvas, sharpened);

        return sharpened;
    }

    private static int clamp(int v) {
        return Math.max(0, Math.min(255, v));
    }
}
