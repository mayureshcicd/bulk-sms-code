package com.message.approval;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class BulkMessageReportControllerTest {
    @Autowired MockMvc mockMvc;

    @Test
    void adminCanRenderDailyReportGrid() throws Exception {
        mockMvc.perform(get("/reports/bulk-messages")
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Bulk Message Reports")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Download PDF")));
    }

    @Test
    void adminCanDownloadPdfReport() throws Exception {
        mockMvc.perform(get("/reports/bulk-messages.pdf")
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/pdf"));
    }

    @Test
    void usernameFilterIsRetainedInGridAndPdfLink() throws Exception {
        mockMvc.perform(get("/reports/bulk-messages")
                        .param("username", "prakash")
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("value=\"prakash\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("username=prakash")));
    }

    @Test
    void emptyReportDeleteSelectionRedirectsSafely() throws Exception {
        mockMvc.perform(post("/reports/bulk-messages/delete")
                        .with(user("admin").roles("ADMIN")).with(csrf()))
                .andExpect(status().is3xxRedirection());
    }
}
