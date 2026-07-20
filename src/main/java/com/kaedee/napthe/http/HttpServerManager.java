package com.kaedee.napthe.http;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.kaedee.napthe.ConfigManager;
import com.kaedee.napthe.NapThe;
import com.kaedee.napthe.utils.MD5Utils;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.bukkit.Bukkit;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

public class HttpServerManager {

    private final NapThe plugin;
    private final ConfigManager configManager;
    private HttpServer server;

    public HttpServerManager(NapThe plugin, ConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;
    }

    public void startServer() {
        int port = configManager.getConfig().getInt("web-server.port", 8080);
        try {
            server = HttpServer.create(new InetSocketAddress(port), 0);
            server.createContext("/charge/callback", new TsrCallbackHandler());
            server.createContext("/sepay/webhook", new SepayWebhookHandler());
            server.setExecutor(null);
            server.start();
            plugin.getLogger().info("Started HTTP Server on port " + port);
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to start HTTP Server: " + e.getMessage());
        }
    }

    public void stopServer() {
        if (server != null) {
            server.stop(0);
            plugin.getLogger().info("Stopped HTTP Server.");
        }
    }

    class TsrCallbackHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                InputStream is = exchange.getRequestBody();
                String body = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                
                try {
                    JsonObject json = JsonParser.parseString(body).getAsJsonObject();
                    String partnerKey = configManager.getConfig().getString("thesieure.partner_key");
                    
                    int status = json.has("status") ? json.get("status").getAsInt() : 0;
                    String requestId = json.has("request_id") ? json.get("request_id").getAsString() : "";
                    int declaredValue = json.has("declared_value") ? json.get("declared_value").getAsInt() : 0;
                    int value = json.has("value") ? json.get("value").getAsInt() : 0;
                    String code = json.has("code") ? json.get("code").getAsString() : "";
                    String serial = json.has("serial") ? json.get("serial").getAsString() : "";
                    String callbackSign = json.has("callback_sign") ? json.get("callback_sign").getAsString() : "";
                    
                    String validSign = MD5Utils.getMD5(partnerKey + code + serial);
                    
                    if (!validSign.equals(callbackSign)) {
                        sendResponse(exchange, 400, "Invalid Signature");
                        return;
                    }
                    
                    UUID uuid = plugin.getSQLiteManager().getUuidByTxId(requestId);
                    if (uuid == null) {
                        sendResponse(exchange, 200, "OK but transaction not found");
                        return;
                    }

                    if (status == 1) { // Success
                        double crystalRate = configManager.getConfig().getDouble("rates.card_rate", 0.8);
                        double crystals = (value / 10000.0) * crystalRate;
                        
                        plugin.getSQLiteManager().updateTransactionStatus(requestId, "SUCCESS");
                        plugin.getSQLiteManager().addCrystalAndTotalDonated(uuid, crystals, value);
                        
                        plugin.processRewardsAndNotify(uuid, value, crystals, true);
                    } else if (status == 2) { // Wrong value
                        double crystalRate = configManager.getConfig().getDouble("rates.card_rate", 0.8);
                        double penalty = configManager.getConfig().getDouble("rates.wrong_value_penalty", 0.5);
                        double crystals = ((value / 10000.0) * crystalRate) * penalty;
                        
                        plugin.getSQLiteManager().updateTransactionStatus(requestId, "SUCCESS_PENALTY");
                        plugin.getSQLiteManager().addCrystalAndTotalDonated(uuid, crystals, value);
                        
                        plugin.processRewardsAndNotify(uuid, value, crystals, false);
                    } else { // Failed
                        plugin.getSQLiteManager().updateTransactionStatus(requestId, "FAILED");
                    }
                    
                    sendResponse(exchange, 200, "OK");
                } catch (Exception e) {
                    plugin.debug(e);
                    sendResponse(exchange, 500, "Internal Server Error");
                }
            } else {
                sendResponse(exchange, 405, "Method Not Allowed");
            }
        }
    }
    
    class SepayWebhookHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                InputStream is = exchange.getRequestBody();
                String body = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                
                try {
                    plugin.getLogger().info("[SePay Webhook] Đã nhận được POST request. Body: " + body);
                    // Xác thực Token Webhook từ header (nếu người dùng có cấu hình)
                    String webhookToken = configManager.getConfig().getString("sepay.webhook_token", "");
                    String headerKey = exchange.getRequestHeaders().getFirst("Authorization");
                    
                    if (!webhookToken.isEmpty() && (headerKey == null || !headerKey.equals("Apikey " + webhookToken))) {
                        sendResponse(exchange, 401, "{\"success\":false,\"message\":\"Unauthorized\"}");
                        return;
                    }
                    
                    JsonObject json = JsonParser.parseString(body).getAsJsonObject();
                    
                    String requestId = null;
                    double amountReceived = 0;
                    
                    // Kiểm tra xem đây là webhook của Cổng thanh toán (ORDER_PAID) hay Webhook Ngân hàng (transferType)
                    if (json.has("notification_type") && json.get("notification_type").getAsString().equals("ORDER_PAID")) {
                        // Xử lý theo format SePay Payment Gateway
                        JsonObject order = json.getAsJsonObject("order");
                        requestId = order.has("order_invoice_number") ? order.get("order_invoice_number").getAsString() : "";
                        // Dùng số tiền giao dịch thực tế hoặc số tiền của đơn
                        JsonObject transaction = json.has("transaction") && !json.get("transaction").isJsonNull() 
                                ? json.getAsJsonObject("transaction") : null;
                        if (transaction != null && transaction.has("transaction_amount")) {
                            amountReceived = transaction.get("transaction_amount").getAsDouble();
                        } else {
                            amountReceived = order.has("order_amount") ? order.get("order_amount").getAsDouble() : 0;
                        }
                    } else if (json.has("transferType")) {
                        // Xử lý theo format SePay Bank Webhook (Biến động số dư)
                        String transferType = json.get("transferType").getAsString();
                        if (!"in".equalsIgnoreCase(transferType)) {
                            sendResponse(exchange, 200, "{\"success\":true,\"message\":\"Ignored outgoing transfer\"}");
                            return;
                        }
                        
                        amountReceived = json.has("transferAmount") ? json.get("transferAmount").getAsDouble() : 0;
                        String content = json.has("content") ? json.get("content").getAsString() : "";
                        
                        // Tìm requestId trong nội dung chuyển khoản (format: NAP_PlayerName_Amount)
                        requestId = extractRequestId(content);
                    } else {
                        plugin.debug("SePay webhook: Unknown payload format: " + body);
                        sendResponse(exchange, 200, "{\"success\":true,\"message\":\"Unknown payload format\"}");
                        return;
                    }
                    
                    if (requestId == null || requestId.isEmpty()) {
                        plugin.debug("SePay webhook: Could not find request ID in payload");
                        sendResponse(exchange, 200, "{\"success\":true,\"message\":\"No matching request ID\"}");
                        return;
                    }
                    
                    UUID uuid = plugin.getSQLiteManager().getUuidByTxId(requestId);
                    if (uuid == null) {
                        plugin.debug("SePay webhook: Transaction not found for ID: " + requestId);
                        sendResponse(exchange, 200, "{\"success\":true,\"message\":\"Transaction not found\"}");
                        return;
                    }
                    
                    // Tính Crystal theo tỉ lệ QR
                    double crystalRate = configManager.getConfig().getDouble("rates.qr_rate", 1.0);
                    double crystals = (amountReceived / 10000.0) * crystalRate;
                    
                    plugin.getSQLiteManager().updateTransactionStatus(requestId, "SUCCESS");
                    plugin.getSQLiteManager().addCrystalAndTotalDonated(uuid, crystals, amountReceived);
                    
                    plugin.processRewardsAndNotify(uuid, amountReceived, crystals, true);
                    
                    plugin.debug("SePay payment received: " + amountReceived + " VND -> " + crystals + " Crystal for " + uuid);
                    sendResponse(exchange, 200, "{\"success\":true}");
                } catch (Exception e) {
                    plugin.debug(e);
                    sendResponse(exchange, 500, "Internal Server Error");
                }
            } else {
                sendResponse(exchange, 405, "Method Not Allowed");
            }
        }
        
        /**
         * Trích xuất requestId (format NAP_PlayerName_Amount) từ nội dung chuyển khoản.
         * Nội dung CK có thể chứa các ký tự khác, cần tìm pattern phù hợp.
         */
        private String extractRequestId(String content) {
            if (content == null) return null;
            // Tìm pattern "NAP_" trong nội dung
            content = content.toUpperCase();
            int idx = content.indexOf("NAP_");
            if (idx >= 0) {
                // Lấy từ NAP_ đến hết chuỗi hoặc đến khoảng trắng tiếp theo
                String sub = content.substring(idx);
                int spaceIdx = sub.indexOf(' ');
                if (spaceIdx > 0) {
                    return sub.substring(0, spaceIdx);
                }
                return sub;
            }
            return null;
        }
    }

    private void sendResponse(HttpExchange exchange, int statusCode, String response) throws IOException {
        byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(statusCode, bytes.length);
        OutputStream os = exchange.getResponseBody();
        os.write(bytes);
        os.close();
    }
}
