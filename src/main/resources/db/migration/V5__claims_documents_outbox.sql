-- Course fee claims, their controlled documents, and the transactional email outbox.

CREATE TABLE course_claim (
  id                      BIGINT        NOT NULL AUTO_INCREMENT,
  application_id          BIGINT        NOT NULL,
  revision                INT           NOT NULL,
  amount                  DECIMAL(12,2) NOT NULL,
  paid_by_employee        BOOLEAN       NOT NULL,
  approver_id             BIGINT        NOT NULL,
  status                  VARCHAR(20)   NOT NULL,
  submitted_at            DATETIME(6)   NOT NULL,
  reviewed_by             BIGINT        NULL,
  review_comment          VARCHAR(2000) NULL,
  reviewed_at             DATETIME(6)   NULL,
  reimbursed_by           BIGINT        NULL,
  reimbursed_at           DATETIME(6)   NULL,
  reimbursement_reference VARCHAR(80)   NULL,
  version                 BIGINT        NOT NULL,
  CONSTRAINT pk_course_claim PRIMARY KEY (id),
  CONSTRAINT uk_claim_application UNIQUE (application_id),
  CONSTRAINT uk_claim_reimbursement_reference UNIQUE (reimbursement_reference),
  CONSTRAINT fk_claim_application FOREIGN KEY (application_id) REFERENCES course_application (id),
  CONSTRAINT fk_claim_approver FOREIGN KEY (approver_id) REFERENCES employee (id),
  CONSTRAINT fk_claim_reviewer FOREIGN KEY (reviewed_by) REFERENCES employee (id),
  CONSTRAINT fk_claim_reimbursed_by FOREIGN KEY (reimbursed_by) REFERENCES employee (id),
  CONSTRAINT ck_claim_amount CHECK (amount > 0),
  CONSTRAINT ck_claim_revision CHECK (revision >= 1),
  CONSTRAINT ck_claim_status CHECK (status IN ('SUBMITTED', 'APPROVED', 'REJECTED', 'REIMBURSED'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE INDEX ix_claim_approver ON course_claim (approver_id, status, submitted_at);

ALTER TABLE training_ledger
  ADD CONSTRAINT fk_ledger_claim FOREIGN KEY (claim_id) REFERENCES course_claim (id);

CREATE TABLE claim_document (
  id                    BIGINT       NOT NULL AUTO_INCREMENT,
  claim_id              BIGINT       NOT NULL,
  claim_revision        INT          NOT NULL,
  document_type         VARCHAR(24)  NOT NULL,
  storage_key           VARCHAR(200) NOT NULL,
  original_name         VARCHAR(200) NOT NULL,
  detected_content_type VARCHAR(100) NOT NULL,
  size_bytes            BIGINT       NOT NULL,
  sha256                VARCHAR(64)  NOT NULL,
  uploaded_by           BIGINT       NOT NULL,
  uploaded_at           DATETIME(6)  NOT NULL,
  CONSTRAINT pk_claim_document PRIMARY KEY (id),
  CONSTRAINT uk_claim_document_revision_type UNIQUE (claim_id, claim_revision, document_type),
  CONSTRAINT uk_claim_document_storage_key UNIQUE (storage_key),
  CONSTRAINT fk_claim_document_claim FOREIGN KEY (claim_id) REFERENCES course_claim (id),
  CONSTRAINT fk_claim_document_uploader FOREIGN KEY (uploaded_by) REFERENCES employee (id),
  CONSTRAINT ck_claim_document_type CHECK (document_type IN ('RECEIPT', 'COMPLETION_CERTIFICATE')),
  CONSTRAINT ck_claim_document_size CHECK (size_bytes > 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE email_outbox (
  id                    BIGINT        NOT NULL AUTO_INCREMENT,
  event_id              BIGINT        NOT NULL,
  recipient_employee_id BIGINT        NOT NULL,
  recipient_email       VARCHAR(254)  NOT NULL,
  template_code         VARCHAR(40)   NOT NULL,
  payload_json          JSON          NOT NULL,
  status                VARCHAR(16)   NOT NULL,
  attempts              INT           NOT NULL DEFAULT 0,
  next_attempt_at       DATETIME(6)   NOT NULL,
  lease_until           DATETIME(6)   NULL,
  sent_at               DATETIME(6)   NULL,
  last_error            VARCHAR(1000) NULL,
  created_at            DATETIME(6)   NOT NULL,
  CONSTRAINT pk_email_outbox PRIMARY KEY (id),
  CONSTRAINT uk_outbox_event_recipient_template UNIQUE (event_id, recipient_employee_id, template_code),
  CONSTRAINT fk_outbox_event FOREIGN KEY (event_id) REFERENCES audit_event (id),
  CONSTRAINT fk_outbox_recipient FOREIGN KEY (recipient_employee_id) REFERENCES employee (id),
  CONSTRAINT ck_outbox_status CHECK (status IN ('PENDING', 'SENDING', 'SENT', 'FAILED'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE INDEX ix_outbox_status_next ON email_outbox (status, next_attempt_at, id);
