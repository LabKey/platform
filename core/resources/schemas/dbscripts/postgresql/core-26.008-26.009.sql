-- Make room for sha256: prefix plus 64-character hash
ALTER TABLE core.Logins ALTER COLUMN Verification TYPE VARCHAR(100);

-- Must match LoginManager.hashVerification(). Legacy setup and reset links (no timeout) expire seven days after upgrade.
UPDATE core.Logins
SET Verification = 'sha256:' || encode(sha256(convert_to(Verification, 'UTF8')), 'hex'),
    VerificationTimeout = COALESCE(VerificationTimeout, LOCALTIMESTAMP + INTERVAL '7 days')
WHERE Verification IS NOT NULL;
