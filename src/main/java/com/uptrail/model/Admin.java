package com.uptrail.model;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;

/** A user who maintains accounts, routing and reference data. */
@Entity
@DiscriminatorValue("ADMIN")
public class Admin extends User {
    protected Admin() { }
}
