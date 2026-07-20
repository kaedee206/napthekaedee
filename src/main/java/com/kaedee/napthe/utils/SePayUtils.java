package com.kaedee.napthe.utils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.logging.Logger;

/**
 * Tích hợp SePay Payment Gateway (Cổng Thanh Toán SePay)
 *
 * Tài liệu: https://developer.sepay.vn/vi/cong-thanh-toan/API/tong-quan
 *
 * Luồng:
 *  1. Gọi POST https://pgapi.sepay.vn/v1/checkout/init với Basic Auth (merchant_id:secret_key)
 *  2. Body JSON gồm các trường đơn hàng + signature (HMAC-SHA256)
 *  3. API trả về JSON có trường "redirect_url" → URL trang thanh toán SePay
 *  4. Gửi URL này cho người chơi bấm vào
 */
public class SePayUtils {

    private static final String CHECKOUT_INIT_URL = "https://pay.sepay.vn/v1/checkout/init";

    // Thứ tự các field dùng để tạo chữ ký HMAC-SHA256
    // (theo đúng thứ tự tài liệu SePay PHP SDK)
    private static final String[] SIGNATURE_FIELD_ORDER = {
        "merchant", "currency", "order_amount", "operation",
        "order_description", "payment_method", "order_invoice_number",
        "customer_id", "success_url", "error_url", "cancel_url"
    };

