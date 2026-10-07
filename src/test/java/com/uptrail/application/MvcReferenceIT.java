package com.uptrail.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.bind.annotation.RestController;

@ActiveProfiles("mvc-reference")
class MvcReferenceIT extends AbstractApplicationIT {

    @Autowired
    private ApplicationContext context;

    @Test
    void restControllersAreAbsentAndTheirRoutesReturnNotFound() throws Exception {
        assertThat(context.getBeansWithAnnotation(RestController.class)).isEmpty();
        MockHttpSession session = staffSession(employee);
        mvc.perform(get("/api/v1/catalogue").session(session)).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/calendar").session(session)).andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/applications/preview").session(session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void employeeSubmitsAndManagerApprovesThroughHtmlFormsOnly() throws Exception {
        MockHttpSession employeeSession = staffSession(employee);
        mvc.perform(get("/staff/applications/new").session(employeeSession))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("checked when you submit")))
                .andExpect(content().string(not(containsString("data-preview-url"))))
                .andExpect(content().string(not(containsString("catalogue-search"))));

        mvc.perform(post("/staff/applications").session(employeeSession).with(csrf())
                        .param("category", "EXTERNAL").param("courseTitle", "MVC Reference Course")
                        .param("providerName", "Sample Provider").param("courseFee", "100.00")
                        .param("startDate", MON_12_OCT.toString()).param("endDate", MON_12_OCT.toString())
                        .param("startSession", "AM").param("endSession", "PM")
                        .param("justification", "Apply this training to our project.")
                        .param("clientRequestId", UUID.randomUUID().toString()))
                .andExpect(redirectedUrlPattern("/staff/applications/*"));
        Long id = jdbc.queryForObject("SELECT id FROM course_application WHERE applicant_id = ?", Long.class,
                employee.id());
        assertThat(statusOf(id)).isEqualTo("APPLIED");
        assertThat(jdbc.queryForObject("SELECT approver_id FROM course_application WHERE id = ?", Long.class, id))
                .isEqualTo(manager.id());

        MockHttpSession managerSession = staffSession(manager);
        mvc.perform(get("/manager/approvals").session(managerSession))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("MVC Reference Course")));
        mvc.perform(post("/manager/applications/" + id + "/decision").session(managerSession).with(csrf())
                        .param("decision", "APPROVE").param("reason", "Useful for our team project.")
                        .param("expectedVersion", Long.toString(version(id))))
                .andExpect(redirectedUrl("/manager/approvals"));
        assertThat(statusOf(id)).isEqualTo("APPROVED");
        mvc.perform(get("/employee/applications/" + id).session(employeeSession))
                .andExpect(redirectedUrl("/staff/applications/" + id));
        mvc.perform(get("/staff/applications/" + id).session(employeeSession))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Useful for our team project.")));
        MockHttpSession otherStaff = staffSession(fixtures.employee("other"));
        mvc.perform(get("/employee/applications/" + id).session(otherStaff))
                .andExpect(redirectedUrl("/staff/applications/" + id));
        mvc.perform(get("/staff/applications/" + id).session(otherStaff)).andExpect(status().isNotFound());
        mvc.perform(get("/staff/home").session(managerSession)).andExpect(status().isOk());
    }

    @Test
    void calendarRendersAndFiltersOnTheServerWithJavascriptEnabledOrDisabled() throws Exception {
        MockHttpSession session = staffSession(employee);
        mvc.perform(get("/calendar").param("month", "2026-10").param("category", "EXTERNAL").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("October 2026")))
                .andExpect(content().string(containsString("No approved courses this month.")))
                .andExpect(content().string(containsString("type=\"submit\">Show")))
                .andExpect(content().string(not(containsString("calendar.js"))))
                .andExpect(content().string(not(containsString("data-api"))));
    }
}
