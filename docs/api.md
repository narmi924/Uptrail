# Uptrail — HTTP interfaces

The JSON API requires the rest-enhanced profile; the default application uses MVC. This document describes the optional JSON API, the CSV exports and the document downloads. Everything else is server-rendered HTML with ordinary form posts.

## Conventions

- **Authentication.** All endpoints use the signed-in session of the staff workspace (`/employee/login`). The administration workspace (`/admin/login`) can call the calendar API only. There are no API keys or tokens.
- **CSRF.** Every `POST` must carry the CSRF token. Pages expose it in `<meta name="_csrf">` and `<meta name="_csrf_header">`; `static/js/uptrail.js` adds the header to every `fetch` it makes.
- **Errors.** API errors are JSON, never a login page or HTML:

  ```json
  {
    "status": 422,
    "code": "VALIDATION_FAILED",
    "message": "Choose a start date.",
    "fieldErrors": { "startDate": "Choose a start date." },
    "correlationId": "5469ca93-ccae-4051-9f20-a947f3b6848b"
  }
  ```

  | Status | Codes | Meaning |
  | --- | --- | --- |
  | 400 | `MALFORMED_REQUEST` | Unreadable JSON or a parameter of the wrong type (for example `month=2026-13`) |
  | 401 | `AUTHENTICATION_REQUIRED` | No session, or the session expired |
  | 403 | `FORBIDDEN`, `CSRF_INVALID` | Wrong role or workspace, or a missing CSRF token |
  | 404 | `RESOURCE_NOT_FOUND` | Unknown record, or a record outside the caller's scope |
  | 409 | `STALE_VERSION`, `ALREADY_PROCESSED`, `CONFLICT` | The record changed or was already decided |
  | 422 | `VALIDATION_FAILED` and business rule codes | A rule refused the request |
  | 503 | `TEMPORARY_CONTENTION` | Another change to the same records is in progress; retry |

  The `correlationId` also appears in the server log lines of the same request.
- **Dates** are ISO `yyyy-MM-dd`; months are `yyyy-MM`. **Money** is a decimal number with two places, in SGD. **Training time** is counted in half-day units internally; the API also returns readable day texts such as `"1.5 days"`.

## `POST /api/v1/applications/preview`

Checks a draft application without saving anything. The application form calls it while the employee types; the final submission repeats every check on the server.

Role: `EMPLOYEE` (staff workspace).

Request body (all fields optional; missing fields become problems in the response):

```json
{
  "applicationId": null,
  "category": "EXTERNAL",
  "catalogueId": 5,
  "courseTitle": "Cloud Architecture Foundations",
  "providerName": "Cloud Guild Academy",
  "startDate": "2026-10-21",
  "endDate": "2026-10-22",
  "startSession": "AM",
  "endSession": "PM",
  "courseFee": 850.00,
  "justification": "Needed for the platform migration.",
  "workDissemination": null
}
```

`applicationId` is set when an existing application is being edited; its own reservation and dates are then left out of the balance and overlap checks. `category` is `INTERNAL`, `EXTERNAL` or `CERTIFICATION`; sessions are `AM` or `PM`.

Response `200`:

```json
{
  "eligible": true,
  "trainingUnits": 4,
  "trainingDays": "2 days",
  "includedDates": [
    { "date": "2026-10-21", "session": "BOTH", "units": 2 },
    { "date": "2026-10-22", "session": "BOTH", "units": 2 }
  ],
  "excludedDates": [],
  "annualAllocations": [
    {
      "year": 2026, "configured": true,
      "unitsRequested": 4, "unitsAvailableBefore": 18, "unitsAvailableAfter": 14,
      "daysRequested": "2 days", "daysAvailableBefore": "9 days", "daysAvailableAfter": "7 days",
      "feeRequested": 850.00, "budgetAvailableBefore": 1600.00, "budgetAvailableAfter": 750.00
    }
  ],
  "conflicts": [],
  "approverName": "Daniel Wong",
  "problems": []
}
```

When `eligible` is `false`, `problems` lists each reason with a rule code (for example `PERIOD_OVERLAP`, `INSUFFICIENT_BUDGET`, `NON_WORKING_BOUNDARY`, `HOLIDAY_CALENDAR_UNCONFIRMED`), the form field it concerns and a message. A course spanning two years gets one allocation per year; the fee is charged to the year in which the course starts.

## `GET /api/v1/catalogue?q=&category=`

Searches the active catalogue courses offered as shortcuts on the application form.

Role: `EMPLOYEE` (staff workspace). Parameters: `q` (part of the title, optional), `category` (optional).

```json
[
  {
    "id": 5,
    "category": "EXTERNAL",
    "title": "Cloud Architecture Foundations",
    "providerName": "Cloud Guild Academy",
    "defaultFee": 850.00,
    "description": "Designing resilient cloud systems."
  }
]
```

## `GET /api/v1/calendar?month=&category=`

The training calendar of one month: approved and completed courses of all staff, and the public holidays of that month. Cancelled, pending, rejected and deleted applications are never included. Only names, titles, categories and dates are returned.

Roles: any signed-in user, in either workspace. Parameters: `month` (`yyyy-MM`, default the current month), `category` (optional).

```json
{
  "month": "2026-10",
  "previousMonth": "2026-09",
  "nextMonth": "2026-11",
  "entries": [
    {
      "employeeName": "Kelvin Ng",
      "courseTitle": "Effective Code Reviews",
      "category": "INTERNAL",
      "categoryName": "Internal Training",
      "startDate": "2026-10-08", "startSession": "AM",
      "endDate": "2026-10-08", "endSession": "PM"
    }
  ],
  "holidays": []
}
```

## CSV exports

| Endpoint | Role | Parameters | Content |
| --- | --- | --- | --- |
| `GET /manager/reports/training.csv` | `MANAGER` | `from`, `to` (default: the current year), `category`, `employeeId` | Approved and completed courses of the manager's current direct reports that overlap the period; training days count only the days inside the period |
| `GET /manager/reports/budget.csv` | `MANAGER` | `year` (previous, current or next), `employeeId` | Per direct report: entitled, pending, approved, available and completed days; budget, pending, approved and available amounts; claims submitted, approved and reimbursed |

Both exports reuse the queries of the report page, so the file contains exactly the rows on the page. Files are UTF-8 with a byte order mark, comma-separated with CRLF line ends and RFC 4180 quoting. Text cells that a spreadsheet would read as a formula (starting with `=`, `+`, `-`, `@`, tab or carriage return) are prefixed with an apostrophe. An `employeeId` outside the manager's team gives `404`.

## Document downloads

| Endpoint | Who may download |
| --- | --- |
| `GET /claims/documents/{id}` | The claimant, and the manager the claim is assigned to or who decided it |
| `GET /admin/claims/documents/{id}` | Administrators, once the claim is approved or reimbursed |

Documents are addressed by their database id only; the file location is never taken from the request. Responses are always `Content-Disposition: attachment` with the detected content type (`application/pdf`, `image/png` or `image/jpeg`), `X-Content-Type-Options: nosniff` and `Cache-Control: no-store, private`. Anyone else gets `404`.
