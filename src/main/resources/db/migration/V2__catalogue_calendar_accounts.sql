-- Course categories, providers, catalogue, public holiday calendar and annual training accounts.

CREATE TABLE course_category (
  code         VARCHAR(20)  NOT NULL,
  display_name VARCHAR(80)  NOT NULL,
  description  VARCHAR(400) NULL,
  version      BIGINT       NOT NULL,
  CONSTRAINT pk_course_category PRIMARY KEY (code),
  CONSTRAINT ck_course_category_code CHECK (code IN ('INTERNAL', 'EXTERNAL', 'CERTIFICATION'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- The three categories are fixed; administrators may only edit their name and description.
INSERT INTO course_category (code, display_name, description, version) VALUES
  ('INTERNAL', 'Internal Training', 'Conducted in-house. No course fee. Half-day sessions allowed.', 0),
  ('EXTERNAL', 'External Course', 'Fee-paying course run by an external provider. Full days only.', 0),
  ('CERTIFICATION', 'Professional Certification', 'Fee-paying professional certification. Full days only.', 0);

CREATE TABLE training_provider (
  id      BIGINT       NOT NULL AUTO_INCREMENT,
  name    VARCHAR(160) NOT NULL,
  active  BOOLEAN      NOT NULL,
  version BIGINT       NOT NULL,
  CONSTRAINT pk_training_provider PRIMARY KEY (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE INDEX ix_training_provider_name ON training_provider (name, id);

CREATE TABLE course_catalogue (
  id            BIGINT        NOT NULL AUTO_INCREMENT,
  category_code VARCHAR(20)   NOT NULL,
  provider_id   BIGINT        NULL,
  title         VARCHAR(200)  NOT NULL,
  default_fee   DECIMAL(12,2) NOT NULL,
  description   VARCHAR(1000) NULL,
  active        BOOLEAN       NOT NULL,
  version       BIGINT        NOT NULL,
  CONSTRAINT pk_course_catalogue PRIMARY KEY (id),
  CONSTRAINT fk_catalogue_category FOREIGN KEY (category_code) REFERENCES course_category (code),
  CONSTRAINT fk_catalogue_provider FOREIGN KEY (provider_id) REFERENCES training_provider (id),
  CONSTRAINT ck_catalogue_fee CHECK (default_fee >= 0),
  CONSTRAINT ck_catalogue_internal_free CHECK (category_code <> 'INTERNAL' OR default_fee = 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE INDEX ix_catalogue_category ON course_catalogue (category_code, active, title);

-- Supplement to the 16-table design baseline: preparation and confirmation state of each year's holiday calendar.
CREATE TABLE training_calendar_year (
  calendar_year           SMALLINT     NOT NULL,
  status                  VARCHAR(16)  NOT NULL,
  source_note             VARCHAR(400) NOT NULL,
  confirmed_by            BIGINT       NULL,
  confirmed_at            DATETIME(6)  NULL,
  confirmed_holiday_count INT          NULL,
  version                 BIGINT       NOT NULL,
  created_at              DATETIME(6)  NOT NULL,
  updated_at              DATETIME(6)  NOT NULL,
  CONSTRAINT pk_training_calendar_year PRIMARY KEY (calendar_year),
  CONSTRAINT fk_calendar_year_confirmed_by FOREIGN KEY (confirmed_by) REFERENCES employee (id),
  CONSTRAINT ck_calendar_year_status CHECK (status IN ('DRAFT', 'CONFIRMED')),
  CONSTRAINT ck_calendar_year_range CHECK (calendar_year BETWEEN 2000 AND 2100)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE public_holiday (
  holiday_date  DATE         NOT NULL,
  name          VARCHAR(120) NOT NULL,
  source_note   VARCHAR(400) NOT NULL,
  updated_at    DATETIME(6)  NOT NULL,
  -- Derived year so the database itself refuses holidays for a year that has no calendar row.
  calendar_year SMALLINT AS (YEAR(holiday_date)) STORED NOT NULL,
  CONSTRAINT pk_public_holiday PRIMARY KEY (holiday_date),
  CONSTRAINT fk_holiday_calendar_year FOREIGN KEY (calendar_year) REFERENCES training_calendar_year (calendar_year)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE training_account (
  id             BIGINT        NOT NULL AUTO_INCREMENT,
  employee_id    BIGINT        NOT NULL,
  calendar_year  SMALLINT      NOT NULL,
  entitled_units INT           NOT NULL,
  budget_amount  DECIMAL(12,2) NOT NULL,
  version        BIGINT        NOT NULL,
  updated_at     DATETIME(6)   NOT NULL,
  CONSTRAINT pk_training_account PRIMARY KEY (id),
  CONSTRAINT uk_training_account_year UNIQUE (employee_id, calendar_year),
  CONSTRAINT fk_training_account_employee FOREIGN KEY (employee_id) REFERENCES employee (id),
  CONSTRAINT ck_training_account_units CHECK (entitled_units >= 0),
  CONSTRAINT ck_training_account_budget CHECK (budget_amount >= 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
