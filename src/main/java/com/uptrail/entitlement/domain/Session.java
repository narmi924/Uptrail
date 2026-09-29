package com.uptrail.entitlement.domain;

/**
 * Half of a working day. A course runs from the start session of its first day to the end session of its
 * last day; external courses and certifications always run AM to PM.
 */
public enum Session {
    AM,
    PM
}
