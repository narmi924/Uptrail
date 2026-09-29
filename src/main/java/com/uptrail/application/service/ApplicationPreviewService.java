package com.uptrail.application.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.uptrail.application.domain.ApplicationDetails;
import com.uptrail.application.domain.CourseApplication;
import com.uptrail.application.repository.CourseApplicationRepository;
import com.uptrail.identity.domain.Actor;
import com.uptrail.organisation.service.AccessScopePolicy;
import com.uptrail.shared.error.BusinessException;
import com.uptrail.shared.error.ErrorCode;
import com.uptrail.shared.error.NotFoundException;

/**
 * Read-only eligibility check for the application form. It writes nothing, reserves nothing and sends
 * nothing; the submission repeats every check inside its own locks.
 */
@Service
@Transactional(readOnly = true)
public class ApplicationPreviewService {

    private final ApplicationEvaluator evaluator;
    private final CourseApplicationRepository applications;
    private final AccessScopePolicy scope;

    public ApplicationPreviewService(ApplicationEvaluator evaluator, CourseApplicationRepository applications,
            AccessScopePolicy scope) {
        this.evaluator = evaluator;
        this.applications = applications;
        this.scope = scope;
    }

    public ApplicationEvaluator.Evaluation preview(Actor actor, ApplicationDetails details, Long editedApplicationId) {
        ApplicationCommandService.requireApplicant(actor);
        if (editedApplicationId != null) {
            CourseApplication application = applications.findById(editedApplicationId)
                    .orElseThrow(NotFoundException::new);
            scope.requireOwner(actor, application.getEmployeeId());
            if (!application.getStatus().isPending()) {
                throw new BusinessException(ErrorCode.INVALID_STATE,
                        "Only applications waiting for a decision can be edited.");
            }
        }
        return evaluator.evaluate(actor.employeeId(), details, editedApplicationId);
    }
}
