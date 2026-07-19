package com.kaedee.napthe.http;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.kaedee.napthe.ConfigManager;
import com.kaedee.napthe.NapThe;
import com.kaedee.napthe.utils.MD5Utils;
import org.bukkit.Bukkit;

import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

public class TsrManager {

    private final NapThe plugin;
    private final ConfigManager configManager;

    public TsrManager(NapThe plugin, ConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;
    }

    public void sendCard(UUID playerUUID, String telco, String code, String serial, int amount, String requestId) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                String partnerId = configManager.getConfig().getString("thesieure.partner_id");
                String partnerKey = configManager.getConfig().getString("thesieure.partner_key");
                String apiUrl = configManager.getConfig().getString("thesieure.url", "https://thesieure.com/chargingws/v2");

                String sign = MD5Utils.getMD5(partnerKey + code + serial);

                URL url = new URL(apiUrl);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setDoOutput(true);
                conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");

                String postData = "telco=" + URLEncoder.encode(telco, "UTF-8") +
                        "&code=" + URLEncoder.encode(code, "UTF-8") +
                        "&serial=" + URLEncoder.encode(serial, "UTF-8") +
                        "&amount=" + amount +
                        "&request_id=" + URLEncoder.encode(requestId, "UTF-8") +
                        "&partner_id=" + URLEncoder.encode(partnerId, "UTF-8") +
                        "&sign=" + URLEncoder.encode(sign, "UTF-8") +
                        "&command=charging";

                try (OutputStream os = conn.getOutputStream()) {
                    byte[] input = postData.getBytes(StandardCharsets.UTF_8);
                    os.write(input, 0, input.length);
                }

                int responseCode = conn.getResponseCode();
                if (responseCode == 200) {
                    try (InputStreamReader reader = new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8)) {
                        JsonObject response = JsonParser.parseReader(reader).getAsJsonObject();
                        int status = response.has("status") ? response.get("status").getAsInt() : 0;
                        String message = response.has("message") ? response.get("message").getAsString() : "";
                        
                        // Status 99 means pending, 1 means success, etc.
                        plugin.getSQLiteManager().addTransaction(requestId, playerUUID, "CARD", amount, 0, "PENDING");
                        
                        plugin.debug("Sent card to TSR: " + response.toString());
                    }
                } else {
                    plugin.debug("TSR returned HTTP " + responseCode);
                }

            } catch (Exception e) {
                plugin.debug(e);
            }
        });
    }
}
