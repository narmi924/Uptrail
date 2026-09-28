package com.uptrail.reporting.web;

import java.time.LocalDate;
import java.util.List;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.uptrail.catalogue.domain.CategoryCode;
import com.uptrail.catalogue.service.CatalogueQueryService;
import com.uptrail.identity.service.UptrailUserPrincipal;
import com.uptrail.reporting.service.ReportQueryService;
import com.uptrail.reporting.service.ReportQueryService.BudgetFilter;
import com.uptrail.reporting.service.ReportQueryService.TrainingFilter;
import com.uptrail.shared.csv.CsvWriter;
import com.uptrail.shared.error.BusinessException;
import com.uptrail.shared.time.BusinessClock;
import com.uptrail.shared.web.Paging;

/**
 * Manager reports: training participation by period and category, and budget use with fee claims by year.
 * The CSV downloads apply exactly the same filters and scope as the page.
 */
@Controller
public class ManagerReportController {

    private final ReportQueryService reports;
    private final CatalogueQueryService catalogue;
    private final BusinessClock clock;

    public ManagerReportController(ReportQueryService reports, CatalogueQueryService catalogue, BusinessClock clock) {
        this.reports = reports;
        this.catalogue = catalogue;
        this.clock = clock;
    }

    @GetMapping("/manager/reports")
    public String page(@AuthenticationPrincipal UptrailUserPrincipal principal,
            @RequestParam(defaultValue = "training") String tab,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) CategoryCode category, @RequestParam(required = false) Long employeeId,
            @RequestParam(required = false) Integer year, @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size, HttpServletRequest request, Model model) {
        boolean budgetTab = "budget".equals(tab);
        model.addAttribute("tab", budgetTab ? "budget" : "training");
        model.addAttribute("team", reports.team(principal.actor()));
        model.addAttribute("categories", catalogue.categories());
        model.addAttribute("employeeId", employeeId);
        if (budgetTab) {
            BudgetFilter filter = budgetFilter(year, employeeId);
            model.addAttribute("budget", reports.budget(principal.actor(), filter));
            model.addAttribute("year", filter.year());
            model.addAttribute("years", List.of(clock.currentYear() - 1, clock.currentYear(), clock.currentYear() + 1));
        } else {
            TrainingFilter filter = trainingFilter(from, to, category, employeeId);
            try {
                ReportQueryService.TrainingReport report = reports.training(principal.actor(), filter);
                model.addAttribute("training", report);
                model.addAttribute("view", Paging.view(Paging.slice(report.rows(), Paging.request(page, size)), request));
            } catch (BusinessException e) {
                if (e instanceof com.uptrail.shared.error.NotFoundException) {
                    throw e;
                }
                model.addAttribute("flashError", e.getMessage());
            }
            model.addAttribute("filter", filter);
        }
        return "manager/reports";
    }

    @GetMapping("/manager/reports/training.csv")
    public ResponseEntity<byte[]> trainingCsv(@AuthenticationPrincipal UptrailUserPrincipal principal,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) CategoryCode category, @RequestParam(required = false) Long employeeId) {
        TrainingFilter filter = trainingFilter(from, to, category, employeeId);
        ReportQueryService.TrainingReport report = reports.training(principal.actor(), filter);
        CsvWriter csv = new CsvWriter().header("Reference", "Staff member", "Department", "Course", "Category",
                "Provider", "Start date", "End date", "Status", "Training days in period", "Course fee (SGD)");
        for (ReportQueryService.TrainingRow row : report.rows()) {
            csv.text(row.referenceNo()).text(row.employeeName()).text(row.department()).text(row.courseTitle())
                    .text(row.categoryName()).text(row.providerName()).text(row.startDate().toString())
                    .text(row.endDate().toString()).text(row.status().label()).days(row.unitsInPeriod())
                    .number(row.fee()).endRow();
        }
        return download(csv, "training-participation-" + filter.from() + "-to-" + filter.to() + ".csv");
    }

    @GetMapping("/manager/reports/budget.csv")
    public ResponseEntity<byte[]> budgetCsv(@AuthenticationPrincipal UptrailUserPrincipal principal,
            @RequestParam(required = false) Integer year, @RequestParam(required = false) Long employeeId) {
        BudgetFilter filter = budgetFilter(year, employeeId);
        ReportQueryService.BudgetReport report = reports.budget(principal.actor(), filter);
        CsvWriter csv = new CsvWriter().header("Staff no.", "Staff member", "Department", "Year", "Entitled days",
                "Pending days", "Approved days", "Available days", "Completed days", "Budget (SGD)",
                "Pending reservations (SGD)", "Approved commitments (SGD)", "Available budget (SGD)",
                "Claims submitted (SGD)", "Claims approved, not yet reimbursed (SGD)", "Reimbursed (SGD)");
        for (ReportQueryService.BudgetRow row : report.rows()) {
            var b = row.balance();
            csv.text(row.staffNo()).text(row.employeeName()).text(row.department()).number(filter.year());
            if (b.configured()) {
                csv.days(b.entitledUnits()).days(b.reservedUnits()).days(b.committedUnits()).days(b.availableUnits())
                        .days(row.completedUnits()).number(b.budget()).number(b.reservedAmount())
                        .number(b.committedAmount()).number(b.availableBudget());
            } else {
                csv.text("Not configured").text("").text("").text("").days(row.completedUnits()).text("").text("")
                        .text("").text("");
            }
            csv.number(row.claimsSubmitted()).number(row.claimsApproved()).number(b.reimbursedAmount()).endRow();
        }
        return download(csv, "budget-and-claims-" + filter.year() + ".csv");
    }

    private TrainingFilter trainingFilter(LocalDate from, LocalDate to, CategoryCode category, Long employeeId) {
        int year = clock.currentYear();
        return new TrainingFilter(from == null ? LocalDate.of(year, 1, 1) : from,
                to == null ? LocalDate.of(year, 12, 31) : to, category, employeeId);
    }

    private BudgetFilter budgetFilter(Integer year, Long employeeId) {
        int current = clock.currentYear();
        int selected = year == null || year < current - 1 || year > current + 1 ? current : year;
        return new BudgetFilter(selected, employeeId);
    }

    private static ResponseEntity<byte[]> download(CsvWriter csv, String filename) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(filename).build()
                        .toString())
                .contentType(new MediaType("text", "csv", java.nio.charset.StandardCharsets.UTF_8))
                .body(csv.toBytes());
    }
}
