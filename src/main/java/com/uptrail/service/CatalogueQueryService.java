package com.uptrail.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.uptrail.model.CatalogueCourse;
import com.uptrail.model.CategoryCode;
import com.uptrail.model.CourseCategory;
import com.uptrail.model.TrainingProvider;
import com.uptrail.repo.CatalogueCourseRepo;
import com.uptrail.repo.CourseCategoryRepo;
import com.uptrail.repo.TrainingProviderRepo;

/**
 * Read access to categories, providers and the course catalogue for forms and the catalogue search API.
 */
@Service
@Transactional(readOnly = true)
public class CatalogueQueryService {

    public static final int MAX_RESULTS = 20;

    public record CatalogueItem(Long id, CategoryCode category, String title, String providerName,
            BigDecimal defaultFee, String description) {
    }

    public record CategoryView(CategoryCode code, String displayName, String description) {
    }

    private final CatalogueCourseRepo courses;
    private final TrainingProviderRepo providers;
    private final CourseCategoryRepo categories;

    public CatalogueQueryService(CatalogueCourseRepo courses, TrainingProviderRepo providers,
            CourseCategoryRepo categories) {
        this.courses = courses;
        this.providers = providers;
        this.categories = categories;
    }

    public List<CatalogueItem> search(String query, CategoryCode category) {
        String normalised = query == null ? "" : query.strip().toLowerCase(Locale.ROOT);
        List<CatalogueCourse> found = courses.searchActive(normalised, category, PageRequest.of(0, MAX_RESULTS));
        Map<Long, String> providerNames = providerNames();
        return found.stream().map(c -> new CatalogueItem(c.getId(), c.getCategory(), c.getTitle(),
                c.getProviderId() == null ? null : providerNames.get(c.getProviderId()), c.getDefaultFee(),
                c.getDescription())).toList();
    }

    public Optional<CatalogueItem> activeCourse(Long id) {
        return courses.findById(id).filter(CatalogueCourse::isActive).map(c -> new CatalogueItem(c.getId(),
                c.getCategory(), c.getTitle(), c.getProviderId() == null ? null
                        : providers.findById(c.getProviderId()).map(TrainingProvider::getName).orElse(null),
                c.getDefaultFee(), c.getDescription()));
    }

    public List<CategoryView> categories() {
        return categories.findAll().stream()
                .sorted((a, b) -> a.getCode().compareTo(b.getCode()))
                .map(c -> new CategoryView(c.getCode(), c.getDisplayName(), c.getDescription())).toList();
    }

    public Map<CategoryCode, String> categoryNames() {
        return categories.findAll().stream()
                .collect(Collectors.toMap(CourseCategory::getCode, CourseCategory::getDisplayName));
    }

    public List<String> activeProviderNames() {
        return providers.findAll().stream().filter(TrainingProvider::isActive).map(TrainingProvider::getName)
                .sorted(String.CASE_INSENSITIVE_ORDER).toList();
    }

    private Map<Long, String> providerNames() {
        return providers.findAll().stream()
                .collect(Collectors.toMap(TrainingProvider::getId, TrainingProvider::getName, (a, b) -> a));
    }
}
