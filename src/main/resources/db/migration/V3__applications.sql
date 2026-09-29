-- Course applications and their per-working-day allocation snapshot.

CREATE TABLE course_application (
  id                  BIGINT        NOT NULL AUTO_INCREMENT,
  reference_no        VARCHAR(30)   NOT NULL,
  employee_id         BIGINT        NOT NULL,
  approver_id         BIGINT        NOT NULL,
  catalogue_id        BIGINT        NULL,
  category_code       VARCHAR(20)   NOT NULL,
  course_title        VARCHAR(200)  NOT NULL,
  provider_name       VARCHAR(160)  NOT NULL,
  start_date          DATE          NOT NULL,
  end_date            DATE          NOT NULL,
  start_session       VARCHAR(2)    NOT NULL,
  end_session         VARCHAR(2)    NOT NULL,
  course_fee          DECIMAL(12,2) NOT NULL,
  justification       VARCHAR(2000) NOT NULL,
  work_dissemination  VARCHAR(2000) NULL,
  status              VARCHAR(20)   NOT NULL,
  submitted_at        DATETIME(6)   NOT NULL,
  updated_at          DATETIME(6)   NOT NULL,
  reviewed_by         BIGINT        NULL,
  reviewed_at         DATETIME(6)   NULL,
  review_comment      VARCHAR(2000) NULL,
  completion_comment  VARCHAR(2000) NULL,
  completed_at        DATETIME(6)   NULL,
  cancel_reason       VARCHAR(2000) NULL,
  version             BIGINT        NOT NULL,
  client_request_id   VARCHAR(36)   NOT NULL,
  create_request_hash VARCHAR(64)   NOT NULL,
  CONSTRAINT pk_course_application PRIMARY KEY (id),
  CONSTRAINT uk_application_reference UNIQUE (reference_no),
  CONSTRAINT uk_application_request UNIQUE (employee_id, client_request_id),
  CONSTRAINT fk_application_employee FOREIGN KEY (employee_id) REFERENCES employee (id),
  CONSTRAINT fk_application_approver FOREIGN KEY (approver_id) REFERENCES employee (id),
  CONSTRAINT fk_application_reviewer FOREIGN KEY (reviewed_by) REFERENCES employee (id),
  CONSTRAINT fk_application_catalogue FOREIGN KEY (catalogue_id) REFERENCES course_catalogue (id),
  CONSTRAINT fk_application_category FOREIGN KEY (category_code) REFERENCES course_category (code),
  CONSTRAINT ck_application_dates CHECK (start_date <= end_date),
  CONSTRAINT ck_application_start_session CHECK (start_session IN ('AM', 'PM')),
  CONSTRAINT ck_application_end_session CHECK (end_session IN ('AM', 'PM')),
  CONSTRAINT ck_application_status CHECK (status IN ('APPLIED', 'UPDATED', 'APPROVED', 'REJECTED', 'DELETED', 'CANCELLED', 'COMPLETED')),
  CONSTRAINT ck_application_fee CHECK (
    (category_code = 'INTERNAL' AND course_fee = 0) OR (category_code <> 'INTERNAL' AND course_fee > 0)),
  CONSTRAINT ck_application_decision_reason CHECK (
    status NOT IN ('APPROVED', 'REJECTED', 'CANCELLED', 'COMPLETED') OR (review_comment IS NOT NULL AND reviewed_by IS NOT NULL))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE INDEX ix_application_employee_period ON course_application (employee_id, status, start_date, end_date);
CREATE INDEX ix_application_approver ON course_application (approver_id, status, employee_id);
CREATE INDEX ix_application_status_period ON course_application (status, start_date, end_date);

CREATE TABLE application_day (
  application_id BIGINT     NOT NULL,
  training_date  DATE       NOT NULL,
  units          TINYINT    NOT NULL,
  session_code   VARCHAR(4) NOT NULL,
  CONSTRAINT pk_application_day PRIMARY KEY (application_id, training_date),
  CONSTRAINT fk_application_day_application FOREIGN KEY (application_id) REFERENCES course_application (id),
  CONSTRAINT ck_application_day_units CHECK (
    (units = 1 AND session_code IN ('AM', 'PM')) OR (units = 2 AND session_code = 'BOTH'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE INDEX ix_application_day_date ON application_day (training_date, application_id);
