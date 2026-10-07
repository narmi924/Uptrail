package com.uptrail.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.uptrail.model.ApplicationDetails;
import com.uptrail.model.CourseApplication;
import com.uptrail.repo.CourseApplicationRepo;
import com.uptrail.model.User;
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
    private final CourseApplicationRepo applications;
    private final AccessScopePolicy scope;

    public ApplicationPreviewService(ApplicationEvaluator evaluator, CourseApplicationRepo applications,
            AccessScopePolicy scope) {
        this.evaluator = evaluator;
        this.applications = applications;
        this.scope = scope;
    }

    public ApplicationEvaluator.Evaluation preview(User actor, ApplicationDetails details, Long editedApplicationId) {
        CourseApplicationService.requireApplicant(actor);
        if (editedApplicationId != null) {
            CourseApplication application = applications.findById(editedApplicationId)
                    .orElseThrow(NotFoundException::new);
            scope.requireOwner(actor, application.getApplicantId());
            if (!application.getStatus().isPending()) {
                throw new BusinessException(ErrorCode.INVALID_STATE,
                        "Only applications waiting for a decision can be edited.");
            }
        }
        return evaluator.evaluate(actor.getUserId(), details, editedApplicationId);
    }
}
