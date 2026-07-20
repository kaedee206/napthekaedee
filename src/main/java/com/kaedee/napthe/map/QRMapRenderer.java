package com.kaedee.napthe.map;

import org.bukkit.entity.Player;
import org.bukkit.map.MapCanvas;
import org.bukkit.map.MapRenderer;
import org.bukkit.map.MapView;

import javax.imageio.ImageIO;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.net.URL;

public class QRMapRenderer extends MapRenderer {

    private boolean rendered = false;
    private BufferedImage image;

    public QRMapRenderer(BufferedImage img) {
        if (img != null) {
            this.image = resize(img, 128, 128);
        }
    }

    public QRMapRenderer(String urlString) {
        try {
            URL url = new URL(urlString);
            java.net.HttpURLConnection connection = (java.net.HttpURLConnection) url.openConnection();
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)");
            BufferedImage img = ImageIO.read(connection.getInputStream());
            if (img != null) {
                this.image = resize(img, 128, 128);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void render(MapView map, MapCanvas canvas, Player player) {
        if (rendered) return;

        if (image != null) {
            canvas.drawImage(0, 0, image);
        }
        rendered = true;
    }

    private BufferedImage resize(BufferedImage img, int newW, int newH) {
        Image tmp = img.getScaledInstance(newW, newH, Image.SCALE_SMOOTH);
        BufferedImage dimg = new BufferedImage(newW, newH, BufferedImage.TYPE_INT_ARGB);

        java.awt.Graphics2D g2d = dimg.createGraphics();
        g2d.drawImage(tmp, 0, 0, null);
        g2d.dispose();

        return dimg;
    }
}
