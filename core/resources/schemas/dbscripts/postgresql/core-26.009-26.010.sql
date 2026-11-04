-- GH Issue 1778: separate password tokens from email-change tokens so neither link can stand in for the other
ALTER TABLE core.Logins RENAME COLUMN Verification TO PasswordVerification;
ALTER TABLE core.Logins RENAME COLUMN VerificationTimeout TO PasswordVerificationTimeout;
ALTER TABLE core.Logins ADD COLUMN EmailVerification VARCHAR(100);
ALTER TABLE core.Logins ADD COLUMN EmailVerificationTimeout TIMESTAMP;

-- A RequestedEmail that differs from the current address marks a pending email change; completed changes leave RequestedEmail equal to it
UPDATE core.Logins l
SET EmailVerification = PasswordVerification,
    EmailVerificationTimeout = PasswordVerificationTimeout,
    PasswordVerification = NULL,
    PasswordVerificationTimeout = NULL
FROM core.Principals p
WHERE p.UserId = l.UserId
  AND l.RequestedEmail IS NOT NULL
  AND l.RequestedEmail <> p.Name
  AND l.PasswordVerification IS NOT NULL;
