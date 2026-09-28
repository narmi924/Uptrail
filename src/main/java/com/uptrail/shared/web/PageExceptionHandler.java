package com.uptrail.shared.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.http.HttpStatus;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.ModelAndView;

import com.uptrail.shared.error.BusinessException;
import com.uptrail.shared.error.ErrorCode;
import com.uptrail.shared.error.NotFoundException;

/**
 * Fallback error pages for page controllers. Controllers handle expected form errors themselves and
 * re-render the form; whatever reaches this class is shown on a dedicated error page.
 */
@ControllerAdvice(annotations = Controller.class)
@Order(Ordered.LOWEST_PRECEDENCE)
public class PageExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(PageExceptionHandler.class);

    @ExceptionHandler(NotFoundException.class)
    ModelAndView notFound(NotFoundException e) {
        return page("error/404", HttpStatus.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler(BusinessException.class)
    ModelAndView business(BusinessException e) {
        HttpStatus status = HttpStatus.valueOf(e.code().httpStatus());
        String view = e.code() == ErrorCode.STALE_VERSION || e.code().httpStatus() == 409 ? "error/409" : "error/business";
        return page(view, status, e.getMessage());
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    ModelAndView stale(ObjectOptimisticLockingFailureException e) {
        return page("error/409", HttpStatus.CONFLICT,
                "This record changed since you opened it. Review the current version and try again.");
    }

    @ExceptionHandler({PessimisticLockingFailureException.class, CannotAcquireLockException.class,
            QueryTimeoutException.class})
    ModelAndView contention(Exception e) {
        log.warn("Lock contention: {}", e.getMessage());
        return page("error/503", HttpStatus.SERVICE_UNAVAILABLE,
                "Another change to the same records is in progress. Please try again in a moment.");
    }

    @ExceptionHandler({MethodArgumentTypeMismatchException.class, MissingServletRequestParameterException.class})
    ModelAndView badRequest(Exception e) {
        return page("error/400", HttpStatus.BAD_REQUEST, "The address or form contained an invalid value.");
    }

    @ExceptionHandler(Exception.class)
    ModelAndView unexpected(Exception e) {
        log.error("Unexpected page error", e);
        return page("error/500", HttpStatus.INTERNAL_SERVER_ERROR,
                "Something went wrong on the server. Quote the reference below if you report it.");
    }

    private static ModelAndView page(String view, HttpStatus status, String message) {
        ModelAndView mav = new ModelAndView(view, status);
        mav.addObject("status", status.value());
        mav.addObject("message", message);
        mav.addObject("correlationId", CorrelationId.current());
        return mav;
    }
}
