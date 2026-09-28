-- Identity and organisation: people, login accounts, roles, direct approval routing.
-- Foreign keys use the InnoDB default (RESTRICT): referenced rows cannot be physically deleted.

CREATE TABLE employee (
  id               BIGINT       NOT NULL AUTO_INCREMENT,
  staff_no         VARCHAR(30)  NOT NULL,
  full_name        VARCHAR(120) NOT NULL,
  email            VARCHAR(254) NOT NULL,
  department       VARCHAR(100) NOT NULL,
  designation_code VARCHAR(40)  NOT NULL,
  active           BOOLEAN      NOT NULL,
  version          BIGINT       NOT NULL,
  created_at       DATETIME(6)  NOT NULL,
  updated_at       DATETIME(6)  NOT NULL,
  CONSTRAINT pk_employee PRIMARY KEY (id),
  CONSTRAINT uk_employee_staff_no UNIQUE (staff_no)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE INDEX ix_employee_name ON employee (full_name, id);
CREATE INDEX ix_employee_department ON employee (department, active);

CREATE TABLE user_account (
  id            BIGINT       NOT NULL AUTO_INCREMENT,
  employee_id   BIGINT       NOT NULL,
  username      VARCHAR(80)  NOT NULL,
  password_hash VARCHAR(255) NOT NULL,
  enabled       BOOLEAN      NOT NULL,
  version       BIGINT       NOT NULL,
  created_at    DATETIME(6)  NOT NULL,
  updated_at    DATETIME(6)  NOT NULL,
  CONSTRAINT pk_user_account PRIMARY KEY (id),
  CONSTRAINT uk_user_account_employee UNIQUE (employee_id),
  CONSTRAINT uk_user_account_username UNIQUE (username),
  CONSTRAINT fk_user_account_employee FOREIGN KEY (employee_id) REFERENCES employee (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE user_role (
  user_id   BIGINT      NOT NULL,
  role_code VARCHAR(16) NOT NULL,
  CONSTRAINT pk_user_role PRIMARY KEY (user_id, role_code),
  CONSTRAINT fk_user_role_account FOREIGN KEY (user_id) REFERENCES user_account (id),
  CONSTRAINT ck_user_role_code CHECK (role_code IN ('EMPLOYEE', 'MANAGER', 'ADMIN'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE approval_assignment (
  employee_id BIGINT      NOT NULL,
  manager_id  BIGINT      NOT NULL,
  version     BIGINT      NOT NULL,
  updated_at  DATETIME(6) NOT NULL,
  CONSTRAINT pk_approval_assignment PRIMARY KEY (employee_id),
  CONSTRAINT fk_assignment_employee FOREIGN KEY (employee_id) REFERENCES employee (id),
  CONSTRAINT fk_assignment_manager FOREIGN KEY (manager_id) REFERENCES employee (id),
  CONSTRAINT ck_assignment_not_self CHECK (employee_id <> manager_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE INDEX ix_assignment_manager ON approval_assignment (manager_id, employee_id);
