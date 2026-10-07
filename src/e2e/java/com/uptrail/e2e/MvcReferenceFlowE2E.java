package com.uptrail.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.options.AriaRole;
import com.uptrail.service.HolidayCalendarService;
import com.uptrail.shared.time.BusinessClock;
import com.uptrail.support.MySqlTestDatabase;

@ActiveProfiles("mvc-reference")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "uptrail.sample-data.enabled=true",
        "uptrail.mail.worker.enabled=false",
        "uptrail.documents.root=target/e2e-documents",
        "uptrail.documents.sweep.enabled=false",
        "uptrail.mail.capture-dir=target/e2e-mail"
})
class MvcReferenceFlowE2E {

    @LocalServerPort
    private int port;

    @Value("${uptrail.sample-data.password}")
    private String password;

    @Autowired
    private BusinessClock clock;

    @Autowired
    private HolidayCalendarService holidays;

    @DynamicPropertySource
    static void mysql(DynamicPropertyRegistry registry) {
        MySqlTestDatabase database = MySqlTestDatabase.get();
        registry.add("spring.datasource.url", database::jdbcUrl);
        registry.add("spring.datasource.username", database::username);
        registry.add("spring.datasource.password", database::password);
    }

    @Test
    void employeeAndManagerCompleteTheFlowWithoutAnyApiRequests() throws Exception {
        List<String> apiRequests = new ArrayList<>();
        List<String> pageErrors = new ArrayList<>();
        Path screens = Path.of("target", "e2e");
        Files.createDirectories(screens);
        LocalDate day = clock.today().plusDays(45);
        while (day.getDayOfWeek() == DayOfWeek.SATURDAY || day.getDayOfWeek() == DayOfWeek.SUNDAY
                || !holidays.holidaysBetween(day, day).isEmpty()) {
            day = day.plusDays(1);
        }

        try (Playwright playwright = Playwright.create();
                Browser browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true));
                BrowserContext employeeContext = browser.newContext();
                BrowserContext managerContext = browser.newContext()) {
            for (BrowserContext context : List.of(employeeContext, managerContext)) {
                context.onRequest(request -> {
                    if (request.url().contains("/api/")) {
                        apiRequests.add(request.url());
                    }
                });
                context.onPage(page -> page.onPageError(pageErrors::add));
            }
            Page employee = employeeContext.newPage();
            signIn(employee, "hannah");
            employee.navigate(url("/staff/applications/new"));
            assertThat(employee.locator("#catalogue-search").count()).isZero();
            assertThat(employee.locator("#eligibility").innerText()).contains("checked when you submit");
            employee.locator("#category").selectOption("INTERNAL");
            employee.locator("#courseTitle").fill("MVC browser reference course");
            employee.locator("#startDate").fill(day.toString());
            employee.locator("#endDate").fill(day.toString());
            employee.locator("#startSession").selectOption("AM");
            employee.locator("#endSession").selectOption("AM");
            employee.locator("#justification").fill("Practice the course application and approval flow.");
            employee.screenshot(new Page.ScreenshotOptions().setPath(screens.resolve("mvc-reference-form.png"))
                    .setFullPage(true));
            employee.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Submit application")).click();
            employee.waitForURL(Pattern.compile(".*/staff/applications/\\d+"));
            String id = employee.url().substring(employee.url().lastIndexOf('/') + 1);

            Page manager = managerContext.newPage();
            signIn(manager, "daniel");
            assertThat(manager.url()).endsWith("/manager/home");
            assertThat(manager.getByRole(AriaRole.LINK, new Page.GetByRoleOptions()
                    .setName("My Staff Workspace").setExact(true)).isVisible()).isTrue();
            manager.screenshot(new Page.ScreenshotOptions().setPath(screens.resolve("mvc-reference-manager-home.png"))
                    .setFullPage(true));
            manager.navigate(url("/manager/applications/" + id));
            manager.onDialog(dialog -> dialog.accept());
            manager.locator("#decision-approve").check();
            manager.locator("#reason").fill("Useful for our team's MVC implementation.");
            manager.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Record decision")).click();
            manager.waitForURL(Pattern.compile(".*/manager/approvals"));
            employee.reload();
            assertThat(employee.locator("body").innerText()).contains("Useful for our team's MVC implementation.");

            manager.navigate(url("/staff/home"));
            assertThat(manager.locator("body").innerText()).contains("Daniel");
            manager.navigate(url("/calendar?month=" + day.toString().substring(0, 7) + "&category=INTERNAL"));
            assertThat(manager.locator("#calendar").innerText()).contains("MVC browser reference course");
            assertThat(manager.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Show")).isVisible())
                    .isTrue();
            manager.screenshot(new Page.ScreenshotOptions().setPath(screens.resolve("mvc-reference-calendar.png"))
                    .setFullPage(true));
            manager.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName("Next").setExact(true)).click();
            assertThat(manager.url()).contains("/calendar?month=");
            assertThat(apiRequests).as("REST requests in MVC reference mode").isEmpty();
            assertThat(pageErrors).as("JavaScript errors").isEmpty();
        }
    }

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    private void signIn(Page page, String username) {
        page.navigate(url("/employee/login"));
        page.locator("#username").fill(username);
        page.locator("#password").fill(password);
        page.locator("button[type=submit]").click();
        page.waitForURL(Pattern.compile(".*/(staff|manager)/.*"));
    }
}
