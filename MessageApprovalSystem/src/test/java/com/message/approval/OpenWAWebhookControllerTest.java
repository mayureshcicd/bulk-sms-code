package com.message.approval;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import com.message.approval.domain.MonitoredWhatsAppSession;
import com.message.approval.repository.IncomingWhatsAppMessageRepository;
import com.message.approval.repository.MonitoredWhatsAppSessionRepository;
import com.message.approval.service.OpenWAAdministrationService;

@SpringBootTest(properties = "openwa.webhook.secret=test-webhook-secret-123456")
@AutoConfigureMockMvc
@Transactional
class OpenWAWebhookControllerTest {
    @Autowired MockMvc mockMvc;
    @Autowired MonitoredWhatsAppSessionRepository monitoredRepository;
    @Autowired IncomingWhatsAppMessageRepository messageRepository;
    @Autowired RestTemplate restTemplate;
    @MockBean OpenWAAdministrationService openWAAdministrationService;

    @BeforeEach
    void enableSession() {
        MonitoredWhatsAppSession session = new MonitoredWhatsAppSession();
        session.setSessionId("session-1");
        session.setSessionName("device-one");
        session.setMobileNumber("919822004153");
        session.setEnabled(true);
        session.setUpdatedAt(java.time.LocalDateTime.now());
        monitoredRepository.save(session);
    }

    @Test
    void verifiesSignatureStoresMessageAndDeduplicatesRetry() throws Exception {
        byte[] body = ("{\"event\":\"message.received\",\"sessionId\":\"session-1\","
                + "\"idempotencyKey\":\"event-1\",\"deliveryId\":\"delivery-1\",\"data\":{"
                + "\"id\":\"wa-message-1\",\"from\":\"919876543210@c.us\","
                + "\"to\":\"919822004153@c.us\",\"chatId\":\"919876543210@c.us\","
                + "\"body\":\"Hello admin\",\"type\":\"text\",\"timestamp\":1786354200}}")
                .getBytes(StandardCharsets.UTF_8);
        String signature = signature(body, "test-webhook-secret-123456");

        mockMvc.perform(post("/api/openwa/webhook").contentType(MediaType.APPLICATION_JSON)
                        .header("X-OpenWA-Signature", signature)
                        .header("X-OpenWA-Idempotency-Key", "event-1").content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("created"));
        mockMvc.perform(post("/api/openwa/webhook").contentType(MediaType.APPLICATION_JSON)
                        .header("X-OpenWA-Signature", signature)
                        .header("X-OpenWA-Idempotency-Key", "event-1").content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("duplicate"));

        assertEquals(1, messageRepository.count());
        var stored = messageRepository.findAll().get(0);
        assertEquals("Hello admin", stored.getMessageBody());
        assertEquals("919822004153", stored.getLinkedMobile());
    }