    /**
     * Tạo đơn hàng trên SePay và nhận URL trang thanh toán.
     *
     * @param merchantId     Merchant ID từ SePay Dashboard
     * @param secretKey      Secret Key từ SePay Dashboard
     * @param amount         Số tiền (VND)
     * @param invoiceNumber  Mã đơn hàng duy nhất (ví dụ: NAP_Steve_50000)
     * @param description    Nội dung chuyển khoản (ví dụ: "Nap the Steve")
     * @param customerId     Tên người chơi (gửi lên cho SePay lưu)
     * @param successUrl     URL callback khi thanh toán thành công
     * @param errorUrl       URL callback khi thanh toán thất bại
     * @param cancelUrl      URL callback khi hủy thanh toán
     * @return URL trang thanh toán SePay, hoặc null nếu lỗi
     */
    public static String createCheckoutUrl(
            String merchantId,
            String secretKey,
            int amount,
            String invoiceNumber,
            String description,
            String customerId,
            String successUrl,
            String errorUrl,
            String cancelUrl,
            Logger logger) {
        try {
            // 1. Chuẩn bị các trường dữ liệu
            String amountStr = String.valueOf(amount);
            String paymentMethod = "BANK_TRANSFER";
            String currency = "VND";
            String operation = "PURCHASE";

            // 2. Tạo chữ ký HMAC-SHA256
            // Format: "field1=value1,field2=value2,..." theo thứ tự SIGNATURE_FIELD_ORDER
            // Chỉ include trường nào có giá trị không rỗng
            String[] fieldValues = {
                merchantId,      // merchant
                currency,        // currency
                amountStr,       // order_amount
                operation,       // operation
                description,     // order_description
                paymentMethod,   // payment_method
                invoiceNumber,   // order_invoice_number
                customerId,      // customer_id
                successUrl,      // success_url
                errorUrl,        // error_url
                cancelUrl        // cancel_url
            };

            StringBuilder rawSignature = new StringBuilder();
            for (int i = 0; i < SIGNATURE_FIELD_ORDER.length; i++) {
                String val = fieldValues[i];
                if (val != null && !val.isEmpty()) {
                    if (rawSignature.length() > 0) rawSignature.append(",");
                    rawSignature.append(SIGNATURE_FIELD_ORDER[i]).append("=").append(val);
                }
            }

            Mac hmac = Mac.getInstance("HmacSHA256");
            hmac.init(new SecretKeySpec(secretKey.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] hashBytes = hmac.doFinal(rawSignature.toString().getBytes(StandardCharsets.UTF_8));
            String signature = Base64.getEncoder().encodeToString(hashBytes);

            if (logger != null) {
                logger.info("[SePay] Raw signature string: " + rawSignature);
            }

            // 3. Build Form Url Encoded body
            StringBuilder body = new StringBuilder();
            for (int i = 0; i < SIGNATURE_FIELD_ORDER.length; i++) {
                String val = fieldValues[i];
                if (val != null && !val.isEmpty()) {
                    appendForm(body, SIGNATURE_FIELD_ORDER[i], val);
                }
            }
            appendForm(body, "signature", signature);

            URL url = new URL(CHECKOUT_INIT_URL);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            conn.setInstanceFollowRedirects(false); // We want to catch the 302 Location header
            conn.setDoOutput(true);
            conn.setConnectTimeout(10_000);
            conn.setReadTimeout(10_000);

            try (OutputStream os = conn.getOutputStream()) {
                os.write(body.toString().getBytes(StandardCharsets.UTF_8));
            }

            int responseCode = conn.getResponseCode();
            
            // Nếu trả về 301, 302 thì đích đến (Location) chính là trang thanh toán!
            if (responseCode == HttpURLConnection.HTTP_MOVED_TEMP || responseCode == HttpURLConnection.HTTP_MOVED_PERM || responseCode == 303) {
                String location = conn.getHeaderField("Location");
                if (location != null && !location.isEmpty() && location.contains("v1/checkout")) {
                    if (logger != null) logger.info("[SePay] Checkout URL (from Location Header): " + location);
                    return location;
                }
            }

            // Fallback đọc nội dung body nếu không redirect
            java.io.InputStream is = (responseCode >= 200 && responseCode < 300)
                    ? conn.getInputStream() : conn.getErrorStream();

            String responseBody = new String(is.readAllBytes(), StandardCharsets.UTF_8);

            if (logger != null) {
                logger.info("[SePay] Response " + responseCode + ": " + responseBody);
            }

            // Fallback to regex if we got HTML instead of a redirect
            java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("https://(?:pay(?:-sandbox)?|my)\\.sepay\\.vn/v1/checkout[?][^\"'\\\\s}\\\\|<>]+");
            java.util.regex.Matcher matcher = pattern.matcher(responseBody);
            if (matcher.find()) {
                String extractedUrl = matcher.group();
                if (logger != null) logger.info("[SePay] Checkout URL (extracted via Regex): " + extractedUrl);
                return extractedUrl;
            }

            // Fallback JSON (in case SePay returns JSON in the future)
            String redirectUrl = extractJsonField(responseBody, "checkoutUrl");
            if (redirectUrl == null || redirectUrl.isEmpty()) {
                redirectUrl = extractJsonField(responseBody, "paymentUrl");
            }
            if (redirectUrl == null || redirectUrl.isEmpty()) {
                redirectUrl = extractJsonField(responseBody, "redirect_url");
            }

            if (redirectUrl != null && !redirectUrl.isEmpty()) {
                String parsedUrl = redirectUrl.replace("\\/", "/");
                if (logger != null) logger.info("[SePay] Checkout URL (parsed from JSON): " + parsedUrl);
                return parsedUrl;
            }

            if (logger != null) {
                logger.warning("[SePay] Không tìm thấy checkout URL trong response. Body: " + responseBody);
            }
            return null;

        } catch (Exception e) {
            if (logger != null) {
                logger.severe("[SePay] Lỗi khi tạo checkout URL: " + e.getMessage());
            }
            e.printStackTrace();
            return null;
        }
    }

    // ─── Helpers ────────────────────────────────────────────────────────────

    private static void appendForm(StringBuilder sb, String key, String value) {
        if (sb.length() > 0) sb.append("&");
        try {
            sb.append(java.net.URLEncoder.encode(key, "UTF-8").replace("+", "%20"))
              .append("=")
              .append(java.net.URLEncoder.encode(value, "UTF-8").replace("+", "%20"));
        } catch (Exception e) {}
    }

    // ─── Helpers ────────────────────────────────────────────────────────────

    private static void appendJson(StringBuilder sb, String key, String value, boolean last) {
        if (sb.length() > 1) sb.append(",");
        sb.append("\"").append(key).append("\":\"").append(escapeJson(value)).append("\"");
    }

    private static String escapeJson(String value) {
        if (value == null) return "";
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    /**
     * Tìm giá trị của một field trong JSON string đơn giản (không dùng thư viện ngoài).
     * Ví dụ: extractJsonField("{\"redirect_url\":\"https://pay.sepay.vn/abc\"}", "redirect_url")
     *        → "https://pay.sepay.vn/abc"
     */
    private static String extractJsonField(String json, String fieldName) {
        String key = "\"" + fieldName + "\":\"";
        int start = json.indexOf(key);
        if (start == -1) return null;
        start += key.length();
        int end = json.indexOf("\"", start);
        if (end == -1) return null;
        return json.substring(start, end).replace("\\/", "/");
    }

    /**
     * Tải HTML của trang thanh toán SePay và trích xuất link ảnh mã QR (vietqr.app hoặc qr.sepay.vn).
     * @param checkoutUrl URL trang thanh toán
     * @return URL của ảnh mã QR, hoặc null nếu không tìm thấy
     */
    public static String extractQrImageUrl(String checkoutUrl) {
        try {
            URL url = new URL(checkoutUrl);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("User-Agent", "Mozilla/5.0"); // Tránh bị chặn bởi Anti-Bot
            conn.setInstanceFollowRedirects(true);
            conn.setConnectTimeout(10_000);
            conn.setReadTimeout(10_000);

            int responseCode = conn.getResponseCode();
            if (responseCode >= 200 && responseCode < 300) {
                java.io.InputStream is = conn.getInputStream();
                String html = new String(is.readAllBytes(), StandardCharsets.UTF_8);

                // Ưu tiên tìm thẻ img có class="qrcode"
                java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("src=[\"']([^\"']+(?:vietqr\\.app|qr\\.sepay\\.vn)/img[^\"']+)[\"'][^>]*class=[\"']qrcode[\"']");
                java.util.regex.Matcher matcher = pattern.matcher(html);
                if (matcher.find()) {
                    return matcher.group(1).replace("&amp;", "&");
                }

                // Fallback: Tìm bất kỳ ảnh nào có URL vietqr.app/img hoặc qr.sepay.vn/img
                pattern = java.util.regex.Pattern.compile("src=[\"']([^\"']+(?:vietqr\\.app|qr\\.sepay\\.vn)/img[^\"']+)[\"']");
                matcher = pattern.matcher(html);
                if (matcher.find()) {
                    return matcher.group(1).replace("&amp;", "&");
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * Poll trạng thái đơn hàng từ SePay API.
     */
    public static boolean checkOrderStatus(String merchantId, String secretKey, String sepayOrderId, java.util.logging.Logger logger) {
        try {
            URL url = new URL("https://pgapi.sepay.vn/v1/order/detail/" + sepayOrderId);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            String auth = merchantId + ":" + secretKey;
            String encodedAuth = java.util.Base64.getEncoder().encodeToString(auth.getBytes(StandardCharsets.UTF_8));
            conn.setRequestProperty("Authorization", "Basic " + encodedAuth);
            conn.setRequestProperty("Accept", "application/json");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            
            int code = conn.getResponseCode();
            if (code >= 200 && code < 300) {
                String response = new String(conn.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                logger.info("[SePay Polling] Code: " + code + ", Response: " + response);
                // Tìm kiếm order_status trong JSON. Nếu có "order_status":"PAID" hoặc "CAPTURED" thì coi như xong.
                if (response.contains("\"order_status\":\"PAID\"") || response.contains("\"order_status\":\"CAPTURED\"") || response.contains("\"order_status\":\"Paid\"") || response.contains("\"order_status\":\"paid\"")) {
                    return true;
                }
            } else {
                java.io.InputStream errStream = conn.getErrorStream();
                String errResponse = errStream != null ? new String(errStream.readAllBytes(), StandardCharsets.UTF_8) : "";
                logger.warning("[SePay Polling] Lỗi API: Code " + code + " - " + errResponse);
            }
        } catch (Exception e) {
            logger.warning("[SePay Polling] Lỗi Exception: " + e.getMessage());
        }
        return false;
    }
}
