package com.uptrail.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.uptrail.model.AggregateType;
import com.uptrail.model.CatalogueCourse;
import com.uptrail.model.CategoryCode;
import com.uptrail.model.CourseCategory;
import com.uptrail.model.TrainingProvider;
import com.uptrail.repo.CatalogueCourseRepo;
import com.uptrail.repo.CourseCategoryRepo;
import com.uptrail.repo.TrainingProviderRepo;
import com.uptrail.model.User;
import com.uptrail.shared.error.NotFoundException;
import com.uptrail.shared.tx.WriteTransaction;

/**
 * Maintenance of category texts, training providers and the course catalogue. The three category codes are
 * fixed. Catalogue edits never touch submitted applications, which keep their own snapshot.
 */
@Service
public class CatalogueAdminService {

    public record CourseRow(Long id, CategoryCode category, String categoryName, Long providerId, String providerName,
            String title, BigDecimal defaultFee, String description, boolean active) {
    }

    public record CategoryRow(CategoryCode code, String displayName, String description) {
    }

    public record ProviderRow(Long id, String name, boolean active) {
    }

    public record CourseInput(CategoryCode category, Long providerId, String title, BigDecimal defaultFee,
            String description) {
    }

    private final CourseCategoryRepo categories;
    private final TrainingProviderRepo providers;
    private final CatalogueCourseRepo courses;
    private final AuditService audit;

    public CatalogueAdminService(CourseCategoryRepo categories, TrainingProviderRepo providers,
            CatalogueCourseRepo courses, AuditService audit) {
        this.categories = categories;
        this.providers = providers;
        this.courses = courses;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<CategoryRow> categories() {
        return categories.findAll().stream().sorted((a, b) -> a.getCode().compareTo(b.getCode()))
                .map(c -> new CategoryRow(c.getCode(), c.getDisplayName(), c.getDescription())).toList();
    }

    @Transactional(readOnly = true)
    public List<ProviderRow> providers() {
        return providers.findAll().stream()
                .sorted((a, b) -> a.getName().compareToIgnoreCase(b.getName()))
                .map(p -> new ProviderRow(p.getId(), p.getName(), p.isActive())).toList();
    }

    @Transactional(readOnly = true)
    public List<CourseRow> courses() {
        Map<Long, String> names = providerNames();
        Map<CategoryCode, String> categoryNames = new java.util.EnumMap<>(CategoryCode.class);
        categories.findAll().forEach(c -> categoryNames.put(c.getCode(), c.getDisplayName()));
        return courses.findAllByOrderByCategoryAscTitleAsc().stream()
                .map(c -> new CourseRow(c.getId(), c.getCategory(), categoryNames.get(c.getCategory()), c.getProviderId(),
                        c.getProviderId() == null ? null : names.get(c.getProviderId()), c.getTitle(),
                        c.getDefaultFee(), c.getDescription(), c.isActive()))
                .toList();
    }

    @WriteTransaction
    public void updateCategory(User admin, CategoryCode code, String displayName, String description) {
        AdminValidation v = new AdminValidation();
        String name = v.required("displayName", displayName, 80, "display name");
        String text = v.optional("description", description, 400);
        v.throwIfAny();
        CourseCategory category = categories.findById(code).orElseThrow(NotFoundException::new);
        category.rename(name, text);
        record(admin, "CATEGORY", code.name(), "CATEGORY_UPDATED", Map.of("name", name));
    }

    @WriteTransaction
    public Long addProvider(User admin, String name) {
        AdminValidation v = new AdminValidation();
        String value = v.required("name", name, 160, "provider name");
        v.throwIfAny();
        TrainingProvider provider = providers.save(TrainingProvider.create(value));
        record(admin, "PROVIDER", provider.getId().toString(), "PROVIDER_CREATED", Map.of("name", value));
        return provider.getId();
    }

    @WriteTransaction
    public void updateProvider(User admin, Long id, String name, boolean active) {
        AdminValidation v = new AdminValidation();
        String value = v.required("name", name, 160, "provider name");
        v.throwIfAny();
        TrainingProvider provider = providers.findById(id).orElseThrow(NotFoundException::new);
        provider.rename(value);
        provider.setActive(active);
        record(admin, "PROVIDER", id.toString(), "PROVIDER_UPDATED", Map.of("name", value, "active", active));
    }

    @WriteTransaction
    public Long addCourse(User admin, CourseInput input) {
        CourseInput valid = validate(input);
        CatalogueCourse course = courses.save(CatalogueCourse.create(valid.category(), valid.providerId(),
                valid.title(), valid.defaultFee(), valid.description()));
        record(admin, "COURSE", course.getId().toString(), "COURSE_CREATED",
                Map.of("title", valid.title(), "fee", valid.defaultFee()));
        return course.getId();
    }

    @WriteTransaction
    public void updateCourse(User admin, Long id, CourseInput input, boolean active) {
        CourseInput valid = validate(input);
        CatalogueCourse course = courses.findById(id).orElseThrow(NotFoundException::new);
        course.update(valid.category(), valid.providerId(), valid.title(), valid.defaultFee(), valid.description());
        course.setActive(active);
        record(admin, "COURSE", id.toString(), "COURSE_UPDATED",
                Map.of("title", valid.title(), "fee", valid.defaultFee(), "active", active));
    }

    private CourseInput validate(CourseInput input) {
        AdminValidation v = new AdminValidation();
        if (input.category() == null) {
            v.reject("category", "Choose a category.");
        }
        String title = v.required("title", input.title(), 200, "course title");
        BigDecimal fee = v.money("defaultFee", input.defaultFee(), true);
        if (input.category() == CategoryCode.INTERNAL && fee != null && fee.signum() != 0) {
            v.reject("defaultFee", "Internal training carries no course fee.");
        }
        if (input.category() != null && input.category().isFeePaying() && fee != null && fee.signum() == 0) {
            v.reject("defaultFee", "External courses and certifications need a fee.");
        }
        if (input.providerId() != null && providers.findById(input.providerId()).isEmpty()) {
            v.reject("providerId", "Choose an existing provider.");
        }
        String description = v.optional("description", input.description(), 1000);
        v.throwIfAny();
        return new CourseInput(input.category(), input.providerId(), title, fee, description);
    }

    private Map<Long, String> providerNames() {
        Map<Long, String> names = new java.util.HashMap<>();
        providers.findAll().forEach(p -> names.put(p.getId(), p.getName()));
        return names;
    }

    private void record(User admin, String kind, String key, String eventType, Map<String, ?> snapshot) {
        audit.record(new AuditService.Change(AggregateType.CATALOGUE, kind + ":" + key, eventType, admin.getUserId(),
                null, null, null, snapshot));
    }
}
