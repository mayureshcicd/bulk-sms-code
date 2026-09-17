package com.message.approval;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import com.message.approval.service.OpenWAAdministrationService;
import com.message.approval.service.OpenWAAdministrationService.OpenWASession;

@SpringBootTest
@AutoConfigureMockMvc
class IncomingMessageAdminControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private OpenWAAdministrationService openWAService;

    @Test
    void rendersLinkedOpenWASessionWithoutTemplateError() throws Exception {
        when(openWAService.monitoredSessions()).thenReturn(List.of());
        when(openWAService.listSessions()).thenReturn(List.of(
                new OpenWASession("device-1", "Sales phone", "919822004153", "CONNECTED", false)));

        mockMvc.perform(get("/incoming-messages").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("919822004153")))
                .andExpect(content().string(containsString("Sales phone")));
    }

    @Test
    void emptyIncomingDeleteSelectionRedirectsSafely() throws Exception {
        mockMvc.perform(post("/incoming-messages/delete")
                        .with(user("admin").roles("ADMIN")).with(csrf()))
                .andExpect(status().is3xxRedirection());
    }
}
