package com.uptrail.controller;

import java.util.EnumSet;
import java.util.Set;

import com.uptrail.service.StaffAdminService;
import com.uptrail.model.Role;
import com.uptrail.model.Designation;

/** Form backing object for creating a staff member and login account. */
public class StaffForm {

    private String staffId;
    private String name;
    private String email;
    private String department;
    private Designation designation = Designation.PROFESSIONAL;
    private String username;
    private String password;
    private Set<Role> roles = EnumSet.of(Role.STAFF);
    private Long approverId;
    private boolean openCurrentYearAccount = true;

    StaffAdminService.NewStaff toCommand() {
        return new StaffAdminService.NewStaff(staffId, name, email, department, designation, username, password,
                roles, approverId, openCurrentYearAccount);
    }

    public String getStaffId() {
        return staffId;
    }

    public void setStaffId(String staffId) {
        this.staffId = staffId;
    }

    public String getName() {
        return name;
    }

    public void setFullName(String name) {
        this.name = name;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getDepartment() {
        return department;
    }

    public void setDepartment(String department) {
        this.department = department;
    }

    public Designation getDesignation() {
        return designation;
    }

    public void setDesignation(Designation designation) {
        this.designation = designation;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public Set<Role> getRoles() {
        return roles;
    }

    public void setRoles(Set<Role> roles) {
        this.roles = roles;
    }

    public Long getApproverId() {
        return approverId;
    }

    public void setApproverId(Long approverId) {
        this.approverId = approverId;
    }

    public boolean isOpenCurrentYearAccount() {
        return openCurrentYearAccount;
    }

    public void setOpenCurrentYearAccount(boolean openCurrentYearAccount) {
        this.openCurrentYearAccount = openCurrentYearAccount;
    }
}
