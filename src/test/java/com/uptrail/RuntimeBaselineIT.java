package com.uptrail;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;

import com.uptrail.shared.web.CorrelationId;
import com.uptrail.support.AbstractMySqlIT;

class RuntimeBaselineIT extends AbstractMySqlIT {

    @Test
    void healthEndpointReportsUpWithoutDetails() throws Exception {
        mvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.components").doesNotExist());
    }

    @Test
    void stylesheetsAndScriptsAreServedLocally() throws Exception {
        mvc.perform(get("/css/uptrail.css")).andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("--ut-primary")));
        mvc.perform(get("/js/uptrail.js")).andExpect(status().isOk());
        mvc.perform(get("/webjars/bootstrap/5.3.8/dist/css/bootstrap.min.css")).andExpect(status().isOk());
        mvc.perform(get("/webjars/bootstrap/5.3.8/dist/js/bootstrap.bundle.min.js")).andExpect(status().isOk());
    }

    @Test
    void everyResponseCarriesAServerGeneratedCorrelationId() throws Exception {
        mvc.perform(get("/actuator/health").header(CorrelationId.HEADER, "client-chosen"))
                .andExpect(header().string(CorrelationId.HEADER,
                        Matchers.matchesPattern("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")));
    }
}
