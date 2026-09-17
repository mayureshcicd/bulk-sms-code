package com.sms.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

class BulkDocumentServiceTest {

    private final BulkDocumentService service = new BulkDocumentService(null, null, null, null, null);

    @Test
    void routesVideoAsPlayableWhatsAppVideo() {
        assertEquals("/messages/send-video", service.resolveMediaEndpoint("video/mp4", "offer.mp4"));
        assertEquals("/messages/send-video", service.resolveMediaEndpoint(null, "offer.MP4"));
    }

    @Test
    void keepsImageAndDocumentRoutesUnchanged() {
        assertEquals("/messages/send-image", service.resolveMediaEndpoint("image/jpeg", "offer.jpg"));
        assertEquals("/messages/send-document", service.resolveMediaEndpoint("application/pdf", "offer.pdf"));
    }
}
