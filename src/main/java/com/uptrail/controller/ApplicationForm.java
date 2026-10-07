package com.uptrail.controller;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;

import com.uptrail.model.ApplicationDetails;
import com.uptrail.service.ApplicationViews;
import com.uptrail.model.CategoryCode;
import com.uptrail.model.Session;

/**
 * Form backing object of the application form. It only carries what the employee may enter; applicant,
 * approver, status and balances are decided on the server.
 */
public class ApplicationForm {

    private Long catalogueId;
    private CategoryCode category;
    private String courseTitle;
    private String providerName;
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate startDate;
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate endDate;
    private Session startSession = Session.AM;
    private Session endSession = Session.PM;
    private BigDecimal courseFee;
    private String justification;
    private String workDissemination;
    private String clientRequestId;
    private Long expectedVersion;

    public static ApplicationForm from(ApplicationViews.Detail detail) {
        ApplicationForm form = new ApplicationForm();
        form.catalogueId = detail.catalogueId();
        form.category = detail.category();
        form.courseTitle = detail.courseTitle();
        form.providerName = detail.providerName();
        form.startDate = detail.startDate();
        form.endDate = detail.endDate();
        form.startSession = detail.startSession();
        form.endSession = detail.endSession();
        form.courseFee = detail.fee();
        form.justification = detail.justification();
        form.workDissemination = detail.workDissemination();
        form.expectedVersion = detail.version();
        return form;
    }

    public ApplicationDetails toDetails() {
        return new ApplicationDetails(category, catalogueId, courseTitle, providerName, startDate, endDate,
                startSession, endSession, courseFee, justification, workDissemination);
    }

    public Long getCatalogueId() {
        return catalogueId;
    }

    public void setCatalogueId(Long catalogueId) {
        this.catalogueId = catalogueId;
    }

    public CategoryCode getCategory() {
        return category;
    }

    public void setCategory(CategoryCode category) {
        this.category = category;
    }

    public String getCourseTitle() {
        return courseTitle;
    }

    public void setCourseTitle(String courseTitle) {
        this.courseTitle = courseTitle;
    }

    public String getProviderName() {
        return providerName;
    }

    public void setProviderName(String providerName) {
        this.providerName = providerName;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public void setStartDate(LocalDate startDate) {
        this.startDate = startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public void setEndDate(LocalDate endDate) {
        this.endDate = endDate;
    }

    public Session getStartSession() {
        return startSession;
    }

    public void setStartSession(Session startSession) {
        this.startSession = startSession;
    }

    public Session getEndSession() {
        return endSession;
    }

    public void setEndSession(Session endSession) {
        this.endSession = endSession;
    }

    public BigDecimal getCourseFee() {
        return courseFee;
    }

    public void setCourseFee(BigDecimal courseFee) {
        this.courseFee = courseFee;
    }

    public String getJustification() {
        return justification;
    }

    public void setJustification(String justification) {
        this.justification = justification;
    }

    public String getWorkDissemination() {
        return workDissemination;
    }

    public void setWorkDissemination(String workDissemination) {
        this.workDissemination = workDissemination;
    }

    public String getClientRequestId() {
        return clientRequestId;
    }

    public void setClientRequestId(String clientRequestId) {
        this.clientRequestId = clientRequestId;
    }

    public Long getExpectedVersion() {
        return expectedVersion;
    }

    public void setExpectedVersion(Long expectedVersion) {
        this.expectedVersion = expectedVersion;
    }
}
