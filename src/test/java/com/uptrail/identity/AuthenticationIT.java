package com.uptrail.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;

import com.uptrail.identity.domain.Role;
import com.uptrail.identity.service.SessionControlService;
import com.uptrail.organisation.domain.Designation;
import com.uptrail.support.AbstractMySqlIT;
import com.uptrail.support.Fixtures;
import com.uptrail.support.Fixtures.Person;

/**
 * Two entry points, workspace separation, CSRF, session expiry and safe post-login redirects
 * (test scenarios T01, T02, T03, T08, T09).
 */
class AuthenticationIT extends AbstractMySqlIT {

    @Autowired
    private SessionControlService sessionControl;

    private MockHttpSession login(String entry, String username) throws Exception {
        return (MockHttpSession) mvc.perform(formLogin(entry).user(username).password(Fixtures.PASSWORD))
                .andExpect(authenticated())
                .andReturn().getRequest().getSession(false);
    }

    @Test
    void staffSignInOpensTheStaffWorkspace() throws Exception {
        Person employee = fixtures.employee("emma");

        mvc.perform(formLogin("/login").user("emma").password(Fixtures.PASSWORD))
                .andExpect(redirectedUrl("/employee/dashboard"))
                .andExpect(authenticated().withUsername("emma"));

        MockHttpSession session = login("/login", "emma");
        mvc.perform(get("/employee/dashboard").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString(employee.employee().getFullName())))
                .andExpect(content().string(Matchers.containsString("Staff workspace")));
    }

    @Test
    void administratorSignInOpensTheAdministrationWorkspace() throws Exception {
        fixtures.admin("ada");

        mvc.perform(formLogin("/admin/login").user("ada").password(Fixtures.PASSWORD))
                .andExpect(redirectedUrl("/admin/dashboard"));

        MockHttpSession session = login("/admin/login", "ada");
        mvc.perform(get("/admin/dashboard").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("Year readiness")));
    }

    @Test
    void correctPasswordAtTheWrongEntryPointIsRejected() throws Exception {
        fixtures.admin("ada");
        fixtures.employee("emma");

        mvc.perform(formLogin("/login").user("ada").password(Fixtures.PASSWORD))
                .andExpect(redirectedUrl("/login?error"))
                .andExpect(unauthenticated());
        mvc.perform(formLogin("/admin/login").user("emma").password(Fixtures.PASSWORD))
                .andExpect(redirectedUrl("/admin/login?error"))
                .andExpect(unauthenticated());
    }

    @Test
    void wrongPasswordUnknownUserAndInactiveEmployeeCannotSignIn() throws Exception {
        Person leaver = fixtures.employee("leaver");
        fixtures.deactivate(leaver);
        fixtures.employee("emma");

        mvc.perform(formLogin("/login").user("emma").password("wrong-password"))
                .andExpect(redirectedUrl("/login?error"));
        mvc.perform(formLogin("/login").user("nobody").password(Fixtures.PASSWORD))
                .andExpect(redirectedUrl("/login?error"));
        mvc.perform(formLogin("/login").user("leaver").password(Fixtures.PASSWORD))
                .andExpect(redirectedUrl("/login?error"))
                .andExpect(unauthenticated());
    }

    @Test
    void usernamesAreCaseInsensitive() throws Exception {
        fixtures.employee("emma");

        mvc.perform(formLogin("/login").user("  EMMA ").password(Fixtures.PASSWORD))
                .andExpect(authenticated().withUsername("emma"));
    }

    @Test
    void accountWithBothRolesCanUseEachEntryButEachSessionKeepsItsWorkspace() throws Exception {
        fixtures.person("hybrid", Designation.ADMINISTRATIVE, Role.ADMIN, Role.EMPLOYEE);

        MockHttpSession staffSession = login("/login", "hybrid");
        mvc.perform(get("/employee/dashboard").session(staffSession)).andExpect(status().isOk());
        mvc.perform(get("/admin/dashboard").session(staffSession)).andExpect(status().isForbidden());

        MockHttpSession adminSession = login("/admin/login", "hybrid");
        mvc.perform(get("/admin/dashboard").session(adminSession)).andExpect(status().isOk());
        mvc.perform(get("/employee/dashboard").session(adminSession)).andExpect(status().isForbidden());
    }

    @Test
    void employeesCannotOpenManagerOrAdministratorPages() throws Exception {
        fixtures.employee("emma");
        MockHttpSession session = login("/login", "emma");

        mvc.perform(get("/manager/approvals").session(session)).andExpect(status().isForbidden());
        mvc.perform(get("/admin/staff").session(session)).andExpect(status().isForbidden());
    }

    @Test
    void anonymousPageRequestsGoToTheMatchingSignInPage() throws Exception {
        mvc.perform(get("/employee/dashboard")).andExpect(redirectedUrl("/login"));
        mvc.perform(get("/admin/dashboard")).andExpect(redirectedUrl("/admin/login"));
    }

    @Test
    void anonymousApiRequestsGetJsonNotALoginPage() throws Exception {
        mvc.perform(get("/api/v1/calendar").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"))
                .andExpect(jsonPath("$.correlationId").isNotEmpty());
    }

    @Test
    void stateChangingRequestsWithoutCsrfTokenAreRejected() throws Exception {
        fixtures.employee("emma");
        MockHttpSession session = login("/login", "emma");

        mvc.perform(post("/login").param("username", "emma").param("password", Fixtures.PASSWORD))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/applications/preview").session(session)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CSRF_INVALID"));
    }

    @Test
    void postLoginRedirectOnlyFollowsSafePathsInTheSameWorkspace() throws Exception {
        fixtures.employee("emma");

        mvc.perform(loginWithNext("emma", "/employee/applications/42"))
                .andExpect(redirectedUrl("/employee/applications/42"));
        for (String unsafe : new String[] {"//evil.example.com/x", "https://evil.example.com", "/admin/dashboard",
                "/employee\\..\\x", "employee/dashboard"}) {
            mvc.perform(loginWithNext("emma", unsafe)).andExpect(redirectedUrl("/employee/dashboard"));
        }
    }

    private static org.springframework.test.web.servlet.RequestBuilder loginWithNext(String user, String next) {
        return post("/login").param("username", user).param("password", Fixtures.PASSWORD).param("next", next)
                .with(csrf());
    }

    @Test
    void expiringSessionsForcesSignInAgain() throws Exception {
        fixtures.employee("emma");
        MockHttpSession session = login("/login", "emma");
        mvc.perform(get("/employee/dashboard").session(session)).andExpect(status().isOk());

        int expired = sessionControl.expireSessionsOf("emma");

        assertThat(expired).isGreaterThanOrEqualTo(1);
        mvc.perform(get("/employee/dashboard").session(session))
                .andExpect(redirectedUrl("/login?expired"));
    }

    @Test
    void signOutRequiresPost() throws Exception {
        fixtures.employee("emma");
        MockHttpSession session = login("/login", "emma");

        mvc.perform(get("/logout").session(session)).andExpect(status().isNotFound());
        mvc.perform(get("/employee/dashboard").session(session)).andExpect(status().isOk());

        mvc.perform(post("/logout").session(session).with(csrf()))
                .andExpect(redirectedUrl("/login?logout"));
        mvc.perform(get("/employee/dashboard").session(session))
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    void responsesCarrySecurityHeaders() throws Exception {
        mvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Security-Policy", Matchers.containsString("default-src 'self'")))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("X-Frame-Options", "DENY"));
    }
}
