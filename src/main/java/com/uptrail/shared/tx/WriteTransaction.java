package com.uptrail.shared.tx;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.core.annotation.AliasFor;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Marks a business write. Every write runs under READ COMMITTED and must follow the lock order
 * employee -> training accounts (ascending year) -> target record, so that concurrent writes for the
 * same employee are serialised and fresh balances are read inside the locks.
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Transactional(isolation = Isolation.READ_COMMITTED)
public @interface WriteTransaction {

    @AliasFor(annotation = Transactional.class, attribute = "noRollbackFor")
    Class<? extends Throwable>[] noRollbackFor() default {};
}
