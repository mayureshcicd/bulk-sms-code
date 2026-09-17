package com.message.approval;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class UserRegistrationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void duplicateUsernameReturnsReadableMessageInsteadOfServerError() throws Exception {
        mockMvc.perform(post("/register")
                        .with(user("admin").roles("ADMIN"))
                        .param("username", "admin")
                        .param("password", "password123")
                        .param("mobileNumber", "9876543210")
                        .param("role", "ROLE_USER"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/register"))
                .andExpect(flash().attribute("errorMessage", "Username is already registered"));
    }

    @Test
    void invalidInputMessageRendersInsideAccountPage() throws Exception {
        mockMvc.perform(get("/register")
                        .with(user("admin").roles("ADMIN"))
                        .flashAttr("errorMessage", "Enter a valid 10-digit Indian mobile number")
                        .flashAttr("usernameValue", "prakash"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Enter a valid 10-digit Indian mobile number")))
                .andExpect(content().string(containsString("value=\"prakash\"")))
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("Whitelabel Error Page"))));
    }
}
