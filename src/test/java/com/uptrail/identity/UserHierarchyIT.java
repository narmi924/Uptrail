package com.uptrail.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import java.util.EnumSet;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import com.uptrail.model.*;
import com.uptrail.repo.*;
import com.uptrail.service.ManagerService;
import com.uptrail.service.StaffAdminService;
import com.uptrail.support.AbstractMySqlIT;
import com.uptrail.support.Fixtures;

@ActiveProfiles("mvc-reference")
class UserHierarchyIT extends AbstractMySqlIT {
    @Autowired UserRepo users;
    @Autowired StaffRepo staff;
    @Autowired ManagerRepo managers;
    @Autowired AdminRepo admins;
    @Autowired ManagerService managerService;
    @Autowired StaffAdminService administration;

    @Test
    void repositoriesLoadRealSubtypesWithOneInheritedIdentity() {
        var employee = fixtures.employee("staff");
        var manager = fixtures.manager("manager");
        var admin = fixtures.admin("admin");
        assertThat(users.findById(employee.id()).orElseThrow()).isExactlyInstanceOf(Staff.class);
        User stored = users.findById(manager.id()).orElseThrow();
        assertThat(stored).isExactlyInstanceOf(Manager.class).isInstanceOf(Staff.class);
        assertThat(staff.findByStaffId(stored.getStaffId()).orElseThrow().getUserId()).isEqualTo(manager.id());
        assertThat(managers.findByUserName("manager").orElseThrow().getStaffId()).isEqualTo(stored.getStaffId());
        assertThat(admins.findByUserName("admin").orElseThrow().getUserId()).isEqualTo(admin.id());
        assertThat(staff.findById(admin.id())).isEmpty();
    }

    @Test
    void managerUsesOnePasswordFreeSessionAcrossBothWorkspaces() throws Exception {
        var manager = fixtures.manager("manager");
        MockHttpSession session = (MockHttpSession) mvc.perform(formLogin("/employee/login")
                        .user("manager").password(Fixtures.PASSWORD))
                .andExpect(redirectedUrl("/manager/home")).andReturn().getRequest().getSession(false);
        User user = (User) session.getAttribute("user");
        assertThat(user).isExactlyInstanceOf(Manager.class).isInstanceOf(Staff.class);
        assertThat(user.getUserId()).isEqualTo(manager.id());
        assertThat(user.getPasswordHash()).isNull();
        mvc.perform(get("/manager/home").session(session)).andExpect(status().isOk());
        mvc.perform(get("/staff/home").session(session)).andExpect(status().isOk());
        mvc.perform(get("/staff/applications/new").session(session)).andExpect(status().isOk());
        assertThat(((User) session.getAttribute("user")).getUserId()).isEqualTo(manager.id());
    }

    @Test
    void browserParametersAndForgedSessionCannotChangeTheSignedInUser() throws Exception {
        var employee = fixtures.employee("staff");
        var manager = fixtures.manager("manager");
        MockHttpSession session = (MockHttpSession) mvc.perform(formLogin("/employee/login")
                        .user("staff").password(Fixtures.PASSWORD))
                .andReturn().getRequest().getSession(false);
        session.setAttribute("user", manager.employee().sessionView());
        mvc.perform(get("/staff/home").session(session).param("user.userId", manager.id().toString()))
                .andExpect(status().isOk());
        assertThat(((User) session.getAttribute("user")).getUserId()).isEqualTo(employee.id());
        mvc.perform(get("/manager/home").session(session)).andExpect(status().isForbidden());
    }

    @Test
    void roleChangesReloadTheSubtypeWithoutChangingTheUserId() {
        var employee = fixtures.employee("staff");
        var admin = fixtures.admin("admin");
        administration.changeRoles(admin.actor(), employee.id(), EnumSet.of(Role.MANAGER));
        assertThat(users.findById(employee.id()).orElseThrow()).isExactlyInstanceOf(Manager.class);
        administration.changeRoles(admin.actor(), employee.id(), EnumSet.of(Role.STAFF));
        assertThat(users.findById(employee.id()).orElseThrow()).isExactlyInstanceOf(Staff.class);
    }

    @Test
    void managerWithAdministratorPermissionKeepsBothWorkspaces() throws Exception {
        var admin = fixtures.admin("admin");
        Long id = administration.create(admin.actor(), new StaffAdminService.NewStaff(
                "S-MULTI", "Multi-role manager", "multi@example.com", "Testing", Designation.MANAGEMENT,
                "multi", Fixtures.PASSWORD, EnumSet.of(Role.MANAGER, Role.ADMIN), null, false));
        assertThat(users.findById(id).orElseThrow()).isExactlyInstanceOf(Manager.class);
        assertThat(managers.findById(id).orElseThrow().getRoles())
                .containsExactlyInAnyOrder(Role.STAFF, Role.MANAGER, Role.ADMIN);
        MockHttpSession staffSession = (MockHttpSession) mvc.perform(formLogin("/employee/login")
                        .user("multi").password(Fixtures.PASSWORD))
                .andExpect(redirectedUrl("/manager/home")).andReturn().getRequest().getSession(false);
        mvc.perform(get("/manager/home").session(staffSession)).andExpect(status().isOk());
        mvc.perform(get("/staff/home").session(staffSession)).andExpect(status().isOk());
        MockHttpSession adminSession = (MockHttpSession) mvc.perform(formLogin("/admin/login")
                        .user("multi").password(Fixtures.PASSWORD))
                .andExpect(redirectedUrl("/admin/home")).andReturn().getRequest().getSession(false);
        mvc.perform(get("/admin/home").session(adminSession)).andExpect(status().isOk());
    }

    @Test
    void managerLookupUsesStaffIdAndPreservesNotFound() {
        var manager = fixtures.manager("manager");
        assertThat(managerService.getByStaffId(manager.employee().getStaffId()).getUserId()).isEqualTo(manager.id());
        assertThatThrownBy(() -> managerService.getByStaffId("missing"))
                .isInstanceOf(com.uptrail.shared.error.NotFoundException.class);
    }
}
