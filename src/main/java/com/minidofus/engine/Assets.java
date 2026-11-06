package com.minidofus.engine;

import javax.imageio.ImageIO;
import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.Transparency;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * Cache d'images chargées depuis le classpath (donc depuis le JAR). Les images sont converties dans
 * le format natif de l'écran pour un affichage accéléré, et leurs variantes (miroir, teintes) sont
 * calculées une seule fois au lieu d'être recalculées à chaque image.
 */
public final class Assets {

    private final Map<String, BufferedImage> images = new HashMap<>();
    private final Map<String, BufferedImage> derived = new HashMap<>();
    private final Set<String> missing = new HashSet<>();

    /** Image {@code /<path>.png}, ou une image de remplacement magenta si elle est introuvable. */
    public BufferedImage image(String path) {
        BufferedImage img = images.get(path);
        if (img == null) {
            img = load(path);
            images.put(path, img);
        }
        return img;
    }

    /** Version retournée horizontalement. */
    public BufferedImage mirrored(String path) {
        return derived.computeIfAbsent(path + "#mirror", k -> {
            BufferedImage src = image(path);
            BufferedImage out = createImage(src.getWidth(), src.getHeight());
            Graphics2D g = out.createGraphics();
            g.drawImage(src, src.getWidth(), 0, -src.getWidth(), src.getHeight(), null);
            g.dispose();
            return out;
        });
    }

    /** Silhouette unie de la couleur donnée (sert aux flashs de dégâts/soin et au bouclier). */
    public BufferedImage tinted(BufferedImage src, String cacheKey, Color color) {
        return derived.computeIfAbsent(cacheKey + "#tint" + Integer.toHexString(color.getRGB()), k -> {
            BufferedImage out = createImage(src.getWidth(), src.getHeight());
            Graphics2D g = out.createGraphics();
            g.drawImage(src, 0, 0, null);
            g.setComposite(AlphaComposite.SrcAtop);
            g.setColor(color);
            g.fillRect(0, 0, src.getWidth(), src.getHeight());
            g.dispose();
            return out;
        });
    }

    public Properties properties(String path) {
        Properties p = new Properties();
        try (InputStream in = Assets.class.getResourceAsStream("/" + path)) {
            if (in != null) p.load(new InputStreamReader(in, StandardCharsets.UTF_8));
        } catch (IOException e) {
            System.err.println("[assets] lecture impossible : " + path + " (" + e.getMessage() + ")");
        }
        return p;
    }

    private BufferedImage load(String path) {
        String resource = "/" + path + ".png";
        if (Assets.class.getResource(resource) == null && Assets.class.getResource("/" + path + ".jpg") != null) {
            resource = "/" + path + ".jpg"; // illustrations de fond, plus légères en JPEG
        }
        try (InputStream in = Assets.class.getResourceAsStream(resource)) {
            if (in != null) {
                BufferedImage raw = withAlphaMask(ImageIO.read(in), path);
                BufferedImage img = createImage(raw.getWidth(), raw.getHeight());
                Graphics2D g = img.createGraphics();
                g.drawImage(raw, 0, 0, null);
                g.dispose();
                return img;
            }
        } catch (IOException e) {
            System.err.println("[assets] erreur sur " + resource + " : " + e.getMessage());
        }
        if (missing.add(path)) System.err.println("[assets] image manquante : " + resource);
        BufferedImage placeholder = createImage(32, 32);
        Graphics2D g = placeholder.createGraphics();
        g.setColor(Color.MAGENTA);
        g.fillRect(0, 0, 32, 32);
        g.dispose();
        return placeholder;
    }

    /**
     * Une illustration JPEG peut avoir sa transparence dans un masque à part ({@code <path>.alpha.png}, en
     * niveaux de gris) : bien plus léger qu'un PNG en couleurs avec transparence.
     */
    private static BufferedImage withAlphaMask(BufferedImage raw, String path) throws IOException {
        if (raw == null) return null;
        try (InputStream in = Assets.class.getResourceAsStream("/" + path + ".alpha.png")) {
            if (in == null) return raw;
            BufferedImage mask = ImageIO.read(in);
            int w = raw.getWidth();
            int h = raw.getHeight();
            BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
            int[] rgb = raw.getRGB(0, 0, w, h, null, 0, w);
            int[] alpha = mask.getRGB(0, 0, w, h, null, 0, w);
            for (int i = 0; i < rgb.length; i++) rgb[i] = (alpha[i] & 0xFF) << 24 | (rgb[i] & 0xFFFFFF);
            out.setRGB(0, 0, w, h, rgb, 0, w);
            return out;
        }
    }

    /** Image translucide au format natif de l'écran (ou ARGB prémultiplié sans écran). */
    public static BufferedImage createImage(int w, int h) {
        w = Math.max(1, w);
        h = Math.max(1, h);
        if (!GraphicsEnvironment.isHeadless()) {
            return GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice()
                    .getDefaultConfiguration().createCompatibleImage(w, h, Transparency.TRANSLUCENT);
        }
        return new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB_PRE);
    }
}