    @Test
    void rejectsInvalidSignature() throws Exception {
        mockMvc.perform(post("/api/openwa/webhook").contentType(MediaType.APPLICATION_JSON)
                        .header("X-OpenWA-Signature", "sha256=wrong").content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void detectsMediaWithoutHasMediaFlagAndStoresActualCusNumber() throws Exception {
        byte[] body = ("{\"event\":\"message.received\",\"sessionId\":\"session-1\"," 
                + "\"idempotencyKey\":\"image-event-1\",\"data\":{"
                + "\"id\":\"wa-image-1\",\"from\":\"919876543210@c.us\"," 
                + "\"chatId\":\"919876543210@c.us\",\"body\":\"Receipt\",\"type\":\"image\"," 
                + "\"media\":{\"mimetype\":\"image/jpeg\"},\"timestamp\":1786354200}}")
                .getBytes(StandardCharsets.UTF_8);

        mockMvc.perform(post("/api/openwa/webhook").contentType(MediaType.APPLICATION_JSON)
                        .header("X-OpenWA-Signature", signature(body, "test-webhook-secret-123456"))
                        .content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("created"));

        var stored = messageRepository.findAll().stream()
                .filter(item -> "wa-image-1".equals(item.getWhatsappMessageId())).findFirst().orElseThrow();
        assertTrue(stored.isHasMedia());
        assertTrue(stored.isImageMedia());
        assertEquals("919876543210", stored.getSenderMobile());
        assertEquals("image/jpeg", stored.getMediaMimetype());
    }

    @Test
    void ignoresEmptyWebhookMessageWithoutTextCaptionOrMedia() throws Exception {
        byte[] body = ("{\"event\":\"message.received\",\"sessionId\":\"session-1\","
                + "\"idempotencyKey\":\"bodyless-event-1\",\"data\":{"
                + "\"id\":\"wa-bodyless-1\",\"from\":\"919876543210@c.us\","
                + "\"chatId\":\"919876543210@c.us\",\"type\":\"unknown\","
                + "\"timestamp\":1786354200}}")
                .getBytes(StandardCharsets.UTF_8);

        mockMvc.perform(post("/api/openwa/webhook").contentType(MediaType.APPLICATION_JSON)
                        .header("X-OpenWA-Signature", signature(body, "test-webhook-secret-123456"))
                        .content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ignored"));

        assertTrue(messageRepository.findAll().stream()
                .noneMatch(item -> "wa-bodyless-1".equals(item.getWhatsappMessageId())));
    }

    @Test
    void storesMediaMessageEvenWhenCaptionIsEmpty() throws Exception {
        byte[] body = ("{\"event\":\"message.received\",\"sessionId\":\"session-1\","
                + "\"idempotencyKey\":\"captionless-image-event\",\"data\":{"
                + "\"id\":\"captionless-image\",\"from\":\"919876543210@c.us\","
                + "\"chatId\":\"919876543210@c.us\",\"type\":\"image\",\"hasMedia\":true,"
                + "\"media\":{\"mimetype\":\"image/jpeg\",\"filename\":\"photo.jpg\"}}}")
                .getBytes(StandardCharsets.UTF_8);

        mockMvc.perform(post("/api/openwa/webhook").contentType(MediaType.APPLICATION_JSON)
                        .header("X-OpenWA-Signature", signature(body, "test-webhook-secret-123456"))
                        .content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("created"));

        var stored = messageRepository.findAll().stream()
                .filter(item -> "captionless-image".equals(item.getWhatsappMessageId()))
                .findFirst().orElseThrow();
        assertEquals("", stored.getMessageBody());
        assertTrue(stored.isHasMedia());
    }

    @Test
    void storesAndRendersTextImagePdfAndVideoMessages() throws Exception {
        sendMessage("text-flow", "text", "Plain text", null, null, false);
        sendMessage("image-flow", "image", "Image caption", "image/jpeg", "photo.jpg", true);
        sendMessage("pdf-flow", "document", "PDF caption", "application/pdf", "invoice.pdf", true);
        sendMessage("video-flow", "video", "Video caption", "video/mp4", "clip.mp4", true);
        sendMessage("audio-flow", "voice", "Voice caption", "audio/ogg", "voice.ogg", true);

        assertEquals(5, messageRepository.findAll().stream()
                .filter(item -> item.getWhatsappMessageId().endsWith("-flow")).count());

        mockMvc.perform(get("/incoming-messages")
                        .param("sessionId", "session-1")
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Plain text")))
                .andExpect(content().string(containsString("Image caption")))
                .andExpect(content().string(containsString("photo.jpg")))
                .andExpect(content().string(containsString("PDF caption")))
                .andExpect(content().string(containsString("View PDF")))
                .andExpect(content().string(containsString("invoice.pdf")))
                .andExpect(content().string(containsString("Video caption")))
                .andExpect(content().string(containsString("clip.mp4")))
                .andExpect(content().string(containsString("<video")))
                .andExpect(content().string(containsString("Voice caption")))
                .andExpect(content().string(containsString("voice.ogg")))
                .andExpect(content().string(containsString("<audio")))
                .andExpect(content().string(containsString("Download")));
    }

    @Test
    void returnsReadableBadRequestForInvalidPayloadInsteadOfServerError() throws Exception {
        byte[] body = "{\"event\":\"message.received\",\"data\":{}}"
                .getBytes(StandardCharsets.UTF_8);

        mockMvc.perform(post("/api/openwa/webhook").contentType(MediaType.APPLICATION_JSON)
                        .header("X-OpenWA-Signature", signature(body, "test-webhook-secret-123456"))
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("sessionId is required"));
    }

    @Test
    void proxiesIncomingMediaBytesWithBrowserContentType() throws Exception {
        sendMessage("media-proxy-flow", "image", "Preview", "image/jpeg", "photo.jpg", true);
        var stored = messageRepository.findAll().stream()
                .filter(item -> "media-proxy-flow".equals(item.getWhatsappMessageId()))
                .findFirst().orElseThrow();
        byte[] imageBytes = new byte[] { 1, 2, 3, 4 };
        MockRestServiceServer mediaServer = MockRestServiceServer.bindTo(restTemplate).build();
        mediaServer.expect(requestTo("http://localhost:2785/api/sessions/session-1/messages/"
                        + "919876543210@c.us/media-proxy-flow/media"))
                .andRespond(withSuccess(imageBytes, MediaType.IMAGE_JPEG));

        mockMvc.perform(get("/incoming-messages/{id}/media", stored.getId())
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_JPEG))
                .andExpect(content().bytes(imageBytes));
        mediaServer.verify();
    }

    @Test
    void retriesLiveHistoryWhenArchivedMediaIsNotFound() throws Exception {
        sendMessage("history-media-flow", "image", "Preview", "image/jpeg", "history-photo.jpg", true);
        var stored = messageRepository.findAll().stream()
                .filter(item -> "history-media-flow".equals(item.getWhatsappMessageId()))
                .findFirst().orElseThrow();
        byte[] imageBytes = new byte[] { 1, 2, 3, 4 };
        MockRestServiceServer mediaServer = MockRestServiceServer.bindTo(restTemplate).build();
        mediaServer.expect(requestTo("http://localhost:2785/api/sessions/session-1/messages/"
                        + "919876543210@c.us/history-media-flow/media"))
                .andRespond(org.springframework.test.web.client.response.MockRestResponseCreators
                        .withStatus(HttpStatus.NOT_FOUND));
        mediaServer.expect(requestTo("http://localhost:2785/api/sessions/session-1/messages/"
                        + "919876543210@c.us/history?limit=100&includeMedia=true"))
                .andRespond(withSuccess("{\"messages\":[{\"id\":\"history-media-flow\","
                        + "\"media\":{\"mimetype\":\"image/jpeg\",\"data\":\"AQIDBA==\"}}]}",
                        MediaType.APPLICATION_JSON));

        mockMvc.perform(get("/incoming-messages/{id}/media", stored.getId())
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_JPEG))
                .andExpect(content().bytes(imageBytes));
        mediaServer.verify();
    }

    private void sendMessage(String id, String type, String text, String mimetype,
            String filename, boolean hasMedia) throws Exception {
        String media = mimetype == null ? ""
                : ",\"media\":{\"mimetype\":\"" + mimetype + "\",\"filename\":\"" + filename + "\"}";
        byte[] body = ("{\"event\":\"message.received\",\"sessionId\":\"session-1\","
                + "\"idempotencyKey\":\"" + id + "-event\",\"data\":{"
                + "\"id\":\"" + id + "\",\"from\":\"919876543210@c.us\","
                + "\"chatId\":\"919876543210@c.us\",\"body\":\"" + text + "\","
                + "\"type\":\"" + type + "\",\"hasMedia\":" + hasMedia
                + media + ",\"timestamp\":1786354200000}}")
                .getBytes(StandardCharsets.UTF_8);

        mockMvc.perform(post("/api/openwa/webhook").contentType(MediaType.APPLICATION_JSON)
                        .header("X-OpenWA-Signature", signature(body, "test-webhook-secret-123456"))
                        .content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("created"));
    }

    private String signature(byte[] body, String secret) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return "sha256=" + HexFormat.of().formatHex(mac.doFinal(body));
    }
}
