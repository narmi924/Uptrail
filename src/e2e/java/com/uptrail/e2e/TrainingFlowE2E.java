package com.uptrail.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Download;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.assertions.LocatorAssertions;
import com.microsoft.playwright.assertions.PlaywrightAssertions;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.FilePayload;
import com.microsoft.playwright.options.Media;
import com.uptrail.catalogue.service.HolidayCalendarService;
import com.uptrail.shared.time.BusinessClock;
import com.uptrail.support.MySqlTestDatabase;

/**
 * Browser end-to-end flow over the sample organisation on a real server port: an employee applies
 * with the live eligibility check, the manager approves, the employee claims the fee of a completed course
 * with two uploaded documents, the manager approves the claim, the administrator registers the
 * reimbursement, and the manager uses the training calendar and a CSV report. Screenshots go to
 * {@code target/e2e/}.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "uptrail.sample-data.enabled=true",
        "uptrail.mail.worker.enabled=false",
        "uptrail.documents.root=target/e2e-documents",
        "uptrail.documents.sweep.enabled=false",
        "uptrail.mail.capture-dir=target/e2e-mail"
})
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class TrainingFlowE2E {

    private static final Path SCREENS = Paths.get("target", "e2e");
    private static Playwright playwright;
    private static Browser browser;

    @LocalServerPort
    private int port;

    @Value("${uptrail.sample-data.password}")
    private String password;

    @Autowired
    private HolidayCalendarService holidays;

    @Autowired
    private BusinessClock clock;

    @Autowired
    private JdbcTemplate jdbc;

    private BrowserContext context;
    private Page page;
    private String testName;
    private final List<String> pageErrors = new ArrayList<>();

    @DynamicPropertySource
    static void mysql(DynamicPropertyRegistry registry) {
        MySqlTestDatabase database = MySqlTestDatabase.get();
        registry.add("spring.datasource.url", database::jdbcUrl);
        registry.add("spring.datasource.username", database::username);
        registry.add("spring.datasource.password", database::password);
    }

    @BeforeAll
    static void launchBrowser() throws Exception {
        Files.createDirectories(SCREENS);
        playwright = Playwright.create();
        browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true));
    }

    @AfterAll
    static void closeBrowser() {
        if (browser != null) {
            browser.close();
        }
        if (playwright != null) {
            playwright.close();
        }
    }

    @BeforeEach
    void openContext(TestInfo info) {
        testName = info.getTestMethod().map(m -> m.getName()).orElse("test");
        context = browser.newContext(new Browser.NewContextOptions().setViewportSize(1366, 900).setLocale("en-SG"));
        page = context.newPage();
        page.onDialog(dialog -> dialog.accept());
        page.onPageError(error -> pageErrors.add(page.url() + ": " + error));
    }

    @AfterEach
    void closeContext() {
        screenshot("end");
        context.close();
        assertThat(pageErrors).as("JavaScript errors").isEmpty();
    }

    private static LocatorAssertions expect(Locator locator) {
        return PlaywrightAssertions.assertThat(locator);
    }

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    private void screenshot(String step) {
        page.screenshot(new Page.ScreenshotOptions().setPath(SCREENS.resolve(testName + "-" + step + ".png"))
                .setFullPage(true));
    }

    private void signIn(String username) {
        page.navigate(url("/login"));
        page.locator("#username").fill(username);
        page.locator("#password").fill(password);
        page.locator("button[type=submit]").click();
        page.waitForURL(Pattern.compile(".*/(employee|manager)/.*"));
    }

    private void signInAdmin(String username) {
        page.navigate(url("/admin/login"));
        page.locator("#username").fill(username);
        page.locator("#password").fill(password);
        page.locator("button[type=submit]").click();
        page.waitForURL(Pattern.compile(".*/admin/dashboard"));
    }

    /** A Wednesday at least three weeks ahead that is not a public holiday. */
    private LocalDate courseDay() {
        LocalDate day = clock.today().plusWeeks(3);
        while (day.getDayOfWeek() != DayOfWeek.WEDNESDAY || !holidays.holidaysBetween(day, day).isEmpty()) {
            day = day.plusDays(1);
        }
        return day;
    }

    private static byte[] pdf(String text) {
        return ("%PDF-1.4\n% " + text + " (end-to-end test file)\n").getBytes(StandardCharsets.US_ASCII);
    }

    @Test
    @Order(1)
    void anEmployeeAppliesWithTheLiveEligibilityCheck() {
        signIn("farid");
        page.navigate(url("/employee/applications/new"));
        page.locator("#catalogue-search").fill("Cloud");
        page.locator("#catalogue-results button[data-index]").first().click();
        expect(page.locator("#courseTitle")).hasValue("Cloud Architecture Foundations");
        LocalDate day = courseDay();
        page.locator("#startDate").fill(day.toString());
        page.locator("#endDate").fill(day.toString());
        page.locator("#justification").fill("Needed for the platform migration next quarter.");
        expect(page.locator("#eligibility")).containsText("Eligible to submit",
                new LocatorAssertions.ContainsTextOptions().setTimeout(10_000));
        screenshot("form");

        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Submit application")).click();

        page.waitForURL(Pattern.compile(".*/employee/applications/\\d+"));
        expect(page.locator("h1")).hasText("Cloud Architecture Foundations");
        expect(page.locator(".ut-page-header")).containsText("Applied");
    }

    @Test
    @Order(2)
    void theManagerApprovesTheApplication() {
        signIn("daniel");
        page.navigate(url("/manager/approvals"));
        Locator group = page.locator(".ut-card").filter(new Locator.FilterOptions().setHasText("Farid Ismail"));
        group.locator("tr").filter(new Locator.FilterOptions().setHasText("Cloud Architecture Foundations"))
                .getByRole(AriaRole.LINK, new Locator.GetByRoleOptions().setName("Review")).click();
        page.waitForURL(Pattern.compile(".*/manager/applications/\\d+"));
        page.locator("#decision-approve").check();
        page.locator("#reason").fill("Supports the migration roadmap.");
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Record decision")).click();

        page.waitForURL(Pattern.compile(".*/manager/approvals"));
        expect(page.locator("[role=status]")).containsText("Application approved");
    }

    @Test
    @Order(3)
    void theEmployeeClaimsTheFeeOfACompletedCourse() {
        signIn("farid");
        page.navigate(url("/employee/claims"));
        page.locator("tr").filter(new Locator.FilterOptions().setHasText("Test Automation Essentials"))
                .getByRole(AriaRole.LINK, new Locator.GetByRoleOptions().setName("Claim fee")).click();
        page.waitForURL(Pattern.compile(".*/employee/claims/new.*"));
        page.locator("#amount").fill("400.00");
        page.locator("#paidByEmployee").check();
        page.locator("#receipt").setInputFiles(new FilePayload("receipt.pdf", "application/pdf", pdf("receipt")));
        page.locator("#certificate").setInputFiles(new FilePayload("certificate.pdf", "application/pdf",
                pdf("certificate")));
        screenshot("claim-form");
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Submit claim")).click();

        page.waitForURL(Pattern.compile(".*/employee/claims/\\d+"));
        expect(page.locator("[role=status]")).containsText("Claim submitted");
        expect(page.locator(".ut-page-header")).containsText("Submitted");
        expect(page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName("receipt.pdf"))).isVisible();
    }

    @Test
    @Order(4)
    void theManagerApprovesTheClaimAfterDownloadingTheReceipt() throws Exception {
        signIn("daniel");
        page.navigate(url("/manager/claims"));
        page.locator("tr").filter(new Locator.FilterOptions().setHasText("Farid Ismail"))
                .getByRole(AriaRole.LINK, new Locator.GetByRoleOptions().setName("Review")).click();
        page.waitForURL(Pattern.compile(".*/manager/claims/\\d+"));
        Download download = page.waitForDownload(() -> page.getByRole(AriaRole.LINK,
                new Page.GetByRoleOptions().setName("receipt.pdf")).click());
        assertThat(download.suggestedFilename()).isEqualTo("receipt.pdf");
        assertThat(Files.readString(download.path(), StandardCharsets.US_ASCII)).startsWith("%PDF-");
        page.locator("#decision-approve").check();
        page.locator("#reason").fill("Receipt and certificate match the course.");
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Record decision")).click();

        page.waitForURL(Pattern.compile(".*/manager/claims"));
        expect(page.locator("[role=status]")).containsText("Claim approved");
    }

    @Test
    @Order(5)
    void theAdministratorRegistersTheReimbursement() {
        signInAdmin("alex");
        page.navigate(url("/admin/reimbursements"));
        page.locator("tr").filter(new Locator.FilterOptions().setHasText("Farid Ismail"))
                .getByRole(AriaRole.LINK, new Locator.GetByRoleOptions().setName("Check and register")).click();
        page.waitForURL(Pattern.compile(".*/admin/claims/\\d+"));
        page.locator("#register button[type=submit]").click();

        page.waitForURL(Pattern.compile(".*/admin/reimbursements"));
        expect(page.locator("[role=status]")).containsText("SIM-");
        Map<String, Object> ledger = jdbc.queryForMap("SELECT COUNT(*) AS n, SUM(reimbursed_amount_delta) AS total "
                + "FROM training_ledger l JOIN course_claim c ON c.id = l.claim_id JOIN course_application a "
                + "ON a.id = c.application_id WHERE a.course_title = 'Test Automation Essentials'");
        assertThat(((Number) ledger.get("n")).intValue()).isEqualTo(1);
        assertThat(ledger.get("total").toString()).isEqualTo("400.00");
    }

    @Test
    @Order(6)
    void theManagerUsesTheCalendarAndDownloadsAReport() throws Exception {
        signIn("daniel");
        page.navigate(url("/calendar?month=" + courseDay().toString().substring(0, 7)));
        expect(page.locator("#calendar-count")).containsText("course");
        expect(page.locator("#calendar")).containsText("Farid Ismail");
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("List")).click();
        expect(page.locator("#calendar table")).containsText("Cloud Architecture Foundations");
        screenshot("calendar");

        LocalDate day = courseDay();
        page.navigate(url("/manager/reports?from=" + day + "&to=" + day));
        Download csv = page.waitForDownload(() -> page.getByRole(AriaRole.LINK,
                new Page.GetByRoleOptions().setName("Download CSV")).click());
        String content = Files.readString(csv.path(), StandardCharsets.UTF_8);
        assertThat(content).startsWith("﻿Reference,Staff member").contains("Cloud Architecture Foundations");
    }

    @Test
    @Order(7)
    void pagesFitAPhoneScreenAndReportsPrintWithoutNavigation() {
        page.setViewportSize(390, 844);
        signIn("daniel");
        for (String path : List.of("/employee/dashboard", "/employee/applications", "/employee/claims",
                "/employee/entitlement", "/manager/approvals", "/manager/claims", "/manager/reports", "/calendar")) {
            page.navigate(url(path));
            int width = ((Number) page.evaluate("document.documentElement.scrollWidth")).intValue();
            assertThat(width).as("page width of " + path).isLessThanOrEqualTo(390);
        }
        screenshot("phone-calendar");

        page.setViewportSize(1366, 900);
        page.navigate(url("/manager/reports?tab=budget"));
        page.emulateMedia(new Page.EmulateMediaOptions().setMedia(Media.PRINT));
        expect(page.locator(".ut-sidenav")).isHidden();
        expect(page.locator(".ut-filters")).isHidden();
        expect(page.locator(".ut-table")).isVisible();
        screenshot("print-budget");
    }
}
