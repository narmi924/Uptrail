package com.uptrail.catalogue.api;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.uptrail.catalogue.domain.CategoryCode;
import com.uptrail.catalogue.service.CatalogueQueryService;

/**
 * Catalogue search for the application form. Returns at most 20 active courses; the catalogue is only a
 * shortcut, the employee can still describe any course.
 */
@RestController
@RequestMapping("/api/v1/catalogue")
public class CatalogueApiController {

    public record CatalogueCourseDto(Long id, CategoryCode category, String title, String providerName,
            BigDecimal defaultFee, String description) {
    }

    private final CatalogueQueryService catalogue;

    public CatalogueApiController(CatalogueQueryService catalogue) {
        this.catalogue = catalogue;
    }

    @GetMapping
    public List<CatalogueCourseDto> search(@RequestParam(name = "q", required = false) String query,
            @RequestParam(required = false) CategoryCode category) {
        return catalogue.search(query, category).stream()
                .map(c -> new CatalogueCourseDto(c.id(), c.category(), c.title(), c.providerName(), c.defaultFee(),
                        c.description()))
                .toList();
    }
}
