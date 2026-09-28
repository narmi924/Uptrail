-- Append-only business event trail and the annual training ledger derived from those events.

CREATE TABLE audit_event (
  id                BIGINT        NOT NULL AUTO_INCREMENT,
  aggregate_type    VARCHAR(32)   NOT NULL,
  aggregate_key     VARCHAR(64)   NOT NULL,
  event_type        VARCHAR(50)   NOT NULL,
  actor_employee_id BIGINT        NULL,
  from_state        VARCHAR(24)   NULL,
  to_state          VARCHAR(24)   NULL,
  reason            VARCHAR(2000) NULL,
  snapshot_json     JSON          NULL,
  correlation_id    VARCHAR(36)   NOT NULL,
  created_at        DATETIME(6)   NOT NULL,
  CONSTRAINT pk_audit_event PRIMARY KEY (id),
  CONSTRAINT fk_audit_actor FOREIGN KEY (actor_employee_id) REFERENCES employee (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE INDEX ix_audit_aggregate ON audit_event (aggregate_type, aggregate_key, created_at, id);
CREATE INDEX ix_audit_correlation ON audit_event (correlation_id);

CREATE TABLE training_ledger (
  id                      BIGINT        NOT NULL AUTO_INCREMENT,
  account_id              BIGINT        NOT NULL,
  event_id                BIGINT        NOT NULL,
  application_id          BIGINT        NULL,
  claim_id                BIGINT        NULL,
  entry_type              VARCHAR(40)   NOT NULL,
  reserved_units_delta    INT           NOT NULL DEFAULT 0,
  committed_units_delta   INT           NOT NULL DEFAULT 0,
  reserved_amount_delta   DECIMAL(12,2) NOT NULL DEFAULT 0,
  committed_amount_delta  DECIMAL(12,2) NOT NULL DEFAULT 0,
  reimbursed_amount_delta DECIMAL(12,2) NOT NULL DEFAULT 0,
  created_at              DATETIME(6)   NOT NULL,
  CONSTRAINT pk_training_ledger PRIMARY KEY (id),
  -- One net change per business event and annual account: a retried or duplicated event cannot post twice.
  CONSTRAINT uk_ledger_event_account UNIQUE (event_id, account_id),
  CONSTRAINT fk_ledger_account FOREIGN KEY (account_id) REFERENCES training_account (id),
  CONSTRAINT fk_ledger_event FOREIGN KEY (event_id) REFERENCES audit_event (id),
  CONSTRAINT fk_ledger_application FOREIGN KEY (application_id) REFERENCES course_application (id),
  CONSTRAINT ck_ledger_entry_type CHECK (entry_type IN ('RESERVE', 'UPDATE_RESERVATION', 'COMMIT', 'RELEASE', 'REIMBURSE'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE INDEX ix_ledger_account ON training_ledger (account_id, created_at, id);
CREATE INDEX ix_ledger_application ON training_ledger (application_id);
CREATE INDEX ix_ledger_claim ON training_ledger (claim_id);
