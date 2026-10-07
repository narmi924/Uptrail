package com.uptrail.application.api;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.uptrail.application.domain.ApplicationDetails;
import com.uptrail.application.domain.ApplicationStatus;
import com.uptrail.application.service.ApplicationEvaluator;
import com.uptrail.application.service.ApplicationEvaluator.Evaluation;
import com.uptrail.application.service.ApplicationPreviewService;
import com.uptrail.catalogue.domain.CategoryCode;
import com.uptrail.entitlement.domain.Session;
import com.uptrail.identity.service.UptrailUserPrincipal;

/**
 * Eligibility preview used by the application form while the employee types. The same evaluation runs
 * again, inside locks, when the form is submitted.
 */
@RestController
@RequestMapping("/api/v1/applications")
@ConditionalOnProperty(prefix = "uptrail.web", name = "rest-enabled", havingValue = "true", matchIfMissing = true)
public class ApplicationPreviewApiController {

    public record PreviewRequest(Long applicationId, CategoryCode category, Long catalogueId, String courseTitle,
            String providerName, LocalDate startDate, LocalDate endDate, Session startSession, Session endSession,
            BigDecimal courseFee, String justification, String workDissemination) {
    }

    public record IncludedDate(LocalDate date, String session, int units) {
    }

    public record ExcludedDate(LocalDate date, String reason) {
    }

    public record AnnualAllocation(int year, boolean configured, int unitsRequested, int unitsAvailableBefore,
            int unitsAvailableAfter, String daysRequested, String daysAvailableBefore, String daysAvailableAfter,
            BigDecimal feeRequested, BigDecimal budgetAvailableBefore, BigDecimal budgetAvailableAfter) {
    }

    public record Conflict(String referenceNo, String courseTitle, LocalDate startDate, LocalDate endDate,
            ApplicationStatus status) {
    }

    public record Problem(String code, String field, String message) {
    }

    public record PreviewResponse(boolean eligible, int trainingUnits, String trainingDays,
            List<IncludedDate> includedDates, List<ExcludedDate> excludedDates,
            List<AnnualAllocation> annualAllocations, List<Conflict> conflicts, String approverName,
            List<Problem> problems) {
    }

    private final ApplicationPreviewService previews;

    public ApplicationPreviewApiController(ApplicationPreviewService previews) {
        this.previews = previews;
    }

    @PostMapping("/preview")
    public PreviewResponse preview(@AuthenticationPrincipal UptrailUserPrincipal principal,
            @RequestBody PreviewRequest request) {
        ApplicationDetails details = new ApplicationDetails(request.category(), request.catalogueId(),
                request.courseTitle(), request.providerName(), request.startDate(), request.endDate(),
                request.startSession(), request.endSession(), request.courseFee(), request.justification(),
                request.workDissemination());
        Evaluation evaluation = previews.preview(principal.actor(), details, request.applicationId());
        return toResponse(evaluation);
    }

    private static PreviewResponse toResponse(Evaluation evaluation) {
        return new PreviewResponse(evaluation.eligible(), evaluation.totalUnits(),
                ApplicationEvaluator.formatDays(evaluation.totalUnits()),
                evaluation.days().stream()
                        .map(d -> new IncludedDate(d.date(), d.session().name(), d.units())).toList(),
                evaluation.excluded().stream().map(e -> new ExcludedDate(e.date(), e.reason())).toList(),
                evaluation.years().stream().map(y -> new AnnualAllocation(y.year(), y.configured(),
                        y.unitsRequested(), y.unitsAvailableBefore(), y.unitsAvailableAfter(),
                        ApplicationEvaluator.formatDays(y.unitsRequested()),
                        ApplicationEvaluator.formatDays(y.unitsAvailableBefore()),
                        ApplicationEvaluator.formatDays(y.unitsAvailableAfter()), y.feeRequested(),
                        y.budgetAvailableBefore(), y.budgetAvailableAfter())).toList(),
                evaluation.conflicts().stream().map(c -> new Conflict(c.referenceNo(), c.courseTitle(),
                        c.startDate(), c.endDate(), c.status())).toList(),
                evaluation.approverName(),
                evaluation.problems().stream()
                        .map(p -> new Problem(p.code().name(), p.field(), p.message())).toList());
    }
}
