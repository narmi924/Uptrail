-- Preserve each person's ID and every existing FK/history entry while unifying login and profile.
RENAME TABLE employee TO users;
ALTER TABLE users
  RENAME COLUMN staff_no TO staff_id,
  RENAME COLUMN full_name TO name;
ALTER TABLE users
  ADD COLUMN user_type VARCHAR(16) NOT NULL DEFAULT 'STAFF',
  ADD COLUMN user_name VARCHAR(80) NULL,
  ADD COLUMN password_hash VARCHAR(255) NULL,
  ADD COLUMN enabled BOOLEAN NOT NULL DEFAULT FALSE,
  ADD CONSTRAINT uk_users_user_name UNIQUE (user_name),
  ADD CONSTRAINT ck_users_type CHECK (user_type IN ('STAFF', 'MANAGER', 'ADMIN'));
UPDATE users u LEFT JOIN user_account a ON a.employee_id = u.id
SET u.user_name = a.username, u.password_hash = a.password_hash,
    u.enabled = COALESCE(a.enabled, FALSE),
    u.user_type = CASE
      WHEN EXISTS(SELECT 1 FROM user_role r WHERE r.user_id = a.id AND r.role_code = 'MANAGER') THEN 'MANAGER'
      WHEN EXISTS(SELECT 1 FROM user_role r WHERE r.user_id = a.id AND r.role_code = 'ADMIN') THEN 'ADMIN'
      ELSE 'STAFF' END;
CREATE TABLE user_roles (
  user_id BIGINT NOT NULL,
  role_code VARCHAR(16) NOT NULL,
  PRIMARY KEY (user_id, role_code),
  CONSTRAINT fk_user_roles_users FOREIGN KEY (user_id) REFERENCES users(id),
  CONSTRAINT ck_user_roles_code CHECK (role_code IN ('STAFF', 'MANAGER', 'ADMIN'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
INSERT INTO user_roles(user_id, role_code)
SELECT a.employee_id, CASE WHEN r.role_code = 'EMPLOYEE' THEN 'STAFF' ELSE r.role_code END
FROM user_role r JOIN user_account a ON a.id = r.user_id;
-- Every Manager retains Staff capabilities, including old accounts with only MANAGER permission.
INSERT IGNORE INTO user_roles(user_id, role_code) SELECT id, 'STAFF' FROM users WHERE user_type = 'MANAGER';
-- Staff records without a login still retain their business identity.
INSERT IGNORE INTO user_roles(user_id, role_code) SELECT id, 'STAFF' FROM users WHERE user_name IS NULL;
DROP TABLE user_role;
DROP TABLE user_account;

-- Team-facing business names; MySQL preserves their foreign-key relationships.
RENAME TABLE training_account TO training_entitlement, course_claim TO course_fee_application,
  approval_assignment TO approval_hierarchy, public_holiday TO excluded_days;
-- Rename and recreate the applicant FK together: CHECK constraints require COPY on this table.
ALTER TABLE course_application
  DROP FOREIGN KEY fk_application_employee,
  RENAME COLUMN employee_id TO applicant_id,
  ADD CONSTRAINT fk_application_applicant FOREIGN KEY (applicant_id) REFERENCES users(id);
ALTER TABLE course_application
  DROP CHECK ck_application_decision_reason,
  RENAME COLUMN review_comment TO decision_reason,
  ADD CONSTRAINT ck_application_reason CHECK (
    status NOT IN ('APPROVED', 'REJECTED', 'CANCELLED', 'COMPLETED')
    OR (decision_reason IS NOT NULL AND reviewed_by IS NOT NULL));
ALTER TABLE course_fee_application RENAME COLUMN review_comment TO decision_reason;
