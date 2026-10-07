package com.uptrail.model;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;

/** A Staff user with approval and team-history capabilities. */
@Entity
@DiscriminatorValue("MANAGER")
public class Manager extends Staff {
    protected Manager() { }
}
