package com.uptrail.catalogue.domain;

import java.math.BigDecimal;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * A commonly attended course. It is only a template: choosing it copies title, provider and fee into the
 * application, and later catalogue edits never change submitted applications.
 */
@Entity
@Table(name = "course_catalogue")
public class CatalogueCourse {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "category_code", nullable = false, length = 20)
    private CategoryCode category;

    @Column(name = "provider_id")
    private Long providerId;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "default_fee", nullable = false, precision = 12, scale = 2)
    private BigDecimal defaultFee;

    @Column(name = "description", length = 1000)
    private String description;

    @Column(name = "active", nullable = false)
    private boolean active;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected CatalogueCourse() {
    }

    public static CatalogueCourse create(CategoryCode category, Long providerId, String title, BigDecimal defaultFee,
            String description) {
        CatalogueCourse course = new CatalogueCourse();
        course.active = true;
        course.update(category, providerId, title, defaultFee, description);
        return course;
    }

    public void update(CategoryCode category, Long providerId, String title, BigDecimal defaultFee,
            String description) {
        Objects.requireNonNull(category);
        Objects.requireNonNull(defaultFee);
        if (!category.isFeePaying() && defaultFee.signum() != 0) {
            throw new IllegalArgumentException("Internal training carries no course fee");
        }
        this.category = category;
        this.providerId = providerId;
        this.title = Objects.requireNonNull(title);
        this.defaultFee = defaultFee;
        this.description = description;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public Long getId() {
        return id;
    }

    public CategoryCode getCategory() {
        return category;
    }

    public Long getProviderId() {
        return providerId;
    }

    public String getTitle() {
        return title;
    }

    public BigDecimal getDefaultFee() {
        return defaultFee;
    }

    public String getDescription() {
        return description;
    }

    public boolean isActive() {
        return active;
    }

    public long getVersion() {
        return version;
    }
}
