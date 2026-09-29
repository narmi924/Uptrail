package com.uptrail.catalogue.domain;

import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Display name and description of one of the three fixed categories.
 */
@Entity
@Table(name = "course_category")
public class CourseCategory {

    @Id
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "code", nullable = false, length = 20)
    private CategoryCode code;

    @Column(name = "display_name", nullable = false, length = 80)
    private String displayName;

    @Column(name = "description", length = 400)
    private String description;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected CourseCategory() {
    }

    public void rename(String displayName, String description) {
        this.displayName = Objects.requireNonNull(displayName);
        this.description = description;
    }

    public CategoryCode getCode() {
        return code;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }

    public long getVersion() {
        return version;
    }
}
