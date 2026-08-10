package org.mgmmehrad.sevoqueue;

import org.slf4j.Logger;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * تایید لایسنس از طریق Supabase Edge Function.
 * درخواست فقط یک بار، در هنگام initialize() پراکسی، به صورت سینک ارسال می‌شود.
 */
public class Licence {
    private static final String ENDPOINT_URL =
            "https://phakyvwednaguqqrsook.supabase.co/functions/v1/smooth-worker";

    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);

    // برای استخراج فیلدهای ساده از JSON پاسخ، بدون نیاز به وابستگی جدید (Gson/Jackson)
    private static final Pattern VALID_PATTERN = Pattern.compile("\"valid\"\\s*:\\s*(true|false)");
    private static final Pattern MESSAGE_PATTERN = Pattern.compile("\"message\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"");
    private static final Pattern OWNER_PATTERN = Pattern.compile("\"owner\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"");

    private final Logger logger;
    private final HttpClient httpClient;

    private boolean licenseValid = false;
    private String ownerName = null;
    private String lastMessage = "License not checked yet.";

    public Licence(Logger logger) {
        this.logger = logger;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(REQUEST_TIMEOUT)
                .build();
    }

    /**
     * لایسنس را نسبت به سرور Supabase چک می‌کند. این متد بلاک‌کننده (synchronous) است،
     * چون initialize() پراکسی هم خودش سینک اجرا می‌شود و باید قبل از رجیستر شدن کامندها
     * وضعیت لایسنس مشخص باشد.
     *
     * نکته‌ی امنیتی مهم: پلاگین دیگر IP را خودش تشخیص نمی‌دهد و در درخواست ارسال نمی‌کند.
     * اگر IP توسط کلاینت (پلاگین) فرستاده شود، هرکسی که کد یا config را ببیند می‌تواند
     * مقدار دلخواهی را جایگزین کند و قفل IP را دور بزند. تشخیص IP واقعی باید سمت سرور
     * (در خود Edge Function، از روی هدر واقعی HTTP) انجام شود، نه سمت کلاینت.
     *
     * @param licenseKey کلید لایسنس از config.yml
     * @return true اگر لایسنس معتبر بود
     */
    public boolean checkLicense(String licenseKey) {
        if (licenseKey == null || licenseKey.isBlank() || licenseKey.equals("TypeYourLicenceHere")) {
            licenseValid = false;
            lastMessage = "No license key configured.";
            return false;
        }

        String requestBody = "{\"license_key\":\"" + escapeJson(licenseKey) + "\"}";

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(ENDPOINT_URL))
                    .timeout(REQUEST_TIMEOUT)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return parseResponse(response.statusCode(), response.body());

        } catch (IOException e) {
            licenseValid = false;
            lastMessage = "Could not reach license server: " + e.getMessage();
            logger.warn("[Licence] Failed to reach license server: {}", e.getMessage());
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            licenseValid = false;
            lastMessage = "License check was interrupted.";
            logger.warn("[Licence] License check interrupted.");
            return false;
        } catch (Exception e) {
            licenseValid = false;
            lastMessage = "Unexpected error during license check: " + e.getMessage();
            logger.warn("[Licence] Unexpected error during license check.", e);
            return false;
        }
    }

    private boolean parseResponse(int statusCode, String body) {
        if (body == null || body.isBlank()) {
            licenseValid = false;
            lastMessage = "Empty response from license server (HTTP " + statusCode + ").";
            return false;
        }

        Matcher validMatcher = VALID_PATTERN.matcher(body);
        Matcher messageMatcher = MESSAGE_PATTERN.matcher(body);
        Matcher ownerMatcher = OWNER_PATTERN.matcher(body);

        boolean valid = validMatcher.find() && Boolean.parseBoolean(validMatcher.group(1));
        String message = messageMatcher.find() ? unescapeJson(messageMatcher.group(1)) : null;
        String owner = ownerMatcher.find() ? unescapeJson(ownerMatcher.group(1)) : null;

        licenseValid = valid;
        ownerName = owner;
        lastMessage = message != null ? message
                : (valid ? "License verified successfully." : "License check failed (HTTP " + statusCode + ").");

        if (valid) {
            logger.info("[Licence] License valid. Owner: {}", ownerName != null ? ownerName : "unknown");
        } else {
            logger.warn("[Licence] License invalid: {}", lastMessage);
        }

        return valid;
    }

    private static String escapeJson(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static String unescapeJson(String s) {
        return s.replace("\\\"", "\"").replace("\\\\", "\\");
    }

    public boolean isLicenseValid() {
        return licenseValid;
    }

    public String getOwnerName() {
        return ownerName;
    }

    public String getLastMessage() {
        return lastMessage;
    }
}