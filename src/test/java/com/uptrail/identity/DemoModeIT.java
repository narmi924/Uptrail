package com.uptrail.identity;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

import com.uptrail.support.AbstractMySqlIT;

/**
 * In demo mode the sign-in pages list sample accounts of their own workspace with the shared sample password.
 */
@TestPropertySource(properties = {"uptrail.demo.enabled=true", "uptrail.sample-data.password=Demo-password-1"})
class DemoModeIT extends AbstractMySqlIT {

    @Test
    void theStaffSignInListsStaffAccounts() throws Exception {
        mvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("Public demo")))
                .andExpect(content().string(Matchers.containsString("Demo-password-1")))
                .andExpect(content().string(Matchers.containsString("data-ut-demo-username=\"daniel\"")))
                .andExpect(content().string(Matchers.not(Matchers.containsString("data-ut-demo-username=\"alex\""))));
    }

    @Test
    void theAdministratorSignInListsAdministratorAccounts() throws Exception {
        mvc.perform(get("/admin/login"))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("data-ut-demo-username=\"alex\"")))
                .andExpect(content().string(Matchers.not(Matchers.containsString("data-ut-demo-username=\"siti\""))));
    }
}
