package com.uptrail.model;

public enum LedgerEntryType {

    /** A submitted application reserves days and budget while it waits for a decision. */
    RESERVE,

    /** An update replaces the previous reservation with the new one (net change per year). */
    UPDATE_RESERVATION,

    /** Approval converts the reservation into an approved commitment. */
    COMMIT,

    /** Rejection, deletion or cancellation gives the reserved or committed amounts back. */
    RELEASE,

    /** Registration of a reimbursed claim; it never touches reserved or committed amounts. */
    REIMBURSE
}
