package com.message.approval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.message.approval.controller.IncomingMessageAdminController;
import com.message.approval.controller.OpenWAWebhookController;

@SpringBootTest(properties = "allow-incoming-message=false")
@AutoConfigureMockMvc
class IncomingMessageFeatureDisabledTest {
    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ApplicationContext applicationContext;

    @Test
    void unregistersIncomingMessageUiAndWebhook() throws Exception {
        assertThat(applicationContext.getBeansOfType(IncomingMessageAdminController.class)).isEmpty();
        assertThat(applicationContext.getBeansOfType(OpenWAWebhookController.class)).isEmpty();

        mockMvc.perform(get("/incoming-messages").with(user("admin").roles("ADMIN")))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/openwa/webhook")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/dashboard").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("Incoming Messages"))));
    }
}
