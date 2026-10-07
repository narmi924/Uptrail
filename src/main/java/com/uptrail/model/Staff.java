package com.uptrail.model;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;

/** A user who can submit and manage their own training applications. */
@Entity
@DiscriminatorValue("STAFF")
public class Staff extends User {
    protected Staff() { }
}
