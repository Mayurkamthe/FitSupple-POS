package com.fitsupplepos.whatsapp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fitsupplepos.config.AppConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Raw HTTP wrapper over Meta's WhatsApp Cloud (Graph) API. Every method here does exactly
 * one HTTP call and returns a small typed result — retry/backoff, message logging, and
 * "is this even configured" checks all live one layer up in {@link WhatsAppService}.
 *
 * Credentials (access token, phone number id) are read from {@link AppConfig}, never
 * hardcoded and never committed to source control.
 */
public class WhatsAppApiClient {

    private static final Logger log = LoggerFactory.getLogger(WhatsAppApiClient.class);

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .build();
    private final ObjectMapper mapper = new ObjectMapper();

    /** Result of a send call: either a WhatsApp message id (success) or an error message (failure). */
    public static class SendResult {
        public final boolean success;
        public final String whatsAppMessageId;
        public final String errorMessage;

        private SendResult(boolean success, String whatsAppMessageId, String errorMessage) {
            this.success = success;
            this.whatsAppMessageId = whatsAppMessageId;
            this.errorMessage = errorMessage;
        }

        static SendResult ok(String id) { return new SendResult(true, id, null); }
        static SendResult fail(String error) { return new SendResult(false, null, error); }
    }

    private String baseUrl() {
        return "https://graph.facebook.com/" + AppConfig.whatsAppApiVersion() + "/" + AppConfig.whatsAppPhoneNumberId();
    }

    /** Plain free-text message. Only deliverable within Meta's 24-hour customer-service window. */
    public SendResult sendTextMessage(String toE164, String body) {
        String json = """
                {
                  "messaging_product": "whatsapp",
                  "to": "%s",
                  "type": "text",
                  "text": { "preview_url": false, "body": %s }
                }
                """.formatted(toE164, jsonString(body));
        return post("/messages", json);
    }

    /**
     * Approved-template message — the only message type that can be sent outside the
     * 24-hour window (invoices, order confirmations, campaigns). {@code bodyParams} fill
     * the template's {{1}}, {{2}}, ... placeholders in order.
     */
    public SendResult sendTemplateMessage(String toE164, String templateName, String languageCode, List<String> bodyParams) {
        StringBuilder params = new StringBuilder();
        if (bodyParams != null) {
            for (int i = 0; i < bodyParams.size(); i++) {
                if (i > 0) params.append(",");
                params.append("{\"type\":\"text\",\"text\":").append(jsonString(bodyParams.get(i))).append("}");
            }
        }
        String json = """
                {
                  "messaging_product": "whatsapp",
                  "to": "%s",
                  "type": "template",
                  "template": {
                    "name": "%s",
                    "language": { "code": "%s" },
                    "components": [ { "type": "body", "parameters": [ %s ] } ]
                  }
                }
                """.formatted(toE164, templateName, languageCode, params);
        return post("/messages", json);
    }

    /** Sends a previously-uploaded media item (see {@link #uploadMedia}) as a document, e.g. the invoice PDF. */
    public SendResult sendDocumentMessage(String toE164, String mediaId, String filename, String caption) {
        String json = """
                {
                  "messaging_product": "whatsapp",
                  "to": "%s",
                  "type": "document",
                  "document": { "id": "%s", "filename": %s, "caption": %s }
                }
                """.formatted(toE164, mediaId, jsonString(filename), jsonString(caption));
        return post("/messages", json);
    }

    /**
     * Uploads a local file (e.g. an invoice PDF) to WhatsApp's media endpoint and returns
     * the resulting media id, which {@link #sendDocumentMessage} then references. Two
     * separate HTTP calls total to go from "PDF on disk" to "customer has it on WhatsApp".
     */
    public String uploadMedia(File file, String mimeType) throws IOException, InterruptedException {
        String boundary = "----FitSupplePOS" + UUID.randomUUID();
        byte[] fileBytes = Files.readAllBytes(file.toPath());

        List<byte[]> parts = new ArrayList<>();
        parts.add(field(boundary, "messaging_product", "whatsapp"));
        parts.add(fileField(boundary, "file", file.getName(), mimeType, fileBytes));
        parts.add(("--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));

        int total = parts.stream().mapToInt(p -> p.length).sum();
        byte[] body = new byte[total];
        int pos = 0;
        for (byte[] p : parts) {
            System.arraycopy(p, 0, body, pos, p.length);
            pos += p.length;
        }

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl() + "/media"))
                .timeout(Duration.ofSeconds(60))
                .header("Authorization", "Bearer " + AppConfig.whatsAppAccessToken())
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                .build();

        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        JsonNode node = mapper.readTree(response.body());
        if (response.statusCode() / 100 == 2 && node.has("id")) {
            return node.get("id").asText();
        }
        throw new IOException("WhatsApp media upload failed (" + response.statusCode() + "): " + response.body());
    }

    private SendResult post(String path, String jsonBody) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl() + path))
                    .timeout(Duration.ofSeconds(20))
                    .header("Authorization", "Bearer " + AppConfig.whatsAppAccessToken())
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody, StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            JsonNode node = mapper.readTree(response.body());

            if (response.statusCode() / 100 == 2 && node.has("messages")) {
                String id = node.get("messages").get(0).get("id").asText();
                return SendResult.ok(id);
            }
            String errorMsg = node.has("error") && node.get("error").has("message")
                    ? node.get("error").get("message").asText()
                    : "WhatsApp API returned HTTP " + response.statusCode();
            log.warn("WhatsApp send failed: {}", errorMsg);
            return SendResult.fail(errorMsg);
        } catch (Exception e) {
            log.error("WhatsApp API call failed", e);
            return SendResult.fail(e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
        }
    }

    private String jsonString(String s) {
        try {
            return mapper.writeValueAsString(s == null ? "" : s);
        } catch (IOException e) {
            return "\"\"";
        }
    }

    private byte[] field(String boundary, String name, String value) {
        String part = "--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"" + name + "\"\r\n\r\n"
                + value + "\r\n";
        return part.getBytes(StandardCharsets.UTF_8);
    }

    private byte[] fileField(String boundary, String name, String filename, String mimeType, byte[] content) {
        String header = "--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"" + name + "\"; filename=\"" + filename + "\"\r\n"
                + "Content-Type: " + mimeType + "\r\n\r\n";
        byte[] headerBytes = header.getBytes(StandardCharsets.UTF_8);
        byte[] footerBytes = "\r\n".getBytes(StandardCharsets.UTF_8);
        byte[] result = new byte[headerBytes.length + content.length + footerBytes.length];
        System.arraycopy(headerBytes, 0, result, 0, headerBytes.length);
        System.arraycopy(content, 0, result, headerBytes.length, content.length);
        System.arraycopy(footerBytes, 0, result, headerBytes.length + content.length, footerBytes.length);
        return result;
    }
}
